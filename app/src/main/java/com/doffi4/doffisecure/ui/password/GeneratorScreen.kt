package com.doffi4.doffisecure.ui.password

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.PASSWORD_PRESETS
import com.doffi4.doffisecure.domain.model.PasswordPreset
import com.doffi4.doffisecure.ui.components.PasswordStrengthBadge
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

/**
 * Password generator tab: length slider (8..64), character-class checkboxes,
 * quick presets, a live preview with a hide toggle and a real-time strength
 * badge, and a save dialog that stores the generated password through the
 * encrypted AddPasswordUseCase path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val length by viewModel.length.collectAsState()
    val includeUpper by viewModel.includeUpper.collectAsState()
    val includeLower by viewModel.includeLower.collectAsState()
    val includeDigits by viewModel.includeDigits.collectAsState()
    val includeSymbols by viewModel.includeSymbols.collectAsState()
    val excludeLookalikes by viewModel.excludeLookalikes.collectAsState()
    val password by viewModel.currentPassword.collectAsState()
    val passwordVisible by viewModel.passwordVisible.collectAsState()
    val showSaveDialog by viewModel.showSaveDialog.collectAsState()
    val service by viewModel.service.collectAsState()
    val username by viewModel.username.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is GeneratorEvent.ShowToast) {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    val hasCharset = includeUpper || includeLower || includeDigits || includeSymbols

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.generator_title), fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GeneratorPreviewCard(
                password = password,
                visible = passwordVisible,
                onToggleVisibility = viewModel::togglePasswordVisibility,
                onCopy = viewModel::copyPassword,
                onSave = viewModel::openSaveDialog,
            )

            Button(
                onClick = viewModel::regenerate,
                enabled = hasCharset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.generator_btn_generate))
            }

            PresetsCard(onPreset = viewModel::applyPreset)

            LengthCard(length = length, onLengthChange = viewModel::setLength)

            AdditionalOptionsCard(
                includeUpper = includeUpper,
                includeLower = includeLower,
                includeDigits = includeDigits,
                includeSymbols = includeSymbols,
                excludeLookalikes = excludeLookalikes,
                onUpperChange = viewModel::setIncludeUpper,
                onLowerChange = viewModel::setIncludeLower,
                onDigitsChange = viewModel::setIncludeDigits,
                onSymbolsChange = viewModel::setIncludeSymbols,
                onExcludeLookalikesChange = viewModel::setExcludeLookalikes,
            )
        }
    }

    if (showSaveDialog) {
        SavePasswordDialog(
            service = service,
            username = username,
            password = password,
            passwordVisible = passwordVisible,
            onServiceChange = viewModel::setService,
            onUsernameChange = viewModel::setUsername,
            onToggleVisibility = viewModel::togglePasswordVisibility,
            onRegenerate = viewModel::regenerate,
            onSave = viewModel::savePassword,
            onDismiss = viewModel::dismissSaveDialog,
        )
    }
}

/**
 * Live preview of the current password: masked by default, one tap toggles
 * visibility, strength updates in real time, and Copy/Save act on the current
 * value.
 */
@Composable
private fun GeneratorPreviewCard(
    password: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (password.isEmpty()) {
                    Text(
                        text = stringResource(R.string.generator_hint_select_charset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (visible) password else "•".repeat(password.length),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) stringResource(R.string.action_hide) else stringResource(R.string.action_show)
                    )
                }
            }

            PasswordStrengthBadge(password = password, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }
                Button(
                    onClick = onSave,
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/** One-tap length+charset presets (weak → very strong), compact layout. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PresetsCard(onPreset: (PasswordPreset) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.generator_presets_title), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PASSWORD_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { onPreset(preset) },
                        label = { Text(stringResource(preset.labelRes), style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    )
                }
            }
        }
    }
}

/**
 * Length control, collapsed by default: the header shows the current value
 * (set by the applied preset or the slider), tapping expands the 8..64 slider
 * with a smooth height/fade animation.
 */
@Composable
private fun LengthCard(length: Int, onLengthChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val caretRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "lengthCaret"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.generator_length_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.generator_length_value, length),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) stringResource(R.string.generator_collapse) else stringResource(R.string.generator_expand),
                    modifier = Modifier.rotate(caretRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { onLengthChange(it.roundToInt()) },
                        valueRange = 8f..64f,
                        steps = 55
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("8", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("64", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * "Дополнительно" section - character-class checkboxes plus the look-alike
 * filter, hidden by default and expanded with a smooth height/fade animation.
 */
@Composable
private fun AdditionalOptionsCard(
    includeUpper: Boolean,
    includeLower: Boolean,
    includeDigits: Boolean,
    includeSymbols: Boolean,
    excludeLookalikes: Boolean,
    onUpperChange: (Boolean) -> Unit,
    onLowerChange: (Boolean) -> Unit,
    onDigitsChange: (Boolean) -> Unit,
    onSymbolsChange: (Boolean) -> Unit,
    onExcludeLookalikesChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val caretRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "additionalCaret"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.generator_additional_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.generator_additional_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) stringResource(R.string.generator_collapse) else stringResource(R.string.generator_expand),
                    modifier = Modifier.rotate(caretRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(160))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.generator_upper)) },
                        trailingContent = {
                            Checkbox(checked = includeUpper, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable { onUpperChange(!includeUpper) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.generator_lower)) },
                        trailingContent = {
                            Checkbox(checked = includeLower, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable { onLowerChange(!includeLower) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.generator_digits)) },
                        trailingContent = {
                            Checkbox(checked = includeDigits, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable { onDigitsChange(!includeDigits) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.generator_symbols)) },
                        trailingContent = {
                            Checkbox(checked = includeSymbols, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable { onSymbolsChange(!includeSymbols) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.generator_exclude_lookalikes)) },
                        supportingContent = { Text(stringResource(R.string.generator_exclude_lookalikes_desc)) },
                        trailingContent = {
                            Checkbox(checked = excludeLookalikes, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable { onExcludeLookalikesChange(!excludeLookalikes) }
                    )
                }
            }
        }
    }
}

/**
 * Save dialog: service + login fields, the generated password pre-filled in a
 * read-only field (with visibility toggle and a "regenerate" button), and the
 * live strength badge. Saving goes through the encrypted AddPasswordUseCase.
 */
@Composable
private fun SavePasswordDialog(
    service: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onServiceChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canSave = service.isNotBlank() && username.isNotBlank() && password.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generator_save_dialog_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = service,
                    onValueChange = onServiceChange,
                    label = { Text(stringResource(R.string.field_service)) },
                    placeholder = { Text(stringResource(R.string.placeholder_service)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.field_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_password)) },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRegenerate, enabled = password.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.generator_regenerate),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onToggleVisibility) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (passwordVisible) stringResource(R.string.action_hide) else stringResource(R.string.action_show)
                                )
                            }
                        }
                    }
                )
                PasswordStrengthBadge(password = password, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}