package com.doffi4.doffisecure.dev

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Frame-rate level the app currently renders at. Higher is only used while the
 * user is interacting; the controller steps the app down when nothing is being
 * pressed so the display (and on LTPO panels the physical refresh) can drop.
 */
enum class RefreshTier(val label: String, val fps: Int) {
    ACTIVE("active", 120),
    IDLE_60("idle 60", 60),
}

/**
 * Idle frame-rate governor.
 *
 * While the user interacts the app runs at the panel's maximum ("active").
 * After [IDLE_60_MS] without any interaction it steps down to 60 fps only —
 * never below 60: the old 30 fps idle tier made the first post-pause gesture
 * stutter while the panel ramped back up. ANY interaction jumps straight back
 * to the top tier. The step down has two parts:
 *
 *  1. App side: the only continuously-repainting UI (the frame-time overlay)
 *     stops repainting each second in idle, so we stop producing unneeded
 *     frames. Compose itself only draws on invalidation, so a static screen
 *     already generates nothing.
 *  2. System side (Android 15 / API 35+): [Window.setFrameRatePowerSavingsBalanced]
 *     lets SurfaceFlinger drop the display refresh when the window has no new
 *     buffer, which on LTPO panels (e.g. OnePlus 13) physically lowers the
 *     panel Hz while reading. The touch boost stays enabled so interacting
 *     raises the refresh instantly.
 *
 * The controller is a Koin singleton but holds only a [WeakReference] to the
 * Activity, so it never leaks it. Must be [attach]ed in onCreate and
 * [detach]ed in onDestroy.
 */
class RefreshRateController {

    private companion object {
        /** Seconds of no interaction before dropping to 60 fps. */
        const val IDLE_60_MS = 10_000L
    }

    private val _tier = MutableStateFlow(RefreshTier.ACTIVE)
    val tier: StateFlow<RefreshTier> = _tier.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var lastActivityMs = 0L
    private var activityRef: WeakReference<Activity>? = null

    /** Single re-scheduling runnable: steps down as idle time grows. */
    private val idleCheck = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - lastActivityMs
            if (elapsed >= IDLE_60_MS) {
                _tier.value = RefreshTier.IDLE_60
            } else {
                handler.postDelayed(this, IDLE_60_MS - elapsed)
            }
        }
    }

    /** Call on every touch/key event (from Activity.dispatchTouchEvent/…). */
    fun onUserInteraction() {
        lastActivityMs = SystemClock.uptimeMillis()
        if (_tier.value != RefreshTier.ACTIVE) {
            _tier.value = RefreshTier.ACTIVE
        }
        handler.removeCallbacks(idleCheck)
        handler.postDelayed(idleCheck, IDLE_60_MS)
    }

    /** Binds the controller to the activity window and applies system hints. */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        applyWindowHints(activity)
        onUserInteraction()
    }

    /** Releases the activity reference; call from onDestroy. */
    fun detach() {
        handler.removeCallbacksAndMessages(null)
        activityRef?.clear()
        activityRef = null
    }

    /**
     * API 35+: let SurfaceFlinger lower the panel when no new buffer arrives
     * (LTPO power savings) while keeping the touch boost so the rate always
     * springs back on interaction.
     */
    private fun applyWindowHints(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            activity.window.apply {
                setFrameRatePowerSavingsBalanced(true)
                setFrameRateBoostOnTouchEnabled(true)
            }
        }
    }
}