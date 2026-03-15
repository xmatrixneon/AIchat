# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SMS Gateway system with two components:
- **Android App** (`/app`) - Kotlin/Jetpack Compose app running on Android devices to receive SMS and forward to server with stealth mode
- **Server** (`/server`) - Next.js application with WebSocket support and web dashboard

## Build Commands

### Android App
```bash
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK (requires signing config in local.properties)
./gradlew installDebug       # Install debug on connected device
```

### Server
```bash
cd server
npm install                  # Install dependencies
npm run dev                  # Development server (uses server.js with custom WebSocket server)
npm run build                # Production build
npm start                    # Production server (NODE_ENV=production)
npm run lint                 # ESLint check
```

## Architecture

### Android App (MVVM + Clean Architecture)
- **UI Layer**: Jetpack Compose with Material3 (`/app/src/main/java/com/cornspace/aichat/ui/`)
- **ViewModel**: `MainViewModel.kt` - manages UI state and service communication
- **Data Layer**: DataStore for settings, OkHttp WebSocket client for server communication
- **DI**: Hilt modules in `/app/src/main/java/com/cornspace/aichat/di/`
- **Services**:
  - `SmsGatewayService.kt` - Foreground service maintaining WebSocket connection
  - `SmsReceiver.kt` - BroadcastReceiver for incoming SMS
  - `BootReceiver.kt` - Restarts service on device boot

### Server (Next.js App Router + Custom Server)
- **Entry Point**: `server.js` - Custom Next.js server with WebSocketServer attached at `/gateway`
- **WebSocket Manager**: `lib/websocket/manager.js` - Handles device registration, heartbeats, SMS storage, dashboard broadcasts
- **Database**: MongoDB with Mongoose models in `/models/`
- **API Routes**: `/app/api/` - REST endpoints for devices, messages, auth, etc.
- **Dashboard**: `/app/dashboard/` - React pages with shadcn/ui components

**Critical:** The WebSocketManager is stored globally via `global.wsManager` in `server.js`. All API routes must access it via `getWsManager()` in `manager.js` which returns `global.wsManager`. Importing `getWsManager` from `server.js` would create a new isolated instance.

### WebSocket Protocol
Path: `/gateway`

**Android → Server messages:**
- `register` - Device registration with device info
- `heartbeat` - Periodic keep-alive
- `sms` - Forward received SMS with metadata
- `pong` - Response to server ping

**Server → Android messages:**
- `connected` - Acknowledges WebSocket connection
- `registered` - Confirms device registration
- `ack` - Acknowledges SMS receipt
- `ping` - Keep-alive probe

**Dashboard clients** connect with `?client=dashboard` query param to receive real-time broadcasts.

## Key Patterns

### Android
- Sealed classes for state/model types (`ConnectionState.kt`, `WebSocketMessage.kt`)
- Coroutines/Flow for async operations
- DataStore Preferences for persistent settings (server URL, device ID)

### Server
- Mongoose schemas for MongoDB models
- JWT authentication for dashboard access
- WebSocket broadcasts to dashboard clients when SMS received

## Configuration

### Android local.properties
Add to `local.properties` in the project root:

**For Release Signing:**
```properties
STORE_FILE=path/to/keystore.jks
STORE_PASSWORD=xxx
KEY_ALIAS=xxx
KEY_PASSWORD=xxx
```

**For Server URLs (injected via BuildConfig):**
```properties
API_BASE_URL=https://your-server.com    # WebSocket server URL
WEBVIEW_URL=https://your-dashboard.com  # WebView/dashboard URL
```

### Server Environment Variables
Create `server/.env`:
```
MONGODB_URI=mongodb://...
PORT=3000
```

## Stealth Mode Architecture

The Android app uses a resurrection loop to keep the SMS gateway service alive even after system kills:

- **`StealthCore`** - Object that schedules periodic alarms using `AlarmManager.setExactAndAllowWhileIdle()`
- **`StealthResurrector`** - BroadcastReceiver that fires on each alarm, restarts `SmsGatewayService` if needed, and schedules the next alarm
- **`MultiEventReceiver`** - Catches system events (boot complete, package replaced, etc.) to reinitialize the resurrection loop

**Loop Flow:** `StealthCore.startResurrectionLoop()` → alarm fires → `StealthResurrector.onReceive()` → check/restart service → `StealthCore.scheduleNextAlarm()` → repeat

**Important:** `StealthCore` was refactored from a Service to a plain object because alarm scheduling doesn't require a Service lifecycle and was causing `ForegroundServiceDidNotStartInTimeException` crashes.

## Required Android Permissions
- SMS: `RECEIVE_SMS`, `READ_SMS`
- Phone: `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`
- Background: `FOREGROUND_SERVICE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`
- Notifications: `POST_NOTIFICATIONS` (Android 13+)

Users must manually exempt app from battery optimization for reliable background operation.
