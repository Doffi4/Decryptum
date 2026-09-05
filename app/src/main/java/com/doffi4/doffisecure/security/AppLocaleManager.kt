package com.doffi4.doffisecure.security

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Modern Android 13–16+ locale manager with backwards compatibility down to Android 8.0 (API 26).
 *
 * On API 33+ (Android 13 to Android 16/targetSdk 37), delegates to the system [LocaleManager],
 * synchronizing with the OS Per-App Language settings.
 * On older Android versions (API 26–32), wraps the base context via [createConfigurationContext].
 */
object AppLocaleManager {
    const val LANG_SYSTEM = "system"
    const val LANG_RU = "ru"
    const val LANG_EN = "en"

    fun getLocale(languageCode: String): Locale? {
        return when (languageCode) {
            LANG_RU -> Locale.forLanguageTag("ru")
            LANG_EN -> Locale.forLanguageTag("en")
            else -> null
        }
    }

    /**
     * Applies the selected language across the app.
     * On Android 13+ (API 33+), updates the system [LocaleManager].
     * On API < 33, recreates the activity so [wrapContext] takes effect.
     */
    fun applyLanguage(activity: Activity, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = activity.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                val localeList = when (languageCode) {
                    LANG_RU -> LocaleList.forLanguageTags("ru")
                    LANG_EN -> LocaleList.forLanguageTags("en")
                    else -> LocaleList.getEmptyLocaleList()
                }
                localeManager.applicationLocales = localeList
                return
            }
        }
        // Fallback for API < 33 or when LocaleManager is not available
        activity.recreate()
    }

    /**
     * Wraps [baseContext] with the user-selected locale configuration.
     * Used in [Activity.attachBaseContext].
     */
    fun wrapContext(baseContext: Context, languageCode: String): Context {
        val targetLocale = getLocale(languageCode) ?: return baseContext
        Locale.setDefault(targetLocale)

        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(targetLocale)
        val localeList = LocaleList(targetLocale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)

        return baseContext.createConfigurationContext(config)
    }
}
