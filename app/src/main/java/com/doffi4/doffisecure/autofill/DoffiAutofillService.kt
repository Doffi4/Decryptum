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
        val isSavedAccount: Boolean
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
        defaultPickerTitle: String = "Select account to fill"
    ): AutofillDecision<ID> {
        // If neither field was identified as an auth input, suppress autofill
        if (targetUsernameId == null && targetPasswordId == null) {
            return AutofillDecision.Empty
        }

        // Requirement R2: If no password field is present on screen AND no saved accounts exist for
        // this domain or package, immediately suppress autofill.
        if (targetPasswordId == null && matches.isEmpty()) {
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
            isSavedAccount = matches.isNotEmpty()
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
        callback: FillCallback
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

        if (targetUsernameId == null && targetPasswordId == null) {
            callback.onSuccess(null)
            return
        }

        android.util.Log.w(
            "DecryptumAutofill",
            "onFillRequest: domain=${parsed.webDomain}, pkg=${parsed.packageName}, user=$targetUsernameId, pass=$targetPasswordId, focused=$focusedId, " +
                    "inlineRequest=${request.inlineSuggestionsRequest != null}, specsCount=${request.inlineSuggestionsRequest?.inlinePresentationSpecs?.size ?: 0}"
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
                val domainFavicon = withTimeoutOrNull(500) {
                    AutofillPresentationHelper.loadFaviconBitmap(
                        context = this@DecryptumAutofillService,
                        domainOrUrl = parsed.webDomain ?: showPicker.displayTitle,
                        sizeDp = 38
                    )
                }

                // Trigger native Android Fill Dialog (Bottom Sheet presentation) ONLY when matching credentials exist
                val triggerIds = listOfNotNull(showPicker.targetUsernameId, showPicker.targetPasswordId)
                    .distinct()
                    .toTypedArray()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && triggerIds.isNotEmpty() && matches.isNotEmpty()) {
                    fillResponseBuilder.setFillDialogTriggerIds(*triggerIds)
                    val dialogHeader = AutofillPresentationHelper.createDialogHeader(
                        context = this@DecryptumAutofillService,
                        domainOrService = domainTitle,
                        faviconBitmap = domainFavicon
                    )
                    fillResponseBuilder.setDialogHeader(dialogHeader)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        fillResponseBuilder.setShowFillDialogIcon(false)
                    }
                }

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

                // 1. Populate matched accounts as native datasets (login + mask, direct fill when unlocked)
                for ((index, match) in distinctMatches.asSequence().take(4).withIndex()) {
                    val datasetBuilder = Dataset.Builder()
                    val matchFavicon = domainFavicon ?: withTimeoutOrNull(300) {
                        AutofillPresentationHelper.loadFaviconBitmap(
                            context = this@DecryptumAutofillService,
                            domainOrUrl = match.url ?: match.service,
                            sizeDp = 38
                        )
                    }
                    val presentation = AutofillPresentationHelper.createDropdownPresentation(
                        context = this@DecryptumAutofillService,
                        service = match.service,
                        username = match.username,
                        isLocked = mustAuth,
                        faviconBitmap = matchFavicon
                    )
                    val spec = inlineSpecs?.getOrNull(index) ?: inlineSpecs?.firstOrNull()
                    val inlinePresentation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && spec != null) {
                        AutofillPresentationHelper.createInlinePresentation(
                            context = this@DecryptumAutofillService,
                            spec = spec,
                            service = match.service,
                            username = match.username,
                            isLocked = mustAuth
                        )
                    } else null

                    val fullPassword = if (!mustAuth) {
                        passwordRepository.getPasswordById(match.id)?.password ?: match.password
                    } else {
                        ""
                    }

                    if (mustAuth) {
                        val authIntent = Intent(this@DecryptumAutofillService, AutofillAuthActivity::class.java).apply {
                            putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, match.id)
                            putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, showPicker.targetUsernameId)
                            putExtra(AutofillAuthActivity.EXTRA_PASSWORD_FIELD_ID, showPicker.targetPasswordId)
                        }
                        val authPendingIntent = PendingIntent.getActivity(
                            this@DecryptumAutofillService,
                            (match.id * 31 + (showPicker.targetPasswordId?.hashCode() ?: 0)).toInt(),
                            authIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        datasetBuilder.setAuthentication(authPendingIntent.intentSender)
                    }

                    val accountDialogPresentation = if (matches.isNotEmpty()) presentation else null

                    if (showPicker.targetUsernameId != null) {
                        AutofillPresentationHelper.setDatasetValue(
                            builder = datasetBuilder,
                            id = showPicker.targetUsernameId,
                            value = if (mustAuth) null else AutofillValue.forText(match.username),
                            presentation = presentation,
                            inlinePresentation = inlinePresentation,
                            dialogPresentation = accountDialogPresentation,
                            suppressMenuPresentation = false
                        )
                    }
                    if (showPicker.targetPasswordId != null) {
                        AutofillPresentationHelper.setDatasetValue(
                            builder = datasetBuilder,
                            id = showPicker.targetPasswordId,
                            value = if (mustAuth) null else AutofillValue.forText(fullPassword),
                            presentation = presentation,
                            inlinePresentation = inlinePresentation,
                            dialogPresentation = accountDialogPresentation,
                            suppressMenuPresentation = false
                        )
                    }
                    fillResponseBuilder.addDataset(datasetBuilder.build())
                }

                // 2. Add manual selection / picker dataset formatted as "Search vault" button
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

                val pickerDataset = Dataset.Builder()
                    .setAuthentication(pickerPendingIntent.intentSender)

                val pickerSpec = inlineSpecs?.getOrNull(distinctMatches.size) ?: inlineSpecs?.lastOrNull()
                val inlinePicker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pickerSpec != null) {
                    AutofillPresentationHelper.createPickerInlinePresentation(
                        context = this@DecryptumAutofillService,
                        spec = pickerSpec,
                        pendingIntent = pickerPendingIntent
                    )
                } else null

                val pickerDialogPresentation = if (distinctMatches.isNotEmpty()) pickerPresentation else null

                if (showPicker.targetUsernameId != null) {
                    AutofillPresentationHelper.setDatasetValue(
                        builder = pickerDataset,
                        id = showPicker.targetUsernameId,
                        value = null,
                        presentation = pickerPresentation,
                        inlinePresentation = inlinePicker,
                        dialogPresentation = pickerDialogPresentation,
                        suppressMenuPresentation = false
                    )
                }
                if (showPicker.targetPasswordId != null) {
                    AutofillPresentationHelper.setDatasetValue(
                        builder = pickerDataset,
                        id = showPicker.targetPasswordId,
                        value = null,
                        presentation = pickerPresentation,
                        inlinePresentation = inlinePicker,
                        dialogPresentation = pickerDialogPresentation,
                        suppressMenuPresentation = false
                    )
                }
                fillResponseBuilder.addDataset(pickerDataset.build())

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
