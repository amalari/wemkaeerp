package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.process.FlowPhase
import com.eventverse.app.domain.process.PhaseTaggableStage
import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.clayDashedOutline
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tahap wajib yang dipilah per fase (Cuci, Setrika).
 *
 * Desain bersih & terpadu:
 * - Ketinggian dan baseline sejajar sempurna dengan [StagePill] standar.
 * - Penomoran langkah (step) tetap berjalan normal baik dipilih Sampling, Produksi, maupun keduanya.
 * - Tidak ada label tambahan yang mengacaukan tampilan (cukup toggle [Sampling] dan [Produksi]).
 * - Coretan dan outline putus-putus hanya aktif jika kedua fase dilepas (ditiadakan total).
 */
@Composable
internal fun PhaseTaggedStagePill(
    step: Int,
    label: String,
    stage: PhaseTaggableStage,
    tags: StagePhaseTags,
    isLocked: Boolean,
    onToggle: (PhaseTaggableStage, FlowPhase) -> Unit
) {
    val inSampling = tags.appliesTo(stage, FlowPhase.SAMPLING)
    val inProduction = tags.appliesTo(stage, FlowPhase.PRODUCTION)
    val isCompletelySkipped = !inSampling && !inProduction

    val surfaceModifier = if (isCompletelySkipped) {
        Modifier.clayDashedOutline(
            shape = ClayShapes.Chip,
            background = WeMadeColors.Surface,
            outline = WeMadeColors.Border,
            borderWidth = ClayBorder.Hairline,
            dashLength = 4.dp,
            gapLength = 3.dp
        )
    } else {
        Modifier.clayFlat(
            shape = ClayShapes.Chip,
            background = WeMadeColors.SurfaceMuted,
            outline = WeMadeColors.Outline,
            borderWidth = ClayBorder.Medium
        )
    }

    Row(
        modifier = surfaceModifier
            .padding(
                start = ClaySpacing.Sm,
                end = ClaySpacing.Md,
                top = ClaySpacing.Sm,
                bottom = ClaySpacing.Sm
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // Step bubble: penomoran tetap konsisten selama aktif di salah satu fase
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(ClayShapes.Pill)
                .background(if (isCompletelySkipped) WeMadeColors.Border else WeMadeColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCompletelySkipped) "-" else step.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompletelySkipped) WeMadeColors.OnSurfaceMuted else WeMadeColors.Surface
            )
        }

        // Label tahap: bersih tanpa coretan selama masih ada fase yang aktif
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCompletelySkipped) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurface,
            textDecoration = if (isCompletelySkipped) TextDecoration.LineThrough else null,
            maxLines = 1
        )

        // Pemisah vertikal tipis
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(WeMadeColors.Border)
        )

        // Toggle fase terpadu: Sampling & Produksi saja (tanpa label ekstra)
        Row(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhaseCompactToggle(
                text = FlowPhase.SAMPLING.label,
                tint = WeMadeColors.Primary,
                active = inSampling,
                enabled = !isLocked,
                onToggle = { onToggle(stage, FlowPhase.SAMPLING) }
            )
            PhaseCompactToggle(
                text = FlowPhase.PRODUCTION.label,
                tint = WeMadeColors.Accent,
                active = inProduction,
                enabled = !isLocked,
                onToggle = { onToggle(stage, FlowPhase.PRODUCTION) }
            )
        }
    }
}

@Composable
private fun PhaseCompactToggle(
    text: String,
    tint: Color,
    active: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val contentColor = if (active) tint else WeMadeColors.OnSurfaceMuted
    val bgColor = if (active) tint.copy(alpha = 0.12f) else WeMadeColors.Surface
    val outlineColor = if (active) tint.copy(alpha = 0.45f) else WeMadeColors.Border

    Row(
        modifier = Modifier
            .clip(ClayShapes.Chip)
            .clayFlat(
                shape = ClayShapes.Chip,
                background = bgColor,
                outline = outlineColor,
                borderWidth = ClayBorder.Hairline
            )
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            IconCheck(
                modifier = Modifier.size(9.dp),
                color = contentColor
            )
        }
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}
