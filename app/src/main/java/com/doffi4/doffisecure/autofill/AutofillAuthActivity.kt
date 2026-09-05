package com.doffi4.doffisecure.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.service.autofill.Dataset
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.AppLocaleManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AutofillAuthActivity : FragmentActivity() {

    private val lockManager: AppLockManager by inject()
    private val passwordRepository: IPasswordRepository by inject()

    override fun attachBaseContext(newBase: Context) {
        val savedLang = com.doffi4.doffisecure.security.UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val passwordId = intent.getLongExtra(EXTRA_PASSWORD_ID, -1L)
        @Suppress("DEPRECATION")
        val usernameId: AutofillId? = intent.getParcelableExtra(EXTRA_USERNAME_ID)
        @Suppress("DEPRECATION")
        val passwordFieldId: AutofillId? = intent.getParcelableExtra(EXTRA_PASSWORD_FIELD_ID)

        if (passwordId == -1L || (usernameId == null && passwordFieldId == null)) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Try biometric prompt first if available
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt(passwordId, usernameId, passwordFieldId)
        } else {
            showMasterPasswordFallback(passwordId, usernameId, passwordFieldId)
        }
    }

    private fun showBiometricPrompt(
        passwordId: Long,
        usernameId: AutofillId?,
        passwordFieldId: AutofillId?
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthSuccess(passwordId, usernameId, passwordFieldId)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    } else {
                        showMasterPasswordFallback(passwordId, usernameId, passwordFieldId)
                    }
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@AutofillAuthActivity, getString(R.string.biometric_error_not_recognized), Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.autofill_auth_prompt_title))
            .setSubtitle(getString(R.string.autofill_auth_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.autofill_auth_enter_pin))
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun showMasterPasswordFallback(
        passwordId: Long,
        usernameId: AutofillId?,
        passwordFieldId: AutofillId?
    ) {
        setContent {
            var pinInput by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.autofill_auth_prompt_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.autofill_auth_prompt_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it
                                isError = false
                            },
                            label = { Text(stringResource(R.string.lock_field_master_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = isError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                if (lockManager.verifyPassword(pinInput) || !lockManager.hasMasterPassword()) {
                                    onAuthSuccess(passwordId, usernameId, passwordFieldId)
                                } else {
                                    isError = true
                                    Toast.makeText(this@AutofillAuthActivity, getString(R.string.error_password_incorrect), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text(stringResource(R.string.autofill_fill_action))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onAuthSuccess(
        passwordId: Long,
        usernameId: AutofillId?,
        passwordFieldId: AutofillId?
    ) {
        lockManager.setLocked(false)
        lockManager.touchLastActive()

        lifecycleScope.launch {
            val pass = passwordRepository.getPasswordById(passwordId)
            if (pass == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }

            val datasetBuilder = Dataset.Builder()
            val presentation = AutofillPresentationHelper.createDropdownPresentation(
                this@AutofillAuthActivity,
                pass.service,
                pass.username,
                isLocked = false
            )

            if (usernameId != null) {
                AutofillPresentationHelper.setDatasetValue(
                    builder = datasetBuilder,
                    id = usernameId,
                    value = AutofillValue.forText(pass.username),
                    presentation = presentation
                )
            }
            if (passwordFieldId != null) {
                AutofillPresentationHelper.setDatasetValue(
                    builder = datasetBuilder,
                    id = passwordFieldId,
                    value = AutofillValue.forText(pass.password),
                    presentation = presentation
                )
            }

            val reply = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
            }
            setResult(Activity.RESULT_OK, reply)
            finish()
        }
    }

    companion object {
        const val EXTRA_PASSWORD_ID = "extra_password_id"
        const val EXTRA_USERNAME_ID = "extra_username_id"
        const val EXTRA_PASSWORD_FIELD_ID = "extra_password_field_id"
    }
}
