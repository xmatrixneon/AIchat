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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Boot completed received")

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val serviceEnabled = settingsDataStore.serviceEnabled.first()

                    if (serviceEnabled) {
                        Log.d(TAG, "Auto-starting SMS gateway service")
                        SmsGatewayService.startService(context)
                    } else {
                        Log.d(TAG, "Service auto-start disabled")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking service enabled state", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
