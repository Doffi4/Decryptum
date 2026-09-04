package com.doffi4.doffisecure.ui.password

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.ImportPasswordsUseCase
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.CsvManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.UserSettingsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent
    data class ShowConfirmation(val title: String, val message: String) : SettingsEvent
}

class SettingsViewModel(
    private val lockManager: AppLockManager,
    private val getPasswordsUseCase: GetPasswordsUseCase,
    private val importPasswordsUseCase: ImportPasswordsUseCase,
    private val deleteAllPasswordsUseCase: DeleteAllPasswordsUseCase,
    private val devModeManager: DevModeManager,
    private val userSettings: UserSettingsManager
) : ViewModel() {

    private val _lockTimeout = MutableStateFlow(lockManager.getLockTimeoutSeconds())
    val lockTimeout: StateFlow<Int> = _lockTimeout.asStateFlow()

    private val _event = MutableSharedFlow<SettingsEvent>()
    val event: SharedFlow<SettingsEvent> = _event.asSharedFlow()

    init {
        _lockTimeout.value = lockManager.getLockTimeoutSeconds()
    }

    val allowScreenshots = MutableStateFlow(lockManager.getAllowScreenshots())

    // ---- Developer mode (hidden section in Settings) ----

    val devModeEnabled: StateFlow<Boolean> = devModeManager.devModeEnabled
    val showPasswordCount: StateFlow<Boolean> = devModeManager.showPasswordCount

    fun setShowPasswordCount(show: Boolean) {
        devModeManager.setShowPasswordCount(show)
        viewModelScope.launch {
            _event.emit(
                SettingsEvent.ShowToast(
                    if (show) "Password count display enabled" else "Password count display disabled"
                )
            )
        }
    }

    fun disableDevMode() {
        devModeManager.enableDevMode(false)
        viewModelScope.launch { _event.emit(SettingsEvent.ShowToast("Developer mode disabled")) }
    }

    fun setAllowScreenshots(allow: Boolean) {
        allowScreenshots.value = allow
        lockManager.setAllowScreenshots(allow)
        viewModelScope.launch {
            _event.emit(SettingsEvent.ShowToast(if (allow) "Screenshots enabled" else "Screenshots blocked"))
        }
    }

    // ---- User settings: password strength meter ----

    val showPasswordStrength: StateFlow<Boolean> = userSettings.showPasswordStrength

    fun setShowPasswordStrength(show: Boolean) {
        userSettings.setShowPasswordStrength(show)
        viewModelScope.launch {
            _event.emit(
                SettingsEvent.ShowToast(
                    if (show) "Password strength meter shown" else "Password strength meter hidden"
                )
            )
        }
    }

    fun setLockTimeout(seconds: Int) {
        _lockTimeout.value = seconds
        lockManager.setLockTimeoutSeconds(seconds)
        viewModelScope.launch { _event.emit(SettingsEvent.ShowToast("Auto-lock timeout updated")) }
    }

    fun exportPasswords(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val passwords = getPasswordsUseCase().first()
                if (passwords.isEmpty()) {
                    _event.emit(SettingsEvent.ShowToast("No passwords to export"))
                    return@launch
                }
                val count = CsvManager.export(context, uri, passwords)
                _event.emit(SettingsEvent.ShowToast("Exported $count passwords"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _event.emit(SettingsEvent.ShowToast("Export failed: $reason"))
            }
        }
    }

    fun importPasswords(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Parse the CSV off the main thread - large files would otherwise
                // freeze the UI (the root cause of import lag).
                val passwords = withContext(Dispatchers.Default) {
                    CsvManager.import(context, uri)
                }
                if (passwords.isEmpty()) {
                    _event.emit(SettingsEvent.ShowToast("No valid passwords found in file"))
                    return@launch
                }
                // Single bulk transaction - fast and reliable for large imports.
                // Encryption + DB write both run on background dispatchers inside
                // the repository, so the UI stays responsive.
                val imported = importPasswordsUseCase(passwords)
                val msg = if (imported == passwords.size) {
                    "Imported $imported passwords"
                } else {
                    "Imported $imported of ${passwords.size} passwords"
                }
                _event.emit(SettingsEvent.ShowToast(msg))
            } catch (e: CancellationException) {
                // Coroutine was cancelled (e.g. navigating away) - not an import error
                throw e
            } catch (e: Exception) {
                // e.message is often null for NPE/IllegalState - show a useful fallback
                val reason = e.message ?: e.javaClass.simpleName
                _event.emit(SettingsEvent.ShowToast("Import failed: $reason"))
            }
        }
    }

    fun deleteAllPasswords() {
        viewModelScope.launch {
            try {
                deleteAllPasswordsUseCase()
                _event.emit(SettingsEvent.ShowToast("All passwords deleted"))
            } catch (e: Exception) {
                _event.emit(SettingsEvent.ShowToast("Delete failed: ${e.message}"))
            }
        }
    }
}