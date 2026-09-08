package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "doffisecure_settings"
        const val KEY_SHOW_PASSWORD_STRENGTH = "show_password_strength"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_AUTOFILL_ALWAYS_REQUIRE_AUTH = "autofill_always_require_auth"
        const val KEY_LOAD_FAVICONS = "load_site_favicons"

        fun getSavedLanguage(context: Context): String {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return p.getString(KEY_APP_LANGUAGE, AppLocaleManager.LANG_SYSTEM) ?: AppLocaleManager.LANG_SYSTEM
        }
    }

    /** Whether the password-strength chip is shown across the app (default: on). */
    private val _showPasswordStrength =
        MutableStateFlow(prefs.getBoolean(KEY_SHOW_PASSWORD_STRENGTH, true))
    val showPasswordStrength: StateFlow<Boolean> = _showPasswordStrength.asStateFlow()

    fun setShowPasswordStrength(show: Boolean) {
        _showPasswordStrength.value = show
        prefs.edit { putBoolean(KEY_SHOW_PASSWORD_STRENGTH, show) }
    }

    /** Selected app language: "system", "ru", "en" (default: "system"). */
    private val _appLanguage =
        MutableStateFlow(prefs.getString(KEY_APP_LANGUAGE, AppLocaleManager.LANG_SYSTEM) ?: AppLocaleManager.LANG_SYSTEM)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setAppLanguage(languageCode: String) {
        _appLanguage.value = languageCode
        prefs.edit { putString(KEY_APP_LANGUAGE, languageCode) }
    }

    /** Whether autofill always requires biometric/PIN confirmation (even if vault unlocked). */
    private val _autofillAlwaysRequireAuth =
        MutableStateFlow(prefs.getBoolean(KEY_AUTOFILL_ALWAYS_REQUIRE_AUTH, false))
    val autofillAlwaysRequireAuth: StateFlow<Boolean> = _autofillAlwaysRequireAuth.asStateFlow()

    fun setAutofillAlwaysRequireAuth(alwaysRequire: Boolean) {
        _autofillAlwaysRequireAuth.value = alwaysRequire
        prefs.edit { putBoolean(KEY_AUTOFILL_ALWAYS_REQUIRE_AUTH, alwaysRequire) }
    }

    /** Whether website favicons are downloaded directly from sites and cached (default: true). */
    private val _loadFavicons =
        MutableStateFlow(prefs.getBoolean(KEY_LOAD_FAVICONS, true))
    val loadFavicons: StateFlow<Boolean> = _loadFavicons.asStateFlow()

    fun setLoadFavicons(enabled: Boolean) {
        _loadFavicons.value = enabled
        prefs.edit { putBoolean(KEY_LOAD_FAVICONS, enabled) }
    }
}