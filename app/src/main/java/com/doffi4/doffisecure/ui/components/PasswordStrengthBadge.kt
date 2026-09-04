package com.doffi4.doffisecure.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doffi4.doffisecure.domain.model.PasswordStrength

/** Fill color for each strength level. */
private val PasswordStrength.color: Color
    get() = when (this) {
        PasswordStrength.WEAK -> Color(0xFFD32F2F)
        PasswordStrength.MEDIUM -> Color(0xFFF57C00)
        PasswordStrength.STRONG -> Color(0xFF388E3C)
        PasswordStrength.VERY_STRONG -> Color(0xFF1B5E20)
    }

/**
 * Compact rounded chip showing password strength as 4 proportional segments
 * plus a small label ("Слабый".."Очень сильный").
 *
 * The whole chip - all four segments - is ALWAYS rendered, so an empty or very
 * weak password still shows the full structure (users can see how many levels
 * exist). Segment colors ease to their targets and the label fades/slides in,
 * so typing animates the strength smoothly instead of popping.
 */
@Composable
fun PasswordStrengthBadge(
    password: String,
    modifier: Modifier = Modifier,
) {
    val strength = remember(password) { PasswordStrength.fromPassword(password) }
    val filled = if (password.isBlank()) 0 else strength.level

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 4 proportional segments - always drawn. Filled ones tint toward
            // the current level color; the rest keep a quiet "empty" color.
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.width(64.dp)
            ) {
                repeat(4) { index ->
                    val target = if (index < filled) strength.color
                    else MaterialTheme.colorScheme.surfaceVariant
                    val segmentColor by animateColorAsState(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 320),
                        label = "segment_fill_$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(segmentColor)
                    )
                }
            }
            // Label appears smoothly the moment a level is assigned.
            AnimatedVisibility(
                visible = filled > 0,
                enter = fadeIn(animationSpec = tween(200)) +
                    slideInVertically(animationSpec = tween(200)) { it / 2 },
                exit = fadeOut(animationSpec = tween(140)) +
                    slideOutVertically(animationSpec = tween(140)) { -it / 2 }
            ) {
                val labelColor by animateColorAsState(
                    targetValue = strength.color,
                    animationSpec = tween(durationMillis = 260),
                    label = "labelColor"
                )
                Text(
                    text = strength.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor
                )
            }
        }
    }
}