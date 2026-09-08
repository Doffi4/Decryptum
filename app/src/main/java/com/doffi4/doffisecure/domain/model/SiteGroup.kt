package com.doffi4.doffisecure.domain.model

/**
 * A group of accounts (passwords) that share the same Service,
 * e.g. service "apple.com" with 4 different accounts.
 */
data class SiteGroup(
    val domain: String,
    val displayName: String,
    val faviconUrl: String = "",
    val parsedDomain: ParsedDomain = ParsedDomain(host = domain),
    val accounts: List<Password>
) {
    val accountCount: Int get() = accounts.size
}

/**
 * Groups a list of passwords by their SERVICE name (case-insensitive,
 * surrounding whitespace ignored). URLs never affect the grouping:
 * accounts with the same service but different URLs land in one block.
 * Preserves the first-seen service name for display.
 */
fun List<Password>.groupBySite(): List<SiteGroup> {
    if (isEmpty()) return emptyList()

    val grouped = linkedMapOf<String, MutableList<Password>>()
    val displayNames = linkedMapOf<String, String>()

    for (pwd in this) {
        // Group by the service name only — never by the URL.
        val service = pwd.service.trim()
        val key = service.ifBlank { "Unknown" }.lowercase()
        grouped.getOrPut(key) { mutableListOf() }.add(pwd)
        displayNames.getOrPut(key) { service.ifBlank { "Unknown" } }
    }

    return grouped.map { (key, accounts) ->
        val rawCandidate = accounts.firstNotNullOfOrNull { it.url?.takeIf(String::isNotBlank) }
            ?: accounts.first().service
        val parsed = DomainUtils.parse(rawCandidate)

        SiteGroup(
            domain = key,
            displayName = displayNames[key] ?: key,
            faviconUrl = parsed.host,
            parsedDomain = parsed,
            accounts = accounts
        )
    }.sortedBy { it.displayName.lowercase() }
}