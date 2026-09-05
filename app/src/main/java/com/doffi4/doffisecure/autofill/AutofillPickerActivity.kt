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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.SecureClipboard
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.ui.password.SiteAvatar
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject

/**
 * Modern Material 3 compact miniature Bottom Sheet autofill and credential picker for Decryptum.
 * Styled after Google Password Manager:
 * - Compact height hugging the content (miniature mode by default)
 * - Domain-aware: immediately filters and surfaces matching credentials for current site/app
 * - Tap card to fill credentials with instant biometric confirmation
 * - Expandable on demand to search the full vault
 */
class AutofillPickerActivity : FragmentActivity() {

    private val lockManager: AppLockManager by inject()
    private val passwordRepository: IPasswordRepository by inject()
    private val userSettings: UserSettingsManager by inject()
    private val secureClipboard: SecureClipboard by inject()

    private var usernameId: AutofillId? = null
    private var passwordFieldId: AutofillId? = null
    private var webDomain: String? = null
    private var packageNameArg: String? = null
    private var serviceNameArg: String? = null
    private var preselectedPasswordId: Long = -1L

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        usernameId = intent.getParcelableExtra(AutofillAuthActivity.EXTRA_USERNAME_ID)
            ?: intent.getParcelableExtra(EXTRA_USERNAME_ID)
        @Suppress("DEPRECATION")
        passwordFieldId = intent.getParcelableExtra(AutofillAuthActivity.EXTRA_PASSWORD_FIELD_ID)
            ?: intent.getParcelableExtra(EXTRA_PASSWORD_FIELD_ID)

