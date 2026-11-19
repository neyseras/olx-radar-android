package ua.magerko.olxradar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * B12: Receiver for BOOT_COMPLETED.
 * In Android 14/15, starting a dataSync foreground service directly from BOOT_COMPLETED is forbidden
 * (ForegroundServiceStartNotAllowedException).
 * Instead, if the service was running prior to reboot, we display a heads-up notification
 * prompting the user to resume monitoring.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean(MonitorService.KEY_SERVICE_WAS_RUNNING, false)
            if (wasRunning) {
                NotificationHelper(context).sendResumeServiceNotification()
            }
        }
    }
}
