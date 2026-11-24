package ua.magerko.olxradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.magerko.olxradar.ai.AiFilterEngine
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.PriceHistoryDao
import ua.magerko.olxradar.data.local.SellerBlacklistDao
import ua.magerko.olxradar.data.local.WatchDao
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.PriceHistory
import ua.magerko.olxradar.data.model.SellerBlacklist
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.util.AppLog

enum class FindingsFeedTab {
    MATCHES,
    QUEUED,
    REJECTED
}

enum class FindingsSortMode(val label: String) {
    NEWEST("Свежие"),
    DEAL_SCORE("Выгодные"),
    CHEAPEST("Дешевле"),
    EXPENSIVE("Дороже"),
    AI_SCORE("Рейтинг ИИ"),
    CONDITION("По состоянию")
}

enum class QuickFeedFilter(val label: String) {
    ALL("Все"),
    FAVORITES("Избранное"),
    DELIVERY("OLX Доставка"),
    HOT_DEALS("Выгодные (70+)")
}

@OptIn(ExperimentalCoroutinesApi::class)
class FindingsViewModel(
    private val findingDao: FindingDao,
    watchDao: WatchDao,
    private val aiFilterEngine: AiFilterEngine,
    private val sellerBlacklistDao: SellerBlacklistDao? = null,
    private val priceHistoryDao: PriceHistoryDao? = null
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

    private val quickFilter = MutableStateFlow(QuickFeedFilter.ALL)
    val quickFilterState: StateFlow<QuickFeedFilter> = quickFilter.asStateFlow()

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

    private val watchesOrNull: StateFlow<List<Watch>?> = watchDao.getAllWatchesFlow()
        .map<List<Watch>, List<Watch>?> { it }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val watchesLoaded: StateFlow<Boolean> = watchesOrNull
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val watches: StateFlow<List<Watch>> = watchesOrNull
        .map { it ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeFindingById(id: Long): Flow<Finding?> =
        findingDao.getFindingByIdFlow(id).flowOn(Dispatchers.IO)

    fun observePriceHistory(listingId: Long): Flow<List<PriceHistory>> =
        (priceHistoryDao?.getHistoryForListingFlow(listingId) ?: flowOf(emptyList()))
            .flowOn(Dispatchers.IO)

    private val blockedSellerKeysFlow: Flow<Set<String>> =
        (sellerBlacklistDao?.getBlockedKeysFlow()?.map { it.toSet() } ?: flowOf(emptySet()))
            .distinctUntilChanged()

    private val feedLimitFlow = AppPreferences.state
        .map { it.feedLimit }
        .distinctUntilChanged()

    val activeFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        sortMode,
        searchQuery,
        quickFilter,
        feedLimitFlow
    ) { watchId, sort, query, qFilter, limit ->
        FilterParams(watchId, sort, query, qFilter, limit)
    }.flatMapLatest { p ->
        val baseFlow = if (p.watchId == null) {
            findingDao.getActiveFindingsFlow(p.limit)
        } else {
            findingDao.getActiveFindingsByWatchFlow(p.watchId, p.limit)
        }
        combine(baseFlow, blockedSellerKeysFlow) { list, blocked ->
            filterAndSort(list, p.query, p.sort, p.quickFilter, blocked)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rejectedFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        sortMode,
        searchQuery,
        quickFilter,
        feedLimitFlow
    ) { watchId, sort, query, qFilter, limit ->
        FilterParams(watchId, sort, query, qFilter, limit)
    }.flatMapLatest { p ->
        val baseFlow = if (p.watchId == null) {
            findingDao.getRejectedFindingsFlow(p.limit)
        } else {
            findingDao.getRejectedFindingsByWatchFlow(p.watchId, p.limit)
        }
        combine(baseFlow, blockedSellerKeysFlow) { list, blocked ->
            filterAndSort(list, p.query, p.sort, p.quickFilter, blocked)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedFindings: StateFlow<List<Finding>> = combine(
        selectedWatchId,
        searchQuery,
        feedLimitFlow
    ) { watchId, query, limit ->
        Triple(watchId, query, limit)
    }.flatMapLatest { (watchId, query, limit) ->
        val baseFlow = if (watchId == null) {
            findingDao.getQueuedFindingsFlow(limit)
        } else {
            findingDao.getQueuedFindingsByWatchFlow(watchId, limit)
        }
        baseFlow.map { list ->
            if (query.isBlank()) list
            else {
                val q = query.trim()
                list.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.cityName.contains(q, ignoreCase = true)
                }
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val findings: StateFlow<List<Finding>> = activeFindings

    val unreadCount: StateFlow<Int> = findingDao.getUnreadCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Расчет рыночной цены на основе медианы найденных цен по каждому радару (с приведением к валюте радара).
     */
    val marketPricesByWatch: StateFlow<Map<Long, Int>> = combine(
        findingDao.getAllValidPricesFlow(),
        watches,
        AppPreferences.state
    ) { list, watchList, appSnap ->
        val watchCurrMap = watchList.associate { it.id to it.priceCurrency }
        list.groupBy { it.watchId }
            .mapValues { (watchId, items) ->
                val targetCurr = watchCurrMap[watchId] ?: "UAH"
                val converted = items.mapNotNull { wp ->
                    val srcCurr = ua.magerko.olxradar.data.network.OlxParser.normalizeCurrency(wp.currency)
                    if (srcCurr.isBlank() || srcCurr == ua.magerko.olxradar.data.network.OlxParser.normalizeCurrency(targetCurr)) {
                        wp.price
                    } else {
                        ua.magerko.olxradar.data.network.OlxParser.convertPriceToTargetCurrency(
                            wp.price, srcCurr, targetCurr, appSnap.usdToUahRate, appSnap.eurToUahRate
                        )
                    }
                }.sorted()
                if (converted.size >= 3) converted[converted.size / 2] else 0
            }
            .filterValues { it > 0 }
    }
        .flowOn(Dispatchers.IO)
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

    fun setQuickFilter(filter: QuickFeedFilter) {
        quickFilter.value = filter
    }

    private fun filterAndSort(
        list: List<Finding>,
        query: String,
        sort: FindingsSortMode,
        qFilter: QuickFeedFilter,
        blockedSellers: Set<String> = emptySet()
    ): List<Finding> {
        val visible = if (blockedSellers.isEmpty()) {
            list
        } else {
            list.filter { f ->
                val key = f.sellerName?.trim()?.lowercase()
                key.isNullOrBlank() || key !in blockedSellers
            }
        }
        val afterQuick = when (qFilter) {
            QuickFeedFilter.ALL -> visible
            QuickFeedFilter.FAVORITES -> visible.filter { it.isFavorite }
            QuickFeedFilter.DELIVERY -> visible.filter { it.isDelivery }
            QuickFeedFilter.HOT_DEALS -> visible.filter { (it.dealScore ?: 0) >= 70 || it.reason == "price_drop" }
        }

        val filtered = if (query.isBlank()) {
            afterQuick
        } else {
            val q = query.trim().lowercase()
            afterQuick.filter {
                it.title.lowercase().contains(q) ||
                    it.cityName.lowercase().contains(q) ||
                    (it.aiProduct?.lowercase()?.contains(q) == true) ||
                    (it.aiModel?.lowercase()?.contains(q) == true)
            }
        }

        return when (sort) {
            FindingsSortMode.NEWEST -> filtered.sortedByDescending { it.foundAt }
            FindingsSortMode.DEAL_SCORE -> filtered.sortedWith(
                compareByDescending<Finding> { it.dealScore ?: -1 }
                    .thenByDescending { it.aiScore ?: -1 }
                    .thenByDescending { it.foundAt }
            )
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

    fun markAllAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            findingDao.markAllAsRead()
        }
    }

    fun blockSeller(sellerDisplayName: String) {
        val clean = sellerDisplayName.trim()
        if (clean.isBlank()) return
        val key = clean.lowercase()
        viewModelScope.launch(Dispatchers.IO) {
            sellerBlacklistDao?.upsert(SellerBlacklist(sellerKey = key, displayName = clean))
        }
    }

    fun toggleFavorite(finding: Finding, onDone: ((Boolean) -> Unit)? = null) {
        val next = !finding.isFavorite
        viewModelScope.launch {
            findingDao.setFavorite(finding.id, next)
            onDone?.invoke(next)
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

    private data class FilterParams(
        val watchId: Long?,
        val sort: FindingsSortMode,
        val query: String,
        val quickFilter: QuickFeedFilter,
        val limit: Int
    )
}
