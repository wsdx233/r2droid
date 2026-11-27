package top.wsdx233.r2droid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.activity.MainActivity
import top.wsdx233.r2droid.core.data.prefs.SettingsManager

class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        const val CHANNEL_ID = "r2droid_keep_alive"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start keep-alive service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.keep_alive_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsManager.initialize(applicationContext)
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launchIntent = (packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            NotificationCompat.Builder(this)
        }

        val useFluidCloud = SettingsManager.useFluidCloudNotification
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(getString(R.string.keep_alive_notification_title))
            .bigText(getString(R.string.keep_alive_notification_text))

        val notification = builder
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_text))
            .setSmallIcon(R.drawable.ic_stat_r2droid_live)
            .setColor(0xFF40C47A.toInt())
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .also { b ->
                if (useFluidCloud) {
                    // Fluid-cloud / Live Updates style. Android 16+ can request promoted ongoing.
                    b.setStyle(bigTextStyle)
                    if (Build.VERSION.SDK_INT >= 36) {
                        b.setRequestPromotedOngoing(true)
                        b.setShortCriticalText("R2Droid")
                    }
                }
            }
            .build()

        try {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to promote keep-alive service to foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
