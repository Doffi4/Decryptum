package com.doffi4.doffisecure.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.PasswordGeneratorOptions
import com.doffi4.doffisecure.domain.model.PasswordPreset
import com.doffi4.doffisecure.domain.usecase.AddPasswordUseCase
import com.doffi4.doffisecure.domain.usecase.GeneratePasswordUseCase
import com.doffi4.doffisecure.security.SecureClipboard
import com.doffi4.doffisecure.ui.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GeneratorEvent {
    data class ShowToast(val message: UiText) : GeneratorEvent
}

/**
 * Backing logic for the "Генератор" tab.
 *
 * Options (length + character classes + look-alike filter) live as separate
 * StateFlows so the UI can bind directly to them; every option change schedules
 * a short-debounced regeneration, which gives the "live preview" while the
 * "Сгенерировать" button regenerates instantly. Saving goes through
 * [AddPasswordUseCase], so the password is stored encrypted like every other
 * vault row.
 */
class GeneratorViewModel(
    private val generatePasswordUseCase: GeneratePasswordUseCase,
    private val addPasswordUseCase: AddPasswordUseCase,
    private val secureClipboard: SecureClipboard,
) : ViewModel() {

    private val _length = MutableStateFlow(PasswordPreset.STRONG.options.length)
    val length: StateFlow<Int> = _length.asStateFlow()

    private val _includeUpper = MutableStateFlow(true)
    val includeUpper: StateFlow<Boolean> = _includeUpper.asStateFlow()

    private val _includeLower = MutableStateFlow(true)
    val includeLower: StateFlow<Boolean> = _includeLower.asStateFlow()

    private val _includeDigits = MutableStateFlow(true)
    val includeDigits: StateFlow<Boolean> = _includeDigits.asStateFlow()

    private val _includeSymbols = MutableStateFlow(true)
    val includeSymbols: StateFlow<Boolean> = _includeSymbols.asStateFlow()

    private val _excludeLookalikes = MutableStateFlow(false)
    val excludeLookalikes: StateFlow<Boolean> = _excludeLookalikes.asStateFlow()

    private val _currentPassword = MutableStateFlow("")
    val currentPassword: StateFlow<String> = _currentPassword.asStateFlow()

    private val _passwordVisible = MutableStateFlow(false)
    val passwordVisible: StateFlow<Boolean> = _passwordVisible.asStateFlow()

    // Save-password dialog state.
    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val _service = MutableStateFlow("")
    val service: StateFlow<String> = _service.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _event = MutableSharedFlow<GeneratorEvent>()
    val event: SharedFlow<GeneratorEvent> = _event.asSharedFlow()

    private var regenJob: Job? = null

    init {
        regenerate()
    }

    /** Snapshot of the current option state. */
    private val currentOptions: PasswordGeneratorOptions
        get() = PasswordGeneratorOptions(
            length = _length.value,
            includeUpper = _includeUpper.value,
            includeLower = _includeLower.value,
            includeDigits = _includeDigits.value,
            includeSymbols = _includeSymbols.value,
            excludeLookalikes = _excludeLookalikes.value,
        )

    // ── Generation ─────────────────────────────────────────────────────────

    /** Generates a fresh password immediately (respects current options). */
    fun regenerate() {
        val options = currentOptions
        if (!options.hasCharset) {
            _currentPassword.value = ""
            emitToast(UiText.StringResource(R.string.toast_select_charset))
            return
        }
        _currentPassword.value = generatePasswordUseCase(options)
    }

    /** Debounced regeneration after any option change ("live preview"). */
    private fun scheduleRegenerate() {
        regenJob?.cancel()
        regenJob = viewModelScope.launch {
            delay(200)
            regenerate()
        }
    }

    fun applyPreset(preset: PasswordPreset) {
        val options = preset.options
        _length.value = options.length
        _includeUpper.value = options.includeUpper
        _includeLower.value = options.includeLower
        _includeDigits.value = options.includeDigits
        _includeSymbols.value = options.includeSymbols
        _excludeLookalikes.value = options.excludeLookalikes
        regenerate()
    }

    // ── Options ────────────────────────────────────────────────────────────

    fun setLength(value: Int) {
        val clamped = value.coerceIn(8, 64)
        if (clamped != _length.value) {
            _length.value = clamped
            scheduleRegenerate()
        }
    }

    fun setIncludeUpper(value: Boolean) {
        _includeUpper.value = value
        scheduleRegenerate()
    }

    fun setIncludeLower(value: Boolean) {
        _includeLower.value = value
        scheduleRegenerate()
    }

    fun setIncludeDigits(value: Boolean) {
        _includeDigits.value = value
        scheduleRegenerate()
    }

    fun setIncludeSymbols(value: Boolean) {
        _includeSymbols.value = value
        scheduleRegenerate()
    }

    fun setExcludeLookalikes(value: Boolean) {
        _excludeLookalikes.value = value
        scheduleRegenerate()
    }

    fun togglePasswordVisibility() {
        _passwordVisible.value = !_passwordVisible.value
    }

    // ── Actions ────────────────────────────────────────────────────────────

    /** Copies the current password through the auto-clearing clipboard. */
    fun copyPassword() {
        val password = _currentPassword.value
        if (password.isEmpty()) {
            emitToast(UiText.StringResource(R.string.toast_generate_first))
            return
        }
        secureClipboard.copy(password)
        emitToast(UiText.StringResource(R.string.toast_password_copied_clipboard))
    }

    fun openSaveDialog() {
        _service.value = ""
        _username.value = ""
        _showSaveDialog.value = true
    }

    fun dismissSaveDialog() {
        _showSaveDialog.value = false
    }

    fun setService(value: String) {
        _service.value = value
    }

    fun setUsername(value: String) {
        _username.value = value
    }

    /** Saves the generated password through the encrypted AddPassword path. */
    fun savePassword() {
        val serviceName = _service.value.trim()
        val login = _username.value.trim()
        if (serviceName.isBlank() || login.isBlank()) {
            emitToast(UiText.StringResource(R.string.toast_fill_service_username))
            return
        }
        if (_currentPassword.value.isEmpty()) {
            emitToast(UiText.StringResource(R.string.toast_generate_first))
            return
        }
        viewModelScope.launch {
            try {
                addPasswordUseCase(serviceName, login, _currentPassword.value)
                _showSaveDialog.value = false
                emitToast(UiText.StringResource(R.string.toast_password_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitToast(UiText.StringResource(R.string.toast_save_failed, arrayOf(e.message ?: e.javaClass.simpleName)))
            }
        }
    }

    private fun emitToast(message: UiText) {
        viewModelScope.launch { _event.emit(GeneratorEvent.ShowToast(message)) }
    }
}