package com.doffi4.doffisecure.domain.advisor

/**
 * Structural outbound whitelist, shared by consent and evaluation.
 * Only validated Int properties can be interpolated; no reflective or generic object serializer.
 * Keep the four fields explicit even if local analysis gains new checks.
 */
object AdvisorPayloadJson {
    fun encode(summary: AdvisorSummary): String =
        """{"password_entry_count":${summary.passwordEntryCount},"weak_password_count":${summary.weakPasswordCount},"reused_password_count":${summary.reusedPasswordCount},"duplicate_credential_count":${summary.duplicateCredentialCount}}"""
}
