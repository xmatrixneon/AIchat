package com.settingpro.camera.data.model

data class DeviceInfo(
    val deviceId: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val batteryLevel: Int,
    val batteryStatus: String,
    val simInfo: List<SimInfo>,
    val networkInfo: NetworkInfo,
    val deviceBrand: DeviceBrand = DeviceBrand(manufacturer)
)

data class SimInfo(
    val slot: Int,
    val number: String?,
    val carrierName: String?,
    val country: String?,
    val signalStrength: Int?,
    val networkType: String?,
    val isActive: Boolean
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "slot" to (slot + 1),
        "phoneNumber" to number,
        "carrierName" to carrierName,
        "country" to country,
        "signalStrength" to (signalStrength ?: 0),
        "networkType" to networkType,
        "isActive" to isActive
    )
}

data class NetworkInfo(
    val isConnected: Boolean,
    val networkType: String,
    val wifiSSID: String? = null
)

data class DeviceBrand(val brand: String)
