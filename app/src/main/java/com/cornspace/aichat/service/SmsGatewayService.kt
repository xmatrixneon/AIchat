package com.cornspace.aichat.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var smsForwardedCount = 0
    private var serviceStartTime = 0L

    companion object {
        private const val TAG = "SmsGatewayService"
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
            val intent = Intent(context, SmsGatewayService::class.java)
            context.stopService(intent)
        }

        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        serviceStartTime = System.currentTimeMillis()
        isRunning = true

        acquireWakeLock()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Start foreground immediately
        startForeground(Constants.NOTIFICATION_ID, createNotification())

        // Handle incoming SMS data from receiver
        intent?.let { handleIntent(it) }

        // Connect WebSocket if not connected
        if (!webSocketClient.isConnected()) {
            connectWebSocket()
        }

        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val sender = intent.getStringExtra("sms_sender")
        val message = intent.getStringExtra("sms_message")

        if (sender != null && message != null) {
            val timestamp = intent.getLongExtra("sms_timestamp", System.currentTimeMillis())
            val simSlot = intent.getIntExtra("sms_sim_slot", 0)
            val receiverNumber = intent.getStringExtra("sms_receiver_number")
            val simCarrier = intent.getStringExtra("sim_carrier")
            val simNetworkType = intent.getStringExtra("sim_network_type")

            handleIncomingSms(
                sender = sender,
                message = message,
                timestamp = timestamp,
                simSlot = simSlot,
                receiverNumber = receiverNumber,
                simCarrier = simCarrier,
                simNetworkType = simNetworkType
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        isRunning = false
        webSocketClient.disconnect()
        serviceScope.cancel()
        releaseWakeLock()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, restarting service")

        // Restart service
        val restartIntent = Intent(applicationContext, SmsGatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AIChat::SmsGatewayWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "SMS Gateway Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SMS Gateway running in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val uptimeMinutes = (System.currentTimeMillis() - serviceStartTime) / 1000 / 60
        val statusText = buildString {
            append("Active")
            if (smsForwardedCount > 0) append(" • $smsForwardedCount SMS forwarded")
            if (uptimeMinutes > 0) append(" • ${uptimeMinutes}m uptime")
        }

        return androidx.core.app.NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SMS Gateway")
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, createNotification())
    }

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
                    onMessageReceived = { message ->
                        handleWebSocketMessage(message)
                    },
                    onConnectionStateChanged = { state ->
                        handleConnectionStateChange(state)
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting WebSocket", e)
            }
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.Connected -> {
                Log.d(TAG, "Connected to server: ${message.connectionId}")
            }
            is WebSocketMessage.Registered -> {
                Log.d(TAG, "Device registered: ${message.deviceId}")
                serviceScope.launch {
                    settingsDataStore.setDeviceId(message.deviceId)
                }
            }
            is WebSocketMessage.Ping -> {
                webSocketClient.send(WebSocketMessage.Pong(message.timestamp))
            }
            is WebSocketMessage.Ack -> {
                Log.d(TAG, "Message acknowledged: ${message.messageId}")
            }
            is WebSocketMessage.Error -> {
                Log.e(TAG, "Server error: ${message.code} - ${message.message}")
            }
            else -> {
                Log.d(TAG, "Unknown message type: ${message.type}")
            }
        }
    }

    private fun handleConnectionStateChange(state: ConnectionState) {
        Log.d(TAG, "Connection state: $state")
        updateNotification()
    }

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

                smsForwardedCount++
                updateNotification()

                Log.d(TAG, "SMS forwarded successfully (total: $smsForwardedCount)")

            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SMS", e)
            }
        }
    }
}
