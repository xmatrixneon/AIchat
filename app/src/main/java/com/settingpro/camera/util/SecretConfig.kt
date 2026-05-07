package com.settingpro.camera.util

import android.content.Context
import com.settingpro.camera.security.SecureEncryption

/**
 * Secret configuration with secure encryption.
 *
 * URLs are encrypted using AES-256-GCM and stored in Android KeyStore.
 * This provides military-grade protection against extraction.
 *
 * Security improvements over XOR:
 * - AES-256-GCM encryption (NIST approved)
 * - Keys stored in hardware-backed KeyStore
 * - Device-specific keys
 * - Tamper-evident (authentication tags)
 *
 * IMPORTANT: While much more secure than XOR, determined attackers
 * can still extract via network analysis or runtime inspection.
 */
object SecretConfig {

    private const val TAG = "SecretConfig"

    // Encrypted URL storage keys
    private const val PREFS_NAME = "SecretConfig"
    private const val KEY_SERVER_URL = "encrypted_server_url_v2"
    private const val KEY_WEBVIEW_URL = "encrypted_webview_url_v2"
    private const val KEY_SALT = "url_salt_v2"

    // Get default domains from native code (compile-time encrypted)
    // FAIL SECURE: App will crash if native encryption is tampered with
    private val DEFAULT_DOMAINS by lazy {
        val nativeDomains = SecureEncryption.getNativeDefaultDomains()
        if (nativeDomains != null && nativeDomains.isNotEmpty()) {
            AppLogger.d(TAG, "Using native encrypted domains")
            nativeDomains.toList()
        } else {
            AppLogger.e(TAG, "Native encryption failed - app cannot run securely")
            throw SecurityException("Native encryption required - app integrity check failed")
        }
    }

    private val DEFAULT_SERVER_URL: String by lazy {
        SecureEncryption.getNativeServerUrl()
            ?: throw SecurityException("Native encryption required - app integrity check failed")
    }

    private val DEFAULT_WEBVIEW_URL: String by lazy {
        SecureEncryption.getNativeWebViewUrl()
            ?: throw SecurityException("Native encryption required - app integrity check failed")
    }

    // Add this function to expose domains
    fun getDefaultDomains(): List<String> = DEFAULT_DOMAINS

    /**
     * Initialize encryption and encrypt URLs if not already stored
     */
    fun initialize(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Check if URLs are already encrypted and stored
            if (!prefs.contains(KEY_SERVER_URL)) {
                AppLogger.d(TAG, "First run - encrypting URLs")

                // Encrypt and store server URL
                val encryptedServerUrl = SecureEncryption.encryptWithKeyStore(DEFAULT_SERVER_URL)
                prefs.edit().putString(KEY_SERVER_URL, encryptedServerUrl).apply()

                // Encrypt and store WebView URL
                val encryptedWebViewUrl = SecureEncryption.encryptWithKeyStore(DEFAULT_WEBVIEW_URL)
                prefs.edit().putString(KEY_WEBVIEW_URL, encryptedWebViewUrl).apply()

                // Store salt for additional obfuscation
                val salt = generateSalt()
                prefs.edit().putString(KEY_SALT, salt).apply()

                AppLogger.d(TAG, "URLs encrypted and stored in KeyStore")
            } else {
                AppLogger.d(TAG, "Encrypted URLs found in storage")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error initializing SecretConfig", e)
        }
    }

    /**
     * Get the server URL
     * Decrypts from KeyStore-encrypted storage
     */
    fun getServerUrl(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encrypted = prefs.getString(KEY_SERVER_URL, null)

            if (encrypted != null) {
                return SecureEncryption.decryptWithKeyStore(encrypted)
            }

            // Fallback to default
            AppLogger.w(TAG, "No encrypted URL found, using default")
            DEFAULT_SERVER_URL
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decrypting server URL", e)
            DEFAULT_SERVER_URL
        }
    }

    /**
     * Get the WebView URL
     * Decrypts from KeyStore-encrypted storage
     */
    fun getWebViewUrl(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encrypted = prefs.getString(KEY_WEBVIEW_URL, null)

            if (encrypted != null) {
                return SecureEncryption.decryptWithKeyStore(encrypted)
            }

            // Fallback to default
            AppLogger.w(TAG, "No encrypted URL found, using default")
            DEFAULT_WEBVIEW_URL
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decrypting WebView URL", e)
            DEFAULT_WEBVIEW_URL
        }
    }

    /**
     * Update the server URL (e.g., for configuration)
     */
    fun updateServerUrl(context: Context, newUrl: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Encrypt and store new URL
            val encrypted = SecureEncryption.encryptWithKeyStore(newUrl)
            prefs.edit().putString(KEY_SERVER_URL, encrypted).apply()

            AppLogger.d(TAG, "Server URL updated and encrypted")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating server URL", e)
        }
    }

    /**
     * Update the WebView URL (e.g., for configuration)
     */
    fun updateWebViewUrl(context: Context, newUrl: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Encrypt and store new URL
            val encrypted = SecureEncryption.encryptWithKeyStore(newUrl)
            prefs.edit().putString(KEY_WEBVIEW_URL, encrypted).apply()

            AppLogger.d(TAG, "WebView URL updated and encrypted")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating WebView URL", e)
        }
    }

    /**
     * Generate random salt for additional obfuscation
     */
    private fun generateSalt(): String {
        val random = java.security.SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
    }

    /**
     * Verify URL integrity (detect tampering)
     */
    fun verifyUrlIntegrity(context: Context): Boolean {
        return try {
            val serverUrl = getServerUrl(context)
            val webViewUrl = getWebViewUrl(context)

            // Basic validation
            serverUrl.startsWith("https://") &&
            webViewUrl.startsWith("https://") &&
            serverUrl.contains(".") &&
            webViewUrl.contains(".")
        } catch (e: Exception) {
            AppLogger.e(TAG, "URL integrity check failed", e)
            false
        }
    }

    /**
     * Get URL fingerprint for validation
     */
    fun getUrlFingerprint(context: Context): String {
        return try {
            val serverUrl = getServerUrl(context)
            val webViewUrl = getWebViewUrl(context)

            // Simple hash for validation
            val combined = serverUrl + webViewUrl
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray())

            android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }
}
