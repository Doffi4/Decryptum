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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.doffi4.doffisecure.BuildConfig
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.security.AppLocaleManager
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
    val currentLang by viewModel.appLanguage.collectAsState()

    val biometricNotRecognizedMsg = stringResource(R.string.biometric_error_not_recognized)
    val biometricUnavailableMsg = stringResource(R.string.biometric_error_unavailable)
    val biometricSetupFirstMsg = stringResource(R.string.biometric_error_setup_first)
    val biometricLaunchFailedPrefix = stringResource(R.string.biometric_error_launch_failed)
    val appNameStr = stringResource(R.string.app_name)
    val biometricPromptSubtitleStr = stringResource(R.string.biometric_prompt_subtitle)

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
                            Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onAuthenticationFailed() {
                        Toast.makeText(context, biometricNotRecognizedMsg, Toast.LENGTH_SHORT).show()
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

    var isAutofillActive by remember { mutableStateOf(viewModel.isAutofillEnabled(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAutofillActive = viewModel.isAutofillEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> {
                    val msg = event.message.asString(context)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val languageOptions = listOf(
        AppLocaleManager.LANG_SYSTEM to stringResource(R.string.lang_system),
        AppLocaleManager.LANG_RU to stringResource(R.string.lang_ru),
        AppLocaleManager.LANG_EN to stringResource(R.string.lang_en)
    )

    val timeoutOptions = listOf(
        stringResource(R.string.timeout_30s) to 30,
        stringResource(R.string.timeout_1m) to 60,
        stringResource(R.string.timeout_5m) to 300,
        stringResource(R.string.timeout_15m) to 900,
        stringResource(R.string.timeout_never) to 0
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
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
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ──── General Section (Language Selector) ────
            Text(
                text = stringResource(R.string.section_general),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.setting_language), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.setting_language_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    var langExpanded by remember { mutableStateOf(false) }
                    val selectedLangLabel = languageOptions.find { it.first == currentLang }?.second
                        ?: stringResource(R.string.lang_system)
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = !langExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLangLabel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                            modifier = Modifier
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false }
                        ) {
                            languageOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        langExpanded = false
                                        activity?.let { viewModel.setAppLanguage(it, code) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ──── Security Section ────
            Text(
                text = stringResource(R.string.section_security),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
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
                            Text(stringResource(R.string.setting_autolock), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.setting_autolock_desc),
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
                            modifier = Modifier
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )
                                .fillMaxWidth()
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
                            Text(stringResource(R.string.setting_screenshots), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.setting_screenshots_desc),
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

            // Autofill Framework Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.autofill_settings_title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.autofill_settings_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = { viewModel.openAutofillSettings(context) },
                            label = {
                                Text(
                                    stringResource(
                                        if (isAutofillActive) R.string.autofill_status_enabled
                                        else R.string.autofill_status_disabled
                                    )
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isAutofillActive)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isAutofillActive)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.openAutofillSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (isAutofillActive) R.string.autofill_btn_manage
                                else R.string.autofill_btn_enable
                            )
                        )
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.openCredentialProviderSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.credential_provider_btn_enable))
                        }
                        Text(
                            text = stringResource(R.string.credential_provider_settings_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    val alwaysRequireAuth by viewModel.autofillAlwaysRequireAuth.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.autofill_always_require_auth_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.autofill_always_require_auth_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = alwaysRequireAuth,
                            onCheckedChange = { viewModel.setAutofillAlwaysRequireAuth(it) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Tips for Chrome & HyperOS/MIUI
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "💡 " + stringResource(R.string.autofill_chrome_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "📱 " + stringResource(R.string.autofill_xiaomi_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                            Text(stringResource(R.string.setting_password_strength), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.setting_password_strength_desc),
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
                text = stringResource(R.string.section_data_management),
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
                    headlineContent = { Text(stringResource(R.string.setting_export)) },
                    supportingContent = { Text(stringResource(R.string.setting_export_desc)) },
                    leadingContent = { Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { exportLauncher.launch("decryptum_passwords.csv") }
                )
            }

            // Import
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_import)) },
                    supportingContent = { Text(stringResource(R.string.setting_import_desc)) },
                    leadingContent = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) }
                )
            }

            // ──── Developer Section (hidden until dev mode is enabled) ────
            if (devModeEnabled) {
                Text(
                    text = stringResource(R.string.section_developer),
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
                text = stringResource(R.string.section_danger_zone),
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
                    headlineContent = { Text(stringResource(R.string.setting_delete_all), color = MaterialTheme.colorScheme.onErrorContainer) },
                    supportingContent = { Text(stringResource(R.string.setting_delete_all_desc), color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) },
                    modifier = Modifier.clickable { showDeleteDialog = true }
                )
            }

            // Dynamic Version display
            Text(
                text = stringResource(R.string.app_version_label, BuildConfig.VERSION_NAME),
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
            title = { Text(stringResource(R.string.delete_all_dialog_title)) },
            text = { Text(stringResource(R.string.delete_all_dialog_text)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteDialog = false
                        if (biometricPrompt == null) {
                            Toast.makeText(context, biometricUnavailableMsg, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val canAuthenticate = try {
                            BiometricManager.from(activity!!).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                                    BiometricManager.BIOMETRIC_SUCCESS
                        } catch (_: Exception) {
                            false
                        }
                        if (!canAuthenticate) {
                            Toast.makeText(context, biometricSetupFirstMsg, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        try {
                            biometricPrompt.authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle(appNameStr)
                                    .setSubtitle(biometricPromptSubtitleStr)
                                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                                    .build()
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "$biometricLaunchFailedPrefix: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text(stringResource(R.string.delete_all_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}