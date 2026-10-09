package com.doffi4.doffisecure.domain.advisor

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
