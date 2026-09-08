package com.doffi4.doffisecure.security

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * BroadcastReceiver triggered by AlarmManager to clear the primary clip
 * after the clipboard auto-clear timeout expires, even if the app process died.
 */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val desc = try { cm?.primaryClipDescription } catch (_: Throwable) { null }
            if (desc != null && desc.label != null && desc.label != "Decryptum") {
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm?.clearPrimaryClip()
            } else {
                cm?.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Throwable) {
            // Background clipboard modification restrictions on newer Android versions
        }
    }
}
