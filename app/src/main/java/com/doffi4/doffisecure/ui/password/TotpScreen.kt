package com.doffi4.doffisecure.ui.password

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.ui.components.scanQrCodeFromUri
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.TotpConfig
import com.doffi4.doffisecure.security.totp.GoogleAuthMigrationParser
import com.doffi4.doffisecure.security.totp.TotpGenerator
import com.doffi4.doffisecure.ui.components.GoogleAuthImportDialog
import com.doffi4.doffisecure.ui.components.ManualTotpInputDialog
import com.doffi4.doffisecure.ui.components.QrCodeScannerDialog
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpScreen(
    modifier: Modifier = Modifier,
    viewModel: PasswordViewModel = koinViewModel(),
    onNavigateToDetail: (Long) -> Unit,
) {
    val totpPasswords by viewModel.totpPasswords.collectAsState()
    val loadFavicons by viewModel.loadFavicons.collectAsState()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val noQrMessage = stringResource(R.string.totp_error_no_qr_found)
    LaunchedEffect(viewModel, snackbarHostState, context) {
        viewModel.uiEvent.collect { event ->
            if (event is PasswordUiEvent.ShowToast) {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    var searchQuery by remember { mutableStateOf("") }

    var showAddOptionsDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var pendingGoogleAuthAccounts by remember { mutableStateOf<List<TotpConfig>?>(null) }

    val handleScannedCode: (String) -> Unit = remember {
        { rawCode ->
            if (GoogleAuthMigrationParser.isMigrationUri(rawCode)) {
                val accounts = GoogleAuthMigrationParser.parseMigrationUri(rawCode)
                if (accounts.isNotEmpty()) {
                    pendingGoogleAuthAccounts = accounts
                } else {
                    val parsed = TotpGenerator.parseOtpAuth(rawCode)
                    val service = parsed?.issuer?.takeIf { it.isNotBlank() } ?: "2FA Account"
                    val username = parsed?.accountName ?: ""
                    viewModel.createTotpAccount(service, username, rawCode)
                }
            } else {
                val parsed = TotpGenerator.parseOtpAuth(rawCode)
                val service = parsed?.issuer?.takeIf { it.isNotBlank() } ?: "2FA Account"
                val username = parsed?.accountName ?: ""
                viewModel.createTotpAccount(service, username, rawCode)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanQrCodeFromUri(
                context = context,
                uri = uri,
                onSuccess = handleScannedCode,
                onNotFound = {
                    Toast.makeText(context, noQrMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val filteredList = remember(totpPasswords, searchQuery) {
        if (searchQuery.isBlank()) {
            totpPasswords
        } else {
            totpPasswords.filter {
                it.service.contains(searchQuery, ignoreCase = true) ||
                        it.username.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (pendingGoogleAuthAccounts != null) {
        GoogleAuthImportDialog(
            accounts = pendingGoogleAuthAccounts!!,
            onDismiss = { pendingGoogleAuthAccounts = null },
            onConfirm = {
                val toImport = pendingGoogleAuthAccounts!!
                pendingGoogleAuthAccounts = null
                viewModel.importTotpAccounts(toImport)
            }
        )
    }

    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.totp_btn_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        onClick = {
                            showAddOptionsDialog = false
                            showQrScanner = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.totp_btn_scan_qr),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.totp_btn_scan_qr_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showAddOptionsDialog = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.totp_btn_pick_gallery),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.totp_btn_pick_gallery_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showAddOptionsDialog = false
                            showManualInput = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.totp_btn_enter_manually),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onCodeScanned = { rawCode ->
                showQrScanner = false
                handleScannedCode(rawCode)
            },
            onManualInputRequested = {
                showQrScanner = false
                showManualInput = true
            },
        )
    }

    if (showManualInput) {
        ManualTotpInputDialog(
            onDismiss = { showManualInput = false },
            onConfirm = { secret ->
                showManualInput = false
                handleScannedCode(secret)
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.totp_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddOptionsDialog = true },
                modifier = Modifier.padding(bottom = 90.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.totp_search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )

            if (uiState is PasswordUiState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState is PasswordUiState.Error) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.vault_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.loadPasswords() }) {
                        Text(stringResource(R.string.breach_action_retry))
                    }
                }
            } else if (totpPasswords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(80.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.totp_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = stringResource(R.string.totp_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = { showAddOptionsDialog = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.totp_add_button))
                        }
                    }
                }
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty_search_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        TotpAccountCard(
                            account = item,
                            loadFavicons = loadFavicons,
                            onCopyCode = { code -> viewModel.copyTotpCode(code) },
                            onClick = { onNavigateToDetail(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotpAccountCard(
    account: Password,
    loadFavicons: Boolean,
    onCopyCode: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    SiteAvatar(
                        displayName = account.service,
                        size = 38.dp,
                        faviconUrl = account.url ?: "",
                        enabled = loadFavicons,
                    )
                    Column {
                        Text(
                            text = account.service,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (account.username.isNotBlank()) {
                            Text(
                                text = account.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
            )

            val config = remember(account.totpSecret) {
                TotpGenerator.parseOtpAuth(account.totpSecret ?: "")
            }

            if (config != null) {
                var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        currentTimeMillis = System.currentTimeMillis()
                        delay(250)
                    }
                }

                val currentSeconds = currentTimeMillis / 1000L
                val code = remember(currentSeconds / config.periodSeconds, config) {
                    TotpGenerator.generateTotp(config, currentSeconds)
                }
                val remainingSeconds = TotpGenerator.getRemainingSeconds(config.periodSeconds, currentSeconds)
                val progressFraction = TotpGenerator.getRemainingFraction(config.periodSeconds, currentTimeMillis)

                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label = "totpProgress",
                )

                val timerColor by animateColorAsState(
                    targetValue = when {
                        remainingSeconds <= 3 -> MaterialTheme.colorScheme.error
                        remainingSeconds <= 6 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "totpColor",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = TotpGenerator.formatCode(code),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onCopyCode(code) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.totp_copy_code),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(42.dp),
                    ) {
                        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 3.5.dp.toPx()
                            val diameter = size.minDimension - strokeWidth
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                            val arcSize = Size(diameter, diameter)

                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth),
                            )

                            drawArc(
                                color = timerColor,
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            )
                        }

                        Text(
                            text = remainingSeconds.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = timerColor,
                        )
                    }
                }
            }
        }
    }
}
