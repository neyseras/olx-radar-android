package ua.magerko.olxradar.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.network.ChromiumBridge
import ua.magerko.olxradar.data.network.OlxParser
import ua.magerko.olxradar.util.AppLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"

        const val ACTION_START = "ua.magerko.olxradar.ACTION_START"
        const val ACTION_PAUSE = "ua.magerko.olxradar.ACTION_PAUSE"
        const val ACTION_RESUME = "ua.magerko.olxradar.ACTION_RESUME"
        const val ACTION_STOP = "ua.magerko.olxradar.ACTION_STOP"

        const val PREFS_NAME = "olx_radar_prefs"
        const val KEY_INTERVAL = "poll_interval_sec"
        const val DEFAULT_INTERVAL_SEC = 25
        const val KEY_SERVICE_WAS_RUNNING = "service_was_running"

        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_HOURS_FROM_HOUR = "quiet_hours_from_hour"
        const val KEY_QUIET_HOURS_FROM_MIN = "quiet_hours_from_min"
        const val KEY_QUIET_HOURS_TO_HOUR = "quiet_hours_to_hour"
        const val KEY_QUIET_HOURS_TO_MIN = "quiet_hours_to_min"

        internal fun isInQuietHoursRange(
            enabled: Boolean,
            fromHour: Int,
            fromMin: Int,
            toHour: Int,
            toMin: Int,
            curMinutes: Int
        ): Boolean {
            if (!enabled) return false
            val startMinutes = fromHour * 60 + fromMin
            val endMinutes = toHour * 60 + toMin

            return if (startMinutes <= endMinutes) {
                curMinutes in startMinutes..endMinutes
            } else {
                curMinutes >= startMinutes || curMinutes <= endMinutes
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private lateinit var db: AppDatabase
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var chromiumBridge: ChromiumBridge
    private lateinit var prefs: SharedPreferences
    private lateinit var aiPreferences: ua.magerko.olxradar.ai.AiPreferences
    private lateinit var aiImageProcessor: ua.magerko.olxradar.ai.AiImageProcessor
    private lateinit var groqVisionClient: ua.magerko.olxradar.ai.GroqVisionClient
    private lateinit var aiFilterEngine: ua.magerko.olxradar.ai.AiFilterEngine

    private val nextRunMap = ConcurrentHashMap<Long, Long>()
    private val consecutiveErrorsMap = ConcurrentHashMap<Long, Int>()
    private val lastErrorAlertMap = ConcurrentHashMap<Long, Long>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        notificationHelper = NotificationHelper(this)
        chromiumBridge = ChromiumBridge(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        aiPreferences = ua.magerko.olxradar.ai.AiPreferences(this)
        aiImageProcessor = ua.magerko.olxradar.ai.AiImageProcessor()
        groqVisionClient = ua.magerko.olxradar.ai.GroqVisionClient(aiPreferences)
        aiFilterEngine = ua.magerko.olxradar.ai.AiFilterEngine(
            aiPreferences = aiPreferences,
            groqClient = groqVisionClient,
            imageProcessor = aiImageProcessor,
            aiCacheDao = db.aiCacheDao()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                RadarState.setRunning(false)
                RadarState.setPaused(false)
                prefs.edit().putBoolean(KEY_SERVICE_WAS_RUNNING, false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                RadarState.setPaused(true)
                updateNotification(getString(R.string.service_paused))
            }
            ACTION_RESUME -> {
                RadarState.setPaused(false)
                updateNotification(getString(R.string.service_running))
            }
            ACTION_START, null -> {
                try {
                    val notif = notificationHelper.buildServiceNotification(getString(R.string.service_starting), false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NotificationHelper.SERVICE_NOTIFICATION_ID,
                            notif,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notif)
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to startForeground: ${e.message}", e)
                }

                if (!RadarState.status.value.isRunning) {
                    RadarState.setRunning(true)
                    RadarState.setPaused(false)
                    prefs.edit().putBoolean(KEY_SERVICE_WAS_RUNNING, true).apply()
                    startMonitoringLoop()
                }
            }
        }
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            while (isActive) {
                if (RadarState.status.value.isPaused) {
                    delay(2000)
                    continue
                }

                val activeWatches = db.watchDao().getActiveWatches()
                if (activeWatches.isEmpty()) {
                    updateNotification(getString(R.string.status_no_active_watches))
                    delay(4000)
                    continue
                }

                val now = System.currentTimeMillis()
                val globalIntervalSec = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC)

                // Clean up stale watches from scheduling maps
                val activeIds = activeWatches.map { it.id }.toSet()
                nextRunMap.keys.retainAll(activeIds)
                consecutiveErrorsMap.keys.retainAll(activeIds)
                lastErrorAlertMap.keys.retainAll(activeIds)

                // Initialize next run time for new watches with slight offset to prevent simultaneous burst calls
                var staggerMs = 0L
                for (w in activeWatches) {
                    if (!nextRunMap.containsKey(w.id)) {
                        nextRunMap[w.id] = now + staggerMs
                        staggerMs += 2000L
                    }
                }

                // B8: Select the watch with the earliest nextRunAt
                val dueWatch = activeWatches.minByOrNull { nextRunMap[it.id] ?: now }
                if (dueWatch == null) {
                    delay(1000)
                    continue
                }

                val dueTime = nextRunMap[dueWatch.id] ?: now
                if (dueTime > now) {
                    val waitMs = (dueTime - now).coerceAtMost(2000L)
                    delay(waitMs)
                    continue
                }

                // Poll this due watch
                pollWatch(dueWatch, globalIntervalSec)

                // Polite pause between individual radar requests (1800-2600 ms) to avoid OLX rate limiting / HTTP 500
                delay(Random.nextLong(1800, 2600))
            }
        }
    }

    private suspend fun pollWatch(watch: Watch, globalIntervalSec: Int) {
        val now = System.currentTimeMillis()
        try {
            val searchUrl = OlxParser.buildSearchUrl(watch)
            val rawJson = chromiumBridge.fetchJson(searchUrl)
            val listings = OlxParser.parseListings(rawJson)

            val seenIds = db.seenItemDao().getSeenIds(watch.id).toSet()

            // B2: Bug "First lot swallowed" fixed:
            // Use watch.initialized flag instead of seenIds.isEmpty().
            if (!watch.initialized) {
                // First successful poll: silently mark all listings as seen
                for (listing in listings) {
                    db.seenItemDao().markSeenAndPrune(
                        watchId = watch.id,
                        listingId = listing.id,
                        lastPushedAt = listing.pushedAt,
                        lastPrice = listing.price
                    )
                }
                db.watchDao().setInitialized(watch.id, true)
            } else {
                val inQuietHours = isQuietHours(prefs)
                val isSnoozed = watch.snoozedUntil != null && now < watch.snoozedUntil

                for (listing in listings) {
                    if (!OlxParser.matchesWatch(listing, watch)) {
                        continue
                    }

                    val isNew = listing.id !in seenIds
                    val isPushup = if (!isNew && watch.notifyMode == "new_pushup" && listing.pushedAt != null) {
                        val lastPushed = db.seenItemDao().getLastPushedAt(watch.id, listing.id)
                        lastPushed == null || listing.pushedAt > lastPushed
                    } else false

                    if (isNew || isPushup) {
                        val reason = if (isNew) "new" else "pushup"

                        // Multi-stage filtering: evaluate with AI filter engine
                        val aiEval = aiFilterEngine.evaluateListing(listing, watch)

                        // Mark seen and update last price/pushed_at
                        db.seenItemDao().markSeenAndPrune(
                            watchId = watch.id,
                            listingId = listing.id,
                            lastPushedAt = listing.pushedAt,
                            lastPrice = listing.price
                        )

                        if (aiEval.shouldSaveFinding) {
                            val finding = Finding(
                                watchId = watch.id,
                                listingId = listing.id,
                                title = listing.title,
                                description = listing.description,
                                price = listing.price,
                                currency = listing.currency,
                                cityName = listing.cityName,
                                url = listing.url,
                                photoUrl = listing.photoUrl,
                                reason = reason,
                                createdAt = listing.createdAt,
                                foundAt = now,
                                isRead = false,
                                conditionScore = OlxParser.conditionScore(listing),
                                aiScore = aiEval.result?.confidence,
                                aiStatus = aiEval.status,
                                aiProduct = aiEval.result?.detectedProduct,
                                aiModel = aiEval.result?.detectedModel,
                                aiPhotoMatch = aiEval.result?.photoMatchesTitle,
                                aiReasons = aiEval.result?.reasons?.joinToString("\n• ", prefix = "• "),
                                aiRejectReason = aiEval.result?.rejectReason,
                                aiDetailsJson = aiEval.result?.toJsonString()
                            )
                            val insertedId = db.findingDao().insertFinding(finding)
                            val savedFinding = if (finding.id == 0L) finding.copy(id = insertedId) else finding

                            // B11: Silence notifications in quiet hours and when snoozed
                            val shouldNotify = if (aiEval.status == ua.magerko.olxradar.ai.AiStatus.REJECTED) {
                                aiPreferences.isNotifyRejected
                            } else {
                                aiEval.shouldNotify
                            }

                            if (!inQuietHours && !isSnoozed && shouldNotify) {
                                notificationHelper.sendAlertNotificationAsync(savedFinding, serviceScope)
                            }
                        }
                    }
                }
            }

            // B1: Success! Update counters and lastChecked ONLY upon success
            consecutiveErrorsMap[watch.id] = 0
            AppLog.i(TAG, "Опрос успешен: '${watch.title}'")
            db.watchDao().updateSuccess(watch.id, now)
            RadarState.recordSuccess(watch.id, now)

            val updatedTimeStr = timeFormat.format(Date(now))
            updateNotification(getString(R.string.service_status_updated, updatedTimeStr))

            // B8: Next run with ±10% jitter
            val baseIntervalSec = watch.intervalSecOverride ?: globalIntervalSec
            val jitter = 1.0 + Random.nextDouble(-0.1, 0.1)
            val nextIntervalMs = (baseIntervalSec * 1000L * jitter).toLong().coerceAtLeast(5000L)
            nextRunMap[watch.id] = now + nextIntervalMs

        } catch (e: Exception) {
            // B1: Error handling — classify error, update DAO, RadarState, Log.w
            val (errorMsg, httpCode) = classifyError(e)
            AppLog.w(TAG, "Ошибка радара '${watch.query}': $errorMsg", e)

            db.watchDao().updateError(watch.id, errorMsg, httpCode)
            RadarState.recordError(watch.id, errorMsg, httpCode)

            // B9: Exponential backoff up to 5 minutes
            val errorsCount = (consecutiveErrorsMap[watch.id] ?: 0) + 1
            consecutiveErrorsMap[watch.id] = errorsCount

            val baseIntervalSec = watch.intervalSecOverride ?: globalIntervalSec
            val backoffMultiplier = 1L shl (errorsCount - 1).coerceAtMost(6)
            val backoffMs = (baseIntervalSec * 1000L * backoffMultiplier).coerceAtMost(5 * 60 * 1000L)
            nextRunMap[watch.id] = now + backoffMs

            // Send error alert after 5 consecutive failures (rate limited to once every 30m per watch)
            val lastAlertTime = lastErrorAlertMap[watch.id] ?: 0L
            if (errorsCount >= 5 && now - lastAlertTime >= 30 * 60 * 1000L) {
                lastErrorAlertMap[watch.id] = now
                notificationHelper.sendErrorAlertNotification(watch.title)
            }
        }
    }

    internal fun classifyError(e: Throwable): Pair<String, Int?> {
        val msg = e.message ?: e.toString()
        val httpMatch = Regex("""HTTP\s*(\d{3})""", RegexOption.IGNORE_CASE).find(msg)
        val extractedCode = httpMatch?.groupValues?.get(1)?.toIntOrNull()

        return when {
            extractedCode == 403 || msg.contains("HTTP 403", ignoreCase = true) ->
                "HTTP 403 Forbidden" to 403
            extractedCode == 429 || msg.contains("HTTP 429", ignoreCase = true) ->
                "HTTP 429 Too Many Requests" to 429
            extractedCode == 500 || msg.contains("HTTP 500", ignoreCase = true) ->
                "HTTP 500 (сервер OLX перегружен)" to 500
            extractedCode != null ->
                "HTTP $extractedCode" to extractedCode
            e is TimeoutCancellationException ||
                    e is java.net.SocketTimeoutException ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("timed out", ignoreCase = true) ->
                "Timeout" to null
            e is org.json.JSONException || msg.contains("JSON", ignoreCase = true) ->
                "Invalid JSON" to null
            msg.contains("renderer", ignoreCase = true) ->
                "Renderer crashed" to null
            else ->
                (e.localizedMessage ?: "Network error") to null
        }
    }

    internal fun isQuietHours(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)
        if (!enabled) return false

        val fromHour = prefs.getInt(KEY_QUIET_HOURS_FROM_HOUR, 23)
        val fromMin = prefs.getInt(KEY_QUIET_HOURS_FROM_MIN, 0)
        val toHour = prefs.getInt(KEY_QUIET_HOURS_TO_HOUR, 7)
        val toMin = prefs.getInt(KEY_QUIET_HOURS_TO_MIN, 0)

        val calendar = Calendar.getInstance()
        val curMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        return isInQuietHoursRange(enabled, fromHour, fromMin, toHour, toMin, curMinutes)
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            notificationHelper.buildServiceNotification(status, RadarState.status.value.isPaused)
        )
    }

    // B12: Note on Android 15 Foreground Service dataSync timeout:
    // Starting in API 35 (Android 15), dataSync services are subject to a timeout limit and onTimeout(startId, fgsType)
    // is called by the OS. In compileSdk 34, Service.onTimeout(Int, Int) is not present in the compile classpath.
    // If upgraded to compileSdk 35+, override fun onTimeout(startId: Int, fgsType: Int) { stopSelf(); } must be implemented.

    override fun onDestroy() {
        RadarState.setRunning(false)
        RadarState.setPaused(false)
        loopJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
