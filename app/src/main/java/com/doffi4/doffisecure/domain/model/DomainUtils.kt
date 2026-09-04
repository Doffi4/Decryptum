package com.doffi4.doffisecure.domain.model

import java.net.URI

/**
 * Extracts a host/domain from a URL or service name string so passwords can
 * be grouped by site and their favicon can be looked up.
 */
object DomainUtils {

    /**
     * Returns the domain (e.g. "apple.com") from a URL or service string.
     * Falls back to the raw trimmed input if no URL structure is found.
     */
    fun extract(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()

        return try {
            var candidate = trimmed
            // Prepend scheme if missing so URI can parse host
            if (!candidate.contains("://")) candidate = "https://$candidate"
            val host = URI(candidate).host ?: ""
            host.removePrefix("www.")
        } catch (_: Exception) {
            // Not a URL - keep the service name as the "domain"
            trimmed.removePrefix("www.")
        }
    }

    /**
     * Builds a favicon URL using Google's public favicon service
     * (no API key required).
     */
    fun faviconUrl(domain: String, size: Int = 128): String {
        val d = domain.trim().lowercase()
        return if (d.isBlank()) "" else "https://www.google.com/s2/favicons?sz=$size&domain=$d"
    }
}