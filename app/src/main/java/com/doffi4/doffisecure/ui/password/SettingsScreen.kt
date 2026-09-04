package com.doffi4.doffisecure.ui.password

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.koin.androidx.compose.koinViewModel

/**
 * Walks up the Context wrapper chain to find the host [FragmentActivity].
 * Compose's LocalContext is often a ContextThemeWrapper, so a direct cast
 * to FragmentActivity silently fails and biometrics never show.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current != null) {
        if (current is FragmentActivity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val lockTimeout by viewModel.lockTimeout.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val devModeEnabled by viewModel.devModeEnabled.collectAsState()
    val showDevPasswordCount by viewModel.showPasswordCount.collectAsState()

    // Crash-protected biometric prompt for the destructive "delete all" action.
    val biometricPrompt = remember(activity) {
        if (activity == null) null else try {
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.deleteAllPasswords()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            Toast.makeText(context, "Authentication failed: $errString", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onAuthenticationFailed() {
                        Toast.makeText(context, "Authentication failed. Try again", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { viewModel.exportPasswords(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importPasswords(context, it) } }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> {
                    // Toast survives tab switches (snackbar is scoped to this screen)
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    val timeoutOptions = listOf(
        "30 seconds" to 30, "1 minute" to 60, "5 minutes" to 300,
        "15 minutes" to 900, "Never" to 0
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()) // Беремо тільки верхній отступ для TopBar!
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), // bottom = 100.dp дає можливість доскролити до останньої кнопки
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ──── Security Section ────
            Text(
                text = "Security",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Auto-lock timeout card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Auto-lock Timeout", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Lock vault after inactivity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    var expanded by remember { mutableStateOf(false) }
                    val selectedLabel = timeoutOptions.find { it.second == lockTimeout }?.first ?: "${lockTimeout}s"
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            timeoutOptions.forEach { (label, seconds) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { viewModel.setLockTimeout(seconds); expanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // Screenshot toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Allow Screenshots", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Permit screen recording and screenshots",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = viewModel.allowScreenshots.collectAsState().value,
                        onCheckedChange = { viewModel.setAllowScreenshots(it) }
                    )
                }
            }

            // Password strength evaluation toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Оценка сложности паролей", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Показывать уровень сложности пароля в приложении",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = viewModel.showPasswordStrength.collectAsState().value,
                        onCheckedChange = { viewModel.setShowPasswordStrength(it) }
                    )
                }
            }

            // ──── Data Management Section ────
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
            )

            // Export
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                ListItem(
                    headlineContent = { Text("Export Passwords") },
                    supportingContent = { Text("Save your passwords to a .csv file") },
                    leadingContent = { Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { exportLauncher.launch("doffisecure_passwords.csv") }
                )
            }

            // Import
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                ListItem(
                    headlineContent = { Text("Import Passwords") },
                    supportingContent = { Text("Load passwords from a .csv file") },
                    leadingContent = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) }
                )
            }

            // ──── Developer Section (hidden until dev mode is enabled) ────
            if (devModeEnabled) {
                Text(
                    text = "Developer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                )
                DeveloperSection(
                    showPasswordCount = showDevPasswordCount,
                    onShowPasswordCountChanged = { viewModel.setShowPasswordCount(it) },
                    onDisableDevMode = { viewModel.disableDevMode() }
                )
            }

            // ──── Danger Zone ────
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
            )

            // Delete all passwords
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                ListItem(
                    headlineContent = { Text("Delete All Passwords", color = MaterialTheme.colorScheme.onErrorContainer) },
                    supportingContent = { Text("Permanently removes every stored password", color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) },
                    modifier = Modifier.clickable { showDeleteDialog = true }
                )
            }

            // Version
            Text(
                text = "Decryptum v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        }
    }

    // Delete-all confirmation dialog (requires biometric to proceed)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete all passwords?") },
            text = { Text("This will permanently remove ALL stored passwords. Your fingerprint or device PIN will be required to confirm this action.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteDialog = false
                        if (biometricPrompt == null) {
                            Toast.makeText(context, "Biometric authentication unavailable", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        // Verify real biometric hardware exists before launching
                        val canAuthenticate = try {
                            BiometricManager.from(activity!!).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                                    BiometricManager.BIOMETRIC_SUCCESS
                        } catch (_: Exception) {
                            false
                        }
                        if (!canAuthenticate) {
                            Toast.makeText(context, "Set up fingerprint or screen lock first", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        try {
                            biometricPrompt.authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Confirm deletion")
                                    .setSubtitle("Authenticate to delete all passwords")
                                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                                    .build()
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Biometric launch failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}