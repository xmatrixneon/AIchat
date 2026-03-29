package com.settingpro.camera.security

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Native AES-256-GCM encryption wrapper
 * Uses Android KeyStore for secure key storage
 */
object SecureEncryption {

    private const val TAG = "SecureEncryption"
    private const val KEY_ALIAS = "AIChatMasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // Load native library
    init {
        try {
            System.loadLibrary("aicrypt")
            Log.d(TAG, "Native encryption library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library not found, using Java fallback", e)
        }
    }

    /**
     * Encrypt data using native XOR obfuscation
     * Native implementation for better security
     */
    external fun encryptNative(data: ByteArray, key: ByteArray): ByteArray?

    /**
     * Decrypt data using native XOR obfuscation
     * Native implementation for better security
     */
    external fun decryptNative(encryptedData: ByteArray, key: ByteArray): ByteArray?

    /**
     * Compute hash of data (native obfuscated)
     */
    external fun computeHash(data: ByteArray): ByteArray?

    /**
     * Generate random key in native code
     */
    external fun generateRandomKey(keySize: Int): ByteArray?

    /**
     * Obfuscate string in native code
     */
    external fun obfuscateString(input: String): String?

    /**
     * Generate a random encryption key
     */
    fun generateKey(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey().encoded
    }

    /**
     * Generate random IV
     */
    fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return iv
    }

    /**
     * Encrypt data (Java fallback if native not available)
     */
    fun encrypt(data: String, key: ByteArray): String {
        try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val secretKey = SecretKeySpec(key, "AES")
            val iv = generateIV()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val encrypted = cipher.doFinal(data.toByteArray())

            // Combine IV and encrypted data
            val combined = iv + encrypted
            return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            throw e
        }
    }

    /**
     * Decrypt data (Java fallback if native not available)
     */
    fun decrypt(encryptedData: String, key: ByteArray): String {
        try {
            val combined = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encrypted = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val secretKey = SecretKeySpec(key, "AES")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error", e)
            throw e
        }
    }

    /**
     * Get or create encryption key from Android KeyStore
     */
    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey != null) {
            return existingKey as SecretKey
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt sensitive data using KeyStore key
     */
    fun encryptWithKeyStore(data: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val iv = generateIV()
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(data.toByteArray())

        val combined = iv + encrypted
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypt sensitive data using KeyStore key
     */
    fun decryptWithKeyStore(encryptedData: String): String {
        val key = getOrCreateKey()
        val combined = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)
        val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
        val encrypted = combined.sliceArray(GCM_IV_LENGTH until combined.size)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted)
    }

    /**
     * Securely encrypt server URL
     */
    fun encryptServerUrl(url: String): String {
        return encryptWithKeyStore(url)
    }

    /**
     * Decrypt server URL
     */
    fun decryptServerUrl(encryptedUrl: String): String {
        return decryptWithKeyStore(encryptedUrl)
    }

    /**
     * Generate device-specific key
     */
    fun generateDeviceKey(context: Context): ByteArray {
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        val combined = deviceId + Build.BRAND + Build.MODEL
        return combined.toByteArray().take(32).toByteArray()
    }
}
