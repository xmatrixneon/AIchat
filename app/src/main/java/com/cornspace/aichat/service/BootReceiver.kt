package com.cornspace.aichat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cornspace.aichat.data.local.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action !in validActions) return

        Log.d(TAG, "Boot completed received — action: ${intent.action}")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverUrl = settingsDataStore.serverUrl.first()

                // Don't start if server URL not configured
                if (serverUrl.isBlank()) {
                    Log.d(TAG, "No server URL configured, skipping auto-start")
                    return@launch
                }

                val serviceEnabled = settingsDataStore.serviceEnabled.first()
                if (serviceEnabled) {
                    Log.d(TAG, "Auto-starting SMS gateway service")
                    SmsGatewayService.startService(context)
                } else {
                    Log.d(TAG, "Service auto-start disabled by user")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during boot auto-start", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}