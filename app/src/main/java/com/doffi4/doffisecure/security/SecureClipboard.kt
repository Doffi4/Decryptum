package com.doffi4.doffisecure.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
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
 *
 * Uses an in-process coroutine delay for immediate foreground clearing and
 * an AlarmManager scheduled broadcast as a best-effort defense against process death.
 */
class SecureClipboard(private val context: Context) {

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val ALARM_REQUEST_CODE = 4421
    }

    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    @Volatile
    private var clearJob: Job? = null

    /**
     * Copies [text] into the system clipboard and schedules an auto-clear
     * after [timeoutMs]. Any previously scheduled auto-clear is cancelled.
     */
    fun copy(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        val clipData = ClipData.newPlainText("Decryptum", text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboardManager?.setPrimaryClip(clipData)

        clearJob?.cancel()
        clearJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            clearPrimaryClipSafely()
        }

        scheduleAlarmClear(timeoutMs)
    }

    /** Immediately clears the clipboard. */
    fun clear() {
        clearJob?.cancel()
        cancelAlarmClear()
        clearPrimaryClipSafely()
    }

    private fun scheduleAlarmClear(timeoutMs: Long) {
        try {
            val intent = Intent(context, ClipboardClearReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                flags
            )
            val triggerAtMillis = System.currentTimeMillis() + timeoutMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager?.canScheduleExactAlarms() == true) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: Throwable) {
            // AlarmManager scheduling failed (e.g. mock context or permission restrictions)
        }
    }

    private fun cancelAlarmClear() {
        try {
            val intent = Intent(context, ClipboardClearReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                flags
            )
            if (pendingIntent != null) {
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Throwable) {}
    }

    /**
     * `ClipboardManager.clearPrimaryClip` only exists on API 28+. On older
     * devices we fall back to overwriting the clip with an empty item, which
     * has the same net effect of removing sensitive data from the clipboard.
     */
    private fun clearPrimaryClipSafely() {
        try {
            val desc = try { clipboardManager?.primaryClipDescription } catch (_: Throwable) { null }
            if (desc != null && desc.label != null && desc.label != "Decryptum") {
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager?.clearPrimaryClip()
            } else {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Throwable) {}
    }
}
