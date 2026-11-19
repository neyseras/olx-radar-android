package ua.magerko.olxradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.WatchDao
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch

@OptIn(ExperimentalCoroutinesApi::class)
class FindingsViewModel(
    private val findingDao: FindingDao,
    watchDao: WatchDao
) : ViewModel() {

    private val selectedWatchId = MutableStateFlow<Long?>(null)
    val selectedWatchIdState: StateFlow<Long?> = selectedWatchId.asStateFlow()

    private val isRejectedTab = MutableStateFlow(false)
    val isRejectedTabState: StateFlow<Boolean> = isRejectedTab.asStateFlow()

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
