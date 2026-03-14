package com.cornspace.aichat.util

object Constants {
    // App Info
    const val APP_VERSION = "1.0.0"

    // Service
    const val NOTIFICATION_CHANNEL_ID = "SmsGatewayService"
    const val NOTIFICATION_ID         = 1

    // WebSocket
    const val HEARTBEAT_INTERVAL      = 60_000L  // 60 seconds
    const val CONNECTION_TIMEOUT      = 30_000L  // 30 seconds
    const val RECONNECT_DELAY_INITIAL = 1_000L   // 1 second
    const val RECONNECT_DELAY_MAX     = 60_000L  // 60 seconds

    // FIX #12: Removed SMS_PROCESSING_TIMEOUT, MAX_RETRY_ATTEMPTS, and RETRY_DELAY.
    // These were defined but never referenced anywhere in the codebase, suggesting
    // retry logic was planned but never implemented. Dead constants are removed to
    // avoid implying retry behaviour that doesn't exist. If retry logic is added
    // later, restore them at that point.
}