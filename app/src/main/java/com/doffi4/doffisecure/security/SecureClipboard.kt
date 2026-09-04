package com.doffi4.doffisecure.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wraps the platform clipboard and automatically clears any copied sensitive
 * data after a configurable timeout.
 *
 * Protects against [CLIPBOARD_SECURITY_ISSUE]: copied passwords lingering on
 * the shared device clipboard where any other app could read them.
 */
class SecureClipboard(private val context: Context) {

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }

    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Volatile
    private var clearJob: Job? = null

    /**
     * Copies [text] into the system clipboard and schedules an auto-clear
     * after [timeoutMs]. Any previously scheduled auto-clear is cancelled.
     */
    fun copy(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Decryptum", text))

        clearJob?.cancel()
        clearJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            clearPrimaryClipSafely()
        }
    }

    /** Immediately clears the clipboard. */
    fun clear() {
        clearJob?.cancel()
        clearPrimaryClipSafely()
    }

    /**
     * `ClipboardManager.clearPrimaryClip` only exists on API 28+. On older
     * devices we fall back to overwriting the clip with an empty item, which
     * has the same net effect of removing sensitive data from the clipboard.
     */
    private fun clearPrimaryClipSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
