package ua.magerko.olxradar

import android.app.Application
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import java.io.File
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.service.NotificationHelper
import ua.magerko.olxradar.ui.compose.applyThemeMode
import ua.magerko.olxradar.ui.compose.ThemeMode
import ua.magerko.olxradar.ui.compose.ThemePreferences
import ua.magerko.olxradar.util.AppLog

class OlxRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()

        installCrashGuard()
        // Применяем сохранённую тему оформления (тёмные темы: Кибернетика / Liquid Glass / Изумруд / Аметист)
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
    }

    /** Сохраняем стектрейс незапланированного завершения для показа пользователю. */
    private fun installCrashGuard() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(filesDir, "last_crash.txt").writeText(
                    "thread: ${thread.name}\n\n" + android.util.Log.getStackTraceString(error)
                )
                ua.magerko.olxradar.util.AppLog.e(
                    "CrashGuard", "Незапланированное завершение (${thread.name})", error
                )
            } catch (_: Throwable) {
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }
}
