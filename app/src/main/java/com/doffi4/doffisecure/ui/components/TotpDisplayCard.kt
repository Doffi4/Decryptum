package com.doffi4.doffisecure.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.TotpConfig
import com.doffi4.doffisecure.security.totp.TotpGenerator
import kotlinx.coroutines.delay

@Composable
fun TotpDisplayCard(
    totpSecret: String,
    onCopyCode: (String) -> Unit,
    onDeleteTotp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = remember(totpSecret) {
        TotpGenerator.parseOtpAuth(totpSecret)
    }

    if (config == null) return

    // Live clock ticks every 200ms
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(200)
        }
    }

    val currentSeconds = currentTimeMillis / 1000L
    val code = remember(currentSeconds / config.periodSeconds, config) {
        TotpGenerator.generateTotp(config, currentSeconds)
    }
    val remainingSeconds = TotpGenerator.getRemainingSeconds(config.periodSeconds, currentSeconds)
    val progressFraction = TotpGenerator.getRemainingFraction(config.periodSeconds, currentTimeMillis)

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "totpProgress"
    )

    val timerColor by animateColorAsState(
        targetValue = when {
            remainingSeconds <= 3 -> MaterialTheme.colorScheme.error
            remainingSeconds <= 6 -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 300),
        label = "totpColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.totp_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onDeleteTotp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.totp_delete_title),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Code & Animated Circular Timer Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = TotpGenerator.formatCode(code),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Optional label/account name
                    val labelText = listOfNotNull(config.issuer, config.accountName)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")

                    if (labelText.isNotEmpty()) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { onCopyCode(code) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.totp_copy_code))
                }

                // Circular Countdown Timer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp)
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    Canvas(modifier = Modifier.size(42.dp)) {
                        val strokeWidth = 3.5.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                        // Background Track
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth)
                        )

                        // Active Progress Arc
                        drawArc(
                            color = timerColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Text(
                        text = "$remainingSeconds",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = timerColor
                    )
                }
            }

            // Metadata footer
            Text(
                text = stringResource(
                    R.string.totp_meta_format,
                    config.periodSeconds,
                    config.algorithm.name,
                    config.digits
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
