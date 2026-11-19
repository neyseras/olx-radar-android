package ua.magerko.olxradar.ai

import android.content.Context
import android.content.SharedPreferences
import ua.magerko.olxradar.BuildConfig
import ua.magerko.olxradar.service.MonitorService

class AiPreferences(context: Context) {

    companion object {
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_CONFIDENCE_THRESHOLD = "ai_confidence_threshold"
        const val KEY_AI_MAX_PHOTOS = "ai_max_photos"
        const val KEY_AI_CHECK_SUSPICIOUS = "ai_check_suspicious"
        const val KEY_AI_STRICT_MODE = "ai_strict_mode"
        const val KEY_AI_MANUAL_REVIEW_MODE = "ai_manual_review_mode"
        const val KEY_AI_MAX_CONCURRENT = "ai_max_concurrent"
        const val KEY_AI_TIMEOUT_SEC = "ai_timeout_sec"
        const val KEY_AI_NOTIFY_REJECTED = "ai_notify_rejected"

        const val DEFAULT_PROVIDER = "groq"
        const val DEFAULT_CONFIDENCE_THRESHOLD = 75
        const val DEFAULT_MAX_PHOTOS = 2
        const val DEFAULT_CHECK_SUSPICIOUS = true
        const val DEFAULT_STRICT_MODE = false
        const val DEFAULT_MANUAL_REVIEW_MODE = true
        const val DEFAULT_MAX_CONCURRENT = 2
        const val DEFAULT_TIMEOUT_SEC = 25
        const val DEFAULT_NOTIFY_REJECTED = false
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)

    var isAiEnabled: Boolean
        get() {
            return if (!prefs.contains(KEY_AI_ENABLED)) {
                // If not set yet, enable if an API key is available
                apiKey.isNotBlank()
            } else {
                prefs.getBoolean(KEY_AI_ENABLED, true)
            }
        }
        set(value) = prefs.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    var provider: String
        get() = prefs.getString(KEY_AI_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()

    var apiKey: String
        get() {
            val stored = prefs.getString(KEY_AI_API_KEY, null)
            return if (!stored.isNullOrBlank()) {
                stored
            } else {
                BuildConfig.DEFAULT_GROQ_API_KEY
            }
        }
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                prefs.edit().remove(KEY_AI_API_KEY).apply()
            } else {
                prefs.edit().putString(KEY_AI_API_KEY, trimmed).apply()
            }
        }

    fun resetApiKey() {
        prefs.edit().remove(KEY_AI_API_KEY).apply()
    }

    fun isDefaultApiKey(): Boolean = prefs.getString(KEY_AI_API_KEY, null).isNullOrBlank()

    var modelId: String
        get() {
            val stored = prefs.getString(KEY_AI_MODEL, null)
            return if (!stored.isNullOrBlank() && !stored.contains("qwen3.8", ignoreCase = true)) {
                stored
            } else {
                BuildConfig.DEFAULT_GROQ_MODEL
            }
        }
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value.trim()).apply()

    var confidenceThreshold: Int
        get() = prefs.getInt(KEY_AI_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_AI_CONFIDENCE_THRESHOLD, value.coerceIn(0, 100)).apply()

    var maxPhotos: Int
        get() = prefs.getInt(KEY_AI_MAX_PHOTOS, DEFAULT_MAX_PHOTOS).coerceIn(1, 5)
        set(value) = prefs.edit().putInt(KEY_AI_MAX_PHOTOS, value.coerceIn(1, 5)).apply()

    var isCheckSuspicious: Boolean
        get() = prefs.getBoolean(KEY_AI_CHECK_SUSPICIOUS, DEFAULT_CHECK_SUSPICIOUS)
        set(value) = prefs.edit().putBoolean(KEY_AI_CHECK_SUSPICIOUS, value).apply()

    var isStrictMode: Boolean
        get() = prefs.getBoolean(KEY_AI_STRICT_MODE, DEFAULT_STRICT_MODE)
        set(value) = prefs.edit().putBoolean(KEY_AI_STRICT_MODE, value).apply()

    var isManualReviewMode: Boolean
        get() = prefs.getBoolean(KEY_AI_MANUAL_REVIEW_MODE, DEFAULT_MANUAL_REVIEW_MODE)
        set(value) = prefs.edit().putBoolean(KEY_AI_MANUAL_REVIEW_MODE, value).apply()

    var maxConcurrentRequests: Int
        get() = prefs.getInt(KEY_AI_MAX_CONCURRENT, DEFAULT_MAX_CONCURRENT).coerceIn(1, 5)
        set(value) = prefs.edit().putInt(KEY_AI_MAX_CONCURRENT, value.coerceIn(1, 5)).apply()

    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_AI_TIMEOUT_SEC, DEFAULT_TIMEOUT_SEC).coerceIn(5, 60)
        set(value) = prefs.edit().putInt(KEY_AI_TIMEOUT_SEC, value.coerceIn(5, 60)).apply()

    var isNotifyRejected: Boolean
        get() = prefs.getBoolean(KEY_AI_NOTIFY_REJECTED, DEFAULT_NOTIFY_REJECTED)
        set(value) = prefs.edit().putBoolean(KEY_AI_NOTIFY_REJECTED, value).apply()

    fun getMaskedApiKey(): String {
        val key = apiKey
        if (key.isBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}...${key.takeLast(4)}"
    }
}
