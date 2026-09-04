package com.doffi4.doffisecure.ui.navigation

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.doffi4.doffisecure.dev.CpuMonitor
import com.doffi4.doffisecure.dev.CpuStats
import com.doffi4.doffisecure.dev.FrameStatsMonitor
import com.doffi4.doffisecure.dev.RefreshRateController
import com.doffi4.doffisecure.dev.RefreshTier
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.ui.password.SettingsScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Set of main tab routes where the bottom navigation bar should be visible.
 */
private val mainTabRoutes = setOf(
    Screen.PasswordList.route,
    Screen.Generator.route,
    Screen.Settings.route,
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val devModeManager = remember { GlobalContext.get().get<DevModeManager>() }
    val refreshController = remember { GlobalContext.get().get<RefreshRateController>() }

    val refreshContext = LocalContext.current
    val frameStatsMonitor = remember(refreshContext) {
        val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (refreshContext as? Activity)?.display?.refreshRate ?: 60f
        } else {
            @Suppress("DEPRECATION")
            (refreshContext as? Activity)?.windowManager?.defaultDisplay?.refreshRate ?: 60f
        }
        FrameStatsMonitor(1000f / refreshRate)
    }

    // Observe the current back stack entry reactively
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine whether the bottom bar should be shown
    val isMainRoute = currentRoute in mainTabRoutes

    // Dev-mode overlays: shown on the main tabs except Generator (which is a
    // calibration screen for password quality) and Settings (which already has
    // the full telemetry card).
    val devMode by devModeManager.devModeEnabled.collectAsState()
    val cpuOverlayEnabled by devModeManager.showCpuOverlay.collectAsState()
    val showFpsOverlayToggle by devModeManager.showFpsOverlay.collectAsState()
    val refreshTier by refreshController.tier.collectAsState()

    val overlayHidden = currentRoute == Screen.Settings.route ||
            currentRoute == Screen.Generator.route
    val showCpuOverlay = devMode && cpuOverlayEnabled && !overlayHidden
    val showFpsOverlay = devMode && showFpsOverlayToggle && !overlayHidden

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isMainRoute,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                CustomBottomNavigation(navController)
            }
        }
    ) { padding ->
        // Прибираємо padding(padding) звідси, щоб екран йшов НА ВСЮ ВИСОТУ під капсулу!
        Box(modifier = Modifier.fillMaxSize()) {
            SetupNavGraph(navController)

            // FPS overlay positioned exactly 4px from the status bar bottom,
            // above the CPU chip (which is at 139dp).
            FrameOverlay(
                monitor = frameStatsMonitor,
                tier = refreshTier,
                show = showFpsOverlay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = padding.calculateTopPadding() + 4.dp)
            )

            CpuOverlayChip(
                visible = showCpuOverlay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = padding.calculateTopPadding() + 139.dp)
            )
        }
    }
}

/**
 * Small dev pill with the approximate CPU temperature and load, refreshed once
 * per second while visible. Positioned at the top-end corner so it does not
 * collide with the "Decryptum" title on the password screen.
 */
@Composable
private fun CpuOverlayChip(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val cpuMonitor = remember { GlobalContext.get().get<CpuMonitor>() }
    var stats by remember { mutableStateOf(CpuStats(null, "н/д", 0f, null)) }

    LaunchedEffect(visible) {
        while (visible && isActive) {
            stats = withContext(Dispatchers.IO) { cpuMonitor.sample() }
            delay(1_000)
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val temp = stats.cpuTempC
        val container = when {
            temp == null -> {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
            temp >= 70f -> {
                MaterialTheme.colorScheme.errorContainer
            }
            temp >= 55f -> {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            else -> MaterialTheme.colorScheme.primaryContainer
        }
        Surface(
            shape = CircleShape,
            color = container.copy(alpha = 0.92f),
            shadowElevation = 2.dp
        ) {
            Text(
                text = buildString {
                    append("CPU")
                    if (temp != null) append(" · ~${"%.1f".format(temp)} °C") else append(" · н/д")
                    append(" · ${stats.cpuLoadPercent.roundToInt()}%")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Live frame-time readout (fps, average frame ms, jank frames in the last
 * second and the worst frame). Positions itself at the TopEnd to match the
 * CPU pill's alignment.
 */
@Composable
private fun FrameOverlay(
    monitor: FrameStatsMonitor,
    tier: RefreshTier,
    show: Boolean,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as? Activity

    DisposableEffect(monitor, show, activity) {
        var listener: Window.OnFrameMetricsAvailableListener? = null
        if (show && activity != null) {
            listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                monitor.onFrame(frameMetrics)
            }
            activity.window.addOnFrameMetricsAvailableListener(
                listener,
                Handler(Looper.getMainLooper())
            )
            monitor.start()
        } else {
            monitor.stop()
        }
        onDispose {
            if (activity != null && listener != null) {
                activity.window.removeOnFrameMetricsAvailableListener(listener)
            }
            monitor.stop()
        }
    }
    if (!show) return

    val stats by monitor.stats.collectAsState()
    val targetFrameMs = monitor.targetFrameMs()
    val container = when {
        stats.fps == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
        stats.avgFrameMs >= targetFrameMs * 2f || stats.jankFrames >= 5 ->
            MaterialTheme.colorScheme.errorContainer
        stats.avgFrameMs >= targetFrameMs * 1.25f || stats.jankFrames >= 1 ->
            MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container.copy(alpha = 0.92f),
        shadowElevation = 2.dp
    ) {
        Text(
            text = if (tier != RefreshTier.ACTIVE) {
                "${tier.label} · capped ${tier.fps} fps"
            } else if (stats.fps == 0) {
                "Frame meter…"
            } else {
                "${stats.fps} fps · ${"%.1f".format(stats.avgFrameMs)} ms · " +
                        "${stats.jankFrames} jank · max ${"%.1f".format(stats.worstFrameMs)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
