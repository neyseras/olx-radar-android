package ua.magerko.olxradar.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.backup.PersistentStorageManager
import ua.magerko.olxradar.data.local.AppDatabase

/**
 * Обрабатывает быстрые действия из push-уведомления (добавить в избранное без запуска UI).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADD_FAVORITE = "ua.magerko.olxradar.ACTION_ADD_FAVORITE"
        const val EXTRA_FINDING_ID = "extra_finding_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ADD_FAVORITE) return
        val findingId = intent.getLongExtra(EXTRA_FINDING_ID, -1L)
        if (findingId <= 0L) return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(appContext).findingDao()
                dao.setFavorite(findingId, true)
                dao.markAsRead(findingId, true)
                PersistentStorageManager.scheduleAutoBackup(appContext)
                if (notifId != -1) {
                    val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notifId)
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "Добавлено в избранное ⭐", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
