package com.settingpro.camera.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.settingpro.camera.util.AppLogger
import java.util.UUID

/**
 * BluetoothLeManager - Manages BLE beacon advertising for connectedDevice service justification.
 *
 * This manager silently advertises a BLE beacon to provide hardware justification for the
 * connectedDevice foreground service type. All failures are silent - the service continues
 * normally regardless of Bluetooth availability or permission state.
 */
class BluetoothLeManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothLeManager"

        // Service UUID for beacon advertising (nearby placeholder UUID)
        private val SERVICE_UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
    }

    private var advertiseCallback: AdvertiseCallback? = null
    private var isAdvertisingFlag = false

    /**
     * Start BLE beacon advertising. Silently fails if Bluetooth unavailable or no permission.
     */
    fun startAdvertising() {
        // Check permission without requesting
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Silent fail - do not log to avoid alerting
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            // Bluetooth not available - silent fail
            return
        }

        if (!adapter.isEnabled) {
            // Bluetooth disabled - silent fail
            return
        }

        val advertiser = adapter.bluetoothLeAdvertiser ?: run {
            // BLE not supported - silent fail
            return
        }

        // Stop any existing advertising
        stopAdvertising()

        // Create advertise settings
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0) // Never timeout
            .build()

        // Create advertise data with service UUID
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        // Create callback
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                isAdvertisingFlag = true
                AppLogger.d(TAG, "BLE beacon advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                isAdvertisingFlag = false
                val error = when (errorCode) {
                    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    else -> "Unknown error: $errorCode"
                }
                AppLogger.d(TAG, "BLE advertising failed: $error")
            }
        }

        // Start advertising
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Stop BLE beacon advertising.
     */
    fun stopAdvertising() {
        val callback = advertiseCallback ?: return

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            val advertiser = adapter?.bluetoothLeAdvertiser

            advertiser?.stopAdvertising(callback)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error stopping BLE advertising", e)
        } finally {
            advertiseCallback = null
            isAdvertisingFlag = false
        }
    }

    /**
     * Check if currently advertising.
     */
    fun isAdvertising(): Boolean = isAdvertisingFlag
}
