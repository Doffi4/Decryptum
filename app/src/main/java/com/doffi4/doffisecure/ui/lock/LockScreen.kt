package com.doffi4.doffisecure.ui.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.doffi4.doffisecure.domain.model.PasswordStrength

@Composable
fun LockScreen(
    viewModel: AppLockViewModel,
) {
    val lockState by viewModel.lockState.collectAsState()
    val input by viewModel.input.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    val isSetup = lockState == LockState.NeedsSetup

    // Dev-mode: live vault warm-up progress while the lock screen is visible.
    val warmupProgress by viewModel.warmupProgress.collectAsState()
    val warmupRunning by viewModel.warmupRunning.collectAsState()
    val showWarmup by viewModel.showWarmupProgress.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    if (activity == null) {
        // Fallback: show a plain message if FragmentActivity is unavailable
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(contentAlignment = Alignment.Center) {
                Text("Unsupported activity type", color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    // Biometric availability check with crash protection
    val biometricAvailable = remember {
        try {
            val bm = androidx.biometric.BiometricManager.from(activity)
            bm.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                    androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    // Safe biometric prompt creation
    val prompt = remember {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            BiometricPrompt(
                activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.onBiometricSuccess()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        ) { viewModel.onBiometricError(errString.toString()) }
                    }
                    override fun onAuthenticationFailed() {
                        viewModel.onBiometricError("Biometric not recognized.")
                    }
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    // Only authenticate if biometric is available AND prompt was created successfully
    LaunchedEffect(lockState) {
        if (lockState == LockState.Locked && biometricAvailable && !isSetup && prompt != null) {
            try {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Decryptum")
                        .setSubtitle("Authenticate to access your passwords")
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                        .build()
                )
            } catch (_: Exception) {
                viewModel.onBiometricError("Biometric authentication unavailable")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isSetup) "Set Up Master Password" else "Decryptum is Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSetup)
                    "Create a password to protect your vault. It cannot be recovered if forgotten."
                else
                    "Enter your master password or use biometrics to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = input.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Master Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSetup) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (!isSetup) viewModel.submit() }),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (isSetup) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = input.confirmPassword,
                    onValueChange = viewModel::onConfirmChange,
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Non-blocking strength hint: the user may pick ANY password, but we
            // gently recommend something stronger when it lands in the red zone.
            if (isSetup && input.password.isNotBlank() &&
                PasswordStrength.fromPassword(input.password) == PasswordStrength.WEAK
            ) {
                Text(
                    text = "Ваш пароль слабый. Рекомендуем поставить более сложный пароль для безопасности ваших аккаунтов.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Error message with animation
            AnimatedVisibility(visible = input.error != null) {
                input.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Error message with animated visibility for smooth appearance
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSetup) "Create & Unlock" else "Unlock", fontSize = 16.sp)
            }

            // Biometric unlock button (only when locked, not during setup)
            if (!isSetup && biometricAvailable && prompt != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            prompt?.authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Decryptum")
                                    .setSubtitle("Authenticate to access your passwords")
                                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                                    .build()
                            )
                        } catch (_: Exception) {
                            viewModel.onBiometricError("Biometric launch failed")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometrics", fontSize = 16.sp)
                }
            }

            // Dev-mode: live warm-up progress while the warm-up runs in the background.
            AnimatedVisibility(
                visible = showWarmup && (warmupRunning || warmupProgress < 100)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text(
                        text = "Warm-up: $warmupProgress%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { warmupProgress / 100f },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            }
        }
    }
}
