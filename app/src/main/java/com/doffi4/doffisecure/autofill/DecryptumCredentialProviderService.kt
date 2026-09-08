package com.doffi4.doffisecure.autofill

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.Action
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.domain.repository.IPasskeyRepository
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android 14–16+ (API 34+) Credential Provider Service for Decryptum.
 * Provides saved passwords and Passkeys (FIDO2 / WebAuthn) to Google Chrome
 * and modern Android apps via the system Credential Manager bottom sheet.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class DecryptumCredentialProviderService : CredentialProviderService(), KoinComponent {

    private val passwordRepository: IPasswordRepository by inject()
    private val passkeyRepository: IPasskeyRepository by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        serviceScope.launch {
            try {
                val passwordOption = request.beginGetCredentialOptions
                    .asSequence()
                    .filterIsInstance<BeginGetPasswordOption>()
                    .firstOrNull()

                val publicKeyOption = request.beginGetCredentialOptions
                    .asSequence()
                    .filterIsInstance<BeginGetPublicKeyCredentialOption>()
                    .firstOrNull()

                val callingAppOrigin = try {
                    request.callingAppInfo?.let { callingApp ->
                        val field = callingApp.javaClass.getDeclaredField("origin")
                        field.isAccessible = true
                        field[callingApp] as? String
                    }
                } catch (_: Exception) { null }

                val origin = callingAppOrigin
                    ?: passwordOption?.candidateQueryData?.getString("androidx.credentials.provider.extra.CREDENTIAL_REQUEST_ORIGIN")
                    ?: passwordOption?.candidateQueryData?.getString("android.service.credentials.extra.ORIGIN")
                    ?: passwordOption?.candidateQueryData?.getString("androidx.credentials.provider.extra.ORIGIN")
                    ?: request.callingAppInfo?.packageName
                    ?: ""

                val domain = DomainUtils.extract(origin)
                val packageName = request.callingAppInfo?.packageName

                val responseBuilder = BeginGetCredentialResponse.Builder()

                // 1. Handle Passkeys (WebAuthn / FIDO2)
                if (publicKeyOption != null) {
                    var targetRpId = domain
                    try {
                        val requestJson = JSONObject(publicKeyOption.requestJson)
                        val jsonRpId = requestJson.optString("rpId", "")
                        if (jsonRpId.isNotBlank()) {
                            targetRpId = jsonRpId
                        }
                    } catch (_: Exception) {}

                    val matchingPasskeys = if (targetRpId.isNotBlank()) {
                        passkeyRepository.getPasskeysForRpIdSync(targetRpId)
                    } else {
                        emptyList()
                    }

                    for (passkey in matchingPasskeys) {
                        val authIntent = Intent(this@DecryptumCredentialProviderService, PasskeyAuthActivity::class.java).apply {
                            putExtra(PasskeyAuthActivity.EXTRA_PASSKEY_ID, passkey.id)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this@DecryptumCredentialProviderService,
                            (passkey.id + 200000).toInt(),
                            authIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )

                        val entry = PublicKeyCredentialEntry.Builder(
                            this@DecryptumCredentialProviderService,
                            passkey.userName,
                            pendingIntent,
                            publicKeyOption,
                        )
                            .setDisplayName(passkey.userDisplayName ?: passkey.userName)
                            .setIcon(Icon.createWithResource(this@DecryptumCredentialProviderService, R.drawable.ic_autofill_key))
                            .build()

                        responseBuilder.addCredentialEntry(entry)
                    }
                }

                // 2. Handle Traditional Passwords
                if (passwordOption != null) {
                    val allHeaders = passwordRepository.getAutofillHeaders().first()
                    val matches = AutofillMatcher.findMatches(
                        passwords = allHeaders,
                        webDomain = domain,
                        packageName = packageName
                    )

                    val itemsToShow = if (matches.isNotEmpty()) {
                        matches.take(5)
                    } else if (origin.contains("chrome", ignoreCase = true) || (packageName?.contains("chrome", ignoreCase = true) == true)) {
                        allHeaders.take(3)
                    } else {
                        emptyList()
                    }

                    for (match in itemsToShow) {
                        val authIntent = Intent(this@DecryptumCredentialProviderService, CredentialAuthActivity::class.java).apply {
                            putExtra(CredentialAuthActivity.EXTRA_PASSWORD_ID, match.id)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this@DecryptumCredentialProviderService,
                            match.id.toInt(),
                            authIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val entry = PasswordCredentialEntry.Builder(
                            this@DecryptumCredentialProviderService,
                            match.username,
                            pendingIntent,
                            passwordOption
                        )
                            .setDisplayName(match.service)
                            .setIcon(Icon.createWithResource(this@DecryptumCredentialProviderService, R.drawable.ic_autofill_key))
                            .build()

                        responseBuilder.addCredentialEntry(entry)
                    }
                }

                // Action to open full vault picker if user has another account or credentials for subdomains
                val pickerIntent = Intent(this@DecryptumCredentialProviderService, AutofillPickerActivity::class.java).apply {
                    putExtra(AutofillPickerActivity.EXTRA_WEB_DOMAIN, domain)
                    putExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME, packageName)
                }
                val pickerPendingIntent = PendingIntent.getActivity(
                    this@DecryptumCredentialProviderService,
                    99992,
                    pickerIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val pickAction = Action.Builder(
                    getString(R.string.autofill_action_pick_other),
                    pickerPendingIntent
                )
                    .setSubtitle(getString(R.string.app_name))
                    .build()
                responseBuilder.addAction(pickAction)

                callback.onResult(responseBuilder.build())
            } catch (e: Exception) {
                callback.onError(GetCredentialUnknownException(e.message))
            }
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        try {
            val isPasswordCreation = (request.type == PasswordCredential.TYPE_PASSWORD_CREDENTIAL) ||
                (request is BeginCreatePasswordCredentialRequest)
            val isPasskeyCreation = (request.type == PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL) ||
                (request is BeginCreatePublicKeyCredentialRequest)

            if (!isPasswordCreation && !isPasskeyCreation) {
                callback.onResult(BeginCreateCredentialResponse.Builder().build())
                return
            }

            if (request is BeginCreatePublicKeyCredentialRequest) {
                val saveIntent = Intent(this, PasskeySaveActivity::class.java).apply {
                    putExtra(PasskeySaveActivity.EXTRA_REQUEST_JSON, request.requestJson)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    99996,
                    saveIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                val createEntry = CreateEntry.Builder(
                    accountName = getString(R.string.passkey_label),
                    pendingIntent = pendingIntent,
                )
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_autofill_key))
                    .setDescription(getString(R.string.passkey_save_title))
                    .build()

                val response = BeginCreateCredentialResponse.Builder()
                    .addCreateEntry(createEntry)
                    .build()

                callback.onResult(response)
                return
            }

            // Traditional Password Creation
            val saveIntent = Intent(this, AutofillSaveActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                99995,
                saveIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val createEntry = CreateEntry.Builder(
                accountName = getString(R.string.app_name),
                pendingIntent = pendingIntent
            )
                .setIcon(Icon.createWithResource(this, R.drawable.ic_autofill_key))
                .setDescription(getString(R.string.autofill_save_title))
                .build()

            val response = BeginCreateCredentialResponse.Builder()
                .addCreateEntry(createEntry)
                .build()

            callback.onResult(response)
        } catch (e: Exception) {
            callback.onError(CreateCredentialUnknownException(e.message))
        }
    }
}

/**
 * Backward compatibility alias for any existing bindings referencing DoffiCredentialProviderService.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DoffiCredentialProviderService : DecryptumCredentialProviderService()
