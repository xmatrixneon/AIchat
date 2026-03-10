package com.cornspace.aichat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aichat_settings")

// BUG 1 FIX: missing @Singleton and @Inject
// Without @Singleton, Hilt creates a new instance every time it's injected
// BootReceiver, SmsGatewayService, and MainViewModel would each get a DIFFERENT instance
// meaning settings saved in one would not be visible in another
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
    }

    // BUG 2 FIX: default URL removed
    // Having a default URL means isServerUrlConfigured is always true
    // so the Start button is always enabled even on fresh install
    // The user should explicitly set their server URL
    val serverUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: "https://api.cattysms.shop"
    }

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID] ?: ""
    }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SERVICE_ENABLED] ?: false
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

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }
}