package ua.magerko.olxradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.backup.PersistentStorageManager
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.WatchDao
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
            PersistentStorageManager.scheduleAutoBackup(getApplication())
        }
    }

    fun deleteWatch(watch: Watch) {
        viewModelScope.launch {
            watchDao.deleteWatchCascade(watch.id)
            PersistentStorageManager.scheduleAutoBackup(getApplication())
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
            PersistentStorageManager.scheduleAutoBackup(getApplication())
        }
    }

    fun saveWatch(watch: Watch) {
        viewModelScope.launch {
            if (watch.id == 0L) {
                val clean = if (ua.magerko.olxradar.data.network.OlxCities.isMultiCity(watch.cityName)) {
                    watch.copy(cityId = null)
                } else watch
                watchDao.insertWatch(clean)
            } else {
                val existing = watchDao.getWatchById(watch.id)
                val queryChanged = existing != null && existing.query.trim() != watch.query.trim()
                val cityChanged = existing != null && existing.cityName?.trim() != watch.cityName?.trim()
                var toSave = watch
                if (queryChanged) toSave = toSave.copy(initialized = false)
                if (cityChanged || ua.magerko.olxradar.data.network.OlxCities.isMultiCity(watch.cityName)) {
                    toSave = toSave.copy(cityId = null)
                }
                watchDao.updateWatch(toSave)
            }
            PersistentStorageManager.scheduleAutoBackup(getApplication())
        }
    }

    suspend fun checkListings(watch: Watch): Result<List<Listing>> = withContext(Dispatchers.IO) {
        try {
            val results = olxSearchService.search(watch, limit = 8)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Экспорт всех радаров в JSON для файла olx-radar-list.config. */
    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val watches = watchDao.getAllWatches()
        RadarBackup.toJson(watches)
    }

    /** Импорт радаров (и при наличии v2 — настроек и находок) из JSON. */
    suspend fun importBackupJson(json: String): Pair<Int, Int> {
        val result = PersistentStorageManager.restoreFromBackupJson(getApplication(), json)
        PersistentStorageManager.scheduleAutoBackup(getApplication())
        return Pair(result.watchesImported, result.watchesSkipped)
    }

    suspend fun discoverSurvivingBackup(): RadarBackup.FullBackupData? {
        val raw = PersistentStorageManager.discoverSurvivingBackupJson(getApplication()) ?: return null
        return runCatching { RadarBackup.parseFullBackup(raw) }.getOrNull()
    }

    suspend fun restoreSurvivingBackup(): PersistentStorageManager.RestoreResult? {
        val raw = PersistentStorageManager.discoverSurvivingBackupJson(getApplication()) ?: return null
        return PersistentStorageManager.restoreFromBackupJson(getApplication(), raw)
    }
}
