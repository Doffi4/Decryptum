package com.doffi4.doffisecure.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.dev.CpuMonitor
import com.doffi4.doffisecure.dev.CpuStats
import com.doffi4.doffisecure.dev.SyntheticVaultGenerator
import com.doffi4.doffisecure.dev.TestDatasetRequest
import com.doffi4.doffisecure.dev.TestDataSeedState
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.usecase.CheckEncryptionIntegrityUseCase
import com.doffi4.doffisecure.domain.usecase.CountEncryptedPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.CountPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.DeleteDuplicatesUseCase
import com.doffi4.doffisecure.domain.usecase.GetDuplicateGroupsUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.ImportPasswordsUseCase
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.VaultWarmup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.ui.util.UiText
import java.util.UUID

sealed interface DevToolsEvent {
    data class ShowToast(val message: UiText) : DevToolsEvent
}

/**
 * Backing logic for the hidden "Developer" section in Settings:
 * test data seeding, DB diagnostics, encryption stats, duplicate cleanup,
 * performance benchmark and quick security toggles.
 */
class DevToolsViewModel(
    private val lockManager: AppLockManager,
    private val getPasswordsUseCase: GetPasswordsUseCase,
    private val countPasswordsUseCase: CountPasswordsUseCase,
    private val countEncryptedPasswordsUseCase: CountEncryptedPasswordsUseCase,
    private val checkEncryptionIntegrityUseCase: CheckEncryptionIntegrityUseCase,
    private val getDuplicateGroupsUseCase: GetDuplicateGroupsUseCase,
    private val deleteDuplicatesUseCase: DeleteDuplicatesUseCase,
    private val importPasswordsUseCase: ImportPasswordsUseCase,
    private val deleteAllPasswordsUseCase: DeleteAllPasswordsUseCase,
    private val vaultWarmup: VaultWarmup,
    private val devModeManager: DevModeManager,
    private val cpuMonitor: CpuMonitor
) : ViewModel() {

    private val _event = MutableSharedFlow<DevToolsEvent>()
    val event: SharedFlow<DevToolsEvent> = _event.asSharedFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _encryptedCount = MutableStateFlow(0)
    val encryptedCount: StateFlow<Int> = _encryptedCount.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _decryptTestResult = MutableStateFlow<String?>(null)
    val decryptTestResult: StateFlow<String?> = _decryptTestResult.asStateFlow()

    private val _integrityCheckResult = MutableStateFlow<String?>(null)
    val integrityCheckResult: StateFlow<String?> = _integrityCheckResult.asStateFlow()

    private val _lockTimeout = MutableStateFlow(lockManager.getLockTimeoutSeconds())
    val lockTimeout: StateFlow<Int> = _lockTimeout.asStateFlow()

    // Live CPU temperature / load / frequency (sysfs + /proc/stat), sampled
    // once per second while the Developer section is on screen.
    private val _cpuStats = MutableStateFlow(CpuStats(null, "—", 0f, null))
    val cpuStats: StateFlow<CpuStats> = _cpuStats.asStateFlow()

    // Dev-mode toggle + live warm-up state (shared singletons).
    val showWarmupProgress: StateFlow<Boolean> = devModeManager.showWarmupProgress
    val warmupProgress: StateFlow<Int> = vaultWarmup.progress
    val warmupRunning: StateFlow<Boolean> = vaultWarmup.isRunning

    fun setShowWarmupProgress(show: Boolean) {
        devModeManager.setShowWarmupProgress(show)
        viewModelScope.launch {
            _event.emit(
                DevToolsEvent.ShowToast(
                    if (show) UiText.StringResource(R.string.dev_toast_warmup_enabled)
                    else UiText.StringResource(R.string.dev_toast_warmup_disabled)
                )
            )
        }
    }

    /** Manually re-runs the cold-start warm-up (useful while testing). */
    fun reWarmWarmup() {
        vaultWarmup.warm()
        viewModelScope.launch {
            _event.emit(DevToolsEvent.ShowToast(UiText.StringResource(R.string.dev_toast_warmup_restarted)))
        }
    }

    // ---- Frame-time overlay + list prefetch tuning ----

    val showFpsOverlay: StateFlow<Boolean> = devModeManager.showFpsOverlay
    val showCpuOverlay: StateFlow<Boolean> = devModeManager.showCpuOverlay
    val prefetchCount: StateFlow<Int> = devModeManager.prefetchCount

    fun setShowFpsOverlay(show: Boolean) {
        devModeManager.setShowFpsOverlay(show)
        viewModelScope.launch {
            _event.emit(
                DevToolsEvent.ShowToast(
                    if (show) UiText.StringResource(R.string.dev_toast_fps_enabled)
                    else UiText.StringResource(R.string.dev_toast_fps_disabled)
                )
            )
        }
    }

    fun setShowCpuOverlay(show: Boolean) {
        devModeManager.setShowCpuOverlay(show)
        viewModelScope.launch {
            _event.emit(
                DevToolsEvent.ShowToast(
                    if (show) UiText.StringResource(R.string.dev_toast_cpu_enabled)
                    else UiText.StringResource(R.string.dev_toast_cpu_disabled)
                )
            )
        }
    }

    fun setPrefetchCount(count: Int) {
        devModeManager.setPrefetchCount(count)
        viewModelScope.launch {
            _event.emit(DevToolsEvent.ShowToast(UiText.StringResource(R.string.dev_toast_prefetch_set, count)))
        }
    }

    init {
        _lockTimeout.value = lockManager.getLockTimeoutSeconds()

        // System-health ticker: re-sample CPU every second on the IO dispatcher.
        // The ViewModel is scoped to the Settings back-stack entry, so the loop
        // stops as soon as the user leaves the Settings screen.
        viewModelScope.launch {
            while (isActive) {
                _cpuStats.value = withContext(Dispatchers.IO) { cpuMonitor.sample() }
                delay(1_000)
            }
        }

        viewModelScope.launch {
            countPasswordsUseCase().catch { /* keep last */ }.collect { _totalCount.value = it }
        }
        viewModelScope.launch {
            countEncryptedPasswordsUseCase().catch { /* keep last */ }.collect { _encryptedCount.value = it }
        }
        viewModelScope.launch {
            getDuplicateGroupsUseCase().catch { /* keep last */ }.collect { _duplicateGroups.value = it }
        }
    }

    // ---- 1. Test data seeding ----

    private val _seedState = MutableStateFlow<TestDataSeedState>(TestDataSeedState.Idle)
    val seedState: StateFlow<TestDataSeedState> = _seedState.asStateFlow()

    fun clearSeedResult() {
        if (_seedState.value != TestDataSeedState.Running) _seedState.value = TestDataSeedState.Idle
    }

    fun insertTestData(request: TestDatasetRequest) {
        if (_seedState.value == TestDataSeedState.Running) return
        if (!devModeManager.devModeEnabled.value) {
            _seedState.value = TestDataSeedState.Disabled
            return
        }
        if (lockManager.isLocked()) {
            _seedState.value = TestDataSeedState.Locked
            return
        }
        // Set synchronously: rapid taps cannot queue two imports before launch starts.
        _seedState.value = TestDataSeedState.Running
        viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.Default) {
                    val now = System.currentTimeMillis()
                    when (request) {
                        is TestDatasetRequest.Realistic -> SyntheticVaultGenerator.generate(request.plan, UUID.randomUUID().toString(), now)
                        TestDatasetRequest.SecurityCheck -> SyntheticVaultGenerator.securityFixture(now)
                        TestDatasetRequest.Totp -> listOf(SyntheticVaultGenerator.totpFixture(now))
                    }
                }
                if (!devModeManager.devModeEnabled.value) {
                    _seedState.value = TestDataSeedState.Disabled
                    return@launch
                }
                if (lockManager.isLocked()) {
                    _seedState.value = TestDataSeedState.Locked
                    return@launch
                }
                val inserted = importPasswordsUseCase(rows)
                _seedState.value = TestDataSeedState.Saved(inserted, rows.size)
            } catch (e: CancellationException) {
                _seedState.value = TestDataSeedState.Idle
                throw e
            } catch (_: Exception) {
                _seedState.value = if (lockManager.isLocked()) TestDataSeedState.Locked else TestDataSeedState.Failed
            }
        }
    }

    // ---- 3. Encryption stats / benchmark ----

    /** Times full list decryption: shows rows and elapsed ms. */
    fun measureDecryption() {
        viewModelScope.launch {
            try {
                val startNanos = System.nanoTime()
                val passwords = getPasswordsUseCase().first()
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                _decryptTestResult.value = "Decrypted ${passwords.size} rows in ${elapsedMs} ms"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _decryptTestResult.value = "Decryption failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** Verifies every "enc:" row can be decrypted. */
    fun checkIntegrity() {
        viewModelScope.launch {
            try {
                val broken = checkEncryptionIntegrityUseCase()
                _integrityCheckResult.value = if (broken == 0) {
                    "All encrypted rows decrypt OK"
                } else {
                    "$broken row(s) failed to decrypt"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _integrityCheckResult.value = "Integrity check failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // ---- 4. Duplicates ----

    fun removeDuplicates() {
        viewModelScope.launch {
            try {
                val removed = deleteDuplicatesUseCase()
                emitToast(
                    if (removed > 0) UiText.StringResource(R.string.dev_toast_duplicates_removed, removed)
                    else UiText.StringResource(R.string.dev_toast_no_duplicates)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitToast(UiText.DynamicString("Duplicate cleanup failed: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    // ---- 7. Quick security toggles ----

    fun setLockTimeout(seconds: Int) {
        lockManager.setLockTimeoutSeconds(seconds)
        _lockTimeout.value = seconds
        emitToast(UiText.StringResource(R.string.dev_toast_autolock_set, if (seconds == 0) "Never" else "$seconds s"))
    }

    fun lockNow() {
        lockManager.setLocked(true)
        emitToast(UiText.StringResource(R.string.dev_toast_app_locked))
    }

    fun resetMasterPassword() {
        lockManager.resetLock()
        emitToast(UiText.StringResource(R.string.dev_toast_master_password_reset))
    }

    // ---- 2. Database wipe (dev) ----

    fun deleteAllPasswords() {
        viewModelScope.launch {
            try {
                deleteAllPasswordsUseCase()
                emitToast(UiText.StringResource(R.string.dev_toast_all_deleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitToast(UiText.DynamicString("Wipe failed: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    private fun emitToast(message: UiText) {
        viewModelScope.launch { _event.emit(DevToolsEvent.ShowToast(message)) }
    }

}
