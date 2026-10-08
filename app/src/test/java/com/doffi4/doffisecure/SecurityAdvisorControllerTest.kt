package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.advisor.*
import com.doffi4.doffisecure.ui.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAdvisorControllerTest {
    private val summary = AdvisorSummary(3, 1, 2, 0)
    private val success = AdvisorOutcome.Success(AdvisorGuidance("Synthetic guidance", listOf("Review reuse")))

    @Test fun `analysis and opening or declining consent send nothing`() = runTest {
        var sends = 0
        val controller = SecurityAdvisorController(SecurityAdvisorService { _, _ -> sends++; success }, backgroundScope) { true }
        controller.setSummary(summary)
        controller.confirm(AdvisorLanguage.RUSSIAN); runCurrent()
        assertEquals(0, sends)
        controller.openConsent()
        assertEquals(AdvisorState.Consent(summary), controller.state.value)
        controller.cancel(); runCurrent()
        assertEquals(0, sends)
        assertEquals(AdvisorState.Idle, controller.state.value)
    }

    @Test fun `confirmation sends displayed snapshot exactly once`() = runTest {
        var sends = 0
        var received: AdvisorSummary? = null
        val controller = SecurityAdvisorController(SecurityAdvisorService { dto, _ -> sends++; received = dto; success }, backgroundScope) { true }
        controller.setSummary(summary); controller.openConsent()
        controller.confirm(AdvisorLanguage.ENGLISH); controller.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(summary, received); assertEquals(1, sends)
        assertEquals(AdvisorState.Result(summary, success.guidance), controller.state.value)
    }

    @Test fun `changed analysis invalidates consent even with identical counts`() = runTest {
        var sends = 0
        val controller = SecurityAdvisorController(SecurityAdvisorService { _, _ -> sends++; success }, backgroundScope) { true }
        controller.setSummary(summary); controller.openConsent(); controller.setSummary(summary)
        controller.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(0, sends)
        assertEquals(AdvisorState.Idle, controller.state.value)
    }

    @Test fun `cancel and lock discard even non cooperative late replies`() = runTest {
        var active = true
        val reply = CompletableDeferred<AdvisorOutcome>()
        val controller = SecurityAdvisorController(SecurityAdvisorService { _, _ -> withContext(NonCancellable) { reply.await() } }, backgroundScope) { active }
        controller.setSummary(summary); controller.openConsent(); controller.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertTrue(controller.state.value is AdvisorState.Loading)
        active = false; controller.setSummary(null); reply.complete(success); runCurrent()
        assertEquals(AdvisorState.Idle, controller.state.value)
    }

    @Test fun `auth guard blocks confirmation before lock observer runs`() = runTest {
        var active = true
        var sends = 0
        val controller = SecurityAdvisorController(SecurityAdvisorService { _, _ -> sends++; success }, backgroundScope) { active }
        controller.setSummary(summary); controller.openConsent(); active = false
        controller.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(0, sends)
    }

    @Test fun `failure retry requires new explicit consent`() = runTest {
        var sends = 0
        val controller = SecurityAdvisorController(SecurityAdvisorService { _, _ -> sends++; AdvisorOutcome.Failure(AdvisorFailure.OFFLINE) }, backgroundScope) { true }
        controller.setSummary(summary); controller.openConsent(); controller.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(AdvisorState.Failed(summary, AdvisorFailure.OFFLINE), controller.state.value)
        controller.openConsent(); runCurrent()
        assertEquals(1, sends)
        controller.confirm(AdvisorLanguage.ENGLISH); runCurrent(); assertEquals(2, sends)
    }

    @Test fun `unexpected exceptions never surface raw text`() = runTest {
        val controller = SecurityAdvisorController(SecurityAdvisorService { _, _ -> error("secret-exception") }, backgroundScope) { true }
        controller.setSummary(summary); controller.openConsent(); controller.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(AdvisorState.Failed(summary, AdvisorFailure.ERROR), controller.state.value)
    }
}
