package com.cornspace.aichat.util

/**
 * Secret configuration with native library protection.
 *
 * URLs are stored in native C++ code with XOR encryption.
 * This makes it harder to extract via simple decompilation.
 *
 * IMPORTANT: No client-side protection is 100% secure.
 * Determined attackers can still extract values via:
 * - Network traffic analysis (mitmproxy, Wireshark)
 * - Runtime memory inspection (Frida)
 * - Native binary reverse engineering (IDA Pro)
 */
object SecretConfig {

    init {
        System.loadLibrary("aichat")
    }

    /**
     * Get the server URL from native library
     */
    external fun getServerUrl(): String

    /**
     * Get the WebView URL from native library
     */
    external fun getWebViewUrl(): String
}
