package com.cornspace.aichat.ui.viewmodel

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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context
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
    private val webSocketClient: WebSocketClient  // ✅ injected to observe real state
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeConnectionState()  // ✅ wire WebSocket state into UI
        loadDeviceInfo()
    }

    // ✅ NEW: Collect the real WebSocket connection state into UI state
    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketClient.connectionState.collect { connectionState ->
                _uiState.update { it.copy(connectionState = connectionState) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsDataStore.deviceId,
                settingsDataStore.serverUrl,
                settingsDataStore.serviceEnabled
            ) { deviceId, serverUrl, serviceEnabled ->
                Triple(deviceId, serverUrl, serviceEnabled)
            }.collect { (deviceId, serverUrl, serviceEnabled) ->
                _uiState.update { state ->
                    state.copy(
                        deviceId = deviceId,
                        serverUrl = serverUrl,
                        // ✅ rely on actual service state, not stale datastore flag
                        isServiceRunning = serviceEnabled || SmsGatewayService.isServiceRunning()
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
            settingsDataStore.setServerUrl(url)
            _uiState.update { it.copy(serverUrl = url) }
        }
    }

    fun startService() {
        viewModelScope.launch {
            if (_uiState.value.serverUrl.isBlank()) {
                _uiState.update { it.copy(error = "Please configure server URL first") }
                return@launch
            }

            settingsDataStore.setServiceEnabled(true)
            SmsGatewayService.startService(context)
            _uiState.update { it.copy(isServiceRunning = true, error = null) }
        }
    }

    fun stopService() {
        viewModelScope.launch {
            settingsDataStore.setServiceEnabled(false)
            SmsGatewayService.stopService(context)
            _uiState.update { it.copy(isServiceRunning = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}