package ua.magerko.olxradar.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.magerko.olxradar.data.backup.PersistentStorageManager
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.WatchDao
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.service.RadarState
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.util.AppLog

class StatusViewModel(
    application: Application,
    private val watchDao: WatchDao,
    private val findingDao: FindingDao
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)

    val aiPreferences = ua.magerko.olxradar.ai.AiPreferences(application)
    private val groqClient = ua.magerko.olxradar.ai.GroqVisionClient(aiPreferences)

    val appSettings: StateFlow<AppPreferences.Snapshot> = AppPreferences.state
    val radarStatus: StateFlow<ServiceStatus> = RadarState.status

    val watches: StateFlow<List<Watch>> = watchDao.getAllWatchesFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalFindings: StateFlow<Int> = findingDao.getActiveFindingsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val blacklistDao = AppDatabase.getInstance(application).sellerBlacklistDao()

    val blockedSellers: StateFlow<List<ua.magerko.olxradar.data.model.SellerBlacklist>> = blacklistDao.getAllFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unblockSeller(sellerKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            blacklistDao.deleteByKey(sellerKey)
        }
    }

    fun clearSellerBlacklist() {
        viewModelScope.launch(Dispatchers.IO) {
            blacklistDao.clearAll()
        }
    }

    val quietHoursEnabled = MutableStateFlow(
        prefs.getBoolean(MonitorService.KEY_QUIET_HOURS_ENABLED, false)
    )
    val quietHoursFromHour = MutableStateFlow(
        prefs.getInt(MonitorService.KEY_QUIET_HOURS_FROM_HOUR, 23)
    )
    val quietHoursFromMin = MutableStateFlow(
        prefs.getInt(MonitorService.KEY_QUIET_HOURS_FROM_MIN, 0)
    )
    val quietHoursToHour = MutableStateFlow(
        prefs.getInt(MonitorService.KEY_QUIET_HOURS_TO_HOUR, 7)
    )
    val quietHoursToMin = MutableStateFlow(
        prefs.getInt(MonitorService.KEY_QUIET_HOURS_TO_MIN, 0)
    )
    val currentInterval = MutableStateFlow(
        prefs.getInt(MonitorService.KEY_INTERVAL, MonitorService.DEFAULT_INTERVAL_SEC)
    )

    // AI Settings StateFlows
    val aiEnabled = MutableStateFlow(aiPreferences.isAiEnabled)
    val aiProvider = MutableStateFlow(aiPreferences.provider)
    val aiBaseUrl = MutableStateFlow(aiPreferences.baseUrl)
    val aiApiKeyMasked = MutableStateFlow(aiPreferences.getMaskedApiKey())
    val aiModelId = MutableStateFlow(aiPreferences.modelId)
    val isModelVerified = MutableStateFlow(aiPreferences.isCurrentModelVerified())

    val isBackupAiEnabled = MutableStateFlow(aiPreferences.isBackupAiEnabled)
    val backupProvider = MutableStateFlow(aiPreferences.backupProvider)
    val backupBaseUrl = MutableStateFlow(aiPreferences.backupBaseUrl)
    val backupApiKeyMasked = MutableStateFlow(aiPreferences.getMaskedBackupApiKey())
    val backupModelId = MutableStateFlow(aiPreferences.backupModelId)
    val isBackupModelVerified = MutableStateFlow(aiPreferences.isBackupModelVerified())

    val aiConfidence = MutableStateFlow(aiPreferences.confidenceThreshold)
    val aiMaxPhotos = MutableStateFlow(aiPreferences.maxPhotos)
    val aiCheckSuspicious = MutableStateFlow(aiPreferences.isCheckSuspicious)
    val aiStrictMode = MutableStateFlow(aiPreferences.isStrictMode)
    val aiManualReview = MutableStateFlow(aiPreferences.isManualReviewMode)
    val aiNotifyRejected = MutableStateFlow(aiPreferences.isNotifyRejected)
    val aiMaxConcurrent = MutableStateFlow(aiPreferences.maxConcurrentRequests)
    val aiTimeoutSec = MutableStateFlow(aiPreferences.timeoutSeconds)

    private fun scheduleBackup() {
        PersistentStorageManager.scheduleAutoBackup(getApplication())
    }

    fun refreshSettingsState() {
        AppPreferences.refresh(getApplication())
        quietHoursEnabled.value = prefs.getBoolean(MonitorService.KEY_QUIET_HOURS_ENABLED, false)
        quietHoursFromHour.value = prefs.getInt(MonitorService.KEY_QUIET_HOURS_FROM_HOUR, 23)
        quietHoursFromMin.value = prefs.getInt(MonitorService.KEY_QUIET_HOURS_FROM_MIN, 0)
        quietHoursToHour.value = prefs.getInt(MonitorService.KEY_QUIET_HOURS_TO_HOUR, 7)
        quietHoursToMin.value = prefs.getInt(MonitorService.KEY_QUIET_HOURS_TO_MIN, 0)
        currentInterval.value = prefs.getInt(MonitorService.KEY_INTERVAL, MonitorService.DEFAULT_INTERVAL_SEC)
        aiEnabled.value = aiPreferences.isAiEnabled
        aiProvider.value = aiPreferences.provider
        aiBaseUrl.value = aiPreferences.baseUrl
        aiApiKeyMasked.value = aiPreferences.getMaskedApiKey()
        aiModelId.value = aiPreferences.modelId
        isModelVerified.value = aiPreferences.isCurrentModelVerified()
        isBackupAiEnabled.value = aiPreferences.isBackupAiEnabled
        backupProvider.value = aiPreferences.backupProvider
        backupBaseUrl.value = aiPreferences.backupBaseUrl
        backupApiKeyMasked.value = aiPreferences.getMaskedBackupApiKey()
        backupModelId.value = aiPreferences.backupModelId
        isBackupModelVerified.value = aiPreferences.isBackupModelVerified()
        aiConfidence.value = aiPreferences.confidenceThreshold
        aiMaxPhotos.value = aiPreferences.maxPhotos
        aiCheckSuspicious.value = aiPreferences.isCheckSuspicious
        aiStrictMode.value = aiPreferences.isStrictMode
        aiManualReview.value = aiPreferences.isManualReviewMode
        aiNotifyRejected.value = aiPreferences.isNotifyRejected
        aiMaxConcurrent.value = aiPreferences.maxConcurrentRequests
        aiTimeoutSec.value = aiPreferences.timeoutSeconds
    }

    fun setAiEnabled(enabled: Boolean) {
        aiPreferences.isAiEnabled = enabled
        aiEnabled.value = enabled
        scheduleBackup()
    }

    fun setApiKey(key: String) {
        aiPreferences.apiKey = key
        aiApiKeyMasked.value = aiPreferences.getMaskedApiKey()
        scheduleBackup()
    }

    fun resetApiKey() {
        aiPreferences.resetApiKey()
        aiApiKeyMasked.value = aiPreferences.getMaskedApiKey()
        scheduleBackup()
    }

    fun setModelId(model: String) {
        aiPreferences.modelId = model
        aiModelId.value = model
        scheduleBackup()
    }

    fun setConfidence(threshold: Int) {
        aiPreferences.confidenceThreshold = threshold
        aiConfidence.value = threshold
        scheduleBackup()
    }

    fun setMaxPhotos(count: Int) {
        aiPreferences.maxPhotos = count
        aiMaxPhotos.value = count
        scheduleBackup()
    }

    fun setCheckSuspicious(enabled: Boolean) {
        aiPreferences.isCheckSuspicious = enabled
        aiCheckSuspicious.value = enabled
        scheduleBackup()
    }

    fun setStrictMode(enabled: Boolean) {
        aiPreferences.isStrictMode = enabled
        aiStrictMode.value = enabled
        scheduleBackup()
    }

    fun setManualReview(enabled: Boolean) {
        aiPreferences.isManualReviewMode = enabled
        aiManualReview.value = enabled
        scheduleBackup()
    }

    fun setAiNotifyRejected(enabled: Boolean) {
        aiPreferences.isNotifyRejected = enabled
        aiNotifyRejected.value = enabled
        scheduleBackup()
    }

    fun setMaxConcurrent(limit: Int) {
        aiPreferences.maxConcurrentRequests = limit
        aiMaxConcurrent.value = limit
        scheduleBackup()
    }

    fun setTimeoutSec(sec: Int) {
        aiPreferences.timeoutSeconds = sec
        aiTimeoutSec.value = sec
        scheduleBackup()
    }

    suspend fun testVisionCapability(
        provider: String,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ): Result<ua.magerko.olxradar.ai.GroqVisionClient.VisionCheckResult> {
        val effectiveKey = if (apiKey.isNotBlank()) apiKey else aiPreferences.apiKey
        return groqClient.testVisionCapability(provider, baseUrl, effectiveKey, modelId)
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> {
        val effectiveKey = if (apiKey.isNotBlank()) apiKey else aiPreferences.apiKey
        return groqClient.fetchAvailableModels(baseUrl, effectiveKey)
    }

    fun applyAiConfig(
        provider: String,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ) {
        aiPreferences.applyProviderConfig(
            newProvider = provider,
            newBaseUrl = baseUrl,
            newApiKey = apiKey,
            newModelId = modelId,
            isVerified = true
        )
        aiProvider.value = aiPreferences.provider
        aiBaseUrl.value = aiPreferences.baseUrl
        aiModelId.value = aiPreferences.modelId
        aiApiKeyMasked.value = aiPreferences.getMaskedApiKey()
        isModelVerified.value = true
        scheduleBackup()
    }

    fun setBackupAiEnabled(enabled: Boolean) {
        aiPreferences.isBackupAiEnabled = enabled
        isBackupAiEnabled.value = enabled
        scheduleBackup()
    }

    fun applyBackupAiConfig(
        provider: String,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ) {
        aiPreferences.applyBackupProviderConfig(
            newProvider = provider,
            newBaseUrl = baseUrl,
            newApiKey = apiKey,
            newModelId = modelId,
            isVerified = true
        )
        backupProvider.value = aiPreferences.backupProvider
        backupBaseUrl.value = aiPreferences.backupBaseUrl
        backupModelId.value = aiPreferences.backupModelId
        backupApiKeyMasked.value = aiPreferences.getMaskedBackupApiKey()
        isBackupModelVerified.value = true
        scheduleBackup()
    }

    suspend fun testBackupApi(): Result<String> {
        val result = groqClient.testConnection(isBackup = true)
        if (result.isSuccess) {
            isBackupModelVerified.value = true
            aiPreferences.backupVerifiedModel = aiPreferences.backupModelId
        }
        return result
    }

    suspend fun testGroqApi(): Result<String> {
        val result = groqClient.testConnection()
        if (result.isSuccess) {
            isModelVerified.value = true
            aiPreferences.verifiedModel = aiPreferences.modelId
        }
        return result
    }

    fun setInterval(sec: Int) {
        currentInterval.value = sec
        prefs.edit().putInt(MonitorService.KEY_INTERVAL, sec).apply()
        scheduleBackup()
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        quietHoursEnabled.value = enabled
        prefs.edit().putBoolean(MonitorService.KEY_QUIET_HOURS_ENABLED, enabled).apply()
        scheduleBackup()
    }

    fun setQuietHoursFrom(hour: Int, min: Int) {
        quietHoursFromHour.value = hour
        quietHoursFromMin.value = min
        prefs.edit()
            .putInt(MonitorService.KEY_QUIET_HOURS_FROM_HOUR, hour)
            .putInt(MonitorService.KEY_QUIET_HOURS_FROM_MIN, min)
            .apply()
        scheduleBackup()
    }

    fun setQuietHoursTo(hour: Int, min: Int) {
        quietHoursToHour.value = hour
        quietHoursToMin.value = min
        prefs.edit()
            .putInt(MonitorService.KEY_QUIET_HOURS_TO_HOUR, hour)
            .putInt(MonitorService.KEY_QUIET_HOURS_TO_MIN, min)
            .apply()
        scheduleBackup()
    }

    fun toggleMonitoringPause() {
        val currentlyPaused = RadarState.status.value.isPaused
        val newAction = if (currentlyPaused) MonitorService.ACTION_RESUME else MonitorService.ACTION_PAUSE
        val intent = android.content.Intent(getApplication(), MonitorService::class.java).apply {
            action = newAction
        }
        getApplication<Application>().startService(intent)
    }

    suspend fun buildFullBackupJson(password: String? = null): String = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val snap = AppPreferences.state.value
        val watches = watchDao.getAllWatches()
        val findings = if (snap.backupIncludeFindings) findingDao.getFindingsForBackup(limit = 300) else emptyList()
        val plain = RadarBackup.toFullBackupJson(
            context = app,
            watches = watches,
            findings = findings,
            includeFindings = snap.backupIncludeFindings,
            includeApiKeys = snap.backupIncludeApiKeys
        )
        val pass = password?.trim()
        if (!pass.isNullOrEmpty()) RadarBackup.encryptBackupJson(plain, pass) else plain
    }

    suspend fun restoreFromFullBackupJson(rawText: String, password: String? = null): PersistentStorageManager.RestoreResult {
        val plain = if (RadarBackup.isEncryptedBackup(rawText)) {
            val pass = password ?: error("Бэкап зашифрован — введите пароль")
            RadarBackup.decryptBackupJson(rawText, pass)
        } else {
            rawText
        }
        val result = PersistentStorageManager.restoreFromBackupJson(getApplication(), plain)
        refreshSettingsState()
        return result
    }

    suspend fun runBackupNow(): PersistentStorageManager.BackupWriteResult {
        return PersistentStorageManager.performFullBackupNow(getApplication())
    }

    suspend fun getTotalFindingsCount(): Int = withContext(Dispatchers.IO) {
        findingDao.getTotalCount()
    }

    fun clearRejectedFindings() {
        viewModelScope.launch(Dispatchers.IO) {
            findingDao.clearRejectedFindings()
        }
    }

    fun clearAiCacheOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(getApplication()).aiCacheDao().clear()
            } catch (e: Exception) {
                AppLog.w("StatusVM", "Не удалось очистить ИИ-кэш", e)
            }
        }
    }

    fun clearAllFindings() {
        viewModelScope.launch(Dispatchers.IO) {
            findingDao.clearAllFindings()
            try {
                AppDatabase.getInstance(getApplication()).aiCacheDao().clear()
            } catch (e: Exception) {
                AppLog.w("StatusVM", "Не удалось очистить ИИ-кэш при удалении находок", e)
            }
        }
    }
}
