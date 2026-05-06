package com.settingpro.camera.data.config

import com.google.gson.Gson

/**
 * Configuration for domain failover system
 */
data class UrlConfig(
    val domains: List<String>,
    val preferredIndex: Int = 0,
    val deadDomains: Map<String, Long> = emptyMap()
) {
    init {
        require(preferredIndex >= 0 && preferredIndex < domains.size) {
            "preferredIndex $preferredIndex is out of bounds for domains list of size ${domains.size}"
        }
    }

    companion object {
        private const val DEAD_DOMAIN_DURATION_MS = 60 * 60 * 1000L // 1 hour

        fun fromJson(json: String, gson: Gson): UrlConfig? {
            return try {
                gson.fromJson(json, UrlConfig::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get the preferred domain URL
     */
    fun getPreferredDomain(): String? {
        return domains.getOrNull(preferredIndex)
    }

    /**
     * Check if a domain is currently marked as dead
     * Checks both with and without https:// prefix for compatibility
     */
    fun isDomainDead(domain: String): Boolean {
        // Check exact match first
        val expiryTime = deadDomains[domain]
        if (expiryTime != null) {
            return System.currentTimeMillis() < expiryTime
        }
        // Check without protocol prefix
        val domainWithoutProtocol = domain.removePrefix("https://").removePrefix("http://")
        val expiryTime2 = deadDomains[domainWithoutProtocol]
        if (expiryTime2 != null) {
            return System.currentTimeMillis() < expiryTime2
        }
        return false
    }

    /**
     * Mark a domain as dead for 1 hour
     */
    fun markDomainDead(domain: String): UrlConfig {
        return copy(
            deadDomains = deadDomains + (domain to (System.currentTimeMillis() + DEAD_DOMAIN_DURATION_MS))
        )
    }

    /**
     * Clear all dead domain markers and set new preferred index
     */
    fun markSuccess(newPreferredIndex: Int): UrlConfig {
        return copy(
            preferredIndex = newPreferredIndex,
            deadDomains = emptyMap()
        )
    }

    /**
     * Clean up expired dead domain entries
     */
    fun cleanupDeadDomains(): UrlConfig {
        val now = System.currentTimeMillis()
        return copy(
            deadDomains = deadDomains.filterValues { it >= now }
        )
    }

    /**
     * Serialize to JSON
     */
    fun toJson(gson: Gson): String {
        return gson.toJson(this)
    }

    /**
     * Merge new domains from FCM, keeping current preferred if still valid
     */
    fun mergeDomains(newDomains: List<String>, replace: Boolean): UrlConfig {
        val cleaned = cleanupDeadDomains()

        return if (replace) {
            // Complete replacement
            copy(domains = newDomains, preferredIndex = 0, deadDomains = emptyMap())
        } else {
            // Smart merge: keep preferred if still in list
            val currentPreferred = cleaned.domains.getOrNull(cleaned.preferredIndex)
            val newIndex = if (currentPreferred in newDomains) {
                newDomains.indexOf(currentPreferred)
            } else {
                0
            }
            copy(domains = newDomains, preferredIndex = newIndex, deadDomains = cleaned.deadDomains)
        }
    }
}
