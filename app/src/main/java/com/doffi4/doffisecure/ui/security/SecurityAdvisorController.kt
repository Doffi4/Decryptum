package com.doffi4.doffisecure.ui.security

import com.doffi4.doffisecure.domain.advisor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AdvisorState {
    data object Idle : AdvisorState
    data class Consent(val payload: AdvisorSummary) : AdvisorState
    data class Loading(val payload: AdvisorSummary) : AdvisorState
    data class Result(val payload: AdvisorSummary, val guidance: AdvisorGuidance) : AdvisorState
    data class Failed(val payload: AdvisorSummary, val reason: AdvisorFailure) : AdvisorState
}

/** Main-thread, screen-scoped state. Holds only sanitized aggregates, never local item references. */
class SecurityAdvisorController(
    private val service: SecurityAdvisorService,
    private val scope: CoroutineScope,
    private val canRequest: () -> Boolean,
) {
    private val mutableState = MutableStateFlow<AdvisorState>(AdvisorState.Idle)
    val state = mutableState.asStateFlow()
    private var summary: AdvisorSummary? = null
    private var job: Job? = null
    private var generation = 0L

    fun setSummary(value: AdvisorSummary?) {
        cancel()
        summary = value
    }

    fun openConsent() {
        if (job?.isActive == true || !canRequest()) return
        val payload = summary ?: return
        mutableState.value = AdvisorState.Consent(payload)
    }

    fun confirm(language: AdvisorLanguage) {
        val consent = mutableState.value as? AdvisorState.Consent ?: return
        if (!canRequest() || summary != consent.payload) { cancel(); return }
        val token = ++generation
        mutableState.value = AdvisorState.Loading(consent.payload)
        job = scope.launch {
            if (!canRequest() || token != generation) return@launch
            val outcome = try { service.advise(consent.payload, language) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { AdvisorOutcome.Failure(AdvisorFailure.ERROR) }
            if (token == generation && canRequest()) {
                mutableState.value = when (outcome) {
                    is AdvisorOutcome.Success -> AdvisorState.Result(consent.payload, outcome.guidance)
                    is AdvisorOutcome.Failure -> AdvisorState.Failed(consent.payload, outcome.reason)
                }
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        mutableState.value = AdvisorState.Idle
    }
}
