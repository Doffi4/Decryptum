package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the application lock, auto-lock timeout, and delegates DEK unlocking
 * to [PasswordCrypto].
 */
class AppLockManager(
    private val prefs: SharedPreferences,
    private val passwordCrypto: PasswordCrypto
) {
    constructor(context: Context, passwordCrypto: PasswordCrypto = PasswordCrypto(context)) :
        this(context.getSharedPreferences("doffisecure_lock", Context.MODE_PRIVATE), passwordCrypto)

    /**
     * Observable mirror of [isLocked]: lets the UI (AppLockViewModel) react
     * immediately when the lock state changes from anywhere (e.g. the dev-mode
     * "Lock now" quick action), not just on app start.
     */
    private val _isLocked = MutableStateFlow(prefs.getBoolean(KEY_IS_LOCKED, true) || !passwordCrypto.isUnlocked())
    val isLockedFlow: StateFlow<Boolean> = _isLocked.asStateFlow()

    private companion object {
        const val KEY_HAS_PASSWORD = "has_master_password"
        const val KEY_IS_LOCKED = "is_locked"
        const val KEY_LOCK_TIMEOUT = "lock_timeout_seconds"
        const val KEY_LAST_ACTIVE = "last_active_timestamp"
        const val KEY_ALLOW_SCREENSHOTS = "allow_screenshots"
        const val KEY_LOCKOUT_UNTIL = "lockout_until_timestamp"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
        const val DEFAULT_TIMEOUT_SEC = 30
    }

    // ---- Master password ----

    fun hasMasterPassword(): Boolean =
        prefs.getBoolean(KEY_HAS_PASSWORD, false) || passwordCrypto.hasVault()

    fun isLocked(): Boolean =
        prefs.getBoolean(KEY_IS_LOCKED, true) || !passwordCrypto.isUnlocked()

    fun setLocked(locked: Boolean) {
        if (locked) {
            passwordCrypto.lock()
        }
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply()
        _isLocked.value = locked
        if (!locked) touchLastActive()
    }

    fun setMasterPassword(password: String): Boolean {
        if (hasMasterPassword() || password.isEmpty()) return false
        val success = passwordCrypto.setupMasterPassword(password)
        if (!success) return false

        prefs.edit()
            .putBoolean(KEY_HAS_PASSWORD, true)
            .putBoolean(KEY_IS_LOCKED, false)
            .apply()
        _isLocked.value = false
        touchLastActive()
        return true
    }

    fun verifyPassword(password: String): Boolean {
        if (!hasMasterPassword() || password.isEmpty()) return false

        val success = passwordCrypto.unlockWithPassword(password)
        if (success) {
            setLocked(false)
            return true
        }
        return false
    }

    fun resetLock() {
        passwordCrypto.lock()
        passwordCrypto.disableBiometric()
        prefs.edit().clear().putBoolean(KEY_IS_LOCKED, true).apply()
        _isLocked.value = true
    }

    fun resetVault() {
        passwordCrypto.resetVault()
        resetLock()
    }

    // ---- Auto‑lock timeout ----

    fun getLockTimeoutSeconds(): Int = prefs.getInt(KEY_LOCK_TIMEOUT, DEFAULT_TIMEOUT_SEC)

    fun setLockTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_LOCK_TIMEOUT, seconds.coerceIn(0, 3600)).apply()
    }

    /** Records the current time as the latest user activity. */
    fun touchLastActive() {
        prefs.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply()
    }

    /**
     * Returns `true` if the auto‑lock timeout has elapsed since the last
     * recorded activity, meaning the app should be re‑locked.
     */
    fun shouldAutoLock(): Boolean {
        val timeout = getLockTimeoutSeconds()
        if (timeout <= 0) return false // "never" timeout
        val lastActive = prefs.getLong(KEY_LAST_ACTIVE, 0L)
        return System.currentTimeMillis() - lastActive >= timeout * 1000L
    }

    // ---- Screenshot protection ----

    fun getAllowScreenshots(): Boolean = prefs.getBoolean(KEY_ALLOW_SCREENSHOTS, false)

    fun setAllowScreenshots(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_SCREENSHOTS, allow).apply()
    }

    // ---- Rate limiting & Lockout ----

    fun getLockoutUntilTimestamp(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    fun setLockoutUntilTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LOCKOUT_UNTIL, timestamp).apply()
    }

    fun clearLockout() {
        prefs.edit().remove(KEY_LOCKOUT_UNTIL).apply()
    }

    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun setFailedAttempts(count: Int) {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, count).apply()
    }
}
