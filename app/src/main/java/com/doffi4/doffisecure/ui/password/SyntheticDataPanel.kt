package com.doffi4.doffisecure.ui.password

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.dev.SyntheticVaultGenerator
import com.doffi4.doffisecure.dev.TestDatasetRequest
import com.doffi4.doffisecure.dev.TestDataSeedState

@Composable
private fun datasetSummary(entries: Int, services: Int) = stringResource(
    R.string.synthetic_summary,
    pluralStringResource(R.plurals.synthetic_records, entries, entries),
    pluralStringResource(R.plurals.synthetic_services, services, services)
)

/** Existing Developer settings surface; counts and metadata only, no secret preview. */
@Composable
fun SyntheticDataPanel(
    state: TestDataSeedState,
    existingCount: Int,
    onClearResult: () -> Unit,
    onGenerate: (TestDatasetRequest) -> Unit,
) {
    var entries by rememberSaveable { mutableStateOf("500") }
    var services by rememberSaveable { mutableStateOf("200") }
    var selected by remember { mutableStateOf<TestDatasetRequest?>(null) }
    val busy = state == TestDataSeedState.Running
    val failure = when (state) {
        TestDataSeedState.Failed -> R.string.synthetic_failed
        TestDataSeedState.Locked -> R.string.synthetic_locked
        TestDataSeedState.Disabled -> R.string.synthetic_disabled
        else -> null
    }
    val entryCount = entries.toIntOrNull()
    val serviceCount = services.toIntOrNull()
    val entriesValid = entryCount != null && entryCount in 1..SyntheticVaultGenerator.MAX_ENTRIES
    val servicesValid = serviceCount != null && entryCount != null && serviceCount in 1..minOf(200, entryCount)
    val plan = remember(entryCount, serviceCount) {
        if (entriesValid && servicesValid) SyntheticVaultGenerator.plan(entryCount!!, serviceCount!!) else null
    }
    fun select(request: TestDatasetRequest) {
        onClearResult()
        selected = request
    }
    LaunchedEffect(state) { if (state is TestDataSeedState.Saved) selected = null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.synthetic_description), style = MaterialTheme.typography.bodyMedium)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("synthetic_progress"))
                Text(stringResource(R.string.synthetic_saving))
            }
            if (failure != null && selected == null) {
                Text(stringResource(failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("synthetic_error"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((records, groups) in listOf(100 to 70, 500 to 200, 1000 to 200)) {
                    FilterChip(
                        selected = entries == records.toString() && services == groups.toString(),
                        onClick = { entries = records.toString(); services = groups.toString() },
                        enabled = !busy,
                        label = { Text(records.toString()) }
                    )
                }
            }
            OutlinedTextField(
                value = entries, onValueChange = { entries = it },
                label = { Text(stringResource(R.string.synthetic_entries_label)) },
                supportingText = { Text(stringResource(R.string.synthetic_entries_help)) },
                singleLine = true, isError = !entriesValid, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("synthetic_entries")
            )
            OutlinedTextField(
                value = services, onValueChange = { services = it },
                label = { Text(stringResource(R.string.synthetic_services_label)) },
                supportingText = { Text(stringResource(R.string.synthetic_services_help)) },
                singleLine = true, isError = !servicesValid, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("synthetic_services")
            )
            if (plan != null) {
                Text(datasetSummary(plan.entryCount, plan.serviceCount), style = MaterialTheme.typography.titleSmall)
                val counts = plan.allocations.map { it.accountCount }
                Text(stringResource(R.string.synthetic_distribution, counts.min(), counts.max()), style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { plan?.let { select(TestDatasetRequest.Realistic(it)) } },
                enabled = plan != null && !busy,
                modifier = Modifier.fillMaxWidth().testTag("synthetic_preview")
            ) { Text(stringResource(R.string.synthetic_preview)) }
            HorizontalDivider()
            Text(stringResource(R.string.synthetic_fixtures_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.synthetic_fixtures_help), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { select(TestDatasetRequest.SecurityCheck) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("synthetic_security")
            ) { Text(stringResource(R.string.synthetic_security)) }
            OutlinedButton(
                onClick = { select(TestDatasetRequest.Totp) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("synthetic_totp")
            ) { Text(stringResource(R.string.synthetic_totp)) }
            if (state is TestDataSeedState.Saved) {
                Text(
                    stringResource(R.string.synthetic_saved, state.inserted, state.requested),
                    modifier = Modifier.testTag("synthetic_result"), style = MaterialTheme.typography.bodyMedium
                )
                if (state.inserted != state.requested) {
                    Text(stringResource(R.string.synthetic_partial), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    selected?.let { request ->
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text(stringResource(R.string.synthetic_confirm_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.synthetic_warning))
                    Text(stringResource(R.string.synthetic_existing, existingCount))
                    when (request) {
                        is TestDatasetRequest.Realistic -> {
                            Text(datasetSummary(request.plan.entryCount, request.plan.serviceCount))
                            Text(stringResource(R.string.synthetic_network_notice), style = MaterialTheme.typography.bodySmall)
                            // Show a representative minimum/maximum and a few intermediate groups.
                            val examples = request.plan.allocations.sortedBy { it.accountCount }
                                .let { list -> listOf(list.first(), list[list.size / 4], list[list.size / 2], list[list.size * 3 / 4], list.last()).distinct() }
                            examples.forEach {
                                Text(stringResource(R.string.synthetic_service_preview, it.service,
                                    pluralStringResource(R.plurals.synthetic_accounts, it.accountCount, it.accountCount)),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TestDatasetRequest.SecurityCheck -> Text(stringResource(R.string.synthetic_security_expected))
                        TestDatasetRequest.Totp -> Text(stringResource(R.string.synthetic_totp_expected))
                    }
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.synthetic_saving))
                    }
                    if (failure != null) Text(stringResource(failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("synthetic_error"))
                }
            },
            confirmButton = {
                TextButton(onClick = { onGenerate(request) }, enabled = !busy, modifier = Modifier.testTag("synthetic_confirm")) {
                    Text(stringResource(R.string.synthetic_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !busy, modifier = Modifier.testTag("synthetic_cancel")) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
