package com.doffi4.doffisecure.ui.password

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.BreachCheckResult
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.security.FaviconFetcher
import com.doffi4.doffisecure.ui.components.ManualTotpInputDialog
import com.doffi4.doffisecure.ui.components.PasswordStrengthBadge
import com.doffi4.doffisecure.ui.components.QrCodeScannerDialog
import com.doffi4.doffisecure.ui.components.TotpDisplayCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    passwordId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToSecurityCenter: () -> Unit = {},
    viewModel: PasswordViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedPassword by viewModel.selectedPassword.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val showPasswordStrength by viewModel.showPasswordStrength.collectAsState()
    val allPasskeys by viewModel.allPasskeys.collectAsState()
    var showDeletePasskeyConfirm by remember { mutableStateOf(value = false) }
    var showDeleteCredentialConfirm by remember(passwordId) { mutableStateOf(false) }
    var showAddTotpOptions by remember { mutableStateOf(value = false) }
    var showQrScanner by remember { mutableStateOf(value = false) }
    var showManualTotpInput by remember { mutableStateOf(value = false) }
    var showDeleteTotpConfirm by remember { mutableStateOf(value = false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    val faviconRefreshedMsg = stringResource(R.string.favicon_refreshed)

    LaunchedEffect(passwordId) {
        viewModel.loadPasswordById(passwordId)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is PasswordUiEvent.ShowToast -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message.asString(context))
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selectedPassword?.let { pwd ->
                                val raw = pwd.url?.takeIf(String::isNotBlank) ?: pwd.service
                                val parsed = DomainUtils.parse(raw)
                                FaviconFetcher.invalidateHost(context, parsed.host)
                                parsed.apexDomain?.let { FaviconFetcher.invalidateHost(context, it) }
                                refreshKey++
                                scope.launch {
                                    snackbarHostState.showSnackbar(faviconRefreshedMsg)
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh_favicon),
                        )
                    }
                    IconButton(
                        onClick = { 
                            selectedPassword?.let { viewModel.onEditPasswordClicked(it) }
                        },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(
                        onClick = { showDeleteCredentialConfirm = selectedPassword != null },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is PasswordUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PasswordUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = (uiState as PasswordUiState.Error).message.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            else -> {
                selectedPassword?.let { pwd ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val rawCandidate = pwd.url?.takeIf(String::isNotBlank) ?: pwd.service
                        val parsed = remember(pwd, refreshKey) { DomainUtils.parse(rawCandidate) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            SiteAvatar(
                                displayName = pwd.service,
                                faviconUrl = parsed.host,
                                isLocalNetwork = parsed.isLocalNetwork,
                                apexDomain = parsed.apexDomain,
                                size = 52.dp,
                                forceRefresh = refreshKey > 0
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = pwd.service,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (parsed.host.isNotBlank()) {
                                    Text(
                                        text = if (parsed.isLocalNetwork) stringResource(R.string.local_network_label) else parsed.host,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val linkedPasskey = remember(allPasskeys, pwd) {
                            allPasskeys.firstOrNull {
                                (it.linkedPasswordId == pwd.id) ||
                                    (it.userName.equals(pwd.username, ignoreCase = true) && it.rpId.contains(pwd.service, ignoreCase = true))
                            }
                        }

                        DetailItem(label = stringResource(R.string.field_service), value = pwd.service)
                        DetailItem(
                            label = stringResource(R.string.field_username),
                            value = pwd.username,
                            copyIcon = Icons.Default.Person,
                        ) {
                            viewModel.copyUsername(pwd.username)
                        }
                        if (pwd.password.isNotEmpty()) {
                            DetailItem(
                                label = stringResource(R.string.field_password),
                                value = pwd.password,
                                isSecret = true,
                                showStrength = showPasswordStrength,
                                copyIcon = Icons.Default.Key,
                            ) {
                                viewModel.copyPassword(pwd.password)
                            }

                            TextButton(onClick = onNavigateToSecurityCenter) {
                                Icon(Icons.Default.CloudOff, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.security_center_title))
                            }
                        } else if (linkedPasskey != null) {
                            DetailItem(
                                label = stringResource(R.string.field_password),
                                value = stringResource(R.string.passkey_login_only),
                                isSecret = false,
                                showStrength = false
                            )
                        }

                        // 2FA / TOTP Authenticator Section
                        if (!pwd.totpSecret.isNullOrBlank()) {
                            TotpDisplayCard(
                                totpSecret = pwd.totpSecret,
                                onCopyCode = { code -> viewModel.copyTotpCode(code) },
                                onDeleteTotp = { showDeleteTotpConfirm = true }
                            )
                        } else {
                            OutlinedCard(
                                onClick = { showAddTotpOptions = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.totp_btn_add),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.totp_scanner_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        pwd.url?.let { DetailItem(label = stringResource(R.string.field_url), value = it) }

                        // Passkey Section
                        if (linkedPasskey != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text(
                                                text = stringResource(R.string.passkey_details_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        IconButton(onClick = { showDeletePasskeyConfirm = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.passkey_delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = stringResource(R.string.passkey_rp_id, linkedPasskey.rpId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.passkey_algorithm, if (linkedPasskey.algorithm == -7) "ECDSA P-256 (ES256)" else "Ed25519 (-8)"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.passkey_sign_count, linkedPasskey.signCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${stringResource(R.string.detail_created)}: ${formatDate(pwd.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (showDeleteCredentialConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteCredentialConfirm = false },
                title = { Text(stringResource(R.string.delete_credential_title)) },
                text = { Text(stringResource(R.string.delete_credential_message)) },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedPassword?.let { viewModel.deletePassword(it.id) }
                            showDeleteCredentialConfirm = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteCredentialConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (showDeletePasskeyConfirm) {
            val passkeyToDelete = allPasskeys.firstOrNull {
                selectedPassword?.let { pwd ->
                    (it.linkedPasswordId == pwd.id) ||
                        (it.userName.equals(pwd.username, ignoreCase = true) && it.rpId.contains(pwd.service, ignoreCase = true))
                } ?: false
            }
            if (passkeyToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeletePasskeyConfirm = false },
                    title = { Text(stringResource(R.string.passkey_delete)) },
                    text = { Text(stringResource(R.string.passkey_delete_confirm)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deletePasskey(passkeyToDelete.id)
                                showDeletePasskeyConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeletePasskeyConfirm = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }

        if (showEditDialog) {
            selectedPassword?.let { pwd ->
                EditPasswordDialog(
                    passwordToEdit = pwd,
                    showStrength = showPasswordStrength,
                    onDismiss = { viewModel.onDismissEditDialog() },
                    onConfirm = { id, s, u, p ->
                        viewModel.updatePassword(id, s, u, p)
                        viewModel.onDismissEditDialog()
                    }
                )
            }
        }

        if (showAddTotpOptions) {
            AlertDialog(
                onDismissRequest = { showAddTotpOptions = false },
                title = { Text(stringResource(R.string.totp_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                showAddTotpOptions = false
                                showQrScanner = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.totp_btn_scan_qr))
                        }
                        OutlinedButton(
                            onClick = {
                                showAddTotpOptions = false
                                showManualTotpInput = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.totp_btn_enter_manually))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddTotpOptions = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showQrScanner) {
            QrCodeScannerDialog(
                onDismiss = { showQrScanner = false },
                onCodeScanned = { rawCode ->
                    showQrScanner = false
                    selectedPassword?.let { pwd ->
                        viewModel.updateTotpSecret(pwd.id, rawCode)
                    }
                },
                onManualInputRequested = {
                    showQrScanner = false
                    showManualTotpInput = true
                },
            )
        }

        if (showManualTotpInput) {
            ManualTotpInputDialog(
                onDismiss = { showManualTotpInput = false },
                onConfirm = { secret ->
                    showManualTotpInput = false
                    selectedPassword?.let { pwd ->
                        viewModel.updateTotpSecret(pwd.id, secret)
                    }
                }
            )
        }

        if (showDeleteTotpConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteTotpConfirm = false },
                title = { Text(stringResource(R.string.totp_delete_title)) },
                text = { Text(stringResource(R.string.totp_delete_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedPassword?.let { pwd ->
                                viewModel.updateTotpSecret(pwd.id, null)
                            }
                            showDeleteTotpConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteTotpConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    isSecret: Boolean = false,
    showStrength: Boolean = false,
    copyIcon: ImageVector = Icons.Default.ContentCopy,
    onCopy: (() -> Unit)? = null,
) {
    var isVisible by remember(value, isSecret) { mutableStateOf(value = !isSecret) }
    val hideDesc = stringResource(R.string.action_hide)
    val showDesc = stringResource(R.string.action_show)
    val copyDesc = stringResource(R.string.action_copy)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isVisible) value else "••••••••",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (isSecret) FontFamily.Monospace else FontFamily.Default,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSecret) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable(role = Role.Button) { isVisible = !isVisible },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isVisible) hideDesc else showDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    onCopy?.let { copyAction ->
                        QuickCopyBadgeIcon(
                            icon = copyIcon,
                            contentDescription = copyDesc,
                            onClick = copyAction,
                        )
                    }
                }
            }
            if (showStrength) {
                Spacer(modifier = Modifier.height(8.dp))
                PasswordStrengthBadge(
                    password = value,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Quick-copy action button: a main icon (person/key) with a small "copy" badge
 * pinned cleanly in the lower-right corner of a rounded squircle button.
 */
@Composable
private fun QuickCopyBadgeIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val isGenericCopy = icon == Icons.Default.ContentCopy
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .then(
                    if (!isGenericCopy) Modifier.offset(x = (-1.5).dp, y = (-1.5).dp)
                    else Modifier
                )
        )
        if (!isGenericCopy) {
            // Mini copy badge in the lower-right corner (never clipped by squircle shape)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.5.dp, bottom = 1.5.dp)
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

@Composable
fun BreachStatusCard(
    breachState: BreachCheckResult,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (breachState is BreachCheckResult.Idle) return

    val containerColor = when (breachState) {
        is BreachCheckResult.Compromised -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (breachState) {
                is BreachCheckResult.Checking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.breach_status_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                is BreachCheckResult.Clean -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.breach_status_clean),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.breach_action_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                is BreachCheckResult.Compromised -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.breach_status_compromised, breachState.count),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.breach_action_refresh),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                is BreachCheckResult.Error -> {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.breach_status_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.breach_action_retry),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                is BreachCheckResult.Idle -> Unit
            }
        }
    }
}
