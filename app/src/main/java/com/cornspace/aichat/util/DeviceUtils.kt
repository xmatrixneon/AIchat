package com.cornspace.aichat.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.cornspace.aichat.data.model.*

object DeviceUtils {

    fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            deviceId     = getDeviceId(context),
            model        = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            osVersion    = "Android ${Build.VERSION.RELEASE}",
            batteryLevel = getBatteryLevel(context),
            batteryStatus = getBatteryStatus(context),
            simInfo      = getSimInfo(context),
            networkInfo  = getNetworkInfo(context),
            deviceBrand  = DeviceBrand(Build.MANUFACTURER)
        )
    }

    fun getDeviceId(context: Context): String {
        return try {
            // Settings.Secure.getString returns a platform type (String!) in Kotlin —
            // the compiler does not enforce null-safety on it. On factory-reset devices,
            // work profiles, and some OEM ROMs, ANDROID_ID can be null at runtime.
            // "9774d56d682e549c" is the well-known bogus ID emitted by some old devices.
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                ?: run {
                    // Build.SERIAL was deprecated in API 26. Build.getSerial() is the
                    // replacement — it throws SecurityException instead of silently
                    // returning Build.UNKNOWN, so we can catch it cleanly.
                    val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try { Build.getSerial() } catch (e: SecurityException) { Build.UNKNOWN }
                    } else {
                        @Suppress("DEPRECATION")
                        Build.SERIAL
                    }

                    if (serial != Build.UNKNOWN) {
                        "serial-$serial"
                    } else {
                        // FIX #3: The previous fallback used System.currentTimeMillis(),
                        // producing a different ID on every app start that hit this path.
                        // The server treated each boot as a new device registration.
                        // We now derive a deterministic fingerprint from stable build
                        // fields so the ID survives reboots even without ANDROID_ID.
                        // This is not unique across factory resets, but it is stable
                        // within a single device lifetime — good enough as a last resort.
                        val fingerprint = "${Build.MANUFACTURER}-${Build.MODEL}-" +
                            "${Build.PRODUCT}-${Build.HARDWARE}-${Build.BOARD}"
                        "fallback-${fingerprint.hashCode().toUInt()}"
                    }
                }
        } catch (e: Exception) {
            // Absolute last resort — still deterministic for the process lifetime
            // but will change on next cold start. Ideally never reached.
            "unknown-${Build.MANUFACTURER}-${Build.MODEL}".hashCode().toUInt().toString()
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            0
        }
    }

    private fun getBatteryStatus(context: Context): String {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (batteryManager.isCharging) "Charging" else "Discharging"
            } else {
                when (batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)) {
                    BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging"
                    BatteryManager.BATTERY_STATUS_FULL        -> "Full"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    else                                      -> "Unknown"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getSimInfo(context: Context): List<SimInfo> {
        val simInfoList = mutableListOf<SimInfo>()

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                return listOf(SimInfo(slot = 0, number = null, carrierName = null,
                    country = null, signalStrength = null, networkType = null, isActive = false))
            }

            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager

            val subscriptionInfoList = try {
                subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            if (subscriptionInfoList.isNotEmpty()) {
                for (subInfo in subscriptionInfoList) {
                    val signalStrength = getSignalStrengthForSubscription(context, subInfo.subscriptionId)
                    // FIX #5: Use the voice radio network type (same as SmsReceiver) so
                    // heartbeat and SMS message fields are consistent on the server side.
                    val networkType = getVoiceNetworkTypeForSubscription(context, subInfo.subscriptionId)

                    simInfoList.add(
                        SimInfo(
                            slot           = subInfo.simSlotIndex,
                            number         = getPhoneNumberForSubscription(context, subInfo.subscriptionId),
                            carrierName    = subInfo.carrierName?.toString(),
                            country        = subInfo.countryIso,
                            signalStrength = signalStrength,
                            networkType    = networkType,
                            isActive       = true
                        )
                    )
                }
            } else {
                simInfoList.add(
                    SimInfo(slot = 0, number = null, carrierName = null,
                        country = null, signalStrength = null, networkType = null, isActive = false)
                )
            }

        } catch (e: Exception) {
            // Return empty — fallback below handles it
        }

        return if (simInfoList.isEmpty()) {
            listOf(SimInfo(slot = 0, number = null, carrierName = null,
                country = null, signalStrength = null, networkType = null, isActive = false))
        } else {
            simInfoList
        }
    }

    fun getSimSlotFromSubscription(context: Context, subscriptionId: Int): Int {
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
            subscriptionManager.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getPhoneNumberForSubscription(context: Context, subscriptionId: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
                    == PackageManager.PERMISSION_GRANTED) {
                    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as SubscriptionManager
                    if (subscriptionId != -1) {
                        sm.getPhoneNumber(subscriptionId).takeIf { it.isNotEmpty() }
                    } else null
                } else null
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as SubscriptionManager
                    val subInfo = if (subscriptionId != -1) sm.getActiveSubscriptionInfo(subscriptionId)
                                  else sm.activeSubscriptionInfoList?.firstOrNull()
                    @Suppress("DEPRECATION")
                    subInfo?.number?.takeIf { it.isNotEmpty() }
                } else null
            } else {
                val hasPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    @Suppress("DEPRECATION")
                    tm.line1Number?.takeIf { it.isNotEmpty() }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getSignalStrengthForSubscription(context: Context, subscriptionId: Int): Int? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val tmForSub = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.createForSubscriptionId(subscriptionId)
            } else tm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tmForSub.signalStrength?.level
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * FIX #1 + #5: Returns the VOICE radio network type, not the data radio type.
     * SMS travels over the voice bearer. On devices where data is LTE/5G but voice
     * falls back to 3G (VoLTE disabled), the old [networkType] / [dataNetworkType]
     * call reported "LTE" for every SMS. [voiceNetworkType] is the correct field.
     *
     * FIX #4: Returns null (not "Unknown") when READ_PHONE_STATE is not granted,
     * so callers can distinguish "permission denied" from "radio type unrecognised".
     */
    fun getVoiceNetworkTypeForSubscription(context: Context, subscriptionId: Int): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            // FIX #4: return null, not "Unknown" — permission denial ≠ unknown radio type.
            return null
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val tmForSub = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.createForSubscriptionId(subscriptionId)
            } else tm

            // FIX #1: voiceNetworkType reflects the bearer SMS actually uses.
            // dataNetworkType (used previously) reflects the internet data bearer,
            // which is typically LTE/5G even when voice/SMS falls back to 3G.
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tmForSub.voiceNetworkType
            } else {
                // Pre-R networkType returns the voice type, consistent with SmsReceiver.
                @Suppress("DEPRECATION")
                tmForSub.networkType
            }
            voiceNetworkTypeToString(type)
        } catch (e: Exception) {
            null
        }
    }

    // CDMA/EVDO/IDEN constants are deprecated because those radio technologies are
    // decommissioned, but they still appear on real devices. @Suppress is intentional.
    @Suppress("DEPRECATION")
    private fun voiceNetworkTypeToString(networkType: Int): String = when (networkType) {
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"

        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN   -> "2G"

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP  -> "3G"

        TelephonyManager.NETWORK_TYPE_LTE    -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR     -> "5G NR"
        else -> "Unrecognised($networkType)"
    }

    private fun getNetworkInfo(context: Context): NetworkInfo {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)

            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            val networkType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "None"
            }

            // Obtaining WiFi SSID requires ACCESS_FINE_LOCATION on API 29+.
            // That permission is not declared in the manifest (intrusive to request
            // just for telemetry), so return null rather than "<unknown ssid>".
            val wifiSSID = if (networkType == "WiFi" &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val wifiInfo = capabilities?.transportInfo as? android.net.wifi.WifiInfo
                        wifiInfo?.ssid?.removeSurrounding("\"")
                            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                    } else {
                        @Suppress("DEPRECATION")
                        val wifiManager = context.applicationContext
                            .getSystemService(Context.WIFI_SERVICE) as WifiManager
                        @Suppress("DEPRECATION")
                        wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
                            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                    }
                } catch (e: Exception) {
                    null
                }
            } else null

            NetworkInfo(
                isConnected = isConnected,
                networkType = networkType,
                wifiSSID    = wifiSSID
            )
        } catch (e: Exception) {
            NetworkInfo(isConnected = false, networkType = "Unknown")
        }
    }
}