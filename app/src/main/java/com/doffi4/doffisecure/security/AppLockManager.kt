package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages the application lock: master‑password backup (salted SHA‑256 hash)
 * and auto‑lock timeout.
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("doffisecure_lock", Context.MODE_PRIVATE)

    /**
     * Observable mirror of [isLocked]: lets the UI (AppLockViewModel) react
     * immediately when the lock state changes from anywhere (e.g. the dev-mode
     * "Lock now" quick action), not just on app start.
     */
    private val _isLocked = MutableStateFlow(prefs.getBoolean(KEY_IS_LOCKED, true))
    val isLockedFlow: StateFlow<Boolean> = _isLocked.asStateFlow()

    private companion object {
        const val KEY_HASH = "master_password_hash"
        const val KEY_SALT = "master_password_salt"
        const val KEY_HAS_PASSWORD = "has_master_password"
        const val KEY_IS_LOCKED = "is_locked"
        const val KEY_LOCK_TIMEOUT = "lock_timeout_seconds"
        const val KEY_LAST_ACTIVE = "last_active_timestamp"
        const val KEY_ALLOW_SCREENSHOTS = "allow_screenshots"
        const val PBKDF_ITERATIONS = 10_000
        const val SALT_BYTES = 16
        const val DEFAULT_TIMEOUT_SEC = 30
    }

    // ---- Master password ----

    fun hasMasterPassword(): Boolean = prefs.getBoolean(KEY_HAS_PASSWORD, false)
    fun isLocked(): Boolean = prefs.getBoolean(KEY_IS_LOCKED, true)

    fun setLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply()
        _isLocked.value = locked
        if (!locked) touchLastActive()
    }

    fun setMasterPassword(password: String): Boolean {
        if (hasMasterPassword() || password.isEmpty()) return false
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.toBase64())
            .putString(KEY_HASH, hash)
            .putBoolean(KEY_HAS_PASSWORD, true)
            .putBoolean(KEY_IS_LOCKED, false)
            .apply()
        _isLocked.value = false
        touchLastActive()
        return true
    }

    fun verifyPassword(password: String): Boolean {
        if (!hasMasterPassword()) return false
        val storedSalt = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        return hashPassword(password, storedSalt.fromBase64()) == storedHash
    }

    fun resetLock() {
        prefs.edit().clear().putBoolean(KEY_IS_LOCKED, true).apply()
        _isLocked.value = true
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

    private fun hashPassword(password: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var buf = salt + password.toByteArray(Charsets.UTF_8)
        repeat(PBKDF_ITERATIONS) { buf = digest.digest(buf) }
        return buf.toBase64()
    }

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}
