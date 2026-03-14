package com.cornspace.aichat.util

import com.cornspace.aichat.BuildConfig

/**
 * Server configuration
 * URL is injected from local.properties via BuildConfig.
 * R8/ProGuard will obfuscate string references in release builds.
 */
object SecretConfig {

    /**
     * Get the server URL from BuildConfig
     */
    fun getServerUrl(): String = BuildConfig.API_BASE_URL

    /**
     * Get the WebView URL from BuildConfig
     */
    fun getWebViewUrl(): String = BuildConfig.WEBVIEW_URL
}
