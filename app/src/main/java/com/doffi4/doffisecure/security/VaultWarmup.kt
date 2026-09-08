package com.doffi4.doffisecure.security

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Post-unlock warm-up. Once the user unlocks the vault (master password entry
 * or biometric prompt), the app prepares local storage for smooth UI navigation:
 *
 *  1. converts legacy rows to the fast envelope format (one-time migration);
 *  2. reads and decrypts the full list - this warms the Room cache, the
 *     in-memory DEK, and the crypto code paths.
 *
 * Network prefetching of favicons has been removed to protect user privacy
 * and prevent bulk transmission of saved domains across the network.
 *
 * Everything is best-effort: failures are logged and never crash or block UI.
 */
class VaultWarmup(
    private val repository: IPasswordRepository,
    private val passwordCrypto: PasswordCrypto
) {
    constructor(
        context: Context,
        repository: IPasswordRepository,
        passwordCrypto: PasswordCrypto = PasswordCrypto(context)
    ) : this(repository, passwordCrypto)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmLock = Any()
    private var warmJob: Job? = null

    private val _progress = MutableStateFlow(0)
    /** How much of the vault has been warmed up: 0..100. */
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    /** True while warm-up work is in progress in the background. */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Starts (or restarts) the warm-up. Idempotent while already running. */
    fun warm() {
        synchronized(warmLock) {
            if (!passwordCrypto.isUnlocked()) {
                Log.d(TAG, "Warm-up skipped: vault is locked")
                return
            }
            if (warmJob?.isActive == true) return
            _isRunning.value = true
            _progress.value = 0
            warmJob = scope.launch {
                try {
                    val startMs = SystemClock.elapsedRealtime()
                    // 1) Ensure fast envelope format first (migration),
                    //    otherwise the read below would hit the slow path.
                    try {
                        val migrated = repository.migrateLegacyEncryption()
                        if (migrated > 0) {
                            Log.d(TAG, "Migrated $migrated rows to fast encryption")
                        }
                    } catch (_: Exception) {
                        // Fresh install / no legacy rows - nothing to migrate.
                    }
                    _progress.value = 40

                    // 2) Touch all accounts: opens the DB, unwraps the DEK,
                    //    decrypts every row and populates Room + crypto caches.
                    val passwords = repository.getAllPasswords().first()
                    _progress.value = 100
                    Log.d(
                        TAG,
                        "Warm-up finished in ${SystemClock.elapsedRealtime() - startMs} ms " +
                            "(${passwords.size} passwords loaded into cache)"
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Warm-up skipped", t)
                } finally {
                    _isRunning.value = false
                }
            }
        }
    }

    private companion object {
        const val TAG = "VaultWarmup"
    }
}