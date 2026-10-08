package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.advisor.*
import com.doffi4.doffisecure.ui.security.AdvisorState
import com.doffi4.doffisecure.ui.security.SecurityCenterState
import com.doffi4.doffisecure.ui.security.SecurityCenterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val locked = MutableStateFlow(false)
    private val source = MutableStateFlow(listOf(Password(1, "Synthetic", "alice", "short", null, 0)))
    private fun model() = SecurityCenterViewModel({ source }, locked, { !locked.value }, analysisDispatcher = dispatcher)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `inactive screen does not read vault`() = runTest(dispatcher) {
        var reads = 0
        val model = SecurityCenterViewModel({ reads++; source }, locked, { true }, analysisDispatcher = dispatcher)
        advanceUntilIdle()
        assertEquals(0, reads)
        assertEquals(SecurityCenterState.Inactive, model.state.value)
        model.stop()
    }

    @Test fun `lock clears ready findings and unlock starts fresh analysis`() = runTest(dispatcher) {
        val model = model()
        model.start(); advanceUntilIdle()
        assertEquals(1, (model.state.value as SecurityCenterState.Ready).summary.affectedEntries)
        locked.value = true; advanceUntilIdle()
        assertEquals(SecurityCenterState.Locked, model.state.value)
        source.value = emptyList()
        locked.value = false; advanceUntilIdle()
        assertEquals(0, (model.state.value as SecurityCenterState.Ready).summary.totalEntries)
        model.stop()
    }

    @Test fun `stop clears references and stops observing changes`() = runTest(dispatcher) {
        val model = model()
        model.start(); advanceUntilIdle()
        model.stop()
        source.value = emptyList(); advanceUntilIdle()
        assertEquals(SecurityCenterState.Inactive, model.state.value)
    }

    @Test fun `repository error is not successful empty vault and retry recovers`() = runTest(dispatcher) {
        var fail = true
        val model = SecurityCenterViewModel({ flow {
            if (fail) error("secret-bearing-exception-must-not-reach-ui")
            emit(emptyList())
        } }, locked, { true }, analysisDispatcher = dispatcher)
        model.start(); advanceUntilIdle()
        assertEquals(SecurityCenterState.Error, model.state.value)
        fail = false
        model.retry(); advanceUntilIdle()
        assertEquals(0, (model.state.value as SecurityCenterState.Ready).summary.totalEntries)
        model.stop()
    }

    @Test fun `edit resolves finding reactively without mutating source`() = runTest(dispatcher) {
        val model = model()
        model.start(); advanceUntilIdle()
        source.value = listOf(source.value.single().copy(password = "x7#M2!qL9@vT6&zR")); advanceUntilIdle()
        assertTrue((model.state.value as SecurityCenterState.Ready).summary.findings.isEmpty())
        assertEquals("x7#M2!qL9@vT6&zR", source.value.single().password)
        model.stop()
    }

    @Test fun `locked screen never subscribes to source`() = runTest(dispatcher) {
        var reads = 0
        locked.value = true
        val model = SecurityCenterViewModel({ reads++; source }, locked, { false }, analysisDispatcher = dispatcher)
        model.start(); advanceUntilIdle()
        assertEquals(0, reads)
        assertEquals(SecurityCenterState.Locked, model.state.value)
        model.stop()
    }

    @Test fun `lock cancels an in flight source and cannot publish ready`() = runTest(dispatcher) {
        var cancelled = false
        val model = SecurityCenterViewModel({ flow {
            try { awaitCancellation() } finally { cancelled = true }
        } }, locked, { !locked.value }, analysisDispatcher = dispatcher)
        model.start(); runCurrent()
        locked.value = true; advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(SecurityCenterState.Locked, model.state.value)
        model.stop()
    }

    @Test fun `independent auth check prevents publication after source changes auth`() = runTest(dispatcher) {
        var readable = true
        val model = SecurityCenterViewModel({ flow {
            readable = false
            emit(source.value)
        } }, locked, { readable }, analysisDispatcher = dispatcher)
        model.start(); advanceUntilIdle()
        assertEquals(SecurityCenterState.Locked, model.state.value)
        model.stop()
    }

    @Test fun `advisor never sends on local analysis and lock cancels in flight transport`() = runTest(dispatcher) {
        var sends = 0
        var cancelled = false
        val model = SecurityCenterViewModel({ source }, locked, { !locked.value }, analysisDispatcher = dispatcher,
            advisorService = SecurityAdvisorService { _, _ ->
                sends++
                try { awaitCancellation() } finally { cancelled = true }
            })
        model.start(); advanceUntilIdle(); assertEquals(0, sends)
        model.advisor.openConsent(); model.advisor.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(1, sends)
        locked.value = true; advanceUntilIdle()
        assertTrue(cancelled); assertEquals(AdvisorState.Idle, model.advisor.state.value)
        model.stop()
    }

    @Test fun `screen stop clears advisor consent and snapshot`() = runTest(dispatcher) {
        val model = model(); model.start(); advanceUntilIdle()
        model.advisor.openConsent(); assertTrue(model.advisor.state.value is AdvisorState.Consent)
        model.stop(); model.advisor.openConsent()
        assertEquals(AdvisorState.Idle, model.advisor.state.value)
    }

    @Test fun `vault edit invalidates pending advisor consent`() = runTest(dispatcher) {
        var sends = 0
        val model = SecurityCenterViewModel({ source }, locked, { !locked.value }, analysisDispatcher = dispatcher,
            advisorService = SecurityAdvisorService { _, _ -> sends++; AdvisorOutcome.Failure(AdvisorFailure.OFFLINE) })
        model.start(); advanceUntilIdle(); model.advisor.openConsent()
        source.value = listOf(source.value.single().copy(username = "another-synthetic-user")); advanceUntilIdle()
        model.advisor.confirm(AdvisorLanguage.ENGLISH); runCurrent()
        assertEquals(0, sends); assertEquals(AdvisorState.Idle, model.advisor.state.value)
        model.stop()
    }
}
