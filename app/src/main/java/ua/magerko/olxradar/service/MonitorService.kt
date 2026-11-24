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
        const val KEY_SERVICE_IS_PAUSED = "service_is_paused"

        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_HOURS_FROM_HOUR = "quiet_hours_from_hour"
        const val KEY_QUIET_HOURS_FROM_MIN = "quiet_hours_from_min"
        const val KEY_QUIET_HOURS_TO_HOUR = "quiet_hours_to_hour"
        const val KEY_QUIET_HOURS_TO_MIN = "quiet_hours_to_min"

        @Volatile
        internal var globalDomainCooldownUntil: Long = 0L

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

        fun isQuietHoursStatic(prefs: SharedPreferences): Boolean {
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
        ua.magerko.olxradar.ai.AiQueueManager.init(
            engine = aiFilterEngine,
            database = db,
            notificationHelper = notificationHelper,
            prefs = prefs
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
                prefs.edit().putBoolean(KEY_SERVICE_IS_PAUSED, true).apply()
                updateNotification(getString(R.string.service_paused))
            }
            ACTION_RESUME -> {
                RadarState.setPaused(false)
                prefs.edit().putBoolean(KEY_SERVICE_IS_PAUSED, false).apply()
                updateNotification(getString(R.string.service_running))
            }
            ACTION_START, null -> {
                val wasPaused = prefs.getBoolean(KEY_SERVICE_IS_PAUSED, false)
                RadarState.setPaused(wasPaused)
                val initialStatus = if (wasPaused) getString(R.string.service_paused) else getString(R.string.service_starting)
                try {
                    val notif = notificationHelper.buildServiceNotification(initialStatus, wasPaused)
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
                    delay(1000)
                    continue
                }

                val activeWatches = db.watchDao().getActiveWatches()
                if (activeWatches.isEmpty()) {
                    updateNotification(getString(R.string.status_no_active_watches))
                    delay(3000)
                    continue
                }

                if (RadarState.status.value.isPaused) continue

                val now = System.currentTimeMillis()
                if (now < globalDomainCooldownUntil) {
                    val waitRemaining = (globalDomainCooldownUntil - now).coerceAtMost(3000L)
                    delay(waitRemaining)
                    continue
                }

                if (RadarState.status.value.isPaused) continue

                val globalIntervalSec = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC)

                // Clean up stale watches from scheduling maps
                val activeIds = activeWatches.map { it.id }.toSet()
                nextRunMap.keys.retainAll(activeIds)
                consecutiveErrorsMap.keys.retainAll(activeIds)
                lastErrorAlertMap.keys.retainAll(activeIds)

                // Initialize next run time for new watches with stagger to prevent simultaneous burst calls
                var staggerMs = 0L
                for (w in activeWatches) {
                    if (!nextRunMap.containsKey(w.id)) {
                        nextRunMap[w.id] = now + staggerMs
                        staggerMs += 4000L
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

                if (RadarState.status.value.isPaused) continue

                // Check that dueWatch is still active in DB
                val currentWatch = db.watchDao().getWatchById(dueWatch.id)
                if (currentWatch == null || !currentWatch.isActive) {
                    nextRunMap.remove(dueWatch.id)
                    continue
                }

                // Poll this due watch
                pollWatch(currentWatch, globalIntervalSec, activeWatches.size)

                if (RadarState.status.value.isPaused) continue

                // Safe pacing between individual radar requests to avoid OLX rate limiting (1 req / 5-7s)
                val pauseMs = when {
                    activeWatches.size >= 5 -> Random.nextLong(4500, 6000)
                    activeWatches.size >= 3 -> Random.nextLong(3500, 4800)
                    else -> Random.nextLong(2500, 3500)
                }
                delay(pauseMs)
            }
        }
    }

    private suspend fun pollWatch(watch: Watch, globalIntervalSec: Int, activeWatchesCount: Int = 1) {
        if (RadarState.status.value.isPaused) return
        val now = System.currentTimeMillis()
        try {
            val appSnap = ua.magerko.olxradar.data.prefs.AppPreferences.state.value
            val scanPages = appSnap.scanDepthPages.coerceIn(1, 3)
            val listings = mutableListOf<ua.magerko.olxradar.data.model.Listing>()
            val seenBatchIds = HashSet<Long>()
            for (pageIdx in 0 until scanPages) {
                if (RadarState.status.value.isPaused) return
                val offset = pageIdx * 40
                val searchUrl = OlxParser.buildSearchUrl(watch, offset = offset, limit = 40)
                val rawJson = try {
                    chromiumBridge.fetchJson(searchUrl)
                } catch (e: Exception) {
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    if (pageIdx == 0) throw e else break
                }
                if (RadarState.status.value.isPaused) return
                val pageListings = OlxParser.parseListings(rawJson)
                for (item in pageListings) {
                    if (seenBatchIds.add(item.id)) {
                        listings.add(item)
                    }
                }
                if (pageListings.size < 40) break
                if (pageIdx < scanPages - 1) {
                    delay(1200L)
                }
            }

            // Авто-определение city_id по строгому совпадению названия города (UA/RU)
            var effectiveWatch = watch
            if (watch.cityId == null && !watch.cityName.isNullOrBlank()) {
                val matchedCity = listings.firstOrNull {
                    it.cityId != null && it.cityId > 0 &&
                        ua.magerko.olxradar.data.network.OlxCities.citiesMatchExact(it.cityName, watch.cityName)
                }
                if (matchedCity?.cityId != null) {
                    db.watchDao().updateCityId(watch.id, matchedCity.cityId)
                    effectiveWatch = watch.copy(cityId = matchedCity.cityId)
                }
            }

            val matchOptions = OlxParser.SearchMatchOptions(
                smartAntiSpam = appSnap.smartAntiSpam,
                convertForeignCurrency = appSnap.convertForeignCurrency,
                usdToUahRate = appSnap.usdToUahRate,
                eurToUahRate = appSnap.eurToUahRate,
                strictQueryWordMatch = appSnap.strictQueryWordMatch,
                onlyDelivery = appSnap.onlyDeliveryGlobal
            )

            // Вычисляем медианную цену только по прошедшим фильтр лотам, приведённым к валюте радара
            val matchedListings = listings.filter { OlxParser.matchesWatchSmart(it, effectiveWatch, matchOptions) }
            val batchPrices = matchedListings.mapNotNull { l ->
                val p = l.price ?: return@mapNotNull null
                if (p <= 0) return@mapNotNull null
                val lCurr = OlxParser.normalizeCurrency(l.currency)
                if (lCurr.isBlank() || lCurr == OlxParser.normalizeCurrency(effectiveWatch.priceCurrency)) p
                else OlxParser.convertPriceToTargetCurrency(
                    p, lCurr, effectiveWatch.priceCurrency, appSnap.usdToUahRate, appSnap.eurToUahRate
                )
            }.sorted()
            val batchMedian = if (batchPrices.size >= 3) batchPrices[batchPrices.size / 2] else null

            val seenIds = db.seenItemDao().getSeenIds(watch.id).toSet()

            // B2: Bug "First lot swallowed" fixed:
            // Use watch.initialized flag instead of seenIds.isEmpty().
            if (!watch.initialized) {
                val batchSeen = listings.map { listing ->
                    ua.magerko.olxradar.data.model.SeenItem(
                        watchId = watch.id,
                        listingId = listing.id,
                        lastPushedAt = listing.pushedAt,
                        lastPrice = listing.price,
                        lastCurrency = listing.currency?.let { OlxParser.normalizeCurrency(it) },
                        addedAt = now
                    )
                }
                db.seenItemDao().markSeenBatchAndPrune(watch.id, batchSeen)

                var initialSaved = 0
                for (listing in matchedListings) {
                    if (initialSaved >= 15 || RadarState.status.value.isPaused) break
                    val sellerKey = listing.sellerName?.trim()?.lowercase()
                    if (!sellerKey.isNullOrBlank() && db.sellerBlacklistDao().isBlocked(sellerKey)) {
                        continue
                    }
                    val existing = db.findingDao().getFindingByWatchAndListing(watch.id, listing.id)
                    if (existing != null) continue
                    val condScore = OlxParser.conditionScore(listing)
                    val dealScore = OlxParser.computeDealScore(
                        listing, effectiveWatch, batchMedian, condScore, appSnap.usdToUahRate, appSnap.eurToUahRate
                    )
                    val photosJson = listing.photoUrls.take(5).takeIf { it.isNotEmpty() }?.let {
                        org.json.JSONArray(it).toString()
                    }
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
                        reason = "new",
                        createdAt = listing.createdAt,
                        foundAt = now,
                        isRead = false,
                        conditionScore = condScore,
                        isDelivery = listing.isDelivery,
                        dealScore = dealScore,
                        photoUrlsJson = photosJson,
                        sellerName = listing.sellerName
                    )
                    db.findingDao().insertFinding(finding)
                    if (listing.price != null && listing.price > 0) {
                        val latestHist = db.priceHistoryDao().getLatestForListing(listing.id)
                        if (latestHist == null || latestHist.price != listing.price) {
                            db.priceHistoryDao().insert(
                                ua.magerko.olxradar.data.model.PriceHistory(
                                    watchId = watch.id,
                                    listingId = listing.id,
                                    price = listing.price,
                                    currency = OlxParser.normalizeCurrency(listing.currency).ifBlank { listing.currency },
                                    recordedAt = now
                                )
                            )
                        }
                    }
                    initialSaved++
                }
                db.watchDao().setInitialized(watch.id, true)
                if (initialSaved > 0) {
                    ua.magerko.olxradar.data.backup.PersistentStorageManager.scheduleAutoBackup(this@MonitorService)
                }
            } else {
                val inQuietHours = isQuietHours(prefs)
                val isSnoozed = watch.snoozedUntil != null && now < watch.snoozedUntil
                var savedAnyNew = false

                for (listing in matchedListings) {
                    if (RadarState.status.value.isPaused) return

                    val isNew = listing.id !in seenIds
                    val seenItem = if (!isNew) db.seenItemDao().getSeenItem(watch.id, listing.id) else null
                    val isPushup = if (!isNew && watch.notifyMode == "new_pushup" && listing.pushedAt != null) {
                        val lastPushed = seenItem?.lastPushedAt
                        lastPushed == null || listing.pushedAt > lastPushed
                    } else false

                    // Отслеживание снижения цены с учётом совпадения/конвертации валют
                    val oldPrice = seenItem?.lastPrice
                    val oldCurrency = OlxParser.normalizeCurrency(seenItem?.lastCurrency)
                    val curCurrency = OlxParser.normalizeCurrency(listing.currency)
                    val normalizedOldPrice = when {
                        oldPrice == null || oldPrice <= 0 -> null
                        oldCurrency.isBlank() || curCurrency.isBlank() || oldCurrency == curCurrency -> oldPrice
                        else -> OlxParser.convertPriceToTargetCurrency(
                            oldPrice, oldCurrency, curCurrency, appSnap.usdToUahRate, appSnap.eurToUahRate
                        )
                    }
                    val isPriceDrop = if (!isNew && appSnap.trackPriceDrops && listing.price != null && normalizedOldPrice != null && normalizedOldPrice > 0 && listing.price < normalizedOldPrice) {
                        val dropPct = ((normalizedOldPrice - listing.price).toDouble() / normalizedOldPrice) * 100.0
                        dropPct >= appSnap.minPriceDropPercent
                    } else false

                    if (isNew || isPushup || isPriceDrop) {
                        val sellerKey = listing.sellerName?.trim()?.lowercase()
                        if (!sellerKey.isNullOrBlank() && db.sellerBlacklistDao().isBlocked(sellerKey)) {
                            db.seenItemDao().markSeenAndPrune(
                                watchId = watch.id,
                                listingId = listing.id,
                                lastPushedAt = listing.pushedAt,
                                lastPrice = listing.price,
                                lastCurrency = curCurrency.takeIf { it.isNotBlank() }
                            )
                            continue
                        }
                        val reason = when {
                            isNew -> "new"
                            isPriceDrop -> "price_drop"
                            else -> "pushup"
                        }

                        // Multi-stage filtering: evaluate with AI filter engine
                        val aiEval = aiFilterEngine.evaluateListing(listing, effectiveWatch)
                        kotlinx.coroutines.delay(500)

                        // Mark seen and update last price/currency/pushed_at
                        db.seenItemDao().markSeenAndPrune(
                            watchId = watch.id,
                            listingId = listing.id,
                            lastPushedAt = listing.pushedAt,
                            lastPrice = listing.price,
                            lastCurrency = curCurrency.takeIf { it.isNotBlank() }
                        )

                        if (aiEval.shouldSaveFinding) {
                            val condScore = OlxParser.conditionScore(listing)
                            val dealScore = OlxParser.computeDealScore(
                                listing, effectiveWatch, batchMedian, condScore, appSnap.usdToUahRate, appSnap.eurToUahRate
                            )
                            val photosJson = listing.photoUrls.take(5).takeIf { it.isNotEmpty() }?.let {
                                org.json.JSONArray(it).toString()
                            }
                            val existingFinding = db.findingDao().getFindingByWatchAndListing(watch.id, listing.id)
                            val finding = if (existingFinding != null) {
                                existingFinding.copy(
                                    title = listing.title,
                                    description = listing.description,
                                    price = listing.price,
                                    currency = listing.currency,
                                    cityName = listing.cityName,
                                    photoUrl = listing.photoUrl ?: existingFinding.photoUrl,
                                    reason = reason,
                                    foundAt = now,
                                    isRead = false,
                                    conditionScore = condScore,
                                    aiScore = aiEval.result?.confidence ?: existingFinding.aiScore,
                                    aiStatus = aiEval.status,
                                    aiProduct = aiEval.result?.detectedProduct ?: existingFinding.aiProduct,
                                    aiModel = aiEval.result?.detectedModel ?: existingFinding.aiModel,
                                    aiPhotoMatch = aiEval.result?.photoMatchesTitle ?: existingFinding.aiPhotoMatch,
                                    aiReasons = aiEval.result?.reasons?.joinToString("\n• ", prefix = "• ") ?: existingFinding.aiReasons,
                                    aiRejectReason = aiEval.result?.rejectReason ?: existingFinding.aiRejectReason,
                                    aiDetailsJson = aiEval.result?.toJsonString() ?: existingFinding.aiDetailsJson,
                                    isDelivery = listing.isDelivery,
                                    oldPrice = if (isPriceDrop) normalizedOldPrice else existingFinding.oldPrice,
                                    dealScore = dealScore,
                                    photoUrlsJson = photosJson ?: existingFinding.photoUrlsJson,
                                    sellerName = listing.sellerName ?: existingFinding.sellerName
                                )
                            } else {
                                Finding(
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
                                    conditionScore = condScore,
                                    aiScore = aiEval.result?.confidence,
                                    aiStatus = aiEval.status,
                                    aiProduct = aiEval.result?.detectedProduct,
                                    aiModel = aiEval.result?.detectedModel,
                                    aiPhotoMatch = aiEval.result?.photoMatchesTitle,
                                    aiReasons = aiEval.result?.reasons?.joinToString("\n• ", prefix = "• "),
                                    aiRejectReason = aiEval.result?.rejectReason,
                                    aiDetailsJson = aiEval.result?.toJsonString(),
                                    isDelivery = listing.isDelivery,
                                    oldPrice = if (isPriceDrop) normalizedOldPrice else null,
                                    dealScore = dealScore,
                                    photoUrlsJson = photosJson,
                                    sellerName = listing.sellerName
                                )
                            }

                            val savedFinding = if (existingFinding != null) {
                                db.findingDao().updateFinding(finding)
                                finding
                            } else {
                                val insertedId = db.findingDao().insertFinding(finding)
                                if (finding.id == 0L) finding.copy(id = insertedId) else finding
                            }
                            if (listing.price != null && listing.price > 0) {
                                val latestHist = db.priceHistoryDao().getLatestForListing(listing.id)
                                if (latestHist == null || latestHist.price != listing.price) {
                                    db.priceHistoryDao().insert(
                                        ua.magerko.olxradar.data.model.PriceHistory(
                                            watchId = watch.id,
                                            listingId = listing.id,
                                            price = listing.price,
                                            currency = curCurrency.ifBlank { listing.currency },
                                            recordedAt = now
                                        )
                                    )
                                }
                            }
                            savedAnyNew = true

                            val shouldNotify = if (aiEval.status == ua.magerko.olxradar.ai.AiStatus.REJECTED) {
                                aiPreferences.isNotifyRejected
                            } else if (isPriceDrop && !appSnap.notifPriceDrops) {
                                false
                            } else {
                                aiEval.shouldNotify
                            }

                            if (!inQuietHours && !isSnoozed && shouldNotify) {
                                notificationHelper.sendAlertNotificationAsync(savedFinding, serviceScope)
                                TelegramNotifier.sendFindingAsync(this@MonitorService, savedFinding, effectiveWatch.title, serviceScope)
                            }
                        }
                    }
                }
                if (savedAnyNew) {
                    ua.magerko.olxradar.data.backup.PersistentStorageManager.scheduleAutoBackup(this@MonitorService)
                }
            }

            // B1: Success! Update counters and lastChecked ONLY upon success
            consecutiveErrorsMap[watch.id] = 0
            AppLog.i(TAG, "Опрос успешен: '${watch.title}'")
            db.watchDao().updateSuccess(watch.id, now)
            RadarState.recordSuccess(watch.id, now)

            if (!RadarState.status.value.isPaused) {
                val updatedTimeStr = timeFormat.format(Date(now))
                updateNotification(getString(R.string.service_status_updated, updatedTimeStr))
            }

            // Scale safe interval based on total active radars to protect from CloudFront throttling (each radar >= activeCount * 7s)
            val baseIntervalSec = watch.intervalSecOverride ?: globalIntervalSec
            val minSafeIntervalSec = (activeWatchesCount * 7).coerceAtLeast(20)
            val effectiveIntervalSec = baseIntervalSec.coerceAtLeast(minSafeIntervalSec)
            val jitter = 1.0 + Random.nextDouble(-0.08, 0.08)
            val nextIntervalMs = (effectiveIntervalSec * 1000L * jitter).toLong().coerceAtLeast(5000L)
            nextRunMap[watch.id] = now + nextIntervalMs

        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            // B1: Error handling — classify error, update DAO, RadarState, Log.w
            val (errorMsg, httpCode) = classifyError(e)
            AppLog.w(TAG, "Ошибка радара '${watch.query}': $errorMsg", e)

            if (httpCode == 500 || httpCode == 429 || httpCode == 503) {
                // If OLX is overwhelmed or rate limiting, set global cooldown so other radars don't cascade into the block
                globalDomainCooldownUntil = now + 25000L
                AppLog.w(TAG, "Включён общий тайм-аут на 25с для всех радаров (HTTP $httpCode)")
            }

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
        if (::chromiumBridge.isInitialized) {
            chromiumBridge.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
