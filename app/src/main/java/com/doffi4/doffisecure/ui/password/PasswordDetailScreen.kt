package com.doffi4.doffisecure.ui.password

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.ui.components.PasswordStrengthBadge
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
    viewModel: PasswordViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedPassword by viewModel.selectedPassword.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val showPasswordStrength by viewModel.showPasswordStrength.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        selectedPassword?.let { viewModel.onEditPasswordClicked(it) }
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = {
                        selectedPassword?.let {
                            viewModel.deletePassword(it.id)
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
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
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DetailItem(label = stringResource(R.string.field_service), value = pwd.service)
                        DetailItem(
                            label = stringResource(R.string.field_username),
                            value = pwd.username,
                            copyIcon = Icons.Default.Person,
                            onCopy = {
                                viewModel.copyUsername(pwd.username)
                            }
                        )
                        DetailItem(
                            label = stringResource(R.string.field_password),
                            value = pwd.password,
                            isSecret = true,
                            showStrength = showPasswordStrength,
                            copyIcon = Icons.Default.Key,
                            onCopy = {
                                viewModel.copyPassword(pwd.password)
                            }
                        )
                        pwd.url?.let { DetailItem(label = stringResource(R.string.field_url), value = it) }

                        Spacer(modifier = Modifier.weight(1f))

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
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    isSecret: Boolean = false,
    showStrength: Boolean = false,
    copyIcon: ImageVector = Icons.Default.ContentCopy,
    onCopy: (() -> Unit)? = null
) {
    var isVisible by remember { mutableStateOf(!isSecret) }
    val hideDesc = stringResource(R.string.action_hide)
    val showDesc = stringResource(R.string.action_show)
    val copyDesc = stringResource(R.string.action_copy)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSecret) {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isVisible) hideDesc else showDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onCopy != null) {
                        QuickCopyBadgeIcon(
                            icon = copyIcon,
                            contentDescription = copyDesc,
                            onClick = onCopy
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
 * Round quick-copy action: a main icon (person/key) with a small "copy" badge
 * pinned to the lower-right corner of the button.
 */
@Composable
private fun QuickCopyBadgeIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        // Mini copy badge in the lower-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(9.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}