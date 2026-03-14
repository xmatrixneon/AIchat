# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Android Components =====
# FIX 6: BroadcastReceivers and Services referenced only by class name in the
# manifest are not automatically kept by R8. If renamed, the manifest entries
# break silently — SMS delivery, boot restart, and alarms all stop working.
-keep class com.cornspace.aichat.service.** { *; }

# ===== Hilt / Dagger =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
# FIX 1: HiltViewModel is an @annotation, not a superclass. The original rule
# "-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel" matched
# nothing and left all @HiltViewModel-annotated ViewModels unprotected.
# R8 would rename/remove the generated _HiltModules and _Factory classes,
# causing "ViewModel not found" crashes in release builds.
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ===== Gson / Data Models =====
-keep class com.cornspace.aichat.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# FIX 2: Gson uses TypeToken for generic type resolution at runtime. RegisterData
# and HeartbeatData both carry List<Map<String, Any?>> fields. Without keeping
# TypeToken, R8 strips its generic signature and Gson throws:
# "RuntimeException: TypeToken must be created with a type argument" in release.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.** { *; }

# ===== WebSocketMessage sealed class =====
# FIX 5: Sealed class subclasses (Connected, Registered, Ping, Ack, Error, etc.)
# are matched in parseMessage() via string type comparison. R8 can rename or
# merge these subclasses unless explicitly kept. Keeps the entire remote package
# including ConnectionState and its subclasses.
-keep class com.cornspace.aichat.data.remote.** { *; }

# ===== OkHttp / WebSocket =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# FIX 8: Removed "-keep class kotlin.** { *; }" — keeping all of kotlin.**
# prevents R8 from optimising the stdlib (dead code elimination, inlining).
# kotlin.Metadata is handled by -keepattributes *Annotation* above.
# Only keep what actually breaks without an explicit rule:
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }

# ===== DataStore =====
-keep class androidx.datastore.** { *; }

# FIX 4: Removed "-keep class androidx.compose.**" — this project uses
# AppCompatActivity + WebView, not Compose. Keeping all of androidx.compose.**
# bloated the APK with dead rules. If Compose is added later, re-enable.

# ===== Telephony / SMS =====
-keep class android.provider.Telephony.** { *; }

# ===== Logging =====
# FIX 3: Removed Log.w() and Log.e() from -assumenosideeffects.
# Stripping error and warning logs in release leaves no diagnostic signal when
# the service fails silently in the field (WebSocket errors, SMS processing
# failures, permission denials). Only strip the truly noise-only levels.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}