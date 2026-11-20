package ua.magerko.olxradar.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ua.magerko.olxradar.util.AppLog

/**
 * Модель обрабатываемого или недавно обработанного лота нейросетью.
 */
data class LiveAiItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val listingId: Long = 0L,
    val title: String,
    val url: String = "",
    val photoUrl: String? = null,
    val price: Int? = null,
    val currency: String? = "грн",
    val radarTitle: String? = null,
    val photosTotal: Int = 0,
    val status: String,      // "PROCESSING", "APPROVED", "REJECTED", "NEEDS_REVIEW", "CACHED", "ERROR"
    val confidence: Int? = null,
    val reasons: String? = null,
    val startedAtMs: Long = System.currentTimeMillis(),
    val finishedAtMs: Long = System.currentTimeMillis()
) {
    val durationSec: Float
        get() = ((finishedAtMs - startedAtMs).coerceAtLeast(0) / 1000f)
}

/**
 * Глобальный «статус нейросети»: что ИИ обрабатывает прямо сейчас,
 * какие ссылки сейчас в анализе и история последних проверенных ссылок.
 */
object AiRuntime {

    data class Finished(
        val title: String,
        val radar: String?,
        val status: String,      // AiStatus.APPROVED / REJECTED / NEEDS_REVIEW / ERROR
        val confidence: Int?,
        val atMs: Long
    )

    data class Activity(
        val isBusy: Boolean = false,
        val title: String? = null,
        val radar: String? = null,
        val photosTotal: Int = 0,
        val startedAtMs: Long = 0L,
        val lastFinished: Finished? = null,
        val currentItem: LiveAiItem? = null,
        val history: List<LiveAiItem> = emptyList(),
        val totalProcessed: Int = 0,
        val approvedCount: Int = 0,
        val rejectedCount: Int = 0
    ) {
        /** Секунд с начала текущего анализа. */
        val busySeconds: Int
            get() = if (isBusy) ((System.currentTimeMillis() - startedAtMs) / 1000).toInt() else 0
    }

    private val _activity = MutableStateFlow(Activity())
    val activity: StateFlow<Activity> = _activity.asStateFlow()

    fun start(
        listingId: Long = 0L,
        listingTitle: String,
        url: String = "",
        photoUrl: String? = null,
        price: Int? = null,
        currency: String? = "грн",
        radarTitle: String?,
        photosTotal: Int
    ) {
        val now = System.currentTimeMillis()
        val item = LiveAiItem(
            listingId = listingId,
            title = listingTitle,
            url = url,
            photoUrl = photoUrl,
            price = price,
            currency = currency,
            radarTitle = radarTitle,
            photosTotal = photosTotal,
            status = "PROCESSING",
            startedAtMs = now,
            finishedAtMs = now
        )
        _activity.update { cur ->
            cur.copy(
                isBusy = true,
                title = listingTitle,
                radar = radarTitle,
                photosTotal = photosTotal,
                startedAtMs = now,
                currentItem = item
            )
        }
    }

    fun start(listingTitle: String, radarTitle: String?, photosTotal: Int) {
        start(
            listingId = 0L,
            listingTitle = listingTitle,
            url = "",
            photoUrl = null,
            price = null,
            currency = "грн",
            radarTitle = radarTitle,
            photosTotal = photosTotal
        )
    }

    fun finish(status: String, confidence: Int?, reasons: String? = null) {
        val now = System.currentTimeMillis()
        _activity.update { cur ->
            val finishedItem = cur.currentItem?.copy(
                status = status,
                confidence = confidence,
                reasons = reasons,
                finishedAtMs = now
            ) ?: LiveAiItem(
                listingId = 0L,
                title = cur.title ?: "Объявление",
                url = "",
                radarTitle = cur.radar,
                photosTotal = cur.photosTotal,
                status = status,
                confidence = confidence,
                reasons = reasons,
                startedAtMs = cur.startedAtMs,
                finishedAtMs = now
            )
            val isApproved = status.equals("APPROVED", ignoreCase = true) || status.equals("MATCH", ignoreCase = true)
            val isRejected = status.equals("REJECTED", ignoreCase = true)
            val newHistory = (listOf(finishedItem) + cur.history).take(60)
            cur.copy(
                isBusy = false,
                currentItem = null,
                lastFinished = Finished(
                    title = cur.title ?: "",
                    radar = cur.radar,
                    status = status,
                    confidence = confidence,
                    atMs = now
                ),
                history = newHistory,
                totalProcessed = cur.totalProcessed + 1,
                approvedCount = if (isApproved) cur.approvedCount + 1 else cur.approvedCount,
                rejectedCount = if (isRejected) cur.rejectedCount + 1 else cur.rejectedCount
            )
        }
    }

    fun finishError(message: String) {
        val now = System.currentTimeMillis()
        _activity.update { cur ->
            val errorItem = cur.currentItem?.copy(
                status = "ERROR",
                reasons = message,
                finishedAtMs = now
            ) ?: LiveAiItem(
                listingId = 0L,
                title = cur.title ?: "Объявление",
                url = "",
                radarTitle = cur.radar,
                photosTotal = cur.photosTotal,
                status = "ERROR",
                reasons = message,
                startedAtMs = cur.startedAtMs,
                finishedAtMs = now
            )
            val newHistory = (listOf(errorItem) + cur.history).take(60)
            cur.copy(
                isBusy = false,
                currentItem = null,
                lastFinished = Finished(
                    title = cur.title ?: "",
                    radar = cur.radar,
                    status = "ERROR",
                    confidence = null,
                    atMs = now
                ),
                history = newHistory,
                totalProcessed = cur.totalProcessed + 1
            )
        }
        AppLog.w("AiRuntime", "Анализ завершился ошибкой: $message")
    }

    fun recordCached(
        listingId: Long,
        title: String,
        url: String,
        photoUrl: String?,
        price: Int?,
        currency: String?,
        radarTitle: String?,
        status: String,
        confidence: Int?,
        reasons: String?
    ) {
        val now = System.currentTimeMillis()
        val cachedItem = LiveAiItem(
            listingId = listingId,
            title = title,
            url = url,
            photoUrl = photoUrl,
            price = price,
            currency = currency,
            radarTitle = radarTitle,
            photosTotal = 0,
            status = "CACHED",
            confidence = confidence,
            reasons = reasons ?: "Из кэша (ранее проверено)",
            startedAtMs = now,
            finishedAtMs = now
        )
        val isApproved = status.equals("APPROVED", ignoreCase = true) || status.equals("MATCH", ignoreCase = true)
        val isRejected = status.equals("REJECTED", ignoreCase = true)
        _activity.update { cur ->
            val newHistory = (listOf(cachedItem) + cur.history).take(60)
            cur.copy(
                history = newHistory,
                totalProcessed = cur.totalProcessed + 1,
                approvedCount = if (isApproved) cur.approvedCount + 1 else cur.approvedCount,
                rejectedCount = if (isRejected) cur.rejectedCount + 1 else cur.rejectedCount
            )
        }
    }

    fun clearHistory() {
        _activity.update { it.copy(history = emptyList()) }
    }
}
