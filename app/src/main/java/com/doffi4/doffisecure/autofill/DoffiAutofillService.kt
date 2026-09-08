package com.doffi4.doffisecure.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.UserSettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Result of autofill response planning, decoupled for fast unit testing.
 */
sealed class AutofillDecision<out ID> {
    object Empty : AutofillDecision<Nothing>()
    data class ShowPicker<ID>(
        val displayTitle: String,
        val inlineSubtitle: String,
        val targetUsernameId: ID?,
        val targetPasswordId: ID?,
        val isSavedAccount: Boolean,
    ) : AutofillDecision<ID>()
}

/**
 * Pure decision engine that plans the appropriate autofill response based on parsed fields and vault matches.
 */
object AutofillResponsePlanner {
    fun <ID> plan(
        targetUsernameId: ID?,
        targetPasswordId: ID?,
        webDomain: String?,
        packageName: String?,
        matches: List<Password>,
        defaultPickerTitle: String = "Select account to fill",
    ): AutofillDecision<ID> {
        // If neither field was identified as an auth input, suppress autofill
        if ((targetUsernameId == null) && (targetPasswordId == null)) {
            return AutofillDecision.Empty
        }

        // Requirement R2: If no password field is present on screen AND no saved accounts exist for
        // this domain or package, immediately suppress autofill.
        if ((targetPasswordId == null) && matches.isEmpty()) {
            return AutofillDecision.Empty
        }

        val displayTitle = when {
            matches.isNotEmpty() -> matches.first().service
            !webDomain.isNullOrBlank() -> webDomain
            !packageName.isNullOrBlank() -> {
                val appKeywords = AutofillMatcher.extractAppKeywords(packageName)
                appKeywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: defaultPickerTitle
            }
            else -> defaultPickerTitle
        }

        val inlineSubtitle = if (matches.isNotEmpty()) {
            matches.first().username
        } else {
            "" // Empty string prevents Gboard from rendering dummy ": Decryptum"
        }

        return AutofillDecision.ShowPicker(
            displayTitle = displayTitle,
            inlineSubtitle = inlineSubtitle,
            targetUsernameId = targetUsernameId,
            targetPasswordId = targetPasswordId,
            isSavedAccount = matches.isNotEmpty(),
        )
    }
}

open class DecryptumAutofillService : AutofillService(), KoinComponent {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val passwordRepository: IPasswordRepository by inject()
    private val lockManager: AppLockManager by inject()
    private val userSettings: UserSettingsManager by inject()

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val fillContexts = request.fillContexts
        if (fillContexts.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val focusedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fillContexts.lastOrNull()?.focusedId
        } else {
            null
        }
        val parsed = AutofillStructureParser.parse(fillContexts)

        // Strict target assignment: do not fall back to arbitrary focusedId
        val targetPasswordId = parsed.passwordId ?: parsed.newPasswordId
        val targetUsernameId = parsed.usernameId

        if ((targetUsernameId == null) && (targetPasswordId == null)) {
            callback.onSuccess(null)
            return
        }

