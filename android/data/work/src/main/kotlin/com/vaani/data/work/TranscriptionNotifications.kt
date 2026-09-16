package com.vaani.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Foreground-service notification plumbing for the durable transcription worker.
 * A single low-importance channel; the notification is non-dismissable while the
 * worker holds the foreground, so the OS won't kill on-device Whisper mid-run
 * when the app is backgrounded (Android 12+ background-execution limits).
 */
internal object TranscriptionNotifications {
    const val CHANNEL_ID = "vaani.transcription"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transcription",
            NotificationManager.IMPORTANCE_LOW, // silent, no sound/vibration
        ).apply { description = "Processing your voice notes on-device." }
        mgr.createNotificationChannel(channel)
    }

    fun build(context: Context, text: String): Notification {
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

    /** API-aware ForegroundInfo (DATA_SYNC type required on Android 10+/29+). */
    fun foregroundInfo(context: Context, text: String): androidx.work.ForegroundInfo {
        val notification = build(context, text)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            androidx.work.ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
