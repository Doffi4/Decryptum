package com.doffi4.doffisecure.autofill

import android.app.Activity
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasskeyRepository
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.security.webauthn.WebAuthnCryptoEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.ext.android.inject

/**
 * BottomSheet UI activity for registering and creating a new Passkey (WebAuthn / FIDO2).
 * Shows site and account info, asks for biometric confirmation, and returns the attestation.
 */
class PasskeySaveActivity : FragmentActivity() {

    private val passkeyRepository: IPasskeyRepository by inject()
    private val passwordRepository: IPasswordRepository by inject()
    private val webAuthnCryptoEngine: WebAuthnCryptoEngine by inject()
    private val lockManager: AppLockManager by inject()
    private val passwordCrypto: PasswordCrypto by inject()

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val createPkRequest = providerRequest?.callingRequest as? CreatePublicKeyCredentialRequest

        val requestJsonString = createPkRequest?.requestJson
            ?: intent.getStringExtra(EXTRA_REQUEST_JSON)
            ?: ""

        var parsedRpId = ""
        var parsedRpName = ""
        var parsedUserName = ""
        var parsedUserDisplayName: String? = null
        var parsedUserId = ByteArray(0)

        try {
            if (requestJsonString.isNotBlank()) {
                val json = JSONObject(requestJsonString)
                val rpObj = json.optJSONObject("rp")
                parsedRpId = rpObj?.optString("id").orEmpty()
                parsedRpName = rpObj?.optString("name", parsedRpId).orEmpty()

                val userObj = json.optJSONObject("user")
                if (userObj != null) {
                    parsedUserName = userObj.optString("name", "")
                    parsedUserDisplayName = userObj.optString("displayName", parsedUserName)
                    val idBase64 = userObj.optString("id", "")
                    parsedUserId = if (idBase64.isNotBlank()) {
                        try {
                            WebAuthnCryptoEngine.base64UrlDecode(idBase64)
                        } catch (_: Exception) {
                            idBase64.toByteArray(Charsets.UTF_8)
                        }
                    } else {
                        ByteArray(0)
                    }
                }
            }
        } catch (_: Exception) {}

        val callingAppOrigin = try {
            providerRequest?.callingAppInfo?.let { callingApp ->
                val field = callingApp.javaClass.getDeclaredField("origin")
                field.isAccessible = true
                field[callingApp] as? String
            }
        } catch (_: Exception) { null }

        val origin = createPkRequest?.origin ?: callingAppOrigin
        if (parsedRpId.isBlank() && (origin != null)) {
            parsedRpId = DomainUtils.extract(origin)
        }
        if (parsedRpName.isBlank()) {
            parsedRpName = parsedRpId.ifBlank { getString(R.string.app_name) }
        }

