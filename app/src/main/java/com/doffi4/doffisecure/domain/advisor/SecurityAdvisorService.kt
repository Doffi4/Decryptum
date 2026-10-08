package com.doffi4.doffisecure.domain.advisor

enum class AdvisorLanguage { RUSSIAN, ENGLISH }
enum class AdvisorFailure { MISSING_CONFIGURATION, CONFIGURATION, OFFLINE, UNAVAILABLE, INVALID_RESPONSE, ERROR }
data class AdvisorGuidance(val overview: String, val checklist: List<String>)
sealed interface AdvisorOutcome {
    data class Success(val guidance: AdvisorGuidance) : AdvisorOutcome
    data class Failure(val reason: AdvisorFailure) : AdvisorOutcome
}

/** Remote implementations can only receive this allowlisted DTO, never a vault model. */
fun interface SecurityAdvisorService {
    suspend fun advise(summary: AdvisorSummary, language: AdvisorLanguage): AdvisorOutcome
}

class DisabledSecurityAdvisorService : SecurityAdvisorService {
    override suspend fun advise(summary: AdvisorSummary, language: AdvisorLanguage) =
        AdvisorOutcome.Failure(AdvisorFailure.MISSING_CONFIGURATION)
}
