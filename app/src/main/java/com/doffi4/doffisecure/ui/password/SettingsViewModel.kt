package com.doffi4.doffisecure.ui.password

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.ImportPasswordsUseCase
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.CsvManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.ui.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsEvent {
    data class ShowToast(val message: UiText) : SettingsEvent
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

    // ---- Language selection ----
    val appLanguage: StateFlow<String> = userSettings.appLanguage

    fun setAppLanguage(activity: Activity, languageCode: String) {
        userSettings.setAppLanguage(languageCode)
        AppLocaleManager.applyLanguage(activity, languageCode)
    }

    // ---- Autofill Framework & Credential Manager ----
    val autofillAlwaysRequireAuth: StateFlow<Boolean> = userSettings.autofillAlwaysRequireAuth

    fun setAutofillAlwaysRequireAuth(require: Boolean) {
        userSettings.setAutofillAlwaysRequireAuth(require)
    }

    fun isAutofillEnabled(context: Context): Boolean {
        return try {
            val afm = context.getSystemService(android.view.autofill.AutofillManager::class.java)
            afm?.hasEnabledAutofillServices() == true
        } catch (_: Exception) {
            false
        }
    }

    fun openAutofillSettings(context: Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = "package:${context.packageName}".toUri()
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(android.content.Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE"))
            } catch (_: Exception) {
                try {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                } catch (_: Exception) {}
            }
        }
    }

    fun openCredentialProviderSettings(context: Context) {
        val candidates = mutableListOf<android.content.Intent>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            candidates.add(android.content.Intent("android.settings.CREDENTIAL_PROVIDER"))
        }
        candidates.add(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = "package:${context.packageName}".toUri()
        })
        candidates.add(android.content.Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE"))
        candidates.add(android.content.Intent("android.settings.AUTOFILL_SETTINGS"))
        candidates.add(android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS))
        candidates.add(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))

        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    // ---- Developer mode (hidden section in Settings) ----

    val devModeEnabled: StateFlow<Boolean> = devModeManager.devModeEnabled
    val showPasswordCount: StateFlow<Boolean> = devModeManager.showPasswordCount

    fun setShowPasswordCount(show: Boolean) {
        devModeManager.setShowPasswordCount(show)
    }

    fun disableDevMode() {
        devModeManager.enableDevMode(false)
        viewModelScope.launch {
            _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.dev_btn_disable_mode)))
        }
    }

    fun setAllowScreenshots(allow: Boolean) {
        allowScreenshots.value = allow
        lockManager.setAllowScreenshots(allow)
    }

    // ---- User settings: password strength meter ----

    val showPasswordStrength: StateFlow<Boolean> = userSettings.showPasswordStrength

    fun setShowPasswordStrength(show: Boolean) {
        userSettings.setShowPasswordStrength(show)
    }

    fun setLockTimeout(seconds: Int) {
        _lockTimeout.value = seconds
        lockManager.setLockTimeoutSeconds(seconds)
    }

    fun exportPasswords(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val passwords = getPasswordsUseCase().first()
                if (passwords.isEmpty()) {
                    _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.passwords_empty)))
                    return@launch
                }
                val count = CsvManager.export(context, uri, passwords)
                _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.toast_export_success, arrayOf(count))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.toast_export_failed, arrayOf(reason))))
            }
        }
    }

    fun importPasswords(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val passwords = withContext(Dispatchers.Default) {
                    CsvManager.import(context, uri)
                }
                if (passwords.isEmpty()) {
                    _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.passwords_empty)))
                    return@launch
                }
                val imported = importPasswordsUseCase(passwords)
                _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.toast_import_success, arrayOf(imported))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.toast_import_failed, arrayOf(reason))))
            }
        }
    }

    fun deleteAllPasswords() {
        viewModelScope.launch {
            try {
                deleteAllPasswordsUseCase()
                _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.toast_delete_all_success)))
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _event.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.toast_delete_all_failed, arrayOf(reason))))
            }
        }
    }
}