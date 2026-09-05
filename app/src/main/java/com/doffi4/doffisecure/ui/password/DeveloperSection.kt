package com.doffi4.doffisecure.ui.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.dev.CpuStats
import com.doffi4.doffisecure.ui.util.UiText
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

private const val DB_FILE_NAME = "password_db"

/** Prefetch options offered in the developer section (items composed ahead). */
private val PREFETCH_OPTIONS = listOf(0, 4, 8, 12)

/**
 * Hidden "Developer" section in Settings (only rendered while developer mode
 * is enabled): general toggles plus test data, DB diagnostics, encryption
 * stats, duplicate cleanup, a decryption benchmark, diagnostics and quick
 * security actions.
 */
@Composable
fun DeveloperSection(
    showPasswordCount: Boolean,
    onShowPasswordCountChanged: (Boolean) -> Unit,
    onDisableDevMode: () -> Unit,
    devTools: DevToolsViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    val totalCount by devTools.totalCount.collectAsState()
    val encryptedCount by devTools.encryptedCount.collectAsState()
    val duplicates by devTools.duplicateGroups.collectAsState()
    val decryptTest by devTools.decryptTestResult.collectAsState()
    val integrity by devTools.integrityCheckResult.collectAsState()
    val lockTimeout by devTools.lockTimeout.collectAsState()
    val warmupProgress by devTools.warmupProgress.collectAsState()
    val warmupRunning by devTools.warmupRunning.collectAsState()
    val showWarmupProgress by devTools.showWarmupProgress.collectAsState()
    val showFpsOverlay by devTools.showFpsOverlay.collectAsState()
    val showCpuOverlay by devTools.showCpuOverlay.collectAsState()
    val prefetchCount by devTools.prefetchCount.collectAsState()

    var showWipeDialog by remember { mutableStateOf(false) }
    var showResetLockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        devTools.event.collect { event ->
            if (event is DevToolsEvent.ShowToast) {
                Toast.makeText(context, event.message.asString(context), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Static device / app info
    val diagnostics = remember(context) { buildDiagnostics(context) }

    // Database file info (size refreshed on demand)
    val dbFile = remember(context) { context.getDatabasePath(DB_FILE_NAME) }
    var dbSize by remember(dbFile) { mutableStateOf(formatFileSize(dbFile.length())) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ──── General toggles ────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dev_setting_password_count)) },
                    supportingContent = {
                        Text(stringResource(R.string.dev_setting_password_count_desc))
                    },
                    leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = showPasswordCount,
                            onCheckedChange = onShowPasswordCountChanged
                        )
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dev_setting_warmup_progress)) },
                    supportingContent = {
                        Text(stringResource(R.string.dev_setting_warmup_progress_desc))
                    },
                    leadingContent = { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = showWarmupProgress,
                            onCheckedChange = { devTools.setShowWarmupProgress(it) }
                        )
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dev_btn_disable_mode)) },
                    supportingContent = { Text(stringResource(R.string.dev_disable_mode_desc)) },
                    leadingContent = { Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onDisableDevMode() }
                )
            }
        }

        // ──── Warm-up (cold-start prefetch) ────
        SectionLabel(stringResource(R.string.dev_section_warmup))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (warmupProgress >= 100) {
                            stringResource(R.string.dev_warmup_warmed)
                        } else {
                            stringResource(R.string.dev_warmup_warming, warmupProgress)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (warmupRunning) {
                        Text(
                            text = stringResource(R.string.dev_warmup_running),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { warmupProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { devTools.reWarmWarmup() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.dev_btn_rerun_warmup))
                }
            }
        }

        // ──── Test data ────
        SectionLabel(stringResource(R.string.dev_section_test_data))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dev_btn_insert_100)) },
                    supportingContent = {
                        Text(stringResource(R.string.dev_desc_insert_100))
                    },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { devTools.insertTestPasswords(100) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dev_btn_insert_500)) },
                    supportingContent = { Text(stringResource(R.string.dev_desc_insert_500)) },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { devTools.insertTestPasswords(500) }
                )
            }
        }

        // ──── Database ────
        SectionLabel(stringResource(R.string.dev_section_db))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dev_section_db), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = dbFile.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dev_db_stats_format, dbSize, totalCount, encryptedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        dbSize = formatFileSize(dbFile.length())
                    }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_refresh))
                    }
                }
                OutlinedButton(
                    onClick = { devTools.checkIntegrity() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dev_btn_check_integrity))
                }
                Text(
                    text = integrity ?: stringResource(R.string.dev_integrity_not_checked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showWipeDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dev_btn_wipe_db))
                }
            }
        }

        // ──── Duplicates ────
        SectionLabel(stringResource(R.string.dev_section_duplicates))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val extraRecords = duplicates.sumOf { it.count - 1 }
                Text(
                    text = if (duplicates.isEmpty()) {
                        stringResource(R.string.dev_duplicates_none)
                    } else {
                        stringResource(R.string.dev_duplicates_summary, duplicates.size, extraRecords)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { devTools.removeDuplicates() },
                    enabled = duplicates.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dev_btn_remove_duplicates))
                }
            }
        }

        // ──── Performance ────
        SectionLabel(stringResource(R.string.dev_section_performance))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Live frame-time readout on the main screen (fps / avg ms / jank).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dev_setting_fps_overlay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dev_setting_fps_overlay_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showFpsOverlay,
                        onCheckedChange = { devTools.setShowFpsOverlay(it) }
                    )
                }
                HorizontalDivider()
                // Number of LazyColumn items composed ahead of the viewport.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dev_setting_prefetch_format, prefetchCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PREFETCH_OPTIONS.forEach { option ->
                        val selected = option == prefetchCount
                        TextButton(
                            onClick = { devTools.setPrefetchCount(option) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        ) {
                            Text(option.toString())
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = decryptTest ?: stringResource(R.string.dev_decryption_not_measured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { devTools.measureDecryption() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dev_btn_measure_decryption_title))
                }
            }
        }

        // ──── System (CPU telemetry) ────
        SectionLabel(stringResource(R.string.dev_section_system))
        CpuSystemCard(
            cpuStats = devTools.cpuStats,
            cpuOverlayEnabled = showCpuOverlay,
            onCpuOverlayChange = { devTools.setShowCpuOverlay(it) }
        )

        // ──── Diagnostics ────
        SectionLabel(stringResource(R.string.dev_section_diagnostics))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(diagnostics, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { copyToClipboard(context, "Decryptum diagnostics", diagnostics) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dev_btn_copy_diagnostics_title))
                }
            }
        }

        // ──── Quick actions ────
        SectionLabel(stringResource(R.string.dev_section_quick_actions))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.dev_autolock_format,
                            if (lockTimeout == 0) stringResource(R.string.timeout_never) else "${lockTimeout}s"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { devTools.setLockTimeout(30) }) { Text("30s") }
                    TextButton(onClick = { devTools.setLockTimeout(300) }) { Text("5m") }
                    TextButton(onClick = { devTools.setLockTimeout(0) }) { Text(stringResource(R.string.timeout_never)) }
                }
                OutlinedButton(
                    onClick = { devTools.lockNow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dev_btn_lock_now))
                }
                TextButton(
                    onClick = { showResetLockDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.dev_btn_reset_master))
                }
            }
        }
    }

    // ──── Confirmations ────
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text(stringResource(R.string.dev_wipe_dialog_title)) },
            text = { Text(stringResource(R.string.dev_wipe_dialog_text)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeDialog = false
                        devTools.deleteAllPasswords()
                    }
                ) { Text(stringResource(R.string.dev_wipe_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showResetLockDialog) {
        AlertDialog(
            onDismissRequest = { showResetLockDialog = false },
            title = { Text(stringResource(R.string.dev_reset_dialog_title)) },
            text = { Text(stringResource(R.string.dev_reset_dialog_text)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showResetLockDialog = false
                        devTools.resetMasterPassword()
                    }
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetLockDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/** Small primary-colored header used to group the developer cards. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/**
 * Live CPU telemetry card for the dev tools: approximate temperature from the
 * SoC thermal zones, overall load from /proc/stat, and current top-core
 * frequency. The ViewModel re-samples once per second; collecting the flow
 * inside this dedicated composable keeps the 1 Hz recomposition local.
 */
@Composable
private fun CpuSystemCard(
    cpuStats: StateFlow<CpuStats>,
    cpuOverlayEnabled: Boolean,
    onCpuOverlayChange: (Boolean) -> Unit,
) {
    val stats by cpuStats.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dev_cpu_system_title), style = MaterialTheme.typography.titleSmall)
                }

                // Approximate temperature with a rough source hint. "~" because it
                // comes from thermal zones, not a dedicated public CPU API.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dev_cpu_temperature),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stats.cpuTempC?.let { "~${"%.1f".format(it)} °C" } ?: stringResource(R.string.not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tempColor(stats.cpuTempC)
                    )
                }
                Text(
                    text = stringResource(R.string.dev_cpu_source, stats.tempSource),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Overall CPU load with a small progress bar.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dev_cpu_load),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${stats.cpuLoadPercent.roundToInt()} %",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LinearProgressIndicator(
                    progress = { (stats.cpuLoadPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Current highest core frequency (dev nicety).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dev_cpu_frequency),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stats.cpuFreqMhz?.let {
                            stringResource(R.string.dev_cpu_freq_ghz, it / 1000f)
                        } ?: stringResource(R.string.not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // CPU overlay toggle: show the pill on every screen except Settings.
            HorizontalDivider()
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dev_setting_cpu_overlay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.dev_setting_cpu_overlay_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = cpuOverlayEnabled,
                    onCheckedChange = onCpuOverlayChange
                )
            }
        }
    }
}

/** Cools down the temperature text as it climbs. */
@Composable
private fun tempColor(tempC: Float?): Color = when {
    tempC == null -> MaterialTheme.colorScheme.onSurfaceVariant
    tempC >= 70f -> MaterialTheme.colorScheme.error
    tempC >= 55f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

private fun buildDiagnostics(context: Context): String {
    val pkgInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: Exception) {
        null
    }
    val versionName = pkgInfo?.versionName ?: "?"
    val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return buildString {
        appendLine("Version: $versionName (${versionCode})")
        appendLine("Build: ${if (isDebuggable) "debug" else "release"}")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("App data: ${context.applicationInfo.dataDir}")
        appendLine("DB: ${context.getDatabasePath(DB_FILE_NAME).absolutePath}")
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.dev_toast_diagnostics_copied), Toast.LENGTH_SHORT).show()
}