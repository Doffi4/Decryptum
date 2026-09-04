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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.doffi4.doffisecure.dev.CpuStats
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
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
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
                    headlineContent = { Text("Show password count") },
                    supportingContent = {
                        Text("Display the number of passwords in the app on the main screen")
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
                    headlineContent = { Text("Show warm-up progress") },
                    supportingContent = {
                        Text("Display the vault warm-up percentage on the main and lock screens")
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
                    headlineContent = { Text("Disable Developer Mode") },
                    supportingContent = { Text("Hides this section and all developer features") },
                    leadingContent = { Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onDisableDevMode() }
                )
            }
        }

        // ──── Warm-up (cold-start prefetch) ────
        SectionLabel("Warm-up")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (warmupProgress >= 100) "Vault warmed (100%)" else "Warming vault: $warmupProgress%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (warmupRunning) {
                        Text(
                            text = "running…",
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
                    Text("Re-run warm-up")
                }
            }
        }

        // ──── Test data ────
        SectionLabel("Test data")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Insert 100 test passwords") },
                    supportingContent = {
                        Text("Fake accounts to test grouping, search, favicons and duplicates")
                    },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { devTools.insertTestPasswords(100) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Insert 500 test passwords") },
                    supportingContent = { Text("Larger volume - check import speed and list performance") },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { devTools.insertTestPasswords(500) }
                )
            }
        }

        // ──── Database ────
        SectionLabel("Database")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Database", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = dbFile.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Size: $dbSize  ·  Rows: $totalCount  ·  Encrypted: $encryptedCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        dbSize = formatFileSize(dbFile.length())
                    }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                OutlinedButton(
                    onClick = { devTools.checkIntegrity() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check encryption integrity")
                }
                Text(
                    text = integrity ?: "Not checked yet",
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
                    Text("Wipe database…")
                }
            }
        }

        // ──── Duplicates ────
        SectionLabel("Duplicates")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val extraRecords = duplicates.sumOf { it.count - 1 }
                Text(
                    text = if (duplicates.isEmpty()) {
                        "No duplicate (service + username) groups"
                    } else {
                        "${duplicates.size} duplicate group(s) · $extraRecords extra record(s)"
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
                    Text("Remove duplicates (keep oldest)")
                }
            }
        }

        // ──── Performance ────
        SectionLabel("Performance")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Live frame-time readout on the main screen (fps / avg ms / jank).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show frame-time overlay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live fps / frame ms / jank readout on the main screen",
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
                        text = "List prefetch: $prefetchCount item(s) ahead",
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
                    text = decryptTest ?: "Not measured yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { devTools.measureDecryption() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Measure full list decryption")
                }
            }
        }

        // ──── System (CPU telemetry) ────
        SectionLabel("System")
        CpuSystemCard(
            cpuStats = devTools.cpuStats,
            cpuOverlayEnabled = showCpuOverlay,
            onCpuOverlayChange = { devTools.setShowCpuOverlay(it) }
        )

        // ──── Diagnostics ────
        SectionLabel("Diagnostics")
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
                    Text("Copy diagnostics to clipboard")
                }
            }
        }

        // ──── Quick actions ────
        SectionLabel("Quick actions")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Auto-lock: ${if (lockTimeout == 0) "Never" else "${lockTimeout}s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { devTools.setLockTimeout(30) }) { Text("30 s") }
                    TextButton(onClick = { devTools.setLockTimeout(300) }) { Text("5 min") }
                    TextButton(onClick = { devTools.setLockTimeout(0) }) { Text("Never") }
                }
                OutlinedButton(
                    onClick = { devTools.lockNow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lock app now")
                }
                TextButton(
                    onClick = { showResetLockDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reset master password (remove app lock)")
                }
            }
        }
    }

    // ──── Confirmations ────
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe database?") },
            text = { Text("This permanently deletes ALL passwords. This action cannot be undone. (Developer tool - no biometric confirmation.)") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeDialog = false
                        devTools.deleteAllPasswords()
                    }
                ) { Text("Wipe") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetLockDialog) {
        AlertDialog(
            onDismissRequest = { showResetLockDialog = false },
            title = { Text("Reset master password?") },
            text = { Text("The master password and auto-lock settings will be cleared. The app will stay locked until you set a new one.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showResetLockDialog = false
                        devTools.resetMasterPassword()
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetLockDialog = false }) { Text("Cancel") }
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
                Text("CPU / система", style = MaterialTheme.typography.titleSmall)
            }

            // Approximate temperature with a rough source hint. "~" because it
            // comes from thermal zones, not a dedicated public CPU API.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Температура CPU",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stats.cpuTempC?.let { "~${"%.1f".format(it)} °C" } ?: "н/д",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tempColor(stats.cpuTempC)
                )
            }
            Text(
                text = "Источник: ${stats.tempSource}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Overall CPU load with a small progress bar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Нагрузка CPU",
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
                    text = "Частота CPU",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stats.cpuFreqMhz?.let { "%.2f ГГц".format(it / 1000f) } ?: "н/д",
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
                        text = "Show CPU overlay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Температура и нагрузка поверх экранов (кроме настроек)",
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
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}