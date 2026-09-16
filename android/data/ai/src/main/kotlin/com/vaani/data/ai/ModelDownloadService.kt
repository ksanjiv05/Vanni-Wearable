package com.vaani.data.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the app process alive (and shows an ongoing notification) while model
 * downloads run, so a large `.task`/`.tar.bz2` fetch survives the user leaving
 * the app — instead of dying with the process. The actual download work still
 * lives in [OkHttpModelRepository] (with its pause/resume/cancel + StateFlow);
 * this service only holds the foreground. Started when the first download begins
 * and stopped when the last one finishes.
 *
 * Downloads are always user-initiated (tapping Download in the foreground), so
 * starting a foreground service at that moment is permitted on Android 12+.
 */
class ModelDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Downloading model…"
        startForegroundCompat(build(this, text))
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "vaani.downloads"
        private const val NOTIFICATION_ID = 4202
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            val intent = Intent(context, ModelDownloadService::class.java).putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModelDownloadService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Downloading on-device AI models." },
            )
        }

        private fun build(context: Context, text: String): Notification {
            ensureChannel(context)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Vaani")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
