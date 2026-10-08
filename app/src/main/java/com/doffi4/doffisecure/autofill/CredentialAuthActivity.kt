package com.doffi4.doffisecure.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
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
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.UserSettingsManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.koin.android.ext.android.inject

/**
 * Translucent activity invoked when the user selects a credential from the
 * Android Credential Manager Bottom Sheet (Option 1: instant biometric on tap).
 */
class CredentialAuthActivity : FragmentActivity() {

    private val lockManager: AppLockManager by inject()
    private val passwordCrypto: PasswordCrypto by inject()
    private val passwordRepository: IPasswordRepository by inject()
    private val userSettings: UserSettingsManager by inject()
    private val pickerFallback = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        setResult(result.resultCode, result.data)
        finish()
    }

    companion object {
        const val EXTRA_PASSWORD_ID = "com.doffi4.doffisecure.EXTRA_PASSWORD_ID"
    }

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!lockManager.getAllowScreenshots()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (lockManager.shouldAutoLock()) lockManager.setLocked(true)

        val passwordId = intent.getLongExtra(EXTRA_PASSWORD_ID, -1L)
        if (passwordId == -1L) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
            val alwaysRequireAuth = userSettings.autofillAlwaysRequireAuth.value
            val mustAuth = (isLocked || alwaysRequireAuth) && lockManager.hasMasterPassword()

            if (mustAuth) {
                authenticateAndDeliver(passwordId)
            } else {
                readAndDeliver(passwordId)
            }
        }
    }

    private fun authenticateAndDeliver(passwordId: Long) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        val bioCipher = passwordCrypto.getBiometricDecryptCipher()
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS && bioCipher != null) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val cipher = result.cryptoObject?.cipher
                        val unlocked = if (cipher != null) passwordCrypto.unlockWithBiometricCipher(cipher) else false
                        if (unlocked && passwordCrypto.isUnlocked()) {
                            lockManager.setLocked(false)
                            lockManager.touchLastActive()
                            readAndDeliver(passwordId)
                        } else {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
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
                .setNegativeButtonText(getString(R.string.action_cancel))
                .build()

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(bioCipher))
        } else {
            // Reuse the existing picker/master-password flow. Unavailable biometrics
            // never downgrade a mandatory authentication request to an unlocked read.
            pickerFallback.launch(Intent(this, AutofillPickerActivity::class.java).apply {
                putExtra(AutofillPickerActivity.EXTRA_PRESELECTED_PASSWORD_ID, passwordId)
                putExtra(AutofillPickerActivity.EXTRA_REQUIRE_AUTH, true)
            })
        }
    }

    private fun readAndDeliver(passwordId: Long) {
        lifecycleScope.launch {
            try {
                val password = passwordRepository.getPasswordById(passwordId)
                if (password != null) deliverCredential(password)
                else { setResult(Activity.RESULT_CANCELED); finish() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { setResult(Activity.RESULT_CANCELED); finish() }
        }
    }

    private fun deliverCredential(password: Password) {
        if (lockManager.isLocked() || lockManager.shouldAutoLock()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
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
