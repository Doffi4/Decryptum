package com.doffi4.doffisecure.autofill

import android.app.Activity
import android.os.Bundle

/**
 * Transparent helper activity to cleanly dismiss the Autofill Fill Dialog
 * when the close (✕) button in the header is clicked.
 */
class AutofillDismissActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
