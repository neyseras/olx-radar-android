package ua.magerko.olxradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.WatchDao
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.network.OlxSearchService
import ua.magerko.olxradar.service.RadarState
import ua.magerko.olxradar.service.ServiceStatus

class WatchesViewModel(
    application: Application,
    private val watchDao: WatchDao,
    private val findingDao: FindingDao,
    private val olxSearchService: OlxSearchService
) : AndroidViewModel(application) {

    val radarStatus: StateFlow<ServiceStatus> = RadarState.status

    // PERF: RadarState.status больше не входит в combine — этот flow обновлялся
    // чуть ли не каждую секунду и заставлял весь список радаров перерисовываться,
    // хотя Compose-карточки «живые» поля не используют (они в StatusViewModel).
    val watchesUi: StateFlow<List<WatchItemUi>> = combine(
        watchDao.getAllWatchesFlow(),
        findingDao.getFindingsCountsFlow()
    ) { watches, countsList ->
        val countsMap = countsList.associate { it.watchId to it.count }
        watches.map { watch ->
            WatchItemUi(
                watch = watch,
                findingsCount = countsMap[watch.id] ?: 0
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getWatchById(id: Long): Watch? = watchDao.getWatchById(id)

    fun toggleActive(watch: Watch, isActive: Boolean) {
        viewModelScope.launch {
            watchDao.setWatchActive(watch.id, isActive)
        }
    }

    fun deleteWatch(watch: Watch) {
        viewModelScope.launch {
            watchDao.deleteWatchCascade(watch.id)
        }
    }

    fun duplicateWatch(watch: Watch) {
        viewModelScope.launch {
            val duplicatedTitle = getApplication<Application>().getString(R.string.watch_duplicate_title, watch.title)
            val copy = watch.copy(
                id = 0,
                title = duplicatedTitle,
                query = watch.query,
                createdAt = System.currentTimeMillis(),
                initialized = false,
                lastSuccessAt = null,
                lastError = null,
                lastHttpCode = null
            )
            watchDao.insertWatch(copy)
        }
    }

    /**
     * BUGFIX: при смене поискового запроса радар должен пройти «первичный опрос» заново,
     * иначе первый цикл после редактирования молча проглотит все совпадения (initialized = true). */
    fun saveWatch(watch: Watch) {
        viewModelScope.launch {
            if (watch.id == 0L) {
                watchDao.insertWatch(watch)
            } else {
                val existing = watchDao.getWatchById(watch.id)
                val toSave =
                    if (existing != null && existing.query.trim() != watch.query.trim()) watch.copy(initialized = false)
                    else watch
                watchDao.updateWatch(toSave)
            }
        }
    }

    suspend fun checkListings(watch: Watch): Result<List<Listing>> {
        return try {
            val results = olxSearchService.search(watch, limit = 5)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /** Экспорт всех радаров в JSON для файла olx-radar-list.config. */
    suspend fun buildBackupJson(): String {
        val watches = watchDao.getAllWatches()
        return RadarBackup.toJson(watches)
    }

    /** Импорт радаров из JSON. Возвращает Pair(импортировано, пропущено-дубликатов). */
    suspend fun importBackupJson(json: String): Pair<Int, Int> {
        val incoming = RadarBackup.fromJson(json)
        val existing = watchDao.getAllWatches()
        var imported = 0
        var skipped = 0
        val known = existing.map { Triple(it.query.lowercase().trim(), it.priceFrom, it.priceTo) }.toMutableSet()
        for (w in incoming) {
            val key = Triple(w.query.lowercase().trim(), w.priceFrom, w.priceTo)
            if (known.contains(key)) {
                skipped++
                continue
            }
            watchDao.insertWatch(
                w.copy(
                    id = 0,
                    createdAt = System.currentTimeMillis(),
                    initialized = false,
                    lastSuccessAt = null,
                    lastError = null,
                    lastHttpCode = null
                )
            )
            known.add(key)
            imported++
        }
        return Pair(imported, skipped)
    }
}
