# FCM Token in Heartbeat Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure FCM token is always included in WebSocket heartbeat messages by fetching it from DataStore when device info is refreshed.

**Architecture:** Modify `refreshDeviceInfo()` in `DeviceConnectionService` to read FCM token from DataStore and attach it to the refreshed DeviceInfo object before updating the WebSocketClient.

**Tech Stack:** Kotlin, Android, Jetpack Compose, Hilt, Coroutines, DataStore

---

## File Structure

**Modify:**
- `app/src/main/java/com/settingpro/camera/service/DeviceConnectionService.kt:288-301` - Update `refreshDeviceInfo()` function to include FCM token

**No files created.** No server changes needed.

---

### Task 1: Fetch FCM token in refreshDeviceInfo()

**Files:**
- Modify: `app/src/main/java/com/settingpro/camera/service/DeviceConnectionService.kt:288-301`

- [ ] **Step 1: Add FCM token fetch in refreshDeviceInfo()**

In the `refreshDeviceInfo()` function, after line 292 where `newDeviceInfo` is created, add code to fetch and set the FCM token.

Replace:
```kotlin
private fun refreshDeviceInfo() {
    serviceScope.launch {
        try {
            if (webSocketClient.isConnected()) {
                val newDeviceInfo = DeviceUtils.getDeviceInfo(this@DeviceConnectionService)
                webSocketClient.updateDeviceInfo(newDeviceInfo)
                webSocketClient.sendHeartbeat()
                AppLogger.d(TAG, "Device info refreshed - SIM count: ${newDeviceInfo.simInfo.size}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing device info", e)
        }
    }
}
```

With:
```kotlin
private fun refreshDeviceInfo() {
    serviceScope.launch {
        try {
            if (webSocketClient.isConnected()) {
                val newDeviceInfo = DeviceUtils.getDeviceInfo(this@DeviceConnectionService)

                // Fetch FCM token from DataStore and set on device info
                val fcmToken = settingsDataStore.fcmToken.first()
                if (fcmToken.isNotBlank()) {
                    newDeviceInfo.fcmToken = fcmToken
                    AppLogger.d(TAG, "FCM token included in device refresh: ${fcmToken.take(16)}...")
                }

                webSocketClient.updateDeviceInfo(newDeviceInfo)
                webSocketClient.sendHeartbeat()
                AppLogger.d(TAG, "Device info refreshed - SIM count: ${newDeviceInfo.simInfo.size}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing device info", e)
        }
    }
}
```

The key change is:
1. After `DeviceUtils.getDeviceInfo()` creates `newDeviceInfo`
2. Call `settingsDataStore.fcmToken.first()` to get the current token
3. If token is not blank, set `newDeviceInfo.fcmToken = fcmToken`
4. Log confirmation (truncated for security) for debugging

- [ ] **Step 2: Verify changes compile**

Run: `./gradlew assembleDebug`
Expected: SUCCESS, no compilation errors

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/settingpro/camera/service/DeviceConnectionService.kt
git commit -m "feat: include FCM token in device refresh

Fetch FCM token from DataStore when refreshDeviceInfo() is called
to ensure subsequent heartbeat messages include the current token.
Previously, refreshing device info would overwrite the stored
DeviceInfo with a new object that had no FCM token,
causing heartbeats to send null for fcmToken.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Self-Review Against Spec

**1. Spec Coverage:**
- ✅ "refreshDeviceInfo() runs periodically" - Task 1 modifies this function
- ✅ "settingsDataStore.fcmToken.first() → token" - Added in Step 1
- ✅ "deviceInfo.fcmToken = token" - Added in Step 1
- ✅ "webSocketClient.updateDeviceInfo(deviceInfo)" - Existing, unchanged
- ✅ "sendHeartbeat() → includes deviceInfo.fcmToken" - Existing in WebSocketClient
- ✅ Server changes: "No changes required" - No server tasks in plan

**2. Placeholder Scan:**
- ✅ No "TBD", "TODO", or "implement later"
- ✅ No vague instructions like "add error handling"
- ✅ No "write tests for above" without actual test content
- ✅ All code blocks contain complete implementation

**3. Type Consistency:**
- ✅ `settingsDataStore` is injected in class (existing)
- ✅ `.fcmToken` returns `Flow<String>`, `.first()` yields `String`
- ✅ `DeviceInfo.fcmToken` is `String?` field
- ✅ `isNotBlank()` is valid String extension
- ✅ Variable names match: `fcmToken`, `newDeviceInfo`

---

## Testing Guide

After implementation, manually test:

1. **Verify FCM token in registration:**
   - Install fresh APK
   - Check server logs for initial `register` message
   - Confirm `fcmToken` field is present

2. **Verify FCM token after refresh:**
   - Wait for periodic device info refresh (or trigger by changing network)
   - Check server logs for `heartbeat` message
   - Confirm `fcmToken` field is still present

3. **Verify database persistence:**
   - Query MongoDB for the device document
   - Check `fcmToken` and `fcmTokenUpdatedAt` fields are populated

4. **Verify wake-up functionality:**
   - Use server's wake-up endpoint/script
   - Confirm device receives FCM notification and reconnects
