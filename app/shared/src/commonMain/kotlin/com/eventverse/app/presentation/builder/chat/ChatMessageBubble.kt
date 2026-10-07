package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Gelembung chat: pesan user rata kanan (outline biru), agent rata kiri; agent punya tombol Hasil/Terapkan. */
@Composable
internal fun ChatMessageBubble(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (entry.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(Modifier.widthIn(max = 560.dp)) {
            ClayCard(
                containerColor = if (entry.isUser) WeMadeColors.SurfaceMuted else WeMadeColors.Surface,
                outlineColor = if (entry.isUser) WeMadeColors.Primary else WeMadeColors.Outline,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    if (!entry.isUser) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClayBadge(text = "Agent", tint = WeMadeColors.Success)
                            if (entry.hasPendingPatch) ClayBadge(text = "Usulan patch", tint = WeMadeColors.Warning)
                            if (entry.applied) ClayBadge(text = "Diterapkan", tint = WeMadeColors.Success)
                        }
                    }
                    Text(
                        text = entry.text,
                        style = typography.bodyMedium,
                        color = WeMadeColors.OnSurface
                    )
                    entry.questions.forEach { q ->
                        Text(
                            text = (if (q.answered) "[x] " else "[ ] ") + q.text,
                            style = typography.bodySmall,
                            color = if (q.answered) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurface
                        )
                    }
                    entry.summary.forEach { line ->
                        Text(
                            text = "• $line",
                            style = typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    if (!entry.isUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                            ClayButton(
                                text = "Hasil",
                                onClick = onShowResult,
                                style = ClayButtonStyle.Secondary,
                                leading = { IconLayers(Modifier.size(14.dp)) }
                            )
                            if (entry.hasPendingPatch) {
                                ClayButton(
                                    text = if (busy) "Memproses…" else "Terapkan",
                                    enabled = !busy,
                                    onClick = onApply
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
