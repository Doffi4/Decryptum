package com.doffi4.doffisecure.domain.advisor

import com.doffi4.doffisecure.domain.security.SecurityFindingType
import com.doffi4.doffisecure.domain.security.SecuritySummary

/** Outbound allowlist. Numeric aggregates only; never add vault objects/text/identifiers. */
data class AdvisorSummary(
    val passwordEntryCount: Int,
    val weakPasswordCount: Int,
    val reusedPasswordCount: Int,
    val duplicateCredentialCount: Int,
) {
    init {
        require(passwordEntryCount >= 0)
        require(weakPasswordCount in 0..passwordEntryCount)
        require(reusedPasswordCount in 0..passwordEntryCount)
        require(duplicateCredentialCount in 0..passwordEntryCount)
    }
}

/** The only mapper across the local/remote boundary. Does not retain source references. */
object AdvisorSanitizer {
    fun sanitize(summary: SecuritySummary): AdvisorSummary = AdvisorSummary(
        passwordEntryCount = summary.passwordEntries,
        weakPasswordCount = summary.count(SecurityFindingType.WEAK_PASSWORD),
        reusedPasswordCount = summary.count(SecurityFindingType.REUSED_PASSWORD),
        duplicateCredentialCount = summary.count(SecurityFindingType.DUPLICATE_CREDENTIAL),
    )
}
