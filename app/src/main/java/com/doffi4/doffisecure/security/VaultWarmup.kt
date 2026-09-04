package com.doffi4.doffisecure.security

import android.content.Context
import android.os.SystemClock
import android.util.Log
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.groupBySite
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Cold-start warm-up. While the lock screen is visible (master password entry
 * or biometric prompt) the app quietly prepares the vault so the main screen
 * opens with zero visible jank:
 *
 *  1. converts legacy rows to the fast envelope format (one-time);
 *  2. reads and decrypts the full list - this warms the Room cache, the
 *     in-memory DEK and the crypto code paths;
 *  3. prefetches group favicons into the shared Coil memory/disk caches in
 *     small batches, so the very first scroll pass is cache hits instead of
 *     network + decode on the fly.
 *
 * Everything is best-effort: failures are logged and never crash or block UI.
 */
class VaultWarmup(
    private val context: Context,
    private val repository: IPasswordRepository
) {

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
                    _progress.value = 20

                    // 2) Touch all accounts: opens the DB, unwraps the DEK,
                    //    decrypts every row and populates Room + crypto caches.
                    val passwords = repository.getAllPasswords().first()
                    _progress.value = 40
                    if (passwords.isNotEmpty()) {
                        warmFavicons(passwords)
                    }
                    _progress.value = 100
                    Log.d(
                        TAG,
                        "Warm-up finished in ${SystemClock.elapsedRealtime() - startMs} ms " +
                            "(${passwords.size} passwords, favicons queued)"
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Warm-up skipped", t)
                } finally {
                    _isRunning.value = false
                }
            }
        }
    }

    private suspend fun warmFavicons(passwords: List<Password>) {
        val urls = passwords.groupBySite()
            .mapNotNull { it.faviconUrl.takeIf(String::isNotBlank) }
        if (urls.isEmpty()) return

        val loader = context.imageLoader
        val total = urls.size
        var warmed = 0

        urls.take(QUICK_WARM).forEach { url -> enqueue(loader, url) }
        warmed += minOf(QUICK_WARM, total)
        _progress.value = 40 + (60 * warmed / total).coerceIn(0, 60)

        urls.drop(QUICK_WARM).chunked(BATCH_SIZE).forEach { chunk ->
            chunk.forEach { url -> enqueue(loader, url) }
            warmed += chunk.size
            _progress.value = 40 + (60 * warmed / total).coerceIn(0, 60)
            delay(BATCH_DELAY_MS)
        }
    }

    private fun enqueue(loader: ImageLoader, url: String) {
        loader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .size(FAVICON_PREFETCH_PX)
                .build()
        )
    }

    private companion object {
        const val TAG = "VaultWarmup"
        /** Prefetch resolution: matches the UI avatar request so the SAME
         *  memory-cache entry is hit on the first pass (no second decode). */
        const val FAVICON_PREFETCH_PX = 160
        /** Enqueued immediately (roughly the first viewport of rows). */
        const val QUICK_WARM = 24
        /** Remaining favicons are warmed in batches to avoid a network storm. */
        const val BATCH_SIZE = 8
        /** Pause between warm-up batches (ms) so the main thread stays responsive. */
        const val BATCH_DELAY_MS = 72L
    }
}