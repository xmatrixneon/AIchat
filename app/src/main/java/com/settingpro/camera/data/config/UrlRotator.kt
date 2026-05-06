package com.settingpro.camera.data.config

import com.settingpro.camera.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages domain rotation for failover
 * Thread-safe for coroutines
 */
class UrlRotator(private var config: UrlConfig) {
    companion object {
        private const val TAG = "UrlRotator"
        private const val CONNECTION_TIMEOUT_MS = 15000L // 15 seconds
    }

    private var currentIndex: Int = config.preferredIndex
    private val mutex = Mutex()

    /**
     * Get the next URL to try, skipping dead domains
     * Returns null if all domains are dead
     */
    suspend fun getNextUrl(): String? = mutex.withLock {
        val cleanedConfig = config.cleanupDeadDomains()
        config = cleanedConfig

        val totalDomains = config.domains.size
        if (totalDomains == 0) {
            AppLogger.w(TAG, "No domains available")
            return null
        }

        // Try starting from current index, cycle through all domains
        var attempts = 0
        var index = currentIndex

        while (attempts < totalDomains) {
            val domain = config.domains[index]

            if (!config.isDomainDead(domain)) {
                currentIndex = index
                AppLogger.d(TAG, "Selected domain: $domain (index: $index)")
                return domain
            }

            AppLogger.d(TAG, "Skipping dead domain: $domain")
            index = (index + 1) % totalDomains
            attempts++
        }

        AppLogger.w(TAG, "All domains are marked as dead, will retry after expiry")
        // Return the preferred one anyway, let the connection attempt
        config.getPreferredDomain()
    }

    /**
     * Mark the current domain as dead and advance to next
     */
    suspend fun markCurrentDead() = mutex.withLock {
        val currentDomain = config.domains.getOrNull(currentIndex)
        if (currentDomain != null) {
            AppLogger.w(TAG, "Marking domain as dead: $currentDomain")
            config = config.markDomainDead(currentDomain)
            currentIndex = (currentIndex + 1) % config.domains.size
        }
    }

    /**
     * Mark a specific domain as dead by URL
     */
    suspend fun markDomainDead(url: String) = mutex.withLock {
        // Extract domain from WebSocket URL (wss://domain/path or https://domain/path)
        var domain = url
        if (domain.startsWith("wss://")) domain = domain.removePrefix("wss://")
        if (domain.startsWith("ws://")) domain = domain.removePrefix("ws://")
        if (domain.startsWith("https://")) domain = domain.removePrefix("https://")
        if (domain.startsWith("http://")) domain = domain.removePrefix("http://")
        // Remove path after domain
        domain = domain.split("/")[0]

        AppLogger.w(TAG, "Marking domain as dead: $domain (from URL: $url)")
        config = config.markDomainDead(domain)

        // If the failed domain was the current one, advance to next
        if (config.domains.getOrNull(currentIndex) == domain) {
            currentIndex = (currentIndex + 1) % config.domains.size
        }
    }

    /**
     * Mark a specific domain as successful (new preferred)
     */
    suspend fun markSuccess(domain: String) = mutex.withLock {
        val index = config.domains.indexOf(domain)
        if (index != -1) {
            AppLogger.d(TAG, "Marking domain as successful: $domain (index: $index)")
            config = config.markSuccess(index)
            currentIndex = index
        }
    }

    /**
     * Get current configuration
     */
    suspend fun getConfig(): UrlConfig = mutex.withLock {
        config
    }

    /**
     * Update configuration (for FCM updates)
     */
    suspend fun updateConfig(newConfig: UrlConfig) = mutex.withLock {
        config = newConfig
        currentIndex = newConfig.preferredIndex
        AppLogger.d(TAG, "Config updated, preferred index: ${newConfig.preferredIndex}")
    }

    /**
     * Get the current preferred domain URL
     */
    suspend fun getPreferredUrl(): String? = mutex.withLock {
        config.getPreferredDomain()
    }

    /**
     * Get the current domain URL
     */
    suspend fun getCurrentDomain(): String? = mutex.withLock {
        config.domains.getOrNull(currentIndex)
    }
}
