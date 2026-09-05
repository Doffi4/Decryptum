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
import com.doffi4.doffisecure.ui.util.UiText
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
    private val userSettings: UserSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<PasswordUiState>(PasswordUiState.Loading)
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()
    
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
                _selectedPassword.value = getPasswordByIdUseCase(id)
                if (_selectedPassword.value == null) {
                    _uiState.value = PasswordUiState.Error(UiText.StringResource(R.string.toast_password_not_found))
                } else {
                    _uiState.value = PasswordUiState.Success(emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to load password"))
            }
        }
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

    fun updatePassword(id: Long, service: String, username: String, password: String) {
        viewModelScope.launch {
            if (service.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.value = PasswordUiState.Error(UiText.StringResource(R.string.error_all_fields_required))
                return@launch
            }
            try {
                updatePasswordUseCase(id, service, username, password)
                _uiEvent.emit(PasswordUiEvent.ShowToast(UiText.StringResource(R.string.toast_password_updated)))
            } catch (e: Exception) {
                _uiState.value = PasswordUiState.Error(UiText.DynamicString(e.message ?: "Failed to update password"))
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
}
