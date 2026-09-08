package com.doffi4.doffisecure.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.VaultWarmup
import com.doffi4.doffisecure.ui.util.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import androidx.biometric.BiometricPrompt
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.security.PasswordCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.crypto.Cipher

sealed interface LockState {
    object NeedsSetup : LockState
    object Locked : LockState
    object Unlocked : LockState
}

sealed interface BiometricEvent {
    data object LaunchBiometric : BiometricEvent
    data class BiometricError(val message: String) : BiometricEvent
}

data class LockInputState(
    val password: String = "",
    val confirmPassword: String = "",
    val error: UiText? = null
)

class AppLockViewModel(
    private val lockManager: AppLockManager,
    private val vaultWarmup: VaultWarmup,
    private val devModeManager: DevModeManager,
    private val passwordCrypto: PasswordCrypto,
    private val deleteAllPasswordsUseCase: DeleteAllPasswordsUseCase
) : ViewModel() {

    // Dev-mode warm-up progress (delegated from the shared singletons so the
    // lock screen can show live percentages while the vault is being warmed).
    val warmupProgress: StateFlow<Int> = vaultWarmup.progress
    val warmupRunning: StateFlow<Boolean> = vaultWarmup.isRunning
    val showWarmupProgress: StateFlow<Boolean> = devModeManager.showWarmupProgress

    private val _lockState = MutableStateFlow(
        when {
            !lockManager.hasMasterPassword() -> LockState.NeedsSetup
            lockManager.isLocked() -> LockState.Locked
            else -> LockState.Unlocked
        }
    )
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_SECONDS = 30
    }

    private var failedAttempts = lockManager.getFailedAttempts()
    private var lockoutJob: Job? = null
    private val _lockoutSecondsRemaining = MutableStateFlow(0)
    val lockoutSecondsRemaining: StateFlow<Int> = _lockoutSecondsRemaining.asStateFlow()

    private val _input = MutableStateFlow(LockInputState())
    val input: StateFlow<LockInputState> = _input.asStateFlow()

    private val _biometricEvent = MutableSharedFlow<BiometricEvent>()
    val biometricEvent: SharedFlow<BiometricEvent> = _biometricEvent.asSharedFlow()

    init {
        // Restore lockout state if active across process recreation
        val lockoutUntil = lockManager.getLockoutUntilTimestamp()
        val remainingMs = lockoutUntil - System.currentTimeMillis()
        if (remainingMs > 0) {
            startLockout((remainingMs / 1000).toInt() + 1)
        } else if (lockoutUntil > 0) {
            lockManager.clearLockout()
            failedAttempts = 0
            lockManager.setFailedAttempts(0)
        }

        // React immediately to lock state changes made outside this ViewModel,
        // such as the dev-mode "Lock now" or "Reset master password" actions.
        viewModelScope.launch {
            lockManager.isLockedFlow.collect { isLocked ->
                _lockState.value = when {
                    !lockManager.hasMasterPassword() -> LockState.NeedsSetup
                    isLocked -> LockState.Locked
                    else -> LockState.Unlocked
                }
            }
        }
    }

    fun onPasswordChange(value: String) {
        if (_lockoutSecondsRemaining.value > 0) return
        _input.value = _input.value.copy(password = value, error = null)
    }

    fun onConfirmChange(value: String) {
        _input.value = _input.value.copy(confirmPassword = value)
    }

    /** Called when the user wants to use biometric instead of master password. */
    fun requestBiometric() {
        viewModelScope.launch {
            _biometricEvent.emit(BiometricEvent.LaunchBiometric)
        }
    }

    fun getBiometricCryptoObject(): BiometricPrompt.CryptoObject? {
        val cipher = passwordCrypto.getBiometricDecryptCipher() ?: return null
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Called by the UI after a successful biometric authentication.
     * Unlocks the DEK via the authenticated Cipher and unlocks the app.
     */
    fun onBiometricSuccess(cipher: Cipher?) {
        if (cipher != null) {
            val unwrapped = passwordCrypto.unlockWithBiometricCipher(cipher)
            if (!unwrapped) {
                _input.value = _input.value.copy(
                    error = UiText.StringResource(R.string.biometric_error_key_invalidated)
                )
                return
            }
            failedAttempts = 0
            lockManager.setFailedAttempts(0)
            stopLockout()
            unlock()
        } else {
            _input.value = _input.value.copy(
                error = UiText.StringResource(R.string.biometric_error_unavailable)
            )
        }
    }

    fun isBiometricEnrolled(): Boolean = passwordCrypto.isBiometricEnabled()

    /** Called by the UI on biometric failure (user can fall back to password). */
    fun onBiometricError(errorMessage: String) {
        _input.value = _input.value.copy(
            error = UiText.StringResource(R.string.error_biometric_prefix, errorMessage)
        )
    }

    fun submit() {
        when (lockState.value) {
            is LockState.NeedsSetup -> {
                val text = _input.value
                if (text.password.isBlank()) {
                    _input.value = text.copy(error = UiText.StringResource(R.string.error_password_empty))
                    return
                }
                if (text.password != text.confirmPassword) {
                    _input.value = text.copy(error = UiText.StringResource(R.string.error_passwords_mismatch))
                    return
                }
                if (text.password.length < 4) {
                    _input.value = text.copy(error = UiText.StringResource(R.string.error_password_too_short))
                    return
                }
                if (lockManager.setMasterPassword(text.password)) {
                    unlock()
                }
            }
            is LockState.Locked -> {
                if (_lockoutSecondsRemaining.value > 0) {
                    _input.value = _input.value.copy(
                        error = UiText.StringResource(
                            R.string.lock_too_many_attempts,
                            _lockoutSecondsRemaining.value
                        )
                    )
                    return
                }

                val text = _input.value
                if (lockManager.verifyPassword(text.password)) {
                    failedAttempts = 0
                    lockManager.setFailedAttempts(0)
                    stopLockout()
                    unlock()
                } else {
                    failedAttempts++
                    lockManager.setFailedAttempts(failedAttempts)
                    if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                        val lockoutUntil = System.currentTimeMillis() + (LOCKOUT_DURATION_SECONDS * 1000L)
                        lockManager.setLockoutUntilTimestamp(lockoutUntil)
                        startLockout(LOCKOUT_DURATION_SECONDS)
                    } else {
                        _input.value = text.copy(error = UiText.StringResource(R.string.error_password_incorrect))
                    }
                }
            }
            is LockState.Unlocked -> Unit
        }
    }

    fun lock() {
        if (lockState.value == LockState.Unlocked) {
            lockManager.setLocked(true)
            _lockState.value = LockState.Locked
            clearInput()
        }
    }

    /**
     * Checks if the auto-lock timeout has elapsed since the last user
     * activity and re-locks the app if necessary. Called when the app
     * returns to the foreground.
     */
    fun checkAndLockIfNeeded() {
        if (lockState.value == LockState.Unlocked && lockManager.shouldAutoLock()) {
            lock()
        }
    }

    /** Records that the user interacted with the app (resets idle timer). */
    fun touchLastActive() {
        if (lockState.value == LockState.Unlocked) {
            lockManager.touchLastActive()
        }
    }

    /** Whether the user has enabled screenshots in Settings. */
    fun isScreenshotsAllowed(): Boolean = lockManager.getAllowScreenshots()

    fun resetVault() {
        viewModelScope.launch {
            try {
                deleteAllPasswordsUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            failedAttempts = 0
            lockManager.setFailedAttempts(0)
            stopLockout()
            lockManager.resetVault()
            _input.value = LockInputState()
        }
    }

    private fun startLockout(durationSeconds: Int) {
        lockoutJob?.cancel()
        _lockoutSecondsRemaining.value = durationSeconds
        _input.value = _input.value.copy(
            error = UiText.StringResource(R.string.lock_too_many_attempts, durationSeconds)
        )
        lockoutJob = viewModelScope.launch {
            for (sec in durationSeconds downTo 1) {
                _lockoutSecondsRemaining.value = sec
                _input.value = _input.value.copy(
                    error = UiText.StringResource(R.string.lock_too_many_attempts, sec)
                )
                delay(1000L)
            }
            _lockoutSecondsRemaining.value = 0
            failedAttempts = 0
            lockManager.setFailedAttempts(0)
            lockManager.clearLockout()
            _input.value = _input.value.copy(error = null)
        }
    }

    private fun stopLockout() {
        lockoutJob?.cancel()
        lockoutJob = null
        _lockoutSecondsRemaining.value = 0
        lockManager.clearLockout()
    }

    private fun unlock() {
        lockManager.setLocked(false)
        lockManager.touchLastActive()
        _lockState.value = LockState.Unlocked
        clearInput()
        vaultWarmup.warm()
    }

    private fun clearInput() {
        _input.value = LockInputState()
    }
}
