package com.doffi4.doffisecure.autofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.security.SecureClipboard
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles copy clicks from RemoteViews autofill presentations.
 * Safely places the credential text into the secure auto-clearing clipboard
 * and provides instant visual feedback via Toast.
 */
class AutofillCopyReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val ACTION_AUTOFILL_COPY = "com.doffi4.doffisecure.ACTION_AUTOFILL_COPY"
        const val EXTRA_COPY_TEXT = "extra_copy_text"
    }

    private val secureClipboard: SecureClipboard by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val textToCopy = intent.getStringExtra(EXTRA_COPY_TEXT) ?: return
        if (textToCopy.isBlank()) return

        try {
            secureClipboard.copy(textToCopy)
        } catch (_: Throwable) {
            // Fallback to manual clipboard if Koin is not initialized
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Decryptum", textToCopy))
        }

        Toast.makeText(context, R.string.autofill_copied_toast, Toast.LENGTH_SHORT).show()
    }
}
