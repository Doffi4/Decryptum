package com.doffi4.doffisecure.ui.password

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel = koinViewModel(),
) {
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
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val hasCharset = includeUpper || includeLower || includeDigits || includeSymbols

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text("Генератор паролей", fontWeight = FontWeight.SemiBold)
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
                .padding(top = padding.calculateTopPadding()) // Беремо тільки верхній отступ для TopBar!
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), // bottom = 100.dp дає можливість прокрутити контент над капсулою
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GeneratorPreviewCard(
                password = password,
                visible = passwordVisible,
                onToggleVisibility = viewModel::togglePasswordVisibility,
                onCopy = viewModel::copyPassword,
                onSave = viewModel::openSaveDialog,
            )

            // Generate is placed right under the preview (above the presets)
            // so generating is always one tap away without scrolling.
            Button(
                onClick = viewModel::regenerate,
                enabled = hasCharset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Сгенерировать")
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
            // Password readout (monospace) with a visibility toggle.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (password.isEmpty()) {
                    Text(
                        text = "Выберите наборы символов",
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
                        contentDescription = if (visible) "Скрыть пароль" else "Показать пароль"
                    )
                }
            }

            // Live strength badge.
            PasswordStrengthBadge(password = password, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Копировать")
                }
                Button(
                    onClick = onSave,
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сохранить")
                }
            }
        }
    }
}
/** One-tap length+charset presets (weak → very strong), compact layout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsCard(onPreset: (PasswordPreset) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Быстрые пресеты", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PASSWORD_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { onPreset(preset) },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
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
                    Text("Длина пароля", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "$length символов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
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
                        steps = 55 // discrete stops: 64 - 8 - 1
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
                    Text("Дополнительно", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Наборы символов и исключение похожих",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
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
                        headlineContent = { Text("Прописные буквы (A–Z)") },
                        trailingContent = {
                            Checkbox(checked = includeUpper, onCheckedChange = onUpperChange)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Строчные буквы (a–z)") },
                        trailingContent = {
                            Checkbox(checked = includeLower, onCheckedChange = onLowerChange)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Цифры (0–9)") },
                        trailingContent = {
                            Checkbox(checked = includeDigits, onCheckedChange = onDigitsChange)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Символы (!@#$…)") },
                        trailingContent = {
                            Checkbox(checked = includeSymbols, onCheckedChange = onSymbolsChange)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Исключить похожие (0O1lI)") },
                        supportingContent = { Text("Убирает неоднозначные символы") },
                        trailingContent = {
                            Checkbox(checked = excludeLookalikes, onCheckedChange = onExcludeLookalikesChange)
                        }
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
        title = { Text("Сохранить пароль", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = service,
                    onValueChange = onServiceChange,
                    label = { Text("Сервис") },
                    placeholder = { Text("Например, google.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Логин") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Pre-filled password (read-only) with eye + regenerate actions.
                OutlinedTextField(
                    value = password,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Пароль") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = onRegenerate, enabled = password.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Перегенерировать",
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
                                    contentDescription = if (passwordVisible) "Скрыть" else "Показать"
                                )
                            }
                        }
                    }
                )
                PasswordStrengthBadge(password = password, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = canSave) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}