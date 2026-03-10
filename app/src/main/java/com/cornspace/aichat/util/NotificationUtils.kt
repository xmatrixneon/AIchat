package com.cornspace.aichat.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cornspace.aichat.MainActivity
import com.cornspace.aichat.R
import com.cornspace.aichat.util.Constants.NOTIFICATION_CHANNEL_ID
import com.cornspace.aichat.util.Constants.NOTIFICATION_ID

class NotificationUtils(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "SMS Gateway Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SMS Gateway running in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createServiceNotification(
        title: String = "SMS Gateway Active",
        content: String = "Monitoring for incoming SMS"
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun createNotification(
        smsCount: Int = 0,
        uptimeMinutes: Long = 0
    ): Notification {
        val statusText = buildString {
            append("Active")
            if (smsCount > 0) append(" • $smsCount SMS forwarded")
            if (uptimeMinutes > 0) append(" • ${uptimeMinutes}m uptime")
        }

        return createServiceNotification(content = statusText)
    }

    fun updateNotification(smsCount: Int, uptimeMinutes: Long) {
        val notification = createNotification(smsCount, uptimeMinutes)
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(Constants.NOTIFICATION_ID)
    }
}
