# FCM Token in Heartbeat Messages

**Date:** 2026-03-30

## Overview

Ensure FCM (Firebase Cloud Messaging) token is always included in WebSocket heartbeat messages sent from Android app to server, enabling reliable remote wake-up functionality.

## Problem

Currently, the `refreshDeviceInfo()` function in `DeviceConnectionService` creates a new `DeviceInfo` object via `DeviceUtils.getDeviceInfo()` which doesn't include the FCM token. When this updates the `WebSocketClient`'s `deviceInfo` field, subsequent heartbeat messages send `null` for the FCM token.

While:
- `HeartbeatData` has `fcmToken` field
- `sendHeartbeat()` includes `info.fcmToken`
- Server's `handleHeartbeat()` extracts and stores the token

The token is lost during device info refreshes, causing the server to have stale or missing FCM tokens for devices.

## Solution

Modify `refreshDeviceInfo()` in `DeviceConnectionService.kt` to fetch the current FCM token from DataStore and set it on the new `DeviceInfo` object before updating the `WebSocketClient`.

## Changes Required

### Android App

**File:** `app/src/main/java/com/settingpro/camera/service/DeviceConnectionService.kt`

In the `refreshDeviceInfo()` function, after creating `newDeviceInfo` via `DeviceUtils.getDeviceInfo()`:

1. Fetch FCM token from `settingsDataStore.fcmToken.first()`
2. If token is not blank, set `newDeviceInfo.fcmToken = token`
3. Then call `webSocketClient.updateDeviceInfo(newDeviceInfo)`

### Server

**No changes required.** The server's `WebSocketManager.handleHeartbeat()` already:
- Extracts `fcmToken` from heartbeat data
- Updates device document with `fcmToken` and `fcmTokenUpdatedAt` if provided

## Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│ DeviceConnectionService                                  │
│                                                         │
│ refreshDeviceInfo() runs periodically:                     │
│   1. DeviceUtils.getDeviceInfo() → new DeviceInfo        │
│   2. settingsDataStore.fcmToken.first() → token         │
│   3. deviceInfo.fcmToken = token                        │
│   4. webSocketClient.updateDeviceInfo(deviceInfo)         │
│                                                         │
│ Periodic heartbeat (via WebSocketClient.startHeartbeat()):   │
│   5. sendHeartbeat() → includes deviceInfo.fcmToken      │
│                                                         │
└─────────────────────────────────────────────────────────────┘
                         ↓ WebSocket
┌─────────────────────────────────────────────────────────────┐
│ Server WebSocketManager.handleHeartbeat()                   │
│                                                         │
│   1. Parse heartbeat data including fcmToken              │
│   2. Device.findOneAndUpdate() → update fcmToken         │
│   3. Store fcmTokenUpdatedAt timestamp                   │
│                                                         │
└─────────────────────────────────────────────────────────────┘
```

## Testing

1. Verify FCM token is included in initial registration message
2. Trigger a device info refresh (SIM change, network change, or manual)
3. Verify subsequent heartbeat messages include the FCM token
4. Confirm server database has up-to-date `fcmToken` and `fcmTokenUpdatedAt`
