package com.doffi4.doffisecure.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.usecase.AddPasswordUseCase
import com.doffi4.doffisecure.domain.usecase.CountPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.DeletePasswordUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordByIdUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.SearchPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.UpdatePasswordUseCase
import com.doffi4.doffisecure.dev.RefreshRateController
import com.doffi4.doffisecure.dev.RefreshTier
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.SecureClipboard
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.security.VaultWarmup
import com.doffi4.doffisecure.domain.model.Passkey
import com.doffi4.doffisecure.domain.model.BreachCheckResult
import com.doffi4.doffisecure.domain.repository.IPasskeyRepository
import com.doffi4.doffisecure.domain.usecase.CheckPasswordBreachUseCase
import com.doffi4.doffisecure.domain.model.TotpConfig
import com.doffi4.doffisecure.ui.util.UiText
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

data class BreachedAccount(
    val password: Password,
    val breachCount: Int,
)

class PasswordViewModel(
    private val getPasswordsUseCase: GetPasswordsUseCase,
    private val getPasswordByIdUseCase: GetPasswordByIdUseCase,
    private val addPasswordUseCase: AddPasswordUseCase,
    private val deletePasswordUseCase: DeletePasswordUseCase,
    private val searchPasswordsUseCase: SearchPasswordsUseCase,
    private val updatePasswordUseCase: UpdatePasswordUseCase,
    private val countPasswordsUseCase: CountPasswordsUseCase,
    private val secureClipboard: SecureClipboard,
    private val devModeManager: DevModeManager,
    private val vaultWarmup: VaultWarmup,
    private val refreshRateController: RefreshRateController,
    private val userSettings: UserSettingsManager,
    private val passkeyRepository: IPasskeyRepository,
    private val checkPasswordBreachUseCase: CheckPasswordBreachUseCase,
) : ViewModel() {

    val allPasskeys: StateFlow<List<Passkey>> = passkeyRepository.getAllPasskeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _breachState = MutableStateFlow<BreachCheckResult>(BreachCheckResult.Idle)
    val breachState: StateFlow<BreachCheckResult> = _breachState.asStateFlow()

    private val _breachedAccounts = MutableStateFlow<List<BreachedAccount>>(emptyList())
    val breachedAccounts: StateFlow<List<BreachedAccount>> = _breachedAccounts.asStateFlow()

    private val _isAuditingBreaches = MutableStateFlow(value = false)
    val isAuditingBreaches: StateFlow<Boolean> = _isAuditingBreaches.asStateFlow()

    private var breachJob: Job? = null
    private var vaultAuditJob: Job? = null

    private val _uiState = MutableStateFlow<PasswordUiState>(PasswordUiState.Loading)
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    val totpPasswords: StateFlow<List<Password>> = _uiState.map { state ->
        (state as? PasswordUiState.Success)?.passwords?.filter { !it.totpSecret.isNullOrBlank() } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _passwordToEdit = MutableStateFlow<Password?>(null)
    val passwordToEdit: StateFlow<Password?> = _passwordToEdit.asStateFlow()

    private val _selectedPassword = MutableStateFlow<Password?>(null)
    val selectedPassword: StateFlow<Password?> = _selectedPassword.asStateFlow()

    /** Total number of passwords in the app (independent of the search filter). */
    private val _totalPasswordsCount = MutableStateFlow(0)
    val totalPasswordsCount: StateFlow<Int> = _totalPasswordsCount.asStateFlow()

    val loadFavicons: StateFlow<Boolean> = userSettings.loadFavicons

    // Developer mode state, delegated directly to the shared DevModeManager
    // singleton so toggles changed in Settings stay live on this screen too.
    val devModeEnabled: StateFlow<Boolean> = devModeManager.devModeEnabled
    val showDevPasswordCount: StateFlow<Boolean> = devModeManager.showPasswordCount
    val showDevWarmupProgress: StateFlow<Boolean> = devModeManager.showWarmupProgress
    val showDevFpsOverlay: StateFlow<Boolean> = devModeManager.showFpsOverlay
    val devPrefetchCount: StateFlow<Int> = devModeManager.prefetchCount

    /** Live warm-up progress (0..100) from the shared startup warmer. */
    val warmupProgress: StateFlow<Int> = vaultWarmup.progress

    /** Current idle frame-rate tier (active / idle 60 / idle 30). */
    val refreshTier: StateFlow<RefreshTier> = refreshRateController.tier

    /** User-facing toggle: show the password-strength chip across the app. */
    val showPasswordStrength: StateFlow<Boolean> = userSettings.showPasswordStrength

    private var searchJob: Job? = null

    private val _uiEvent = MutableSharedFlow<PasswordUiEvent>()
    val uiEvent: SharedFlow<PasswordUiEvent> = _uiEvent.asSharedFlow()

    init {
        loadPasswords()
        observeTotalPasswordsCount()
    }

    fun loadPasswordById(id: Long) {
        viewModelScope.launch {
            _uiState.value = PasswordUiState.Loading
            try {
                val loaded = getPasswordByIdUseCase(id)
                _selectedPassword.value = loaded
                if (loaded == null) {
                    _uiState.value = PasswordUiState.Error(UiText.StringResource(R.string.toast_password_not_found))
                } else {
                    _uiState.value = PasswordUiState.Success(emptyList())
                    checkBreach(loaded.password, forceRefresh = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to load password"))
            }
        }
    }

    fun checkBreach(password: String, forceRefresh: Boolean = false) {
        breachJob?.cancel()
        breachJob = viewModelScope.launch {
            _breachState.value = BreachCheckResult.Checking
            val result = checkPasswordBreachUseCase(password, forceRefresh)
            _breachState.value = result
        }
    }

    fun resetBreachState() {
        breachJob?.cancel()
        _breachState.value = BreachCheckResult.Idle
    }

    fun onEditPasswordClicked(password: Password) {
        _passwordToEdit.value = password
        _showEditDialog.value = true
    }

    fun onDismissEditDialog() {
        _showEditDialog.value = false
        _passwordToEdit.value = null
    }

    fun onShowAddDialog(show: Boolean) {
        _showAddDialog.value = show
    }

    fun onDismissAddDialog() {
        _showAddDialog.value = false
    }

    fun loadPasswords() {
        viewModelScope.launch {
            // Avoid flickering a spinner on restore/re-load: keep the current
            // Success list on screen until the fresh data arrives.
            if (_uiState.value !is PasswordUiState.Success) {
                _uiState.value = PasswordUiState.Loading
            }
            getPasswordsUseCase()
                .catch { e -> _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Unknown Error")) }
                .collect { passwords ->
                    _uiState.value = PasswordUiState.Success(passwords)
                    auditVaultBreaches(passwords = passwords, forceRefresh = false)
                }
        }
    }

    fun auditVaultBreaches(passwords: List<Password>? = null, forceRefresh: Boolean = false) {
        vaultAuditJob?.cancel()
        vaultAuditJob = viewModelScope.launch(Dispatchers.IO) {
            _isAuditingBreaches.value = true
            try {
                val list = passwords ?: (_uiState.value as? PasswordUiState.Success)?.passwords ?: emptyList()
                val compromised = mutableListOf<BreachedAccount>()
                for (item in list) {
                    if (item.password.isNotBlank()) {
                        val result = checkPasswordBreachUseCase(item.password, forceRefresh)
                        if (result is BreachCheckResult.Compromised) {
                            compromised.add(BreachedAccount(item, result.count))
                        }
                    }
                }
                _breachedAccounts.value = compromised
            } catch (_: Exception) {
                // keep current
            } finally {
                _isAuditingBreaches.value = false
            }
        }
    }

    /**
     * Tracks the total number of stored passwords via a cheap COUNT(*) query
     * (no decryption). Used by the dev-mode "password count" display.
     */
    private fun observeTotalPasswordsCount() {
        viewModelScope.launch {
            countPasswordsUseCase()
                .catch { /* keep the last known value on failure */ }
                .collect { _totalPasswordsCount.value = it }
        }
    }

    /**
     * Called when the app title is tapped 6 times. If developer mode is already
     * active this only reminds via toast and returns false; otherwise the caller
     * shows the password dialog and calls [enableDeveloperMode] with the code.
     */
    fun onDevTitleTapped(): Boolean {
        val alreadyEnabled = devModeManager.devModeEnabled.value
        if (alreadyEnabled) {
            viewModelScope.launch {
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.dev_toast_already_active)))
            }
        }
        return !alreadyEnabled
    }

    /**
     * Tries to enable developer mode with [password]. Returns true only when
     * the code matches; the UI keeps the dialog open on a wrong code.
     */
    fun enableDeveloperMode(password: String): Boolean {
        if (!devModeManager.isDevPasswordValid(password)) return false
        devModeManager.enableDevMode(true)
        viewModelScope.launch {
            _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.dev_toast_enabled)))
        }
        return true
    }

    fun addPassword(service: String, username: String, password: String) {
        viewModelScope.launch {
            if (service.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.value = PasswordUiState.Error(UiText.StringResource(R.string.error_all_fields_required))
                return@launch
            }
            try {
                addPasswordUseCase(service, username, password)
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_saved)))
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to add password"))
            }
        }
    }

    fun updatePassword(
        id: Long,
        service: String,
        username: String,
        password: String,
        url: String? = null,
        totpSecret: String? = null,
    ) {
        viewModelScope.launch {
            if (service.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.value = PasswordUiState.Error(UiText.StringResource(R.string.error_all_fields_required))
                return@launch
            }
            try {
                val current = _selectedPassword.value
                val finalTotp = totpSecret ?: current?.totpSecret
                val finalUrl = url ?: current?.url
                updatePasswordUseCase(
                    id = id,
                    service = service,
                    username = username,
                    password = password,
                    url = finalUrl,
                    totpSecret = finalTotp
                )
                _selectedPassword.value = current?.copy(
                    service = service,
                    username = username,
                    password = password,
                    url = finalUrl,
                    totpSecret = finalTotp
                )
                checkBreach(password, forceRefresh = false)
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_updated)))
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to update password"))
            }
        }
    }

    fun updateTotpSecret(id: Long, totpSecret: String?) {
        viewModelScope.launch {
            val current = _selectedPassword.value ?: return@launch
            try {
                updatePasswordUseCase(
                    id = id,
                    service = current.service,
                    username = current.username,
                    password = current.password,
                    url = current.url,
                    totpSecret = totpSecret
                )
                _selectedPassword.value = current.copy(totpSecret = totpSecret)
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_updated)))
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to update 2FA key"))
            }
        }
    }

    fun copyTotpCode(code: String) {
        secureClipboard.copy(code)
        viewModelScope.launch {
            _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.totp_copied_toast, code)))
        }
    }

    fun addTotpToPassword(passwordId: Long, secret: String) {
        viewModelScope.launch {
            try {
                val existing = getPasswordByIdUseCase(passwordId) ?: return@launch
                updatePasswordUseCase(
                    id = existing.id,
                    service = existing.service,
                    username = existing.username,
                    password = existing.password,
                    url = existing.url,
                    totpSecret = secret,
                )
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_updated)))
            } catch (e: Exception) {
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.DynamicString(e.message ?: "Failed to save 2FA")))
            }
        }
    }

    fun createTotpAccount(service: String, username: String, secret: String) {
        viewModelScope.launch {
            try {
                addPasswordUseCase(
                    service = service.ifBlank { "2FA Service" },
                    username = username.trim(),
                    password = "",
                    url = null,
                    totpSecret = secret,
                )
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_saved)))
            } catch (e: Exception) {
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.DynamicString(e.message ?: "Failed to add 2FA account")))
            }
        }
    }

    fun importTotpAccounts(accounts: List<TotpConfig>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            var count = 0
            try {
                for (acc in accounts) {
                    val service = acc.issuer?.takeIf { it.isNotBlank() } ?: "2FA Account"
                    val username = acc.accountName ?: ""
                    addPasswordUseCase(
                        service = service,
                        username = username,
                        password = "",
                        url = null,
                        totpSecret = acc.toOtpAuthUri(),
                    )
                    count++
                }
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.totp_import_success, count)))
                onComplete(count)
            } catch (e: Exception) {
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.DynamicString(e.message ?: "Failed to import 2FA accounts")))
            }
        }
    }

    fun deletePassword(id: Long) {
        viewModelScope.launch {
            try {
                deletePasswordUseCase(id)
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_deleted)))
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to delete password"))
            }
        }
    }

    fun copyUsername(username: String) {
        secureClipboard.copy(username)
        viewModelScope.launch {
            _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_username_copied)))
        }
    }

    fun copyPassword(password: String) {
        secureClipboard.copy(password)
        viewModelScope.launch {
            _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_copied)))
        }
    }

    /**
     * Lazily loads and decrypts a single password by id before copying it.
     * Used from the main list, where passwords are not held in memory.
     */
    fun copyPasswordById(id: Long) {
        viewModelScope.launch {
            try {
                val password = getPasswordByIdUseCase(id)?.password
                if (!password.isNullOrEmpty()) {
                    secureClipboard.copy(password)
                    _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_copied)))
                } else {
                    _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_not_found)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit(
                    PasswordUiEvent.ShowToast(
                        UiText.StringResource(R.string.toast_copy_failed, arrayOf(e.message ?: "unknown"))
                    )
                )
            }
        }
    }

    fun searchPassword(query: String) {
        searchJob?.cancel() // Cancel previous job to prevent race conditions
        searchJob = viewModelScope.launch {
            try {
                // Debounce: avoid a DB round-trip + full decrypt per keystroke
                delay(300)
                if (query.isBlank()) {
                    loadPasswords()
                    return@launch
                }
                searchPasswordsUseCase(query)
                    .catch { e -> _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Search error")) }
                    .collect { passwords ->
                        _uiState.value = PasswordUiState.Success(passwords)
                    }
            } catch (_: CancellationException) {
                // Expected when job is cancelled
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Search failed"))
            }
        }
    }

    fun deletePasskey(passkeyId: Long) {
        viewModelScope.launch {
            try {
                passkeyRepository.deletePasskey(passkeyId)
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.passkey_deleted_toast)))
            } catch (e: Exception) {
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.DynamicString(e.message ?: "Failed to delete Passkey")))
            }
        }
    }
}