        setContent {
            var rpId by remember { mutableStateOf(parsedRpId) }
            var rpName by remember { mutableStateOf(parsedRpName) }
            var userName by remember { mutableStateOf(parsedUserName) }
            var showMasterPasswordDialog by remember { mutableStateOf(value = false) }

            fun executePasskeyCreation() {
                lifecycleScope.launch {
                    try {
                        val allPasswords = passwordRepository.getAllPasswords().first()
                        var matchedPassword = allPasswords.firstOrNull {
                            it.service.contains(rpId, ignoreCase = true) ||
                                ((it.url != null) && DomainUtils.extract(it.url).equals(rpId, ignoreCase = true))
                        }

                        if (matchedPassword == null) {
                            val newPassword = Password(
                                id = 0L,
                                service = rpName.ifBlank { rpId },
                                username = userName.trim(),
                                password = "",
                                url = "https://$rpId",
                                createdAt = System.currentTimeMillis(),
                            )
                            passwordRepository.addPassword(newPassword)
                            matchedPassword = passwordRepository.getAllPasswords().first().firstOrNull {
                                it.service.equals(newPassword.service, ignoreCase = true) && it.username == userName.trim()
                            }
                        }

                        val passkey = webAuthnCryptoEngine.generatePasskey(
                            rpId = rpId.trim(),
                            rpName = rpName.trim(),
                            userId = parsedUserId,
                            userName = userName.trim(),
                            userDisplayName = parsedUserDisplayName,
                            algorithm = WebAuthnCryptoEngine.ALG_ES256,
                            linkedPasswordId = matchedPassword?.id,
                        )

                        passkeyRepository.addPasskey(passkey)

                        val attestation = webAuthnCryptoEngine.createAttestation(rpId, passkey)

                        // ClientDataJSON
                        val clientDataJsonStr = "{\"type\":\"webauthn.create\",\"challenge\":\"\",\"origin\":\"https://$rpId\",\"crossOrigin\":false}"
                        val clientDataJsonB64 = WebAuthnCryptoEngine.base64UrlEncode(clientDataJsonStr.toByteArray(Charsets.UTF_8))

                        val responseJson = JSONObject().apply {
                            put("id", WebAuthnCryptoEngine.base64UrlEncode(passkey.credentialId))
                            put("rawId", WebAuthnCryptoEngine.base64UrlEncode(passkey.credentialId))
                            put("type", "public-key")
                            val resp = JSONObject().apply {
                                put("clientDataJSON", clientDataJsonB64)
                                put("attestationObject", WebAuthnCryptoEngine.base64UrlEncode(attestation.attestationObject))
                            }
                            put("response", resp)
                            put("authenticatorAttachment", "platform")
                            put("clientExtensionResults", JSONObject())
                        }.toString()

                        val resultResponse = CreatePublicKeyCredentialResponse(responseJson)
                        val resultIntent = Intent()
                        PendingIntentHandler.setCreateCredentialResponse(resultIntent, resultResponse)

                        Toast.makeText(
                            this@PasskeySaveActivity,
                            getString(R.string.passkey_saved_toast),
                            Toast.LENGTH_SHORT,
                        ).show()

                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@PasskeySaveActivity,
                            e.localizedMessage ?: "Failed to create Passkey",
                            Toast.LENGTH_SHORT,
                        ).show()
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            }

            fun checkAuthAndSave() {
                val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
                val mustAuth = isLocked && lockManager.hasMasterPassword()
                if (!mustAuth && passwordCrypto.isUnlocked()) {
                    executePasskeyCreation()
                    return
                }

                val biometricManager = BiometricManager.from(this@PasskeySaveActivity)
                val canAuth = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                val bioCipher = passwordCrypto.getBiometricDecryptCipher()

                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS && bioCipher != null) {
                    val executor = ContextCompat.getMainExecutor(this@PasskeySaveActivity)
                    val prompt = BiometricPrompt(
                        this@PasskeySaveActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                val cipher = result.cryptoObject?.cipher
                                val unlocked = if (cipher != null) passwordCrypto.unlockWithBiometricCipher(cipher) else false
                                if (unlocked && passwordCrypto.isUnlocked()) {
                                    lockManager.setLocked(false)
                                    lockManager.touchLastActive()
                                    executePasskeyCreation()
                                } else {
                                    showMasterPasswordDialog = true
                                }
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                showMasterPasswordDialog = true
                            }

                            override fun onAuthenticationFailed() {
                                Toast.makeText(
                                    this@PasskeySaveActivity,
                                    getString(R.string.biometric_error_not_recognized),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setSubtitle(getString(R.string.passkey_save_title))
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.passkey_save_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.passkey_save_desc, rpName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = rpId,
                            onValueChange = { rpId = it },
                            label = { Text(stringResource(R.string.field_service)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = { Text(stringResource(R.string.field_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        // Passkey Algorithm indicator badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.passkey_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "ECDSA P-256 (ES256)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    setResult(Activity.RESULT_CANCELED)
                                    finish()
                                }
                            ) {
                                Text(stringResource(R.string.autofill_save_btn_discard))
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (rpId.isNotBlank() && userName.isNotBlank()) {
                                        checkAuthAndSave()
                                    }
                                },
                                enabled = rpId.isNotBlank() && userName.isNotBlank()
                            ) {
                                Text(stringResource(R.string.autofill_save_btn_save))
                            }
                        }
                    }
                }
            }

            if (showMasterPasswordDialog) {
                var pinInput by remember { mutableStateOf("") }
                var isPinError by remember { mutableStateOf(false) }

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
                                    executePasskeyCreation()
                                } else {
                                    isPinError = true
                                    Toast.makeText(
                                        this@PasskeySaveActivity,
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
        const val EXTRA_REQUEST_JSON = "extra_request_json"
    }
}
