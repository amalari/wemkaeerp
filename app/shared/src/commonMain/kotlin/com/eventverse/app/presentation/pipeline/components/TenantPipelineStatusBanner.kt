package com.eventverse.app.presentation.pipeline.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.pipeline.FactoryFlowUiState
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tells the operator where the topology on screen came from.
 *
 * This matters because the canvas can legitimately show two different things: the tenant's
 * persisted configuration, or — when the server is unreachable — the preset template. Those
 * must never look identical, or a demo would present template data as if it were the
 * factory's real configuration.
 */
@Composable
fun TenantPipelineStatusBanner(
    state: FactoryFlowUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = state.isLoading) {
            StatusRow(
                accent = WeMadeColors.Primary,
                title = "Memuat konfigurasi alur tenant dari server…",
                subtitle = null
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = WeMadeColors.Primary
                )
            }
        }

        AnimatedVisibility(visible = !state.isLoading && state.isOfflineFallback) {
            StatusRow(
                accent = WeMadeColors.Error,
                title = "Menampilkan template preset, bukan konfigurasi tersimpan tenant ini.",
                subtitle = state.error
            ) {
                TextButton(onClick = onRetry) {
                    Text("Coba Lagi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(
            visible = !state.isLoading && !state.isOfflineFallback && state.error != null
        ) {
            StatusRow(
                accent = WeMadeColors.Error,
                title = state.error ?: "",
                subtitle = null
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(visible = state.statusMessage != null && state.error == null) {
            StatusRow(
                accent = WeMadeColors.Success,
                title = state.statusMessage ?: "",
                subtitle = null
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    accent: Color,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        trailing()
    }
}
