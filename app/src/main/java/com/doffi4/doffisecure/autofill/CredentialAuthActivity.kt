package com.doffi4.doffisecure.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.UserSettingsManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Translucent activity invoked when the user selects a credential from the
 * Android Credential Manager Bottom Sheet (Option 1: instant biometric on tap).
 */
class CredentialAuthActivity : FragmentActivity() {

    private val lockManager: AppLockManager by inject()
    private val passwordRepository: IPasswordRepository by inject()
    private val userSettings: UserSettingsManager by inject()

    companion object {
        const val EXTRA_PASSWORD_ID = "com.doffi4.doffisecure.EXTRA_PASSWORD_ID"
    }

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val passwordId = intent.getLongExtra(EXTRA_PASSWORD_ID, -1L)
        if (passwordId == -1L) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            val password = passwordRepository.getPasswordById(passwordId)
            if (password == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }

            val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
            val alwaysRequireAuth = userSettings.autofillAlwaysRequireAuth.value
            val mustAuth = (isLocked || alwaysRequireAuth) && lockManager.hasMasterPassword()

            if (mustAuth) {
                authenticateAndDeliver(password)
            } else {
                deliverCredential(password)
            }
        }
    }

    private fun authenticateAndDeliver(password: Password) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        lockManager.setLocked(false)
                        lockManager.touchLastActive()
                        deliverCredential(password)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(
                            this@CredentialAuthActivity,
                            getString(R.string.biometric_error_not_recognized),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.autofill_auth_prompt_subtitle))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(promptInfo)
        } else {
            // Biometric not available on device, deliver directly
            deliverCredential(password)
        }
    }

    private fun deliverCredential(password: Password) {
        try {
            val credential = PasswordCredential(id = password.username, password = password.password)
            val response = GetCredentialResponse(credential)
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(resultIntent, response)
            setResult(Activity.RESULT_OK, resultIntent)
        } catch (e: Exception) {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}
