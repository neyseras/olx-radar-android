package ua.magerko.olxradar

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.service.MonitorWatchdogWorker
import ua.magerko.olxradar.service.NotificationHelper
import ua.magerko.olxradar.ui.compose.applyThemeMode
import ua.magerko.olxradar.ui.compose.ThemePreferences
import ua.magerko.olxradar.util.AppLog

class OlxRadarApp : Application(), ImageLoaderFactory, Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        installCrashGuard()
        AppPreferences.init(this)

        // Применяем сохранённую тему оформления
        val mode = ThemePreferences.get(this)
        applyThemeMode(mode)
        AppCompatDelegate.setDefaultNightMode(
            if (mode.isDark) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        // Initialize database
        AppDatabase.getInstance(this)
        // Initialize AI thought logger
        ua.magerko.olxradar.ai.AiThoughtLogger.init(this)
        // Initialize notification channels
        AppLog.i("App", "OLX Radar запущен, версия ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        NotificationHelper(this)
        if (AppPreferences.state.value.watchdogEnabled) {
            MonitorWatchdogWorker.schedule(this)
        }
    }

    /**
     * Общий оптимизированный ImageLoader для Coil:
     * 20% RAM под кэш декодированных фото и 64 МБ на диске, чтобы лента с фото листалась без лагов.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .respectCacheHeaders(false)
            .build()
    }

    /** Сохраняем стектрейс незапланированного завершения для показа пользователю. */
    private fun installCrashGuard() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(filesDir, "last_crash.txt").writeText(
                    "thread: ${thread.name}\n\n" + android.util.Log.getStackTraceString(error)
                )
                AppLog.e(
                    "CrashGuard", "Незапланированное завершение (${thread.name})", error
                )
            } catch (_: Throwable) {
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }
}
