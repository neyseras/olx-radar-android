package ua.magerko.olxradar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.ui.MainActivity
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

class NotificationHelper(private val context: Context) {

    companion object {
        const val SERVICE_CHANNEL_ID = "olx_radar_service"
        const val ALERTS_CHANNEL_ID = "olx_radar_alerts"
        const val SERVICE_NOTIFICATION_ID = 1001

        /**
         * B7: Stable hash from 64-bit Long to 32-bit Int without truncating.
         */
        fun toNotificationId(id: Long): Int {
            return (id xor (id ushr 32)).toInt()
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // B6: Maximum 2 concurrent image downloads
    private val imageDownloadSemaphore = Semaphore(2)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.service_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            val alertChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildServiceNotification(statusText: String, isPaused: Boolean): Notification {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pAppIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(context, MonitorService::class.java).apply {
            action = if (isPaused) MonitorService.ACTION_RESUME else MonitorService.ACTION_PAUSE
        }
        val pToggleIntent = PendingIntent.getService(
            context,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionText = if (isPaused) context.getString(R.string.action_resume) else context.getString(R.string.action_pause)
        val toggleIcon = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause

        val stopIntent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentIntent(pAppIntent)
            .setOngoing(true)
            .addAction(toggleIcon, toggleActionText, pToggleIntent)
            .addAction(R.drawable.ic_clear, context.getString(R.string.action_stop), pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * B6: Asynchronously downloads image with 3s timeout, limits concurrent downloads to 2,
     * and sends alert notification without blocking the caller loop.
     */
    fun sendAlertNotificationAsync(finding: Finding, scope: CoroutineScope) {
        scope.launch {
            var bitmap: Bitmap? = null
            if (!finding.photoUrl.isNullOrEmpty()) {
                bitmap = try {
                    withTimeoutOrNull(3000L) {
                        imageDownloadSemaphore.withPermit {
                            withContext(Dispatchers.IO) {
                                downloadBitmap(finding.photoUrl)
                            }
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            sendAlertNotification(finding, bitmap)
        }
    }

    /**
     * B7: ID and requestCode using stable 64->32 bit hash. Grouped by watchId.
     */
    fun sendAlertNotification(finding: Finding, bitmap: Bitmap? = null) {
        val notifId = toNotificationId(finding.listingId)
        val requestCode = notifId
        val groupKey = "watch_group_${finding.watchId}"

        val appIntent = Intent(context, ua.magerko.olxradar.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ua.magerko.olxradar.ui.MainActivity.EXTRA_OPEN_FINDING_ID, finding.id)
            if (finding.aiStatus == ua.magerko.olxradar.ai.AiStatus.REJECTED) {
                putExtra(ua.magerko.olxradar.ui.MainActivity.EXTRA_OPEN_REJECTED_TAB, true)
            }
        }
        val pIntent = PendingIntent.getActivity(
            context,
            requestCode,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(finding.url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pBrowserIntent = PendingIntent.getActivity(
            context,
            requestCode + 1000000,
            browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priceStr = if (finding.price != null) {
            val formatter = NumberFormat.getInstance(Locale("uk", "UA"))
            "${formatter.format(finding.price)} ${finding.currency ?: "UAH"}"
        } else {
            context.getString(R.string.price_negotiable)
        }

        val badge = if (finding.reason == "pushup") {
            context.getString(R.string.notif_badge_pushup)
        } else {
            context.getString(R.string.notif_badge_new)
        }

        val aiBadge = when (finding.aiStatus) {
            ua.magerko.olxradar.ai.AiStatus.MATCH -> finding.aiScore?.let { "[AI $it%] " } ?: "[AI ✓] "
            ua.magerko.olxradar.ai.AiStatus.NEEDS_REVIEW -> "[AI ?] "
            ua.magerko.olxradar.ai.AiStatus.REJECTED -> "[AI ✗] "
            ua.magerko.olxradar.ai.AiStatus.ERROR -> "[AI !] "
            ua.magerko.olxradar.ai.AiStatus.ANALYZING -> "[AI ...] "
            else -> ""
        }

        val title = "$aiBadge$badge$priceStr • ${finding.cityName}"
        val body = if (!finding.aiProduct.isNullOrBlank()) {
            "${finding.title}\n🤖 ${finding.aiProduct}${finding.aiModel?.let { " ($it)" } ?: ""}"
        } else {
            finding.title
        }

        val builder = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(R.drawable.ic_open, context.getString(R.string.action_open_olx), pBrowserIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(body)
            )
        }

        notificationManager.notify(notifId, builder.build())

        // Send group summary notification (B7)
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pSummaryIntent = PendingIntent.getActivity(
            context,
            toNotificationId(finding.watchId + 1_000_000_000L),
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_group_summary))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pSummaryIntent)
            .build()

        notificationManager.notify(toNotificationId(finding.watchId + 1_000_000_000L), summaryNotification)
    }

    /**
     * B9: Error notification after 5 consecutive failures.
     */
    fun sendErrorAlertNotification(watchTitle: String) {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pAppIntent = PendingIntent.getActivity(
            context,
            9999,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle(context.getString(R.string.notif_error_title))
            .setContentText(context.getString(R.string.notif_error_body, watchTitle))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_error_body, watchTitle)))
            .setContentIntent(pAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(8888, notification)
    }

    /**
     * B12: Resume notification after system reboot when auto-start cannot launch dataSync service.
     */
    fun sendResumeServiceNotification() {
        val startIntent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
        }
        val pStartIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                7777,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                7777,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_resume_after_boot))
            .setContentIntent(pStartIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(7777, notification)
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }
}