        val inlineInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val req = request.inlineSuggestionsRequest
            "inlineRequest=${req != null}, specsCount=${req?.inlinePresentationSpecs?.size ?: 0}"
        } else {
            "inlineRequest=unsupported"
        }

        android.util.Log.w(
            "DecryptumAutofill",
            "onFillRequest: domain=${parsed.webDomain}, pkg=${parsed.packageName}, user=$targetUsernameId, pass=$targetPasswordId, focused=$focusedId, $inlineInfo",
        )

        val fillJob = serviceScope.launch {
            try {
                // Use fast unencrypted headers to avoid any crypto micro-freezes during autofill trigger
                val allHeaders = passwordRepository.getAutofillHeaders().first()
                val matches = AutofillMatcher.findMatches(
                    passwords = allHeaders,
                    webDomain = parsed.webDomain,
                    packageName = parsed.packageName
                )

                val decision = AutofillResponsePlanner.plan(
                    targetUsernameId = targetUsernameId,
                    targetPasswordId = targetPasswordId,
                    webDomain = parsed.webDomain,
                    packageName = parsed.packageName,
                    matches = matches,
                    defaultPickerTitle = getString(R.string.autofill_picker_title)
                )

                if (decision is AutofillDecision.Empty) {
                    android.util.Log.w("DecryptumAutofill", "AutofillResponsePlanner returned Empty. Suppressing autofill.")
                    callback.onSuccess(null)
                    return@launch
                }

                @Suppress("UNCHECKED_CAST")
                val showPicker = decision as AutofillDecision.ShowPicker<android.view.autofill.AutofillId>

                val fillResponseBuilder = FillResponse.Builder()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    fillResponseBuilder.setFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                }

                val domainTitle = parsed.webDomain?.removePrefix("www.") ?: showPicker.displayTitle
                val domainFavicon = withTimeoutOrNull(500.milliseconds) {
                    AutofillPresentationHelper.loadFaviconBitmap(
                        context = this@DecryptumAutofillService,
                        domainOrUrl = parsed.webDomain ?: showPicker.displayTitle,
                        sizeDp = 40
                    )
                }

                val triggerIds = listOfNotNull(showPicker.targetUsernameId, showPicker.targetPasswordId)
                    .distinct()
                    .toTypedArray()

                val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    request.inlineSuggestionsRequest?.inlinePresentationSpecs
                } else null
                android.util.Log.w(
                    "DecryptumAutofill",
                    "inlineSpecs resolved: size=${inlineSpecs?.size}, matches=${matches.size}"
                )

                val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
                val alwaysRequireAuth = userSettings.autofillAlwaysRequireAuth.value
                val mustAuth = (isLocked || alwaysRequireAuth) && lockManager.hasMasterPassword()

                // Deduplicate matches by username so multiple identical entries don't clutter the UI
                val distinctMatches = matches.distinctBy { it.username.trim().lowercase() }

                // Configure manual selection / picker intent to launch Decryptum native bottom sheet (AutofillPickerActivity)
                val pickerIntent = Intent(this@DecryptumAutofillService, AutofillPickerActivity::class.java).apply {
                    putExtra(AutofillPickerActivity.EXTRA_USERNAME_ID, showPicker.targetUsernameId)
                    putExtra(AutofillPickerActivity.EXTRA_PASSWORD_FIELD_ID, showPicker.targetPasswordId)
                    putExtra(AutofillPickerActivity.EXTRA_WEB_DOMAIN, parsed.webDomain)
                    putExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME, parsed.packageName)
                    if (showPicker.isSavedAccount) {
                        putExtra(AutofillPickerActivity.EXTRA_SERVICE_NAME, showPicker.displayTitle)
                    }
                }
                val pickerPendingIntent = PendingIntent.getActivity(
                    this@DecryptumAutofillService,
                    9999,
                    pickerIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val pickerPresentation = AutofillPresentationHelper.createPickerDropdownPresentation(
                    context = this@DecryptumAutofillService
                )

                val pickerSpec = inlineSpecs?.getOrNull(distinctMatches.size) ?: inlineSpecs?.lastOrNull() ?: inlineSpecs?.firstOrNull()
                val chipTitle = getString(R.string.autofill_search_vault)
                val chipIcon = R.drawable.ic_autofill_decryptum

                val inlinePicker = if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && (pickerSpec != null)) {
                    AutofillPresentationHelper.createPickerInlinePresentation(
                        context = this@DecryptumAutofillService,
                        spec = pickerSpec,
                        pendingIntent = pickerPendingIntent,
                        title = chipTitle,
                        iconRes = chipIcon
                    )
                } else null

                if (matches.isNotEmpty() && triggerIds.isNotEmpty()) {
                    // Trigger Decryptum native Compose bottom sheet (AutofillPickerActivity) via Gboard chip / auth intent.
                    // This completely avoids the system FillDialog (no "Позже" button), allows compact height,
                    // 12dp rounded square close button, and card outlines with M3 ripple.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePicker != null) {
                        fillResponseBuilder.setAuthentication(
                            triggerIds,
                            pickerPendingIntent.intentSender,
                            pickerPresentation,
                            inlinePicker
                        )
                    } else {
                        fillResponseBuilder.setAuthentication(
                            triggerIds,
                            pickerPendingIntent.intentSender,
                            pickerPresentation
                        )
                    }
                } else {
                    // Fallback when no direct matches exist: add picker dataset to search vault
                    val pickerDataset = Dataset.Builder()
                        .setAuthentication(pickerPendingIntent.intentSender)

                    showPicker.targetUsernameId?.let { id ->
                        AutofillPresentationHelper.setDatasetValue(
                            builder = pickerDataset,
                            id = id,
                            value = null,
                            presentation = pickerPresentation,
                            inlinePresentation = inlinePicker,
                            dialogPresentation = null,
                            suppressMenuPresentation = false
                        )
                    }
                    showPicker.targetPasswordId?.let { id ->
                        AutofillPresentationHelper.setDatasetValue(
                            builder = pickerDataset,
                            id = id,
                            value = null,
                            presentation = pickerPresentation,
                            inlinePresentation = inlinePicker,
                            dialogPresentation = null,
                            suppressMenuPresentation = false
                        )
                    }
                    fillResponseBuilder.addDataset(pickerDataset.build())
                }

                // 3. Configure SaveInfo so users can save new credentials
                val saveIds = listOfNotNull(showPicker.targetUsernameId, showPicker.targetPasswordId, parsed.newPasswordId)
                    .distinct()
                    .toTypedArray()
                if (saveIds.isNotEmpty() && (showPicker.targetPasswordId != null || parsed.newPasswordId != null)) {
                    val saveInfo = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD, saveIds)
                        .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                        .build()
                    fillResponseBuilder.setSaveInfo(saveInfo)
                }

                callback.onSuccess(fillResponseBuilder.build())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                callback.onFailure(e.message)
            }
        }

        cancellationSignal.setOnCancelListener { fillJob.cancel() }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val fillContexts = request.fillContexts
        if (fillContexts.isEmpty()) {
            callback.onSuccess()
            return
        }

        val parsed = AutofillStructureParser.parse(fillContexts)

        val enteredPassword = parsed.enteredPassword
        if (!enteredPassword.isNullOrBlank()) {
            val serviceName = when {
                !parsed.webDomain.isNullOrBlank() -> parsed.webDomain
                !parsed.packageName.isNullOrBlank() -> {
                    val keywords = AutofillMatcher.extractAppKeywords(parsed.packageName)
                    keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: parsed.packageName
                }
                else -> getString(R.string.app_name)
            }

            val saveIntent = Intent(this, AutofillSaveActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AutofillSaveActivity.EXTRA_SERVICE, serviceName)
                putExtra(AutofillSaveActivity.EXTRA_USERNAME, parsed.enteredUsername.orEmpty())
                putExtra(AutofillSaveActivity.EXTRA_PASSWORD, enteredPassword)
                putExtra(AutofillSaveActivity.EXTRA_URL, parsed.webDomain)
            }
            startActivity(saveIntent)
        }

        callback.onSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

/**
 * Backward compatibility alias for existing system bindings.
 */
class DoffiAutofillService : DecryptumAutofillService()
