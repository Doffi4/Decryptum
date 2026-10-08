package com.doffi4.doffisecure.ui.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.data.advisor.ClaudeAdvisorCodec
import com.doffi4.doffisecure.domain.advisor.*
import org.json.JSONObject

@Composable
fun SecurityAdvisorPanel(state: AdvisorState, onOpenConsent: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var showData by remember(state) { mutableStateOf(false) }
    val payload = when (state) {
        AdvisorState.Idle -> null
        is AdvisorState.Consent -> state.payload
        is AdvisorState.Loading -> state.payload
        is AdvisorState.Result -> state.payload
        is AdvisorState.Failed -> state.payload
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("advisor_panel"), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.advisor_optional_label), color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.advisor_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            when (state) {
                AdvisorState.Idle, is AdvisorState.Consent -> {
                    Text(stringResource(R.string.advisor_intro), modifier = Modifier.testTag("advisor_intro"),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenConsent, enabled = state == AdvisorState.Idle,
                        modifier = Modifier.testTag("advisor_open")) { Text(stringResource(R.string.advisor_open)) }
                }
                is AdvisorState.Loading -> {
                    Text(stringResource(R.string.advisor_loading), modifier = Modifier.testTag("advisor_loading"))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("advisor_cancel")) {
                        Text(stringResource(R.string.advisor_cancel))
                    }
                }
                is AdvisorState.Result -> {
                    Text(state.guidance.overview, modifier = Modifier.testTag("advisor_result"), style = MaterialTheme.typography.bodyLarge)
                    state.guidance.checklist.forEachIndexed { index, step ->
                        Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.advisor_limitations), modifier = Modifier.testTag("advisor_limitations"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenConsent) { Text(stringResource(R.string.advisor_request_again)) }
                }
                is AdvisorState.Failed -> {
                    val message = when (state.reason) {
                        AdvisorFailure.MISSING_CONFIGURATION -> R.string.advisor_missing_configuration
                        AdvisorFailure.CONFIGURATION -> R.string.advisor_configuration_error
                        AdvisorFailure.OFFLINE -> R.string.advisor_offline
                        AdvisorFailure.UNAVAILABLE -> R.string.advisor_unavailable
                        AdvisorFailure.INVALID_RESPONSE -> R.string.advisor_invalid_response
                        AdvisorFailure.ERROR -> R.string.advisor_error
                    }
                    Text(stringResource(message), modifier = Modifier.testTag("advisor_failure_${state.reason.name}"),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenConsent, modifier = Modifier.testTag("advisor_retry")) {
                        Text(stringResource(R.string.advisor_retry))
                    }
                }
            }
            if (payload != null && state !is AdvisorState.Consent) {
                TextButton(onClick = { showData = true }, modifier = Modifier.testTag("advisor_view_data")) {
                    Text(stringResource(R.string.advisor_view_data))
                }
            }
        }
    }
    if (state is AdvisorState.Consent && !showData) {
        AlertDialog(onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.advisor_consent_title)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.advisor_consent_description))
                    PayloadPreview(state.payload)
                    TextButton(onClick = { showData = true }, modifier = Modifier.testTag("advisor_view_data")) {
                        Text(stringResource(R.string.advisor_view_data))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.testTag("advisor_confirm")) {
                    Text(stringResource(R.string.advisor_send))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("advisor_decline")) {
                    Text(stringResource(R.string.advisor_decline))
                }
            })
    }
    if (showData && payload != null) {
        AlertDialog(onDismissRequest = { showData = false },
            title = { Text(stringResource(R.string.advisor_data_title)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.advisor_data_description))
                    PayloadPreview(payload)
                }
            },
            confirmButton = {
                TextButton(onClick = { showData = false }, modifier = Modifier.testTag("advisor_data_close")) {
                    Text(stringResource(R.string.advisor_data_close))
                }
            })
    }
}

@Composable
private fun PayloadPreview(payload: AdvisorSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.advisor_counts, payload.passwordEntryCount, payload.weakPasswordCount,
            payload.reusedPasswordCount, payload.duplicateCredentialCount), style = MaterialTheme.typography.bodyMedium)
        val json = remember(payload) { JSONObject(ClaudeAdvisorCodec.summaryJson(payload)).toString(2) }
        SelectionContainer {
            Text(json, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("advisor_payload"))
        }
    }
}
