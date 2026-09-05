package com.doffi4.doffisecure.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.math.cos
import kotlin.math.sin

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.doffi4.doffisecure.R

sealed class NavItem(val route: String, @StringRes val labelRes: Int, val label: String = "") {
    /** Renders the tab icon with the selection tint applied. */
    @Composable
    abstract fun Icon(selected: Boolean)

    object Passwords : NavItem(Screen.PasswordList.route, R.string.nav_passwords, "Passwords") {
        @Composable
        override fun Icon(selected: Boolean) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = tabIconTint(selected),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    object Generator : NavItem(Screen.Generator.route, R.string.nav_generator, "Generator") {
        @Composable
        override fun Icon(selected: Boolean) {
            Icon(
                imageVector = GeneratorIcon,
                contentDescription = null,
                tint = tabIconTint(selected),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    object Settings : NavItem(Screen.Settings.route, R.string.nav_settings, "Settings") {
        @Composable
        override fun Icon(selected: Boolean) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = tabIconTint(selected),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Selected tabs use the accent color; others keep a muted variant. */
@Composable
private fun tabIconTint(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Generator tab icon: a thin horizontal baseline line with two asterisks (*)
 * resting on it and a plus (+) as the third glyph. Drawn as a lightweight
 * custom vector so it tints with the surrounding tab color.
 */
private val GeneratorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GeneratorIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Thin baseline line running across the bottom.
        path(fill = SolidColor(Color.Black)) {
            moveTo(1.2f, 19.5f)
            lineTo(22.8f, 19.5f)
            lineTo(22.8f, 20.5f)
            lineTo(1.2f, 20.5f)
            close()
        }
        // Two asterisks (6-ray stars), then a plus as the third glyph.
        val halfLen = 2.7f
        val halfThick = 0.7f
        for (cx in floatArrayOf(5.6f, 12f)) {
            addGlyphBar(cx, 15f, 0f, halfLen, halfThick)
            addGlyphBar(cx, 15f, 60f, halfLen, halfThick)
            addGlyphBar(cx, 15f, 120f, halfLen, halfThick)
        }
        addGlyphBar(18.4f, 15f, 0f, halfLen, halfThick) // plus horizontal
        addGlyphBar(18.4f, 15f, 90f, halfLen, halfThick) // plus vertical
    }.build()
}

/** Adds one filled bar (thin rectangle through (cx, cy)) oriented at [degrees]. */
private fun ImageVector.Builder.addGlyphBar(
    cx: Float,
    cy: Float,
    degrees: Float,
    halfLen: Float,
    halfThick: Float,
) {
    val rad = Math.toRadians(degrees.toDouble())
    val ux = cos(rad).toFloat()
    val uy = sin(rad).toFloat()
    val px = -uy
    val py = ux
    val hx = ux * halfLen
    val hy = uy * halfLen
    val tx = px * halfThick
    val ty = py * halfThick
    path(fill = SolidColor(Color.Black)) {
        moveTo(cx + hx + tx, cy + hy + ty)
        lineTo(cx + hx - tx, cy + hy - ty)
        lineTo(cx - hx - tx, cy - hy - ty)
        lineTo(cx - hx + tx, cy - hy + ty)
        close()
    }
}

@Composable
fun CustomBottomNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(NavItem.Passwords, NavItem.Generator, NavItem.Settings)
    val selectedIndex = items.indexOfFirst { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }.coerceAtLeast(0)

    // Spring-animated pill position (in "item units": 0 or 1). No-bouncy
    // spring gives a smooth, stable landing without oscillation jank.
    val pillPosition = remember { Animatable(0f) }
    LaunchedEffect(selectedIndex) {
        pillPosition.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // 1. External container: positions the bar at the bottom and insets it
    //    from the screen edges. This "Bottom Panel" MUST be transparent.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp), 
        contentAlignment = Alignment.BottomCenter
    ) {
        // 2. The floating island capsule.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), // Прозорий темний фон для всієї капсули
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val horizontalPadding = 8.dp
                var capsuleHeight by remember { mutableStateOf(0.dp) }
                val tabWidth = (maxWidth - horizontalPadding * 2) / items.size

                // 3. The "Backlight" border indicator (restored as in original screenshot)
                Box(
                    modifier = Modifier
                        // GPU-only translation: no layout/measure passes during
                        // the slide, which keeps the indicator animation smooth.
                        .graphicsLayer {
                            translationX = with(density) { (horizontalPadding + tabWidth * pillPosition.value).toPx() }
                            translationY = with(density) { horizontalPadding.toPx() }
                        }
                        .width(tabWidth)
                        .height((capsuleHeight - horizontalPadding * 2).coerceAtLeast(0.dp))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary, // контурна рамка активної вкладки
                            shape = CircleShape
                        )
                )

                // 4. Tab content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { capsuleHeight = with(density) { it.height.toDp() } }
                        .padding(horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items.forEachIndexed { index, item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val isSelected = index == selectedIndex
                            TabContent(item = item, isSelected = isSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(item: NavItem, isSelected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        item.Icon(isSelected)
        AnimatedContent(
            targetState = isSelected,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(animationSpec = tween(220)) { it / 4 })
                    .togetherWith(
                        fadeOut(animationSpec = tween(140)) +
                                slideOutHorizontally(animationSpec = tween(140)) { -it / 4 }
                    )
            },
            label = "tab_label"
        ) { selected ->
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(item.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
