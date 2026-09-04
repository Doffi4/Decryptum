package com.doffi4.doffisecure.dev

import android.view.FrameMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the last one-second moving window of frames.
 */
data class FrameStats(
    val fps: Int,
    val avgFrameMs: Float,
    val jankFrames: Int,
    val worstFrameMs: Float,
)

/**
 * Real frame-time monitor used by the developer overlay.
 *
 * Measures actual drawn-frame durations instead of vsync intervals: a
 * Choreographer-based delta between callbacks reports a steady 60/120 fps
 * even while a frame itself takes 40 ms of work, which makes real jank
 * invisible. This monitor instead consumes [FrameMetrics] reported by the
 * platform for every actually-drawn frame (total duration includes
 * traversal, measure/layout, draw and the render thread wait).
 *
 * One second of frames is aggregated into a single [FrameStats], so the UI
 * recomposes at most ~1x per second. Per frame we only do a few arithmetic
 * operations on the main thread - far cheaper than the frame being measured.
 * A frame counts as "jank" when its total duration exceeds the device's
 * refresh interval (16.6 ms at 60 Hz, 8.3 ms at 120 Hz), the same definition
 * the system itself uses.
 */
class FrameStatsMonitor(private val targetFrameMs: Float) {

    private val targetFrameNs = (targetFrameMs * 1_000_000f).toLong()

    private val _stats = MutableStateFlow(FrameStats(0, 0f, 0, 0f))
    val stats: StateFlow<FrameStats> = _stats.asStateFlow()

    private var running = false
    private var framesInWindow = 0
    private var sumFrameNs = 0L
    private var jankInWindow = 0
    private var worstFrameNs = 0L
    private var windowStartNs = 0L

    /** Starts aggregating. Safe to call repeatedly. */
    fun start() {
        running = true
    }

    /** Stops aggregating. Frames reported after this are ignored. */
    fun stop() {
        running = false
    }

    /** Target per-frame budget used for the jank counter (e.g. ~16.6 ms for 60 Hz). */
    fun targetFrameMs(): Float = targetFrameMs

    /**
     * Called from a [android.view.Window.OnFrameMetricsAvailableListener] on
     * the main thread, once per actually drawn frame. Aggregates the frame
     * into the current one-second window and publishes a [FrameStats] once a
     * second.
     */
    fun onFrame(metrics: FrameMetrics) {
        if (!running) return

        val durationNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        // Skipped / empty frames report 0 duration - ignore them.
        if (durationNs <= 0L) return

        val nowNs = System.nanoTime()
        if (windowStartNs == 0L) windowStartNs = nowNs

        framesInWindow++
        sumFrameNs += durationNs
        if (durationNs > targetFrameNs) jankInWindow++
        if (durationNs > worstFrameNs) worstFrameNs = durationNs

        if (nowNs - windowStartNs >= 1_000_000_000L) {
            val avgMs = sumFrameNs.toFloat() / framesInWindow / 1_000_000f
            _stats.value = FrameStats(
                fps = if (avgMs > 0f) (1000f / avgMs).toInt() else 0,
                avgFrameMs = avgMs,
                jankFrames = jankInWindow,
                worstFrameMs = worstFrameNs / 1_000_000f,
            )
            framesInWindow = 0
            sumFrameNs = 0L
            jankInWindow = 0
            worstFrameNs = 0L
            windowStartNs = nowNs
        }
    }
}