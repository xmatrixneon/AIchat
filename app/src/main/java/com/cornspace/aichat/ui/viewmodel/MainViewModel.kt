package com.cornspace.aichat.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cornspace.aichat.data.local.SettingsDataStore
import com.cornspace.aichat.data.model.DeviceInfo
import com.cornspace.aichat.data.remote.ConnectionState
import com.cornspace.aichat.data.remote.WebSocketClient
import com.cornspace.aichat.service.SmsGatewayService
import com.cornspace.aichat.util.DeviceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val deviceId: String = "",
    val serverUrl: String = "",
    val isServiceRunning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val deviceInfo: DeviceInfo? = null,
    val smsCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeConnectionState()
        observeServiceRunning()  // BUG 1 FIX: poll actual service state
        observeSmsCount()        // BUG 2 FIX: smsCount was never updated
        loadDeviceInfo()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    // BUG 1 FIX: isServiceRunning was only set on button clicks
    // if service crashed and restarted via START_STICKY, UI would show wrong state
    // poll every 2s to reflect actual runtime state
    private fun observeServiceRunning() {
        viewModelScope.launch {
            while (true) {
                val running = SmsGatewayService.isServiceRunning()
                _uiState.update { it.copy(isServiceRunning = running) }
                delay(2_000)
            }
        }
    }

    // BUG 2 FIX: smsCount was always 0 in UI — never collected from WebSocketClient
    private fun observeSmsCount() {
        viewModelScope.launch {
            while (true) {
                val count = webSocketClient.getSmsForwardedCount()
                _uiState.update { it.copy(smsCount = count) }
                delay(2_000)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsDataStore.deviceId,
                settingsDataStore.serverUrl,
            ) { deviceId, serverUrl ->
                Pair(deviceId, serverUrl)
            }.collect { (deviceId, serverUrl) ->
                // BUG 3 FIX: removed serviceEnabled from isServiceRunning calculation
                // serviceEnabled=true in datastore doesn't mean service is actually running
                // (service could have crashed). Use isServiceRunning() as source of truth.
                _uiState.update { state ->
                    state.copy(
                        deviceId = deviceId,
                        serverUrl = serverUrl,
                    )
                }
            }
        }
    }

    fun loadDeviceInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val deviceInfo = DeviceUtils.getDeviceInfo(context)
                _uiState.update { state ->
                    state.copy(
                        deviceInfo = deviceInfo,
                        deviceId = deviceInfo.deviceId,
                        isLoading = false
                    )
                }
                val savedDeviceId = settingsDataStore.deviceId.first()
                if (savedDeviceId.isBlank()) {
                    settingsDataStore.setDeviceId(deviceInfo.deviceId)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading device info", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Failed to load device info: ${e.message}"
                    )
                }
            }
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(url.trim())
            _uiState.update { it.copy(serverUrl = url.trim()) }
        }
    }

    fun startService() {
        viewModelScope.launch {
            val url = _uiState.value.serverUrl
            if (url.isBlank()) {
                _uiState.update { it.copy(error = "Please configure server URL first") }
                return@launch
            }
            settingsDataStore.setServiceEnabled(true)
            SmsGatewayService.startService(context)
            _uiState.update { it.copy(error = null) }
            // don't manually set isServiceRunning — observeServiceRunning() will pick it up
        }
    }

    fun stopService() {
        viewModelScope.launch {
            settingsDataStore.setServiceEnabled(false)
            SmsGatewayService.stopService(context)
            // don't manually set isServiceRunning — observeServiceRunning() will pick it up
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}