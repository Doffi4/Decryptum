package com.doffi4.doffisecure.domain.advisor

import com.doffi4.doffisecure.domain.security.SecurityFindingType
import com.doffi4.doffisecure.domain.security.SecuritySummary

/** App-side boundary: copies counts only. The contract/client never see local models. */
object AdvisorSanitizer {
    fun sanitize(summary: SecuritySummary): AdvisorSummary = AdvisorSummary(
        passwordEntryCount = summary.passwordEntries,
        weakPasswordCount = summary.count(SecurityFindingType.WEAK_PASSWORD),
        reusedPasswordCount = summary.count(SecurityFindingType.REUSED_PASSWORD),
        duplicateCredentialCount = summary.count(SecurityFindingType.DUPLICATE_CREDENTIAL),
    )
}
