package com.doffi4.doffisecure.ui.password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.security.FaviconRequest

/**
 * Circular avatar that shows:
 *  1. A router icon ([Icons.Default.Router]) for local IPs and LAN devices (no network requests).
 *  2. The service's favicon via [FaviconRequest] waterfall when available and enabled.
 *  3. A letter monogram fallback only when no favicon is loaded or available.
 */
@Composable
fun SiteAvatar(
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    faviconUrl: String = "",
    isLocalNetwork: Boolean = false,
    apexDomain: String? = null,
    enabled: Boolean = true,
    forceRefresh: Boolean = false,
) {
    val isLocal = isLocalNetwork || DomainUtils.isLocalAddress(faviconUrl)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isLocal) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLocal) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.55f)
            )
        } else {
            var isImageLoaded by remember(faviconUrl, apexDomain, forceRefresh) {
                mutableStateOf(false)
            }

            if (enabled && faviconUrl.isNotBlank()) {
                val context = LocalContext.current
                val model = remember(faviconUrl, apexDomain, forceRefresh) {
                    ImageRequest.Builder(context)
                        .data(FaviconRequest(host = faviconUrl, apexDomain = apexDomain, forceRefresh = forceRefresh))
                        .size(160)
                        .build()
                }
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    onState = { state ->
                        isImageLoaded = state is AsyncImagePainter.State.Success
                    },
                    modifier = Modifier
                        .size(size * 0.9f)
                        .clip(CircleShape)
                )
            }

            if (!isImageLoaded) {
                Text(
                    text = displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}