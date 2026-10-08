package com.doffi4.doffisecure.ui.password

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.ui.components.PasswordStrengthBadge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Transient credentials stay in this composition; never put them in saved instance state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEntrySheet(
    onDismiss: () -> Unit,
    onSave: suspend (String, String, String) -> Unit,
    onGenerate: () -> String,
    showStrength: Boolean,
) {
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val serviceFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSave = service.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    val submit: () -> Unit = {
        if (!saving) {
            attempted = true
            if (canSave) {
                saving = true
                failed = false
                keyboard?.hide()
                scope.launch {
                    try {
                        onSave(service.trim(), username.trim(), password)
                        service = ""
                        username = ""
                        password = ""
                        visible = false
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Exceptions may contain credentials: show only localized recovery copy.
                        failed = true
                    } finally {
                        saving = false
                    }
                }
            } else {
                when {
                    service.isBlank() -> serviceFocus.requestFocus()
                    username.isBlank() -> usernameFocus.requestFocus()
                    else -> passwordFocus.requestFocus()
                }
            }
        }
    }
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !saving },
    )
    ModalBottomSheet(
        modifier = Modifier.testTag("entry_sheet"),
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Natural height for three fields; the same scroller keeps actions reachable with the IME.
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.add_password_dialog_title),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.add_password_description),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, enabled = !saving) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                }
            }
            Text(stringResource(R.string.add_password_credentials), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = service, onValueChange = { service = it; failed = false },
                label = { Text(stringResource(R.string.field_service)) },
                placeholder = { Text(stringResource(R.string.add_password_service_hint)) },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                modifier = Modifier.fillMaxWidth().focusRequester(serviceFocus).testTag("entry_service"),
                enabled = !saving, singleLine = true, isError = attempted && service.isBlank(),
                supportingText = if (attempted && service.isBlank()) {
                    { Text(stringResource(R.string.add_password_required)) }
                } else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it; failed = false },
                label = { Text(stringResource(R.string.field_username)) },
                placeholder = { Text(stringResource(R.string.add_password_username_hint)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus).testTag("entry_username"),
                enabled = !saving, singleLine = true, isError = attempted && username.isBlank(),
                supportingText = if (attempted && username.isBlank()) {
                    { Text(stringResource(R.string.add_password_required)) }
                } else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; failed = false },
                label = { Text(stringResource(R.string.field_password)) },
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }, enabled = !saving, modifier = Modifier.testTag("entry_visibility")) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(if (visible) R.string.action_hide else R.string.action_show))
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("entry_password"),
                enabled = !saving, singleLine = true, isError = attempted && password.isBlank(),
                supportingText = if (attempted && password.isBlank()) {
                    { Text(stringResource(R.string.add_password_required)) }
                } else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (showStrength) PasswordStrengthBadge(password, Modifier.fillMaxWidth())
            FilledTonalButton(
                onClick = { password = onGenerate(); visible = false; failed = false },
                enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry_generate"),
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_password_generate))
            }
            if (failed) {
                Text(stringResource(R.string.add_password_save_error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("entry_error").semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(onClick = submit, enabled = canSave && !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry_save")) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (saving) R.string.add_password_saving else R.string.action_save_password))
            }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
