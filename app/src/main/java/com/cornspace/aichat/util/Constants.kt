package com.cornspace.aichat.util

object Constants {
    // App Info
    const val APP_VERSION = "1.0.0"

    // Service
    const val NOTIFICATION_CHANNEL_ID = "SmsGatewayService"
    const val NOTIFICATION_ID = 1001

    // WebSocket
    const val HEARTBEAT_INTERVAL = 60_000L // 60 seconds
    const val CONNECTION_TIMEOUT = 30_000L // 30 seconds
    const val RECONNECT_DELAY_INITIAL = 1_000L // 1 second
    const val RECONNECT_DELAY_MAX = 60_000L // 60 seconds

    // SMS Processing
    const val SMS_PROCESSING_TIMEOUT = 30_000L // 30 seconds
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY = 5_000L // 5 seconds
}
