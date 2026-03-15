# ===== Source map (keep for crash reporting) =====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*

# ===== App components =====
# BroadcastReceivers and Services referenced by class name in the manifest are
# not automatically kept by R8. If renamed, manifest entries break silently —
# SMS delivery, boot restart, and alarms all stop working.
-keep class com.cornspace.aichat.service.** { *; }

# FIX #1: util package kept — CallForwardingUtility contains a
# TelephonyManager.UssdResponseCallback anonymous inner class that must
# survive with its exact structure for USSD callbacks to fire. DeviceUtils
# and SecretConfig are also in this package.
-keep class com.cornspace.aichat.util.** { *; }

# FIX #3: AiChatApplication is kept via manifest but the Hilt-generated
# AiChatApplication_GeneratedInjector must also be kept or injection fails.
-keep class com.cornspace.aichat.AiChatApplication { *; }
-keep class com.cornspace.aichat.*_GeneratedInjector { *; }

# FIX #4: MainActivity Hilt members injector must be kept.
-keep class com.cornspace.aichat.MainActivity { *; }

# ===== Hilt / Dagger =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
# @HiltViewModel is an annotation, not a superclass. The original rule
# "-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel" matched
# nothing. R8 would rename the generated _HiltModules and _Factory classes,
# causing "ViewModel not found" crashes in release builds.
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ===== Gson / Data Models =====
-keep class com.cornspace.aichat.data.model.** { *; }
# Gson uses TypeToken for generic type resolution at runtime. RegisterData
# and HeartbeatData both carry List<Map<String, Any?>> fields. Without keeping
# TypeToken, R8 strips its generic signature and Gson throws:
# "RuntimeException: TypeToken must be created with a type argument" in release.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.** { *; }

# ===== WebSocketMessage sealed class =====
# Sealed class subclasses (Connected, Registered, Ping, Ack, Error, etc.)
# are matched in parseMessage() via string type comparison. R8 can rename or
# merge these subclasses unless explicitly kept. Keeps the entire remote package
# including ConnectionState and its subclasses.
-keep class com.cornspace.aichat.data.remote.** { *; }

# ===== OkHttp / WebSocket =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# FIX #6: Conscrypt/BouncyCastle warnings from OkHttp on modern Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
# FIX #7: Keep coroutine DebugMetadata annotations so crash reports retain
# coroutine context (which coroutine was running, suspension point, etc.).
-keepclassmembers class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.debug.** { *; }

# ===== DataStore =====
-keep class androidx.datastore.** { *; }

# ===== Telephony / SMS =====
-keep class android.provider.Telephony.** { *; }

# ===== Logging =====
# Only strip debug and verbose logs in release. Keeping warn/error/assert
# preserves diagnostic signal when the service fails silently in the field
# (WebSocket errors, SMS processing failures, permission denials).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}