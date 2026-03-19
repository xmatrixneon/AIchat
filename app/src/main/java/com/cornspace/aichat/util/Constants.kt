package com.cornspace.aichat.util

object Constants {
    // App Info
    const val APP_VERSION = "1.1"  // Must match build.gradle.kts versionName

    // Service
    const val NOTIFICATION_CHANNEL_ID = "SmsGatewayService"
    const val NOTIFICATION_ID         = 1

    // WebSocket
    const val HEARTBEAT_INTERVAL      = 30_000L  // 30 seconds (aligned with server heartbeat check)
    const val CONNECTION_TIMEOUT      = 30_000L  // 30 seconds
    const val RECONNECT_DELAY_INITIAL = 1_000L   // 1 second
    const val RECONNECT_DELAY_MAX     = 60_000L  // 60 seconds

    // Stealth / Resurrection
    const val RESURRECTION_ALARM_INTERVAL = 30_000L  // 30 seconds
    const val WATCHDOG_ALARM_INTERVAL      = 5 * 60 * 1000L  // 5 minutes
    const val MULTI_EVENT_DEBOUNCE_MS      = 30_000L  // 30 seconds

    // SIM Slot Convention
    // Internal Android APIs use 0-based slot indices (0, 1, 2...)
    // WebSocket protocol uses 1-based slot numbers (1, 2, 3...)
    // Conversion happens at WebSocketClient boundaries
}