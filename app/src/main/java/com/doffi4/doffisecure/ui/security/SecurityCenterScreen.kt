package com.doffi4.doffisecure.ui.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.security.*
import com.doffi4.doffisecure.domain.advisor.AdvisorLanguage
import org.koin.androidx.compose.koinViewModel

@Composable
fun SecurityCenterRoute(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToGenerator: () -> Unit,
    viewModel: SecurityCenterViewModel = koinViewModel(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.start()
                Lifecycle.Event.ON_STOP -> viewModel.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.start()
        onDispose { lifecycle.removeObserver(observer); viewModel.stop() }
    }
    val state by viewModel.state.collectAsState()
    val advisorState by viewModel.advisor.state.collectAsState()
    val language = if (LocalConfiguration.current.locales[0].language == "ru") AdvisorLanguage.RUSSIAN else AdvisorLanguage.ENGLISH
    SecurityCenterScreen(state, onNavigateBack, onNavigateToDetail, onNavigateToGenerator, viewModel::retry,
        advisorState, viewModel.advisor::openConsent, { viewModel.advisor.confirm(language) }, viewModel.advisor::cancel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(
    state: SecurityCenterState,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToGenerator: () -> Unit,
    onRetry: () -> Unit,
    advisorState: AdvisorState = AdvisorState.Idle,
    onOpenAdvisor: () -> Unit = {},
    onConfirmAdvisor: () -> Unit = {},
    onCancelAdvisor: () -> Unit = {},
) {
    var expanded by remember(state) { mutableStateOf<SecurityFindingType?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_center_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.security_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRetry, enabled = state is SecurityCenterState.Ready || state is SecurityCenterState.Error) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.security_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("security_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "local") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.security_local_label), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.security_local_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                SecurityCenterState.Inactive, SecurityCenterState.Loading -> item(key = "loading") {
                    StatusCard(Icons.Default.Security, R.string.security_loading, R.string.security_loading_description,
                        "security_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                SecurityCenterState.Locked -> item(key = "locked") {
                    StatusCard(Icons.Default.Lock, R.string.security_locked, R.string.security_locked_description, "security_locked")
                }
                SecurityCenterState.Error -> item(key = "error") {
                    StatusCard(Icons.Default.Info, R.string.security_error, R.string.security_error_description, "security_error") {
                        FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("security_retry")) {
                            Text(stringResource(R.string.security_retry))
                        }
                    }
                }
                is SecurityCenterState.Ready -> {
                    val summary = state.summary
                    item(key = "summary") { SummaryCard(summary) }
                    if (summary.totalEntries == 0) {
                        item(key = "empty") {
                            StatusCard(Icons.Default.Key, R.string.security_empty, R.string.security_empty_description, "security_empty") {
                                TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.security_back_to_vault)) }
                            }
                        }
                    } else {
                        item(key = "checks_heading") {
                            Text(stringResource(R.string.security_checks_heading), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                        }
                        for (type in SecurityFindingType.entries) {
                            val finding = summary.findings.firstOrNull { it.type == type }
                            item(key = type.name) {
                                FindingCard(type, finding, expanded == type) {
                                    expanded = if (expanded == type) null else type
                                }
                            }
                            if (finding != null && expanded == type) {
                                finding.groups.forEachIndexed { index, group ->
                                    if (type != SecurityFindingType.WEAK_PASSWORD) {
                                        item(key = "${type.name}_group_$index") {
                                            Text(stringResource(R.string.security_group, index + 1, group.items.size),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    items(group.items, key = { "${type.name}_item_${it.id}" }) { entry ->
                                        AffectedEntry(entry) { onNavigateToDetail(entry.id) }
                                    }
                                }
                            }
                        }
                        item(key = "generator") {
                            FilledTonalButton(onClick = onNavigateToGenerator, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Key, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.security_generate))
                            }
                        }
                    }
                    item(key = "breach") {
                        StatusCard(Icons.Default.CloudOff, R.string.security_breach_unavailable,
                            R.string.security_breach_description, "security_breach_unavailable")
                    }
                    item(key = "limits") {
                        Text(stringResource(R.string.security_limits), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item(key = "advisor") {
                        SecurityAdvisorPanel(advisorState, onOpenAdvisor, onConfirmAdvisor, onCancelAdvisor)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: SecuritySummary) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("security_summary"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Security, null, modifier = Modifier.size(32.dp))
            Text(
                text = if (summary.affectedEntries > 0) stringResource(R.string.security_review_count, summary.affectedEntries)
                else stringResource(R.string.security_no_findings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }.then(
                    if (summary.affectedEntries == 0) Modifier.testTag("security_no_findings") else Modifier
                ),
            )
            Text(stringResource(R.string.security_checked_count, summary.passwordEntries, summary.totalEntries),
                style = MaterialTheme.typography.bodyMedium)
            if (summary.passwordlessEntries > 0) {
                Text(stringResource(R.string.security_passwordless_count, summary.passwordlessEntries),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (summary.affectedEntries == 0) {
                Text(stringResource(R.string.security_no_findings_description), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FindingCard(type: SecurityFindingType, finding: SecurityFinding?, expanded: Boolean, onClick: () -> Unit) {
    val title = when (type) {
        SecurityFindingType.WEAK_PASSWORD -> R.string.security_weak
        SecurityFindingType.REUSED_PASSWORD -> R.string.security_reused
        SecurityFindingType.DUPLICATE_CREDENTIAL -> R.string.security_duplicates
    }
    val description = when (type) {
        SecurityFindingType.WEAK_PASSWORD -> R.string.security_weak_description
        SecurityFindingType.REUSED_PASSWORD -> R.string.security_reused_description
        SecurityFindingType.DUPLICATE_CREDENTIAL -> R.string.security_duplicates_description
    }
    Card(
        onClick = onClick,
        enabled = finding != null,
        modifier = Modifier.fillMaxWidth().testTag("security_${type.name}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text((finding?.affectedCount ?: 0).toString(), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (finding != null) {
                Text(stringResource(if (finding.priority == SecurityPriority.HIGH) R.string.security_priority_high else R.string.security_priority_review),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (expanded) R.string.security_hide_entries else R.string.security_review_entries),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.security_none_in_category), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AffectedEntry(entry: SecurityItemReference, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("security_item_${entry.id}"),
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.service.ifBlank { stringResource(R.string.security_unnamed) }, style = MaterialTheme.typography.titleSmall)
                Text(entry.username.ifBlank { stringResource(R.string.security_no_username) }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, title: Int, description: Int, tag: String, actions: @Composable () -> Unit = {}) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag(tag), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}
