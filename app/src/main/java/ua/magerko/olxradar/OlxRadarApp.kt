package ua.magerko.olxradar

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.service.NotificationHelper
import ua.magerko.olxradar.ui.compose.applyThemeMode
import ua.magerko.olxradar.ui.compose.ThemeMode
import ua.magerko.olxradar.ui.compose.ThemePreferences
import ua.magerko.olxradar.util.AppLog

class OlxRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Применяем сохранённую тему оформления (Кибернетика / Liquid Glass / Белая / Серая)
        val mode = ThemePreferences.get(this)
        applyThemeMode(mode)
        AppCompatDelegate.setDefaultNightMode(
            if (mode == ThemeMode.CYBER || mode == ThemeMode.GLASS) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        // Initialize database
        AppDatabase.getInstance(this)
        // Initialize notification channels
        AppLog.i("App", "OLX Radar запущен, версия ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        NotificationHelper(this)
    }
}
