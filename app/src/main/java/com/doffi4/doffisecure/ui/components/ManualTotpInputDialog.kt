package com.doffi4.doffisecure.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.security.totp.TotpGenerator

@Composable
fun ManualTotpInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    val parsedConfig = remember(text) { TotpGenerator.parseOtpAuth(text) }
    val isError = text.isNotBlank() && parsedConfig == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.totp_dialog_enter_key_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.totp_field_secret_hint)) },
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (secretVisible) R.string.action_hide else R.string.action_show),
                            )
                        }
                    },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.totp_error_invalid_key)) }
                    } else null,
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedConfig != null) {
                        onConfirm(text.trim())
                    }
                },
                enabled = parsedConfig != null
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