        preselectedPasswordId = intent.getLongExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, -1L)
            .takeIf { it != -1L } ?: intent.getLongExtra(EXTRA_PRESELECTED_PASSWORD_ID, -1L)
        webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        packageNameArg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        serviceNameArg = intent.getStringExtra(EXTRA_SERVICE_NAME)

        showBottomSheetContent()
    }

    private fun showBottomSheetContent() {
        setContent {
            DecryptumTheme {
                val context = LocalContext.current
                var allPasswords by remember { mutableStateOf<List<Password>>(emptyList()) }
                var isSearchExpanded by remember {
                    mutableStateOf(preselectedPasswordId == -1L && webDomain.isNullOrBlank() && packageNameArg.isNullOrBlank() && serviceNameArg.isNullOrBlank())
                }
                var searchQuery by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    allPasswords = passwordRepository.getAllPasswords().first()
                }

                // Domain-aware matching passwords for current site/app
                val matchingPasswords = remember(allPasswords, webDomain, packageNameArg, serviceNameArg, preselectedPasswordId) {
                    val targetDomain = webDomain?.takeIf { it.isNotBlank() } ?: serviceNameArg
                    val matches = if (!targetDomain.isNullOrBlank() || !packageNameArg.isNullOrBlank()) {
                        AutofillMatcher.findMatches(
                            passwords = allPasswords,
                            webDomain = targetDomain,
                            packageName = packageNameArg
                        )
                    } else {
                        emptyList()
                    }

                    // If a preselected password ID was passed, ensure it is prioritized at top
                    if (preselectedPasswordId != -1L) {
                        val preselected = allPasswords.firstOrNull { it.id == preselectedPasswordId }
                        if (preselected != null && preselected !in matches) {
                            listOf(preselected) + matches
                        } else if (preselected != null) {
                            listOf(preselected) + (matches - preselected)
                        } else {
                            matches
                        }
                    } else {
                        matches
                    }
                }

                val displayTitle = remember(webDomain, serviceNameArg, packageNameArg, matchingPasswords) {
                    when {
                        !webDomain.isNullOrBlank() -> webDomain!!
                        !serviceNameArg.isNullOrBlank() -> serviceNameArg!!
                        matchingPasswords.isNotEmpty() -> matchingPasswords.first().service
                        !packageNameArg.isNullOrBlank() -> {
                            AutofillMatcher.extractAppKeywords(packageNameArg!!).firstOrNull()?.replaceFirstChar { it.uppercase() } ?: packageNameArg!!
                        }
                        else -> null
                    }
                }

                val serviceFavicon = remember(displayTitle) {
                    displayTitle?.let { DomainUtils.faviconUrl(DomainUtils.extract(it)) }
                }

                // Passwords filtered in expanded search mode
                val filteredAll = remember(searchQuery, allPasswords) {
                    if (searchQuery.isBlank()) allPasswords
                    else allPasswords.filter {
                        it.service.contains(searchQuery, ignoreCase = true) ||
                                it.username.contains(searchQuery, ignoreCase = true) ||
                                it.url?.contains(searchQuery, ignoreCase = true) == true
                    }
                }

                // Root scrim (tap outside to cancel)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSearchExpanded) {
                                    Modifier.fillMaxHeight(0.75f)
                                } else {
                                    Modifier
                                        .wrapContentHeight()
                                        .heightIn(min = 160.dp, max = 420.dp)
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}, // Prevent dismiss when tapping sheet itself
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            // Drag handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )

                            Spacer(Modifier.height(12.dp))

                            if (!isSearchExpanded) {
                                // ==========================================
                                // MINIATURE MODE (Google Password Manager Style)
                                // ==========================================
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (serviceFavicon != null || displayTitle != null) {
                                            SiteAvatar(
                                                displayName = displayTitle ?: stringResource(R.string.app_name),
                                                faviconUrl = serviceFavicon.orEmpty(),
                                                size = 32.dp
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = displayTitle ?: stringResource(R.string.app_name),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = stringResource(R.string.autofill_matching_subtitle),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(R.string.app_name),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            setResult(Activity.RESULT_CANCELED)
                                            finish()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.action_cancel),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                if (matchingPasswords.isNotEmpty()) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(matchingPasswords, key = { it.id }) { item ->
                                            val favicon = remember(item) {
                                                DomainUtils.faviconUrl(DomainUtils.extract(item.url ?: item.service))
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onPasswordSelected(item) },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SiteAvatar(
                                                        displayName = item.service,
                                                        faviconUrl = favicon,
                                                        size = 38.dp
                                                    )

                                                    Spacer(Modifier.width(12.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.username,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "••••••••",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            secureClipboard.copy(item.username)
                                                            Toast.makeText(context, R.string.autofill_copied_toast, Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ContentCopy,
                                                            contentDescription = stringResource(R.string.autofill_copy_username),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Button to choose another password from vault
                                    TextButton(
                                        onClick = { isSearchExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.autofill_choose_other),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                } else {
                                    // No matching passwords for this service
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (displayTitle != null) {
                                                stringResource(R.string.autofill_no_matching_for_service, displayTitle)
                                            } else {
                                                stringResource(R.string.autofill_picker_empty)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { isSearchExpanded = true },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.autofill_search_vault))
                                        }
                                    }
                                }
                            } else {
                                // ==========================================
                                // EXPANDED SEARCH MODE (Full Vault)
                                // ==========================================
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (matchingPasswords.isNotEmpty() || !displayTitle.isNullOrBlank()) {
                                            IconButton(
                                                onClick = {
                                                    isSearchExpanded = false
                                                    searchQuery = ""
                                                }
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.autofill_picker_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            setResult(Activity.RESULT_CANCELED)
                                            finish()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.action_cancel),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.autofill_picker_search_hint)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                )

                                Spacer(Modifier.height(10.dp))

                                if (filteredAll.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.autofill_picker_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(filteredAll, key = { it.id }) { item ->
                                            val favicon = remember(item) {
                                                DomainUtils.faviconUrl(DomainUtils.extract(item.url ?: item.service))
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onPasswordSelected(item) },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SiteAvatar(
                                                        displayName = item.service,
                                                        faviconUrl = favicon,
                                                        size = 40.dp
                                                    )

                                                    Spacer(Modifier.width(12.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.service,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = item.username,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            secureClipboard.copy(item.username)
                                                            Toast.makeText(context, R.string.autofill_copied_toast, Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ContentCopy,
                                                            contentDescription = stringResource(R.string.autofill_copy_username),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onPasswordSelected(password: Password) {
        val isLocked = lockManager.isLocked() || lockManager.shouldAutoLock()
        val alwaysRequireAuth = userSettings.autofillAlwaysRequireAuth.value
        val mustAuth = (isLocked || alwaysRequireAuth) && lockManager.hasMasterPassword()

        if (mustAuth) {
            authenticateAndFill(password)
        } else {
            fillAndFinish(password)
        }
    }

    private fun authenticateAndFill(password: Password) {
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
                        fillAndFinish(password)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(
                            this@AutofillPickerActivity,
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
            fillAndFinish(password)
        }
    }

    private fun fillAndFinish(password: Password) {
        val uId = usernameId
        val pId = passwordFieldId

        val replyIntent = Intent()

        // 1. If invoked from Autofill framework
        if (uId != null || pId != null) {
            val datasetBuilder = Dataset.Builder()
            val presentation = AutofillPresentationHelper.createDropdownPresentation(
                this,
                password.service,
                password.username,
                isLocked = false
            )

            if (uId != null) {
                AutofillPresentationHelper.setDatasetValue(
                    builder = datasetBuilder,
                    id = uId,
                    value = AutofillValue.forText(password.username),
                    presentation = presentation
                )
            }
            if (pId != null) {
                AutofillPresentationHelper.setDatasetValue(
                    builder = datasetBuilder,
                    id = pId,
                    value = AutofillValue.forText(password.password),
                    presentation = presentation
                )
            }
            replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
        }

        // 2. Also package for Credential Manager if invoked via ActionEntry
        try {
            val credential = PasswordCredential(id = password.username, password = password.password)
            val response = GetCredentialResponse(credential)
            PendingIntentHandler.setGetCredentialResponse(replyIntent, response)
        } catch (_: Exception) {}

        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    companion object {
        const val EXTRA_USERNAME_ID = "extra_username_id"
        const val EXTRA_PASSWORD_FIELD_ID = "extra_password_field_id"
        const val EXTRA_PRESELECTED_PASSWORD_ID = "extra_preselected_password_id"
        const val EXTRA_WEB_DOMAIN = "extra_web_domain"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
    }
}
