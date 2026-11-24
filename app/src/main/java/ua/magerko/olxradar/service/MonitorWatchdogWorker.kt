package ua.magerko.olxradar.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * Сторожевой WorkManager-воркер: раз в 15 минут проверяет, не убила ли прошивка MonitorService,
 * когда у пользователя есть активные радары и мониторинг не поставлен на паузу.
 */
class MonitorWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME = "olx_radar_monitor_watchdog"
        private const val TAG = "WatchdogWorker"

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<MonitorWatchdogWorker>(15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure {
                AppLog.w(TAG, "Не удалось запланировать Watchdog: ${it.message}")
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            }
        }
    }

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        AppPreferences.init(appContext)
        if (!AppPreferences.state.value.watchdogEnabled) {
            return Result.success()
        }

        val prefs = appContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean(MonitorService.KEY_SERVICE_WAS_RUNNING, false)
        val isPaused = prefs.getBoolean(MonitorService.KEY_SERVICE_IS_PAUSED, false)
        if (!wasRunning || isPaused || RadarState.status.value.isRunning) {
            return Result.success()
        }

        val hasActiveWatches = runCatching {
            AppDatabase.getInstance(appContext).watchDao().getAllWatches().any { it.isActive }
        }.getOrDefault(false)
        if (!hasActiveWatches) return Result.success()

        AppLog.w(TAG, "MonitorService не активен при наличии активных радаров — пробуем перезапустить")
        val startIntent = Intent(appContext, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(appContext, startIntent)
        } catch (e: Exception) {
            AppLog.w(TAG, "Система запретила фоновый запуск FGS (${e.message}), показываем push для возобновления")
            NotificationHelper(appContext).sendResumeServiceNotification()
        }
        return Result.success()
    }
}
