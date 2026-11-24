package ua.magerko.olxradar.ui

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

enum class FindingsFeedTab {
    MATCHES,
    QUEUED,
    REJECTED
}

enum class FindingsSortMode(val label: String) {
    NEWEST("Свежие"),
    CHEAPEST("Дешевле"),
    EXPENSIVE("Дороже"),
    AI_SCORE("Рейтинг ИИ"),
    CONDITION("По состоянию")
}

@OptIn(ExperimentalCoroutinesApi::class)
class FindingsViewModel(
    private val findingDao: FindingDao,
    watchDao: WatchDao,
    private val aiFilterEngine: AiFilterEngine
) : ViewModel() {

    private val selectedWatchId = MutableStateFlow<Long?>(null)
    val selectedWatchIdState: StateFlow<Long?> = selectedWatchId.asStateFlow()

    private val selectedFeedTab = MutableStateFlow(FindingsFeedTab.MATCHES)
    val selectedFeedTabState: StateFlow<FindingsFeedTab> = selectedFeedTab.asStateFlow()

    private val isRejectedTab = MutableStateFlow(false)
    val isRejectedTabState: StateFlow<Boolean> = isRejectedTab.asStateFlow()

    private val sortMode = MutableStateFlow(FindingsSortMode.NEWEST)
    val sortModeState: StateFlow<FindingsSortMode> = sortMode.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    val searchQueryState: StateFlow<String> = searchQuery.asStateFlow()

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

    val activeFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        sortMode,
        searchQuery
    ) { watchId, sort, query ->
        Triple(watchId, sort, query)
    }.flatMapLatest { (watchId, sort, query) ->
        val baseFlow = if (watchId == null) {
            findingDao.getActiveFindingsFlow()
        } else {
            findingDao.getActiveFindingsByWatchFlow(watchId)
        }
        baseFlow.map { list ->
            filterAndSort(list, query, sort)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rejectedFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        sortMode,
        searchQuery
    ) { watchId, sort, query ->
        Triple(watchId, sort, query)
    }.flatMapLatest { (watchId, sort, query) ->
        val baseFlow = if (watchId == null) {
            findingDao.getRejectedFindingsFlow()
        } else {
            findingDao.getRejectedFindingsByWatchFlow(watchId)
        }
        baseFlow.map { list ->
            filterAndSort(list, query, sort)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        searchQuery
    ) { watchId, query ->
        watchId to query
    }.flatMapLatest { (watchId, query) ->
        val baseFlow = if (watchId == null) {
            findingDao.getQueuedFindingsFlow()
        } else {
            findingDao.getQueuedFindingsByWatchFlow(watchId)
        }
        baseFlow.map { list ->
            if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.cityName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedCount: StateFlow<Int> = findingDao.getQueuedFindingsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentFindings: StateFlow<List<Finding>> = combine(
        selectedFeedTab,
        activeFindings,
        queuedFindings,
        rejectedFindings
    ) { feedTab, active, queued, rejected ->
        when (feedTab) {
            FindingsFeedTab.MATCHES -> active
            FindingsFeedTab.QUEUED -> queued
            FindingsFeedTab.REJECTED -> rejected
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backward-compatible flow: active findings
    val findings: StateFlow<List<Finding>> = activeFindings

    val unreadCount: StateFlow<Int> = findingDao.getUnreadCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Расчет рыночной цены на основе медианы найденных цен по каждому радару.
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

    fun setSortMode(mode: FindingsSortMode) {
        sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /**
     * Поиск и правильная сортировка ленты (по умолчанию - новейшие сверху).
     */
    private fun filterAndSort(
        list: List<Finding>,
        query: String,
        sort: FindingsSortMode
    ): List<Finding> {
        val filtered = if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.title.lowercase().contains(q) ||
                it.cityName.lowercase().contains(q) ||
                (it.aiProduct?.lowercase()?.contains(q) == true)
            }
        }

        return when (sort) {
            FindingsSortMode.NEWEST -> filtered.sortedByDescending { it.foundAt }
            FindingsSortMode.CHEAPEST -> filtered.sortedWith(
                compareBy<Finding> { it.price == null }
                    .thenBy { it.price ?: Int.MAX_VALUE }
            )
            FindingsSortMode.EXPENSIVE -> filtered.sortedWith(
                compareBy<Finding> { it.price == null }
                    .thenByDescending { it.price ?: 0 }
            )
            FindingsSortMode.AI_SCORE -> filtered.sortedWith(
                compareByDescending<Finding> { it.aiScore ?: -1 }
                    .thenByDescending { it.foundAt }
            )
            FindingsSortMode.CONDITION -> filtered.sortedWith(
                compareByDescending<Finding> { it.conditionScore ?: -1 }
                    .thenByDescending { it.foundAt }
            )
        }
    }

    fun setFeedTab(tab: FindingsFeedTab) {
        selectedFeedTab.value = tab
        isRejectedTab.value = (tab == FindingsFeedTab.REJECTED)
    }

    fun setRejectedTab(isRejected: Boolean) {
        selectedFeedTab.value = if (isRejected) FindingsFeedTab.REJECTED else FindingsFeedTab.MATCHES
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
            ua.magerko.olxradar.ai.AiRuntime.dequeue(finding.listingId)
            ua.magerko.olxradar.ai.AiQueueManager.cancel(finding.listingId)
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

    fun clearQueuedFindings() {
        viewModelScope.launch {
            findingDao.clearQueuedFindings()
            ua.magerko.olxradar.ai.AiRuntime.clearQueue()
            ua.magerko.olxradar.ai.AiQueueManager.cancelAll()
        }
    }
}
