package com.settingpro.camera.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.settingpro.camera.util.SecretConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aichat_settings")

// BUG 1 FIX: missing @Singleton and @Inject
// Without @Singleton, Hilt creates a new instance every time it's injected
// BootReceiver, DeviceConnectionService, and MainViewModel would each get a DIFFERENT instance
// meaning settings saved in one would not be visible in another
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val PERMISSIONS_GRANTED = booleanPreferencesKey("permissions_granted")
    }

    // Server URL - defaults to encrypted constant from SecretConfig
    val serverUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: SecretConfig.getServerUrl(context)
    }

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID] ?: ""
    }

    val fcmToken: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FCM_TOKEN] ?: ""
    }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SERVICE_ENABLED] ?: false
    }

    val permissionsGranted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PERMISSIONS_GRANTED] ?: false
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url.trim()
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID] = id
        }
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[FCM_TOKEN] = token
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setPermissionsGranted(granted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERMISSIONS_GRANTED] = granted
        }
    }
}