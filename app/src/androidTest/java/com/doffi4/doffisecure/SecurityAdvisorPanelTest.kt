package com.doffi4.doffisecure

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.doffi4.doffisecure.domain.advisor.*
import com.doffi4.doffisecure.ui.security.*
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SecurityAdvisorPanelTest {
    @get:Rule val compose = createComposeRule()
    private val payload = AdvisorSummary(5, 2, 3, 0)
    private fun show(state: AdvisorState, open: () -> Unit = {}, confirm: () -> Unit = {}, cancel: () -> Unit = {}) {
        compose.setContent { DecryptumTheme(dynamicColor = false) { SecurityAdvisorPanel(state, open, confirm, cancel) } }
    }
    @Test fun introDoesNotConfirmAutomatically() {
        var confirmed = 0
        show(AdvisorState.Idle, confirm = { confirmed++ })
        compose.onNodeWithTag("advisor_intro").assertIsDisplayed()
        assertEquals(0, confirmed)
    }
    @Test fun consentCanDeclineWithoutSending() {
        var confirmed = 0; var declined = 0
        show(AdvisorState.Consent(payload), confirm = { confirmed++ }, cancel = { declined++ })
        compose.onNodeWithTag("advisor_decline").performClick()
        assertEquals(0, confirmed); assertEquals(1, declined)
    }
    @Test fun consentShowsExactPayloadBeforeSending() {
        var confirmed = 0
        show(AdvisorState.Consent(payload), confirm = { confirmed++ })
        compose.onNodeWithTag("advisor_view_data").performClick()
        compose.onNodeWithTag("advisor_payload").assertIsDisplayed()
        compose.onNodeWithText("\"weak_password_count\": 2", substring = true).assertExists()
        assertEquals(0, confirmed)
        compose.onNodeWithTag("advisor_data_close").performClick()
        compose.onNodeWithTag("advisor_confirm").performClick()
        assertEquals(1, confirmed)
    }
    @Test fun loadingCanCancel() {
        var cancelled = 0
        show(AdvisorState.Loading(payload), cancel = { cancelled++ })
        compose.onNodeWithTag("advisor_loading").assertIsDisplayed()
        compose.onNodeWithTag("advisor_cancel").performClick()
        assertEquals(1, cancelled)
    }
    @Test fun resultContainsChecklistAndLimitations() {
        show(AdvisorState.Result(payload, AdvisorGuidance("Synthetic overview", listOf("Synthetic step"))))
        compose.onNodeWithText("Synthetic step", substring = true).assertExists()
        compose.onNodeWithTag("advisor_limitations").assertExists()
    }
    @Test fun missingConfigurationDoesNotShowFakeSuccess() {
        show(AdvisorState.Failed(payload, AdvisorFailure.MISSING_CONFIGURATION))
        compose.onNodeWithTag("advisor_failure_MISSING_CONFIGURATION").assertIsDisplayed()
        compose.onNodeWithTag("advisor_result").assertDoesNotExist()
    }
    @Test fun offlineHasExplicitRetry() {
        var retries = 0
        show(AdvisorState.Failed(payload, AdvisorFailure.OFFLINE), open = { retries++ })
        compose.onNodeWithTag("advisor_retry").performClick()
        assertEquals(1, retries)
    }
}
