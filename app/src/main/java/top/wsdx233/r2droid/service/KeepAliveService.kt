package top.wsdx233.r2droid.service

import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import top.wsdx233.r2droid.R

class KeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "r2droid_keep_alive"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.keep_alive_channel_name),
                                                  NotificationManager.IMPORTANCE_LOW
                )
                context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Some OEM "fluid cloud" / promoted-notification surfaces may pick the large/app
        // icon and then force it through a monochrome mask. The full launcher icon has an
        // opaque rounded-square background, so that path becomes a solid white blob. Keep
        // the notification large icon glyph-only too; it is colored for normal notification
        // shade rendering, while mask-based surfaces still only see the transparent >_ shape.
        val largeIcon = ContextCompat.getDrawable(this, R.drawable.ic_live_large_icon)?.toNotificationBitmap()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            NotificationCompat.Builder(this)
        }

        // Set BigTextStyle (must use one of BigTextStyle / ProgressStyle / CallStyle / MetricStyle)
        val bigTextStyle = NotificationCompat.BigTextStyle()
        .setBigContentTitle(getString(R.string.keep_alive_notification_title))
        .bigText(getString(R.string.keep_alive_notification_text))

        builder
        .setContentTitle(getString(R.string.keep_alive_notification_title))
        .setContentText(getString(R.string.keep_alive_notification_text))
        .setSmallIcon(R.drawable.ic_stat_r2droid_live)
        .apply { largeIcon?.let(::setLargeIcon) }
        .setColor(0xFF40C47A.toInt())
        .setContentIntent(pi)
        .setOngoing(true) // must ongoing
        .setStyle(bigTextStyle) // Key: Meet the real-time update style requirements
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .also { b ->
            // Android 16+ Live Updates
            if (Build.VERSION.SDK_INT >= 36) {
                b.setRequestPromotedOngoing(true)
                b.setShortCriticalText("R2Droid")
            }
        }
        .build()
        .let { notification ->
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun Drawable.toNotificationBitmap(): Bitmap {
        val size = (48 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = copyBounds()
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bounds = oldBounds
        return bitmap
    }
}
