package ua.magerko.olxradar.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.local.WatchDao
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.service.RadarState
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.util.AppLog

class StatusViewModel(
    application: Application,
    watchDao: WatchDao,
    private val findingDao: FindingDao
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)

    val aiPreferences = ua.magerko.olxradar.ai.AiPreferences(application)
    private val groqClient = ua.magerko.olxradar.ai.GroqVisionClient(aiPreferences)

    val radarStatus: StateFlow<ServiceStatus> = RadarState.status

    val watches: StateFlow<List<Watch>> = watchDao.getAllWatchesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalFindings: StateFlow<Int> = findingDao.getActiveFindingsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
    val aiApiKeyMasked = MutableStateFlow(aiPreferences.getMaskedApiKey())
    val aiModelId = MutableStateFlow(aiPreferences.modelId)
    val aiConfidence = MutableStateFlow(aiPreferences.confidenceThreshold)
    val aiMaxPhotos = MutableStateFlow(aiPreferences.maxPhotos)
    val aiCheckSuspicious = MutableStateFlow(aiPreferences.isCheckSuspicious)
    val aiStrictMode = MutableStateFlow(aiPreferences.isStrictMode)
    val aiManualReview = MutableStateFlow(aiPreferences.isManualReviewMode)
    val aiNotifyRejected = MutableStateFlow(aiPreferences.isNotifyRejected)
    val aiMaxConcurrent = MutableStateFlow(aiPreferences.maxConcurrentRequests)
    val aiTimeoutSec = MutableStateFlow(aiPreferences.timeoutSeconds)

    fun setAiEnabled(enabled: Boolean) {
        aiPreferences.isAiEnabled = enabled
        aiEnabled.value = enabled
    }

    fun setApiKey(key: String) {
        aiPreferences.apiKey = key
        aiApiKeyMasked.value = aiPreferences.getMaskedApiKey()
    }

    fun resetApiKey() {
        aiPreferences.resetApiKey()
        aiApiKeyMasked.value = aiPreferences.getMaskedApiKey()
    }

    fun setModelId(model: String) {
        aiPreferences.modelId = model
        aiModelId.value = model
    }

    fun setConfidence(threshold: Int) {
        aiPreferences.confidenceThreshold = threshold
        aiConfidence.value = threshold
    }

    fun setMaxPhotos(count: Int) {
        aiPreferences.maxPhotos = count
        aiMaxPhotos.value = count
    }

    fun setCheckSuspicious(enabled: Boolean) {
        aiPreferences.isCheckSuspicious = enabled
        aiCheckSuspicious.value = enabled
    }

    fun setStrictMode(enabled: Boolean) {
        aiPreferences.isStrictMode = enabled
        aiStrictMode.value = enabled
    }

    fun setManualReview(enabled: Boolean) {
        aiPreferences.isManualReviewMode = enabled
        aiManualReview.value = enabled
    }

    fun setAiNotifyRejected(enabled: Boolean) {
        aiPreferences.isNotifyRejected = enabled
        aiNotifyRejected.value = enabled
    }

    fun setMaxConcurrent(limit: Int) {
        aiPreferences.maxConcurrentRequests = limit
        aiMaxConcurrent.value = limit
    }

    fun setTimeoutSec(sec: Int) {
        aiPreferences.timeoutSeconds = sec
        aiTimeoutSec.value = sec
    }

    suspend fun testGroqApi(): Result<String> {
        return groqClient.testConnection()
    }

    fun setInterval(sec: Int) {
        currentInterval.value = sec
        prefs.edit().putInt(MonitorService.KEY_INTERVAL, sec).apply()
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        quietHoursEnabled.value = enabled
        prefs.edit().putBoolean(MonitorService.KEY_QUIET_HOURS_ENABLED, enabled).apply()
    }

    fun setQuietHoursFrom(hour: Int, min: Int) {
        quietHoursFromHour.value = hour
        quietHoursFromMin.value = min
        prefs.edit()
            .putInt(MonitorService.KEY_QUIET_HOURS_FROM_HOUR, hour)
            .putInt(MonitorService.KEY_QUIET_HOURS_FROM_MIN, min)
            .apply()
    }

    fun setQuietHoursTo(hour: Int, min: Int) {
        quietHoursToHour.value = hour
        quietHoursToMin.value = min
        prefs.edit()
            .putInt(MonitorService.KEY_QUIET_HOURS_TO_HOUR, hour)
            .putInt(MonitorService.KEY_QUIET_HOURS_TO_MIN, min)
            .apply()
    }

    fun toggleMonitoringPause() {
        val currentlyPaused = RadarState.status.value.isPaused
        val newAction = if (currentlyPaused) MonitorService.ACTION_RESUME else MonitorService.ACTION_PAUSE
        val intent = android.content.Intent(getApplication(), MonitorService::class.java).apply {
            action = newAction
        }
        getApplication<Application>().startService(intent)
    }

    fun clearAllFindings() {
        viewModelScope.launch {
            findingDao.clearAllFindings()
            try {
                ua.magerko.olxradar.data.local.AppDatabase.getInstance(getApplication()).aiCacheDao().clear()
            } catch (e: Exception) {
                AppLog.w("StatusVM", "Не удалось очистить ИИ-кэш при удалении находок", e)
            }
        }
    }
}
