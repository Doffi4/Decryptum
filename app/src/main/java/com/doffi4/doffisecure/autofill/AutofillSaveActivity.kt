package com.doffi4.doffisecure.autofill

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.UserSettingsManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AutofillSaveActivity : FragmentActivity() {

    private val passwordRepository: IPasswordRepository by inject()
    private val lockManager: AppLockManager by inject()
    private val passwordCrypto: PasswordCrypto by inject()

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val createRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val callingPasswordRequest = createRequest?.callingRequest as? CreatePasswordRequest

        val initialService: String
        val initialUsername: String
        val initialPassword: String
        val initialUrl: String?

        if (callingPasswordRequest != null) {
            initialUsername = callingPasswordRequest.id
            initialPassword = callingPasswordRequest.password
            val callingAppOrigin = try {
                createRequest.callingAppInfo.let { callingApp ->
                    val field = callingApp.javaClass.getDeclaredField("origin")
                    field.isAccessible = true
                    field[callingApp] as? String
                }
            } catch (_: Exception) { null }
            val origin = callingPasswordRequest.origin ?: callingAppOrigin
            val packageName = createRequest.callingAppInfo.packageName
            val domain = origin?.let { DomainUtils.extract(it) }?.takeIf { it.isNotBlank() }
            initialService = when {
                domain != null -> domain
                packageName.isNotBlank() -> {
                    val keywords = AutofillMatcher.extractAppKeywords(packageName)
                    keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
                }
                else -> getString(R.string.app_name)
            }
            initialUrl = origin
        } else {
            initialService = intent.getStringExtra(EXTRA_SERVICE).orEmpty()
            initialUsername = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
            initialPassword = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
            initialUrl = intent.getStringExtra(EXTRA_URL)
        }

        setContent {
            var service by remember { mutableStateOf(initialService) }
            var username by remember { mutableStateOf(initialUsername) }
            var password by remember { mutableStateOf(initialPassword) }
            var passwordVisible by remember { mutableStateOf(value = false) }
            var showMasterPasswordDialog by remember { mutableStateOf(value = false) }

            fun executeSave(serviceToSave: String, usernameToSave: String, passwordToSave: String) {
                lifecycleScope.launch {
                    try {
                        val newPassword = Password(
                            id = 0L,
                            service = serviceToSave.trim(),
                            username = usernameToSave.trim(),
                            password = passwordToSave,
                            url = initialUrl,
                            createdAt = System.currentTimeMillis(),
                        )
                        passwordRepository.addPassword(newPassword)
                        Toast.makeText(
                            this@AutofillSaveActivity,
                            getString(R.string.autofill_saved_success),
                            Toast.LENGTH_SHORT,
                        ).show()

                        if (createRequest != null) {
                            val resultIntent = Intent()
                            PendingIntentHandler.setCreateCredentialResponse(resultIntent, CreatePasswordResponse())
                            setResult(RESULT_OK, resultIntent)
                        } else {
                            setResult(RESULT_OK)
                        }
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@AutofillSaveActivity,
                            e.localizedMessage ?: "Error saving password",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }

            fun checkAuthAndSave(serviceToSave: String, usernameToSave: String, passwordToSave: String) {
                val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
                val mustAuth = isLocked && lockManager.hasMasterPassword()
                if (!mustAuth) {
                    executeSave(serviceToSave, usernameToSave, passwordToSave)
                    return
                }

                val biometricManager = BiometricManager.from(this@AutofillSaveActivity)
                val canAuth = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                val bioCipher = passwordCrypto.getBiometricDecryptCipher()

                if ((canAuth == BiometricManager.BIOMETRIC_SUCCESS) && (bioCipher != null)) {
                    val executor = ContextCompat.getMainExecutor(this@AutofillSaveActivity)
                    val prompt = BiometricPrompt(
                        this@AutofillSaveActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                val cipher = result.cryptoObject?.cipher
                                val unlocked = if (cipher != null) passwordCrypto.unlockWithBiometricCipher(cipher) else false
                                if (unlocked && passwordCrypto.isUnlocked()) {
                                    lockManager.setLocked(locked = false)
                                    lockManager.touchLastActive()
                                    executeSave(serviceToSave, usernameToSave, passwordToSave)
                                } else {
                                    showMasterPasswordDialog = true
                                }
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                showMasterPasswordDialog = true
                            }

                            override fun onAuthenticationFailed() {
                                Toast.makeText(
                                    this@AutofillSaveActivity,
                                    getString(R.string.biometric_error_not_recognized),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    )

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setSubtitle(getString(R.string.autofill_auth_prompt_subtitle))
                        .setNegativeButtonText(getString(R.string.autofill_auth_enter_pin))
                        .build()

                    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(bioCipher))
                } else {
                    showMasterPasswordDialog = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.autofill_save_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = service,
                            onValueChange = { service = it },
                            label = { Text(stringResource(R.string.autofill_save_service_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.autofill_save_username_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.autofill_save_password_label)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    setResult(RESULT_CANCELED)
                                    finish()
                                },
                            ) {
                                Text(stringResource(R.string.autofill_save_btn_discard))
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (service.isBlank() || password.isBlank()) {
                                        return@Button
                                    }
                                    checkAuthAndSave(service, username, password)
                                },
                                enabled = service.isNotBlank() && password.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.autofill_save_btn_save))
                            }
                        }
                    }
                }
            }

            if (showMasterPasswordDialog) {
                var pinInput by remember { mutableStateOf("") }
                var isPinError by remember { mutableStateOf(value = false) }

                AlertDialog(
                    onDismissRequest = { showMasterPasswordDialog = false },
                    title = { Text(stringResource(R.string.autofill_auth_prompt_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.autofill_auth_prompt_subtitle))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it
                                    isPinError = false
                                },
                                label = { Text(stringResource(R.string.lock_field_master_password)) },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = isPinError,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (lockManager.verifyPassword(pinInput) || !lockManager.hasMasterPassword()) {
                                    showMasterPasswordDialog = false
                                    executeSave(service, username, password)
                                } else {
                                    isPinError = true
                                    Toast.makeText(
                                        this@AutofillSaveActivity,
                                        getString(R.string.error_password_incorrect),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.autofill_save_btn_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMasterPasswordDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SERVICE = "extra_service"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_URL = "extra_url"
    }
}
