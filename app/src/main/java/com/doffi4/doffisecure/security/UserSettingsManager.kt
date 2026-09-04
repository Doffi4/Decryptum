package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Normal, user-facing app settings (as opposed to the hidden [DevModeManager]).
 *
 * Preferences persist across restarts and are exposed as StateFlows so any
 * screen can react to changes live.
 */
class UserSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("doffisecure_settings", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_SHOW_PASSWORD_STRENGTH = "show_password_strength"
    }

    /** Whether the password-strength chip is shown across the app (default: on). */
    private val _showPasswordStrength =
        MutableStateFlow(prefs.getBoolean(KEY_SHOW_PASSWORD_STRENGTH, true))
    val showPasswordStrength: StateFlow<Boolean> = _showPasswordStrength.asStateFlow()

    fun setShowPasswordStrength(show: Boolean) {
        _showPasswordStrength.value = show
        prefs.edit().putBoolean(KEY_SHOW_PASSWORD_STRENGTH, show).apply()
    }
}