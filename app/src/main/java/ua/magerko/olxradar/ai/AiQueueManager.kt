package ua.magerko.olxradar.ai

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.service.NotificationHelper
import ua.magerko.olxradar.util.AppLog

/**
 * Менеджер очереди отложенных ИИ-запросов.
 * Если обработка лота в основном цикле превышает 2 минуты,
 * запрос отправляется сюда и обрабатывается асинхронно в фоне («ИИ-поток»),
 * не блокируя основной радар.
 */
object AiQueueManager {

    private const val TAG = "AiQueueManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: AiFilterEngine? = null
    private var database: AppDatabase? = null
    private var notificationHelper: NotificationHelper? = null
    private var prefs: SharedPreferences? = null

    data class QueuedTask(
        val listing: Listing,
        val watch: Watch,
        val enqueuedAtMs: Long = System.currentTimeMillis()
    )

    private val tasksChannel = Channel<QueuedTask>(Channel.UNLIMITED)
    private val activeJobMutex = Mutex()
    private var isWorkerRunning = false

    private val cancelledListingIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
    private val enqueuedListingIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    fun cancel(listingId: Long) {
        if (enqueuedListingIds.remove(listingId)) {
            cancelledListingIds.add(listingId)
        }
        AiRuntime.dequeue(listingId)
    }

    fun cancelAndDelete(listingId: Long) {
        cancel(listingId)
        scope.launch(Dispatchers.IO) {
            database?.findingDao()?.deleteFindingByListingId(listingId)
        }
    }

    fun cancelAll() {
        cancelledListingIds.clear()
        enqueuedListingIds.clear()
        AiRuntime.clearQueue()
    }

    fun init(
        engine: AiFilterEngine,
        database: AppDatabase,
        notificationHelper: NotificationHelper,
        prefs: SharedPreferences? = null
    ) {
        this.engine = engine
        this.database = database
        this.notificationHelper = notificationHelper
        this.prefs = prefs
        ensureWorkerStarted()
    }

    fun enqueue(listing: Listing, watch: Watch) {
        enqueuedListingIds.add(listing.id)
        val task = QueuedTask(listing, watch)
        tasksChannel.trySend(task)
        ensureWorkerStarted()
    }

    private fun ensureWorkerStarted() {
        scope.launch {
            activeJobMutex.withLock {
                if (isWorkerRunning) return@withLock
                isWorkerRunning = true
                launchQueueWorker()
            }
        }
    }

    private fun launchQueueWorker() = scope.launch {
        AppLog.i(TAG, "Воркер очереди ИИ-потока запущен")
        try {
            for (task in tasksChannel) {
                processQueuedTask(task)
                // Пауза между запросами очереди для соблюдения лимитов Groq
                delay(3000L)
            }
        } catch (_: CancellationException) {
            AppLog.i(TAG, "Воркер очереди ИИ-потока остановлен")
        } catch (e: Exception) {
            AppLog.e(TAG, "Ошибка в воркере очереди ИИ-потока", e)
        } finally {
            activeJobMutex.withLock {
                isWorkerRunning = false
            }
        }
    }

    private suspend fun processQueuedTask(task: QueuedTask) {
        val eng = engine
        val db = database
        if (eng == null || db == null) {
            AppLog.w(TAG, "AiQueueManager не инициализирован для обработки ${task.listing.id}")
            return
        }

        val listing = task.listing
        val watch = task.watch

        // 1. Проверяем, не отменил/удалил ли пользователь этот лот
        if (cancelledListingIds.remove(listing.id)) {
            AppLog.i(TAG, "Очередь ИИ: лот '${listing.title.take(50)}' [ID: ${listing.id}] отменён пользователем, пропуск")
            AiRuntime.dequeue(listing.id)
            return
        }

        // 2. Проверяем, существует ли ещё находка в базе данных (если удалена — не тратим запросы к ИИ)
        val existing = db.findingDao().getFindingByListingId(listing.id)
        if (existing == null) {
            AppLog.i(TAG, "Очередь ИИ: лот '${listing.title.take(50)}' [ID: ${listing.id}] удалён из базы, отмена анализа")
            AiRuntime.dequeue(listing.id)
            return
        }

        AppLog.i(TAG, "Очередь ИИ: начало обработки '${listing.title.take(50)}' (радар '${watch.title}')")

        // Извлекаем из очереди ожидания сразу перед началом анализа
        AiRuntime.dequeue(listing.id)

        // Запускаем анализ без 2-минутного таймаута
        val eval = eng.evaluateListingDirect(listing, watch)

        // Проверяем, не удалил ли пользователь лот, пока шёл анализ нейросетью
        if (cancelledListingIds.remove(listing.id)) {
            AppLog.i(TAG, "Очередь ИИ: лот '${listing.title.take(50)}' был отменён во время анализа, результат отброшен")
            return
        }

        // Обновляем запись находки в базе данных
        try {
            val freshExisting = db.findingDao().getFindingByListingId(listing.id)
            if (freshExisting != null) {
                val updated = freshExisting.copy(
                    aiScore = eval.result?.confidence,
                    aiStatus = eval.status,
                    aiProduct = eval.result?.detectedProduct,
                    aiModel = eval.result?.detectedModel,
                    aiPhotoMatch = eval.result?.photoMatchesTitle,
                    aiReasons = eval.result?.reasons?.joinToString("\n• ", prefix = "• "),
                    aiRejectReason = eval.result?.rejectReason,
                    aiDetailsJson = eval.result?.toJsonString()
                )
                db.findingDao().updateFinding(updated)

                // Если лот подошёл и сейчас не тихие часы/снуз — отправляем уведомление пользователю
                val inQuietHours = prefs?.let {
                    ua.magerko.olxradar.service.MonitorService.isQuietHoursStatic(it)
                } ?: false
                val isSnoozed = watch.snoozedUntil != null && System.currentTimeMillis() < watch.snoozedUntil
                if ((eval.status == AiStatus.MATCH || eval.status == AiStatus.NEEDS_REVIEW) && !inQuietHours && !isSnoozed) {
                    notificationHelper?.sendAlertNotificationAsync(updated, scope)
                }
                enqueuedListingIds.remove(listing.id)
                AppLog.i(TAG, "Очередь ИИ: лот '${listing.title.take(50)}' обработан со статусом ${eval.status}")
            } else {
                AppLog.i(TAG, "Очередь ИИ: лот '${listing.title.take(50)}' был удалён из базы во время анализа")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Ошибка обновления находки из очереди", e)
        }
    }
}
