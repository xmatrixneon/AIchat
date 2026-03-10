package com.cornspace.aichat.data.remote

import android.util.Log
import com.cornspace.aichat.data.model.DeviceInfo
import com.cornspace.aichat.data.model.WebSocketMessage
import com.cornspace.aichat.data.model.RegisterData
import com.cornspace.aichat.data.model.HeartbeatData
import com.cornspace.aichat.data.model.SmsReceivedData
import com.cornspace.aichat.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val gson: Gson
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var reconnectAttempts = 0
    private var serverUrl: String? = null
    private var deviceInfo: DeviceInfo? = null
    private var smsForwardedCount = 0
    private var serviceStartTime = 0L
    private var onMessageCallback: ((WebSocketMessage) -> Unit)? = null
    private var onConnectionCallback: ((ConnectionState) -> Unit)? = null

    companion object {
        private const val TAG = "WebSocketClient"
    }

    fun connect(
        serverUrl: String,
        deviceInfo: DeviceInfo,
        onMessageReceived: (WebSocketMessage) -> Unit,
        onConnectionStateChanged: (ConnectionState) -> Unit
    ) {
        this.serverUrl = serverUrl
        this.deviceInfo = deviceInfo
        this.serviceStartTime = System.currentTimeMillis()
        this.onMessageCallback = onMessageReceived
        this.onConnectionCallback = onConnectionStateChanged

        if (_connectionState.value == ConnectionState.Connecting ||
            _connectionState.value == ConnectionState.Connected) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        val wsUrl = buildWsUrl(serverUrl)
        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        _connectionState.value = ConnectionState.Connecting

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
                onConnectionStateChanged(ConnectionState.Connected)

                this@WebSocketClient.deviceInfo?.let { info ->
                    sendRegistration(info)
                }

                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
                try {
                    val message = parseMessage(text)
                    onMessageReceived(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.Disconnected
                onConnectionStateChanged(ConnectionState.Disconnected)
                stopHeartbeat()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
                onConnectionStateChanged(ConnectionState.Error(t.message ?: "Unknown error"))
                stopHeartbeat()
                scheduleReconnect()
            }
        }

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("User-Agent", "AIChatGateway/${Constants.APP_VERSION} Android")
            .build()

        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        stopHeartbeat()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Connected

    fun incrementSmsCount() {
        smsForwardedCount++
    }

    fun updateDeviceInfo(info: DeviceInfo) {
        this.deviceInfo = info
    }

    fun send(message: WebSocketMessage): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send message: not connected")
            return false
        }

        return try {
            val json = serializeMessage(message)
            Log.d(TAG, "Sending: $json")
            webSocket?.send(json) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    private fun sendRegistration(deviceInfo: DeviceInfo) {
        val networkType = when {
            deviceInfo.networkInfo.networkType.contains("WiFi", ignoreCase = true) -> "wifi"
            deviceInfo.networkInfo.networkType.contains("Mobile", ignoreCase = true) -> "mobile"
            else -> "none"
        }

        val registerData = RegisterData(
            deviceId = deviceInfo.deviceId,
            name = "AIChat Gateway ${deviceInfo.deviceBrand.brand}",
            appVersion = Constants.APP_VERSION,
            osVersion = deviceInfo.osVersion,
            deviceModel = deviceInfo.model,
            manufacturer = deviceInfo.manufacturer,
            batteryLevel = deviceInfo.batteryLevel,
            isCharging = deviceInfo.batteryStatus.contains("Charging", ignoreCase = true),
            signalStrength = deviceInfo.simInfo.firstOrNull { it.isActive }?.signalStrength ?: 0,
            networkType = networkType,
            sims = deviceInfo.simInfo.map { it.toMap() }
        )

        send(WebSocketMessage.Register(registerData))
    }

    fun sendHeartbeat() {
        val info = deviceInfo ?: return
        val networkType = when {
            info.networkInfo.networkType.contains("WiFi", ignoreCase = true) -> "wifi"
            info.networkInfo.networkType.contains("Mobile", ignoreCase = true) -> "mobile"
            else -> "none"
        }

        val heartbeatData = HeartbeatData(
            deviceId = info.deviceId,
            batteryLevel = info.batteryLevel,
            isCharging = info.batteryStatus.contains("Charging", ignoreCase = true),
            signalStrength = info.simInfo.firstOrNull { it.isActive }?.signalStrength ?: 0,
            networkType = networkType,
            sims = info.simInfo.map { it.toMap() },
            uptime = (System.currentTimeMillis() - serviceStartTime) / 1000,
            smsForwarded = smsForwardedCount
        )

        send(WebSocketMessage.Heartbeat(heartbeatData))
    }

    fun sendSmsReceived(
        deviceId: String,
        sender: String,
        content: String,
        timestamp: Long,
        simSlot: Int,
        receiverNumber: String?,
        simCarrier: String?,
        simNetworkType: String?,
        networkType: String?
    ) {
        val smsData = SmsReceivedData(
            deviceId = deviceId,
            sender = sender,
            content = content,
            timestamp = timestamp,
            simSlot = simSlot + 1,
            receiverNumber = receiverNumber,
            simCarrier = simCarrier,
            simNetworkType = simNetworkType,
            networkType = networkType
        )

        if (send(WebSocketMessage.SmsReceived(smsData))) {
            smsForwardedCount++
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(Constants.HEARTBEAT_INTERVAL)
                try {
                    sendHeartbeat()
                    Log.d(TAG, "Heartbeat sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending heartbeat", e)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        val url = serverUrl
        val info = deviceInfo
        val msgCallback = onMessageCallback
        val connCallback = onConnectionCallback

        if (url == null || info == null || msgCallback == null || connCallback == null) {
            Log.d(TAG, "Not reconnecting: missing configuration")
            return
        }

        val delay = calculateReconnectDelay()
        Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt ${reconnectAttempts + 1})")

        scope.launch {
            kotlinx.coroutines.delay(delay)
            if (_connectionState.value !is ConnectionState.Connected) {
                reconnectAttempts++
                connect(url, info, msgCallback, connCallback)
            }
        }
    }

    private fun calculateReconnectDelay(): Long {
        val delay = Constants.RECONNECT_DELAY_INITIAL * (1 shl minOf(reconnectAttempts, 6))
        return minOf(delay, Constants.RECONNECT_DELAY_MAX)
    }

    private fun buildWsUrl(serverUrl: String): String {
        val url = serverUrl.trimEnd('/')
        return when {
            url.startsWith("https://") -> url.replace("https://", "wss://")
            url.startsWith("http://") -> url.replace("http://", "ws://")
            else -> "wss://$url"
        } + "/gateway"
    }

    private fun parseMessage(json: String): WebSocketMessage {
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        val type = jsonObject.get("type")?.asString ?: return WebSocketMessage.Unknown("unknown", json)

        return when (type) {
            "connected" -> {
                val connectionId = jsonObject.get("connectionId")?.asString ?: ""
                WebSocketMessage.Connected(connectionId)
            }
            "registered" -> {
                val deviceId = jsonObject.get("deviceId")?.asString ?: ""
                WebSocketMessage.Registered(deviceId)
            }
            "ack" -> {
                val data = jsonObject.getAsJsonObject("data")
                WebSocketMessage.Ack(
                    messageId = data?.get("messageId")?.asString,
                    success = data?.get("success")?.asBoolean ?: true
                )
            }
            "ping" -> {
                val timestamp = jsonObject.get("timestamp")?.asLong
                WebSocketMessage.Ping(timestamp)
            }
            "error" -> {
                val data = jsonObject.getAsJsonObject("data")
                WebSocketMessage.Error(
                    code = data?.get("code")?.asString,
                    message = data?.get("message")?.asString ?: "Unknown error"
                )
            }
            else -> WebSocketMessage.Unknown(type, json)
        }
    }

    private fun serializeMessage(message: WebSocketMessage): String {
        return when (message) {
            is WebSocketMessage.Register -> gson.toJson(mapOf(
                "type" to message.type,
                "data" to message.data
            ))
            is WebSocketMessage.Heartbeat -> gson.toJson(mapOf(
                "type" to message.type,
                "data" to message.data
            ))
            is WebSocketMessage.SmsReceived -> gson.toJson(mapOf(
                "type" to message.type,
                "data" to message.data
            ))
            is WebSocketMessage.Pong -> gson.toJson(mapOf(
                "type" to message.type,
                "timestamp" to message.timestamp
            ))
            else -> gson.toJson(mapOf("type" to message.type))
        }
    }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()

    val isConnected: Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting
}