package ua.magerko.olxradar.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.magerko.olxradar.ai.AiFilterEngine
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.WatchDao
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.util.AppLog

@OptIn(ExperimentalCoroutinesApi::class)
class FindingsViewModel(
    private val findingDao: FindingDao,
    watchDao: WatchDao,
    private val aiFilterEngine: AiFilterEngine
) : ViewModel() {

    private val selectedWatchId = MutableStateFlow<Long?>(null)
    val selectedWatchIdState: StateFlow<Long?> = selectedWatchId.asStateFlow()

    private val isRejectedTab = MutableStateFlow(false)
    val isRejectedTabState: StateFlow<Boolean> = isRejectedTab.asStateFlow()
    /** ID находок, для которых прямо сейчас выполняется повторный ИИ-анализ. */
    /** ID находок, для которых прямо сейчас выполняется повторный ИИ-анализ. */
    private val aiRetryingIdsInternal = MutableStateFlow<Set<Long>>(emptySet())
    val aiRetryingIds: StateFlow<Set<Long>> = aiRetryingIdsInternal.asStateFlow()

    /** Ручной повторный запуск ИИ-анализа для конкретной находки. */
    fun retryAiAnalysis(finding: Finding, watch: Watch) {
        if (aiRetryingIdsInternal.value.contains(finding.id)) return
        aiRetryingIdsInternal.value = aiRetryingIdsInternal.value + finding.id
        viewModelScope.launch {
            try {
                aiFilterEngine.reanalyzeFinding(finding, watch, findingDao)
                AppLog.i("FindingsVM", "Повторный ИИ-анализ '${finding.title.take(50)}' завершён")
            } catch (e: Exception) {
                AppLog.w("FindingsVM", "Повторный ИИ-анализ не удался", e)
            } finally {
                aiRetryingIdsInternal.value = aiRetryingIdsInternal.value - finding.id
            }
        }
    }

    val watches: StateFlow<List<Watch>> = watchDao.getAllWatchesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFindings: StateFlow<List<Finding>> = selectedWatchId
        .flatMapLatest { watchId ->
            if (watchId == null) {
                findingDao.getActiveFindingsFlow()
            } else {
                findingDao.getActiveFindingsByWatchFlow(watchId)
            }
        }
        .map { list -> sortByConditionPulse(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rejectedFindings: StateFlow<List<Finding>> = selectedWatchId
        .flatMapLatest { watchId ->
            if (watchId == null) {
                findingDao.getRejectedFindingsFlow()
            } else {
                findingDao.getRejectedFindingsByWatchFlow(watchId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        isRejectedTab
    ) { watchId, isRejected ->
        watchId to isRejected
    }.flatMapLatest { (watchId, isRejected) ->
        if (isRejected) {
            if (watchId == null) findingDao.getRejectedFindingsFlow() else findingDao.getRejectedFindingsByWatchFlow(watchId)
        } else {
            if (watchId == null) findingDao.getActiveFindingsFlow() else findingDao.getActiveFindingsByWatchFlow(watchId)
        }
    }.map { list -> sortByConditionPulse(list) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backward-compatible flow: active findings
    val findings: StateFlow<List<Finding>> = activeFindings

    val unreadCount: StateFlow<Int> = findingDao.getUnreadCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Реальный расчет рыночной цены на основе медианы всех найденных цен в рамках каждого радара.
     * Требует минимум 3 лотов для формирования достоверного рыночного ориентира.
     */
    val marketPricesByWatch: StateFlow<Map<Long, Int>> = findingDao.getAllValidPricesFlow()
        .map { list ->
            list.groupBy({ it.watchId }, { it.price })
                .mapValues { (_, prices) ->
                    if (prices.size >= 3) {
                        val sorted = prices.sorted()
                        sorted[sorted.size / 2]
                    } else {
                        0
                    }
                }
                .filterValues { it > 0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun filterByWatch(watchId: Long?) {
        selectedWatchId.value = watchId
    }
    /**
     * «Пульс состояния»: если сенсор давал оценки, сортируем находки
     * по совпадению с нужным состоянием (оценка выше — раньше), иначе по времени.
     */
    private fun sortByConditionPulse(list: List<Finding>): List<Finding> {
        if (list.none { it.conditionScore != null }) return list
        return list.sortedWith(
            compareByDescending<Finding> { it.conditionScore ?: -1 }
                .thenByDescending { it.foundAt }
        )
    }

    fun setRejectedTab(isRejected: Boolean) {
        isRejectedTab.value = isRejected
    }

    fun markAsRead(finding: Finding) {
        if (!finding.isRead) {
            viewModelScope.launch {
                findingDao.markAsRead(finding.id, true)
            }
        }
    }

    fun deleteFinding(finding: Finding) {
        viewModelScope.launch {
            findingDao.deleteFinding(finding.id)
        }
    }

    fun restoreFinding(finding: Finding) {
        viewModelScope.launch {
            findingDao.insertFinding(finding)
        }
    }

    fun clearAllFindings() {
        viewModelScope.launch {
            findingDao.clearAllFindings()
        }
    }

    suspend fun getFindingById(id: Long): Finding? = findingDao.getFindingById(id)

    fun clearRejectedFindings() {
        viewModelScope.launch {
            findingDao.clearRejectedFindings()
        }
    }
}
