package com.doffi4.doffisecure

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import com.doffi4.doffisecure.ui.security.SecurityCenterScreen
import com.doffi4.doffisecure.ui.security.SecurityCenterState
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SecurityCenterScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun show(state: SecurityCenterState, navigate: (Long) -> Unit = {}, retry: () -> Unit = {}) {
        compose.setContent { DecryptumTheme(dynamicColor = false) {
            SecurityCenterScreen(state, {}, navigate, {}, retry)
        } }
    }
    @Test fun emptyVault() {
        show(SecurityCenterState.Ready(LocalSecurityAnalyzer().analyze(emptyList())))
        compose.onNodeWithTag("security_empty").assertIsDisplayed()
    }
    @Test fun loading() {
        show(SecurityCenterState.Loading)
        compose.onNodeWithTag("security_loading").assertIsDisplayed()
        compose.onNodeWithTag("security_summary").assertDoesNotExist()
    }
    @Test fun errorCanRetry() {
        var retries = 0
        show(SecurityCenterState.Error, retry = { retries++ })
        compose.onNodeWithTag("security_retry").performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("security_summary").assertDoesNotExist()
    }
    @Test fun noLocalFindingsIsScopedAndBreachIsNotChecked() {
        show(SecurityCenterState.Ready(LocalSecurityAnalyzer().analyze(listOf(
            Password(1, "Synthetic", "alice", "x7#M2!qL9@vT6&zR", null, 0)
        ))))
        compose.onNodeWithTag("security_no_findings").assertExists()
        compose.onNodeWithTag("security_list").performScrollToNode(hasTestTag("security_breach_unavailable"))
        compose.onNodeWithTag("security_breach_unavailable").assertIsDisplayed()
    }
    @Test fun affectedEntryNavigatesWithoutRenderingSecret() {
        var selected: Long? = null
        show(SecurityCenterState.Ready(LocalSecurityAnalyzer().analyze(listOf(
            Password(7, "Synthetic", "alice", "secret123", null, 0)
        ))), navigate = { selected = it })
        compose.onNodeWithTag("security_WEAK_PASSWORD").performClick()
        compose.onNodeWithTag("security_list").performScrollToNode(hasTestTag("security_item_7"))
        compose.onNodeWithTag("security_item_7").performClick()
        assertEquals(7L, selected)
        compose.onNodeWithText("secret123", substring = true).assertDoesNotExist()
    }
}
