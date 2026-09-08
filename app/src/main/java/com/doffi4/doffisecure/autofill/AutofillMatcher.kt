package com.doffi4.doffisecure.autofill

import com.doffi4.doffisecure.domain.model.Password

object AutofillMatcher {

    /**
     * Matches a list of [passwords] against the requested [webDomain] and/or [packageName].
     * Returns matching passwords ordered by relevance.
     */
    fun findMatches(
        passwords: List<Password>,
        webDomain: String?,
        packageName: String?
    ): List<Password> {
        val cleanDomain = webDomain?.let { AutofillStructureParser.normalizeDomain(it) }
        val packageAppNames = packageName?.let { extractAppKeywords(it) }.orEmpty()

        val scored = passwords.mapNotNull { password ->
            val score = scoreMatch(password, cleanDomain, packageName, packageAppNames)
            if (score > 0) password to score else null
        }

        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun scoreMatch(
        password: Password,
        cleanDomain: String?,
        packageName: String?,
        packageAppNames: List<String>
    ): Int {
        val serviceLower = password.service.trim().lowercase()
        val urlLower = password.url?.trim()?.lowercase().orEmpty()

        // 1. Check webDomain match (if available)
        if (!cleanDomain.isNullOrBlank()) {
            val passDomain = if (urlLower.isNotBlank()) AutofillStructureParser.normalizeDomain(urlLower) else ""
            if (passDomain.isNotBlank()) {
                if (passDomain == cleanDomain) return 100
                if (cleanDomain.endsWith(".$passDomain") || passDomain.endsWith(".$cleanDomain")) return 90
                // When URL is explicitly set in the vault entry, it MUST NOT match other domains via fuzzy service names
                return 0
            }

            // Fallback ONLY when password has no URL stored (e.g. entry created by service name only):
            val mainDomain = extractMainDomainLabel(cleanDomain)
            if (mainDomain.isNotBlank() && mainDomain.equals(serviceLower, ignoreCase = true)) {
                return 85
            }
        }

        // 2. Check packageName match (if available)
        if (!packageName.isNullOrBlank()) {
            if (urlLower.contains(packageName)) return 95
            if (urlLower.startsWith("android://") && urlLower.contains(packageName)) return 95

            for (keyword in packageAppNames) {
                if (keyword.length >= 3) {
                    if (serviceLower == keyword) return 90
                    if (serviceLower.contains(keyword) || keyword.contains(serviceLower)) return 75
                }
            }
        }

        return 0
    }

    /**
     * Extracts meaningful keyword stems from an Android package name.
     * e.g., "org.telegram.messenger" -> ["telegram", "messenger"]
     * "com.vkontakte.android" -> ["vkontakte", "vk"]
     */
    fun extractAppKeywords(packageName: String): List<String> {
        val ignore = setOf("com", "org", "net", "ru", "io", "android", "app", "mobile", "client", "official")
        val parts = packageName.lowercase().split('.', '_', '-')
            .filter { it.isNotBlank() && it !in ignore && it.length >= 2 }
        val keywords = parts.toMutableList()

        if (packageName.contains("vkontakte")) {
            keywords.add("vk")
        }
        return keywords
    }

    /**
     * Extracts the primary domain label (SLD) from a normalized domain name,
     * accounting for common multipart TLDs.
     * e.g. "paypal.com" -> "paypal"
     * "auth.google.com" -> "google"
     * "login.bbc.co.uk" -> "bbc"
     * "paypal.com.evil-phishing.com" -> "evil-phishing"
     */
    fun extractMainDomainLabel(domain: String): String {
        val labels = domain.lowercase().split('.').filter { it.isNotBlank() }
        if (labels.isEmpty()) return ""
        val multiPartTlds = setOf("co.uk", "com.ua", "org.uk", "gov.uk", "ac.uk", "co.jp", "com.au", "net.au")
        val joinedLastTwo = if (labels.size >= 2) "${labels[labels.size - 2]}.${labels[labels.size - 1]}" else ""
        return if (joinedLastTwo in multiPartTlds && labels.size >= 3) {
            labels[labels.size - 3]
        } else if (labels.size >= 2) {
            labels[labels.size - 2]
        } else {
            labels.first()
        }
    }
}
