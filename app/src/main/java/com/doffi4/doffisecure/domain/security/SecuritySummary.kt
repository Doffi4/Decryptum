package com.doffi4.doffisecure.domain.security

enum class SecurityFindingType { WEAK_PASSWORD, REUSED_PASSWORD, DUPLICATE_CREDENTIAL }
enum class SecurityPriority { HIGH, REVIEW }

/** Navigation metadata only. Never contains passwords, OTP seeds or fingerprints. */
data class SecurityItemReference(val id: Long, val service: String, val username: String)
data class SecurityFindingGroup(val items: List<SecurityItemReference>)
data class SecurityFinding(
    val type: SecurityFindingType,
    val priority: SecurityPriority,
    val groups: List<SecurityFindingGroup>,
) {
    val affectedItems: List<SecurityItemReference> = groups.flatMap { it.items }
    val affectedCount: Int = affectedItems.size
}

data class SecuritySummary(
    val totalEntries: Int,
    val passwordEntries: Int,
    val findings: List<SecurityFinding>,
) {
    val passwordlessEntries: Int get() = totalEntries - passwordEntries
    val affectedEntries: Int = findings.flatMap { it.affectedItems }.map { it.id }.toSet().size
    fun count(type: SecurityFindingType): Int = findings.firstOrNull { it.type == type }?.affectedCount ?: 0
    fun groupCount(type: SecurityFindingType): Int = findings.firstOrNull { it.type == type }?.groups?.size ?: 0
}
