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

        AppLog.i(TAG, "Очередь ИИ: начало обработки '${listing.title.take(50)}' (радар '${watch.title}')")

        // Запускаем анализ без 2-минутного таймаута
        val eval = eng.evaluateListingDirect(listing, watch)

        // Извлекаем из очереди в AiRuntime
        AiRuntime.dequeue(listing.id)

        // Обновляем запись находки в базе данных
        try {
            val existing = db.findingDao().getFindingByListingId(listing.id)
            if (existing != null) {
                val updated = existing.copy(
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

                // Если лот подошёл — отправляем уведомление пользователю
                if (eval.status == AiStatus.MATCH || eval.status == AiStatus.NEEDS_REVIEW) {
                    notificationHelper?.sendAlertNotificationAsync(updated, scope)
                }
                AppLog.i(TAG, "Очередь ИИ: лот '${listing.title.take(50)}' обработан со статусом ${eval.status}")
            } else {
                AppLog.w(TAG, "Очередь ИИ: находка для ${listing.id} не найдена в базе")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Ошибка обновления находки из очереди", e)
        }
    }
}
