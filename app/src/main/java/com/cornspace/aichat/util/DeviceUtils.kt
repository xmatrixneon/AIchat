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
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.cornspace.aichat.data.model.*

object DeviceUtils {

    fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            deviceId = getDeviceId(context),
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            batteryLevel = getBatteryLevel(context),
            batteryStatus = getBatteryStatus(context),
            simInfo = getSimInfo(context),
            networkInfo = getNetworkInfo(context),
            deviceBrand = DeviceBrand(Build.MANUFACTURER)
        )
    }

    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "unknown-${System.currentTimeMillis()}"
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
                val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    else -> "Unknown"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getSimInfo(context: Context): List<SimInfo> {
        val simInfoList = mutableListOf<SimInfo>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as android.telephony.SubscriptionManager

                val subscriptionInfoList = try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {
                        subscriptionManager.activeSubscriptionInfoList ?: emptyList()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                for ((index, subInfo) in subscriptionInfoList.withIndex()) {
                    val slotIndex = try {
                        getSimSlotFromSubscription(context, subInfo.subscriptionId)
                    } catch (e: Exception) {
                        index
                    }

                    simInfoList.add(
                        SimInfo(
                            slot = slotIndex,
                            number = getPhoneNumberForSubscription(context, subInfo.subscriptionId),
                            carrierName = subInfo.carrierName?.toString(),
                            country = subInfo.countryIso,
                            signalStrength = getSignalStrength(context, slotIndex),
                            networkType = getNetworkType(context, slotIndex),
                            isActive = true
                        )
                    )
                }
            } else {
                // For older API levels — use activeSubscriptionInfoList via SubscriptionManager
                // instead of deprecated phoneCount/line1Number
                val simCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as android.telephony.SubscriptionManager
                    try {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                            == PackageManager.PERMISSION_GRANTED) {
                            subscriptionManager.activeSubscriptionInfoList?.size ?: 1
                        } else {
                            1
                        }
                    } catch (e: Exception) {
                        1
                    }
                } else {
                    1
                }

                for (i in 0 until simCount) {
                    simInfoList.add(
                        SimInfo(
                            slot = i,
                            number = if (i == 0) getPhoneNumberForSubscription(context, -1) else null,
                            carrierName = try {
                                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                                if (i == 0) tm.networkOperatorName else null
                            } catch (e: Exception) { null },
                            country = try {
                                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                                if (i == 0) tm.networkCountryIso else null
                            } catch (e: Exception) { null },
                            signalStrength = getSignalStrength(context, i),
                            networkType = getNetworkType(context, i),
                            isActive = i == 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Return empty list on error
        }

        return if (simInfoList.isEmpty()) {
            listOf(SimInfo(slot = 0, number = null, carrierName = null, country = null,
                signalStrength = null, networkType = null, isActive = false))
        } else {
            simInfoList
        }
    }

    fun getSimSlotFromSubscription(context: Context, subscriptionId: Int): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as android.telephony.SubscriptionManager
                val subInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                subInfo?.simSlotIndex ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun getPhoneNumberForSubscription(context: Context, subscriptionId: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ — use SubscriptionManager.getPhoneNumber() (non-deprecated)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
                    == PackageManager.PERMISSION_GRANTED) {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as android.telephony.SubscriptionManager
                    if (subscriptionId != -1) {
                        subscriptionManager.getPhoneNumber(subscriptionId)
                            .takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30-32 — use SubscriptionInfo.number (still valid here)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
                    == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as android.telephony.SubscriptionManager
                    val subInfo = if (subscriptionId != -1) {
                        subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                    } else {
                        subscriptionManager.activeSubscriptionInfoList?.firstOrNull()
                    }
                    @Suppress("DEPRECATION")
                    subInfo?.number?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } else {
                // Below API 30 — use TelephonyManager.line1Number
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
                    == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    @Suppress("DEPRECATION")
                    tm.line1Number?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getSignalStrength(context: Context, slotIndex: Int): Int? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telephonyManager.signalStrength?.level
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getNetworkType(context: Context, slotIndex: Int): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getNetworkInfo(context: Context): NetworkInfo {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isConnected = capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            val networkType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "None"
            }

            val wifiSSID = if (networkType == "WiFi") {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // API 29+ — get SSID from NetworkCapabilities TransportInfo
                        val wifiInfo = capabilities?.transportInfo as? android.net.wifi.WifiInfo
                        wifiInfo?.ssid?.removeSurrounding("\"")
                    } else {
                        @Suppress("DEPRECATION")
                        val wifiManager = context.applicationContext
                            .getSystemService(Context.WIFI_SERVICE) as WifiManager
                        @Suppress("DEPRECATION")
                        wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            NetworkInfo(
                isConnected = isConnected,
                networkType = networkType,
                wifiSSID = wifiSSID
            )
        } catch (e: Exception) {
            NetworkInfo(isConnected = false, networkType = "Unknown")
        }
    }
}