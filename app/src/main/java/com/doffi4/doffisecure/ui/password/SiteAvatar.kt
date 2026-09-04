package com.doffi4.doffisecure.ui.password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Circular avatar that shows the service's favicon when available, otherwise
 * falls back to the first letter of the display name.
 */
@Composable
fun SiteAvatar(
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    faviconUrl: String = "",
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (faviconUrl.isNotBlank()) {
            val context = LocalContext.current
            // One request object per URL instead of rebuilding (and re-comparing)
            // it on every row recomposition while scrolling.
            val model = remember(faviconUrl) {
                ImageRequest.Builder(context)
                    .data(faviconUrl)
                    .size(160)
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .size(size * 0.9f)
                    .clip(CircleShape)
            )
        }
        // Fallback letter stays behind the image (or alone if image fails).
        Text(
            text = displayName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}