package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hidden developer mode state.
 *
 * Dev mode is toggled by tapping the "Decryptum" title 6 times on the main
 * screen. While it is enabled the Settings screen shows a "Developer" section
 * with extra toggles (e.g. showing the live password count on the main screen).
 *
 * Backed by SharedPreferences so the state survives app restarts, and exposed
 * as StateFlows so Compose can collect changes reactively from any screen.
 */
class DevModeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("doffisecure_dev", Context.MODE_PRIVATE)

    private companion object {
        /** Unlock code for the developer mode (shared with the test account). */
        const val DEV_MODE_PASSWORD = "IrkaSec08"
        const val KEY_DEV_MODE = "dev_mode_enabled"
        const val KEY_SHOW_PASSWORD_COUNT = "show_password_count"
        const val KEY_SHOW_WARMUP_PROGRESS = "show_warmup_progress"
        const val KEY_SHOW_FPS_OVERLAY = "show_fps_overlay"
        const val KEY_SHOW_CPU_OVERLAY = "show_cpu_overlay"
        const val KEY_PREFETCH_COUNT = "prefetch_count"
    }

    private val _devModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEV_MODE, false))
    val devModeEnabled: StateFlow<Boolean> = _devModeEnabled.asStateFlow()

    private val _showPasswordCount = MutableStateFlow(prefs.getBoolean(KEY_SHOW_PASSWORD_COUNT, false))
    val showPasswordCount: StateFlow<Boolean> = _showPasswordCount.asStateFlow()

    fun enableDevMode(enabled: Boolean) {
        _devModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
    }

    /** Returns true when [code] matches the developer-mode unlock password. */
    fun isDevPasswordValid(code: String): Boolean = code == DEV_MODE_PASSWORD

    fun setShowPasswordCount(show: Boolean) {
        _showPasswordCount.value = show
        prefs.edit().putBoolean(KEY_SHOW_PASSWORD_COUNT, show).apply()
    }

    private val _showWarmupProgress = MutableStateFlow(prefs.getBoolean(KEY_SHOW_WARMUP_PROGRESS, false))
    val showWarmupProgress: StateFlow<Boolean> = _showWarmupProgress.asStateFlow()

    fun setShowWarmupProgress(show: Boolean) {
        _showWarmupProgress.value = show
        prefs.edit().putBoolean(KEY_SHOW_WARMUP_PROGRESS, show).apply()
    }

    private val _showFpsOverlay = MutableStateFlow(prefs.getBoolean(KEY_SHOW_FPS_OVERLAY, false))
    val showFpsOverlay: StateFlow<Boolean> = _showFpsOverlay.asStateFlow()

    fun setShowFpsOverlay(show: Boolean) {
        _showFpsOverlay.value = show
        prefs.edit().putBoolean(KEY_SHOW_FPS_OVERLAY, show).apply()
    }

    /** Persistent CPU-temperature overlay flag (dev tools). */
    private val _showCpuOverlay = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CPU_OVERLAY, true))
    val showCpuOverlay: StateFlow<Boolean> = _showCpuOverlay.asStateFlow()

    fun setShowCpuOverlay(show: Boolean) {
        _showCpuOverlay.value = show
        prefs.edit().putBoolean(KEY_SHOW_CPU_OVERLAY, show).apply()
    }

    /** Items composed ahead of the viewport (LazyColumn prefetch tuning). */
    private val _prefetchCount = MutableStateFlow(prefs.getInt(KEY_PREFETCH_COUNT, 8))
    val prefetchCount: StateFlow<Int> = _prefetchCount.asStateFlow()

    fun setPrefetchCount(count: Int) {
        val clamped = count.coerceIn(0, 16)
        _prefetchCount.value = clamped
        prefs.edit().putInt(KEY_PREFETCH_COUNT, clamped).apply()
    }
}