package com.doffi4.doffisecure.domain.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Result of parsing and normalizing a raw URL or service name string.
 */
data class ParsedDomain(
    val host: String,
    val apexDomain: String? = null,
    val isLocalNetwork: Boolean = false
)

/**
 * Normalizes URLs and service names, detects local IPs / network devices,
 * and extracts the Apex / Root domain for favicon resolution fallbacks.
 */
object DomainUtils {

    private val LOCAL_HOST_EXTENSIONS = setOf(
        "local", "lan", "home.arpa", "internal", "intranet", "router", "fritz.box"
    )

    private val COMPOUND_TLD_SECOND_LEVELS = setOf(
        "co", "com", "net", "org", "edu", "gov", "ac", "ne", "or", "spb", "msk", "pp"
    )

    /**
     * Parses and cleans a raw URL or service name string:
     *  - Decodes URL-encoding (%20, etc.)
     *  - Strips schemes, user credentials, ports, paths, query params, and fragments
     *  - Detects local networks (192.168.x, 10.x, 172.16-31.x, localhost, etc.)
     *  - Computes the Apex domain (e.g. "client.roblox.com" -> "roblox.com")
     */
    fun parse(input: String?): ParsedDomain {
        if (input.isNullOrBlank()) return ParsedDomain(host = "")

        val cleaned = normalizeRaw(input)
        if (cleaned.isBlank()) return ParsedDomain(host = "")

        val isLocal = isLocalAddress(cleaned)
        val apex = if (!isLocal) extractApexDomain(cleaned) else null

        return ParsedDomain(
            host = cleaned,
            apexDomain = apex.takeIf { it != cleaned },
            isLocalNetwork = isLocal
        )
    }

    /**
     * Returns the cleaned host or service name.
     */
    fun extract(input: String?): String = parse(input).host

    /**
     * Returns the apex domain if available, otherwise the host itself.
     */
    fun extractApex(input: String?): String = parse(input).let { it.apexDomain ?: it.host }

    /**
     * Normalizes a raw URL/string into a lowercase clean hostname without paths, ports or schemes.
     */
    private fun normalizeRaw(raw: String): String {
        var str = raw.trim()
        try {
            str = URLDecoder.decode(str, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {}

        // Remove scheme if present
        val schemeIndex = str.indexOf("://")
        if (schemeIndex != -1) {
            str = str.substring(schemeIndex + 3)
        }

        // Remove credentials (e.g. user:pass@)
        val atIndex = str.indexOf('@')
        if (atIndex != -1) {
            str = str.substring(atIndex + 1)
        }

        // Remove path, query, and fragment
        val slashIndex = str.indexOf('/')
        if (slashIndex != -1) {
            str = str.substring(0, slashIndex)
        }
        val queryIndex = str.indexOf('?')
        if (queryIndex != -1) {
            str = str.substring(0, queryIndex)
        }
        val hashIndex = str.indexOf('#')
        if (hashIndex != -1) {
            str = str.substring(0, hashIndex)
        }

        // Remove port (e.g. :8080)
        val colonIndex = str.indexOf(':')
        if (colonIndex != -1) {
            str = str.substring(0, colonIndex)
        }

        var host = str.trim().lowercase()
        host = host.removePrefix("www.")
        return host.trimEnd('.')
    }

    /**
     * Checks if the host is a local IP, localhost, or a local LAN device.
     */
    fun isLocalAddress(host: String): Boolean {
        if (host.isBlank()) return false
        val h = host.lowercase()

        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0.0.0.0") {
            return true
        }

        // Local domain suffixes (e.g. router.local, server.lan)
        if (LOCAL_HOST_EXTENSIONS.any { h.endsWith(".$it") || h == it }) {
            return true
        }

        // Check common local keywords
        if (h == "router" || h == "modem" || h == "gateway") {
            return true
        }

        // Check IPv4 private ranges
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val b0 = parts[0].toInt()
            val b1 = parts[1].toInt()
            if (b0 == 10) return true // 10.0.0.0/8
            if (b0 == 192 && b1 == 168) return true // 192.168.0.0/16
            if (b0 == 172 && b1 in 16..31) return true // 172.16.0.0/12
            if (b0 == 169 && b1 == 254) return true // 169.254.0.0/16
            if (b0 == 127) return true // 127.0.0.0/8
        }

        return false
    }

    /**
     * Extracts the root/apex domain from a hostname.
     * e.g.:
     *  "client.roblox.com" -> "roblox.com"
     *  "anixartd.swiftsoft.com" -> "swiftsoft.com"
     *  "sub.domain.co.uk" -> "domain.co.uk"
     */
    private fun extractApexDomain(host: String): String? {
        val parts = host.split('.')
        if (parts.size <= 2) return null

        // Check for compound ccTLDs (e.g. domain.co.uk, site.spb.ru)
        val last = parts.last()
        val secondLast = parts[parts.size - 2]

        if (last.length == 2 && COMPOUND_TLD_SECOND_LEVELS.contains(secondLast)) {
            // Needs 3 parts for apex domain (e.g. domain.co.uk)
            return if (parts.size >= 3) {
                parts.takeLast(3).joinToString(".")
            } else {
                null
            }
        }

        // Standard single TLD (e.g. roblox.com, swiftsoft.com)
        return parts.takeLast(2).joinToString(".")
    }
}