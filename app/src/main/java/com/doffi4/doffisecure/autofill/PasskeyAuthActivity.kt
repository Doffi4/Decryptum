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
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.activity.compose.setContent
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.Passkey
import com.doffi4.doffisecure.domain.repository.IPasskeyRepository
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.security.webauthn.WebAuthnCryptoEngine
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.ext.android.inject

/**
 * Translucent activity invoked when the user selects a Passkey from the
 * Android Credential Manager Bottom Sheet.
 * Performs immediate biometric verification, signs the WebAuthn assertion,
 * and returns the signature.
 */
class PasskeyAuthActivity : FragmentActivity() {

    private val passkeyRepository: IPasskeyRepository by inject()
    private val webAuthnCryptoEngine: WebAuthnCryptoEngine by inject()
    private val lockManager: AppLockManager by inject()
    private val passwordCrypto: PasswordCrypto by inject()
    private val userSettings: UserSettingsManager by inject()

    companion object {
        const val EXTRA_PASSKEY_ID = "com.doffi4.doffisecure.EXTRA_PASSKEY_ID"
    }

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val passkeyId = intent.getLongExtra(EXTRA_PASSKEY_ID, -1L)
        if (passkeyId == -1L) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
            val alwaysRequireAuth = userSettings.autofillAlwaysRequireAuth.value
            val mustAuth = (isLocked || alwaysRequireAuth) && lockManager.hasMasterPassword()

            if (mustAuth || !passwordCrypto.isUnlocked()) {
                authenticateAndDeliver(passkeyId)
            } else {
                val passkey = passkeyRepository.getPasskeyById(passkeyId)
                if (passkey != null) {
                    deliverPasskeyAssertion(passkey)
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    private fun authenticateAndDeliver(passkeyId: Long) {
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
                        val unlocked = if (cipher != null) {
                            passwordCrypto.unlockWithBiometricCipher(cipher)
                        } else {
                            false
                        }
                        if (unlocked && passwordCrypto.isUnlocked()) {
                            onAuthSuccess(passkeyId)
                        } else {
                            showMasterPasswordFallback(passkeyId)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        } else {
                            showMasterPasswordFallback(passkeyId)
                        }
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(
                            this@PasskeyAuthActivity,
                            getString(R.string.biometric_error_not_recognized),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.passkey_auth_prompt))
                .setNegativeButtonText(getString(R.string.action_cancel))
                .build()

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(bioCipher))
        } else {
            if (!lockManager.isLocked() && passwordCrypto.isUnlocked()) {
                onAuthSuccess(passkeyId)
            } else {
                showMasterPasswordFallback(passkeyId)
            }
        }
    }

    private fun onAuthSuccess(passkeyId: Long) {
        lockManager.setLocked(false)
        lockManager.touchLastActive()
        lifecycleScope.launch {
            val passkey = passkeyRepository.getPasskeyById(passkeyId)
            if (passkey != null) {
                deliverPasskeyAssertion(passkey)
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun showMasterPasswordFallback(passkeyId: Long) {
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
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.passkey_auth_prompt),
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
                                    onAuthSuccess(passkeyId)
                                } else {
                                    isError = true
                                    Toast.makeText(this@PasskeyAuthActivity, getString(R.string.error_password_incorrect), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text(stringResource(R.string.action_unlock))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deliverPasskeyAssertion(passkey: Passkey) {
        try {
            val providerRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
            val getPkOption = providerRequest?.credentialOptions?.filterIsInstance<GetPublicKeyCredentialOption>()?.firstOrNull()

            val requestJson = getPkOption?.requestJson.orEmpty()
            var challengeB64 = ""
            try {
                if (requestJson.isNotBlank()) {
                    val json = JSONObject(requestJson)
                    challengeB64 = json.optString("challenge", "")
                }
            } catch (_: Exception) {}

            val clientDataHash = getPkOption?.clientDataHash
                ?: if (challengeB64.isNotBlank()) {
                    val clientData = "{\"type\":\"webauthn.get\",\"challenge\":\"$challengeB64\",\"origin\":\"https://${passkey.rpId}\",\"crossOrigin\":false}"
                    WebAuthnCryptoEngine.sha256(clientData.toByteArray(Charsets.UTF_8))
                } else {
                    val clientData = "{\"type\":\"webauthn.get\",\"challenge\":\"\",\"origin\":\"https://${passkey.rpId}\",\"crossOrigin\":false}"
                    WebAuthnCryptoEngine.sha256(clientData.toByteArray(Charsets.UTF_8))
                }

            val clientDataJsonStr = "{\"type\":\"webauthn.get\",\"challenge\":\"$challengeB64\",\"origin\":\"https://${passkey.rpId}\",\"crossOrigin\":false}"
            val clientDataJsonB64 = WebAuthnCryptoEngine.base64UrlEncode(clientDataJsonStr.toByteArray(Charsets.UTF_8))

            val newSignCount = passkey.signCount + 1
            val assertion = webAuthnCryptoEngine.createAssertion(
                rpId = passkey.rpId,
                passkey = passkey,
                clientDataHash = clientDataHash,
                newSignCount = newSignCount
            )

            lifecycleScope.launch {
                passkeyRepository.updateSignCount(passkey.id, newSignCount)
            }

            val responseJson = JSONObject().apply {
                put("id", WebAuthnCryptoEngine.base64UrlEncode(passkey.credentialId))
                put("rawId", WebAuthnCryptoEngine.base64UrlEncode(passkey.credentialId))
                put("type", "public-key")
                val resp = JSONObject().apply {
                    put("clientDataJSON", clientDataJsonB64)
                    put("authenticatorData", WebAuthnCryptoEngine.base64UrlEncode(assertion.authenticatorData))
                    put("signature", WebAuthnCryptoEngine.base64UrlEncode(assertion.signature))
                    put("userHandle", WebAuthnCryptoEngine.base64UrlEncode(assertion.userHandle))
                }
                put("response", resp)
                put("authenticatorAttachment", "platform")
                put("clientExtensionResults", JSONObject())
            }.toString()

            val credential = PublicKeyCredential(responseJson)
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
