package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Label badge asal ringkasan: AI vs fallback deterministik (terlihat di UI, TRD-BLDR-001 FR-7). */
internal fun briefSourceLabel(source: BriefSource): String = when (source) {
    BriefSource.AI -> "AI"
    BriefSource.DETERMINISTIC -> "Disusun otomatis dari data"
}

/** Keterangan satu Tahap: penanda opsional dan loop rework (`reworkTo`). Murni, mudah diuji. */
internal fun stageCaption(stage: StageSketch): String? = listOfNotNull(
    "opsional".takeIf { stage.optional },
    stage.reworkTo?.let { "rework ke $it" }
).joinToString(" · ").ifEmpty { null }

/** Satu tag keterangan Tahap; [rework] = tag loop rework (diberi warna peringatan). */
internal data class StageTag(val text: String, val rework: Boolean)

/** Tag terpisah per keterangan Tahap (opsional, rework): masing-masing pendek supaya bisa turun baris utuh. Murni. */
internal fun stageTags(stage: StageSketch): List<StageTag> = listOfNotNull(
    StageTag("opsional", rework = false).takeIf { stage.optional },
    stage.reworkTo?.let { StageTag("rework ke $it", rework = true) }
)

/**
 * Ringkasan BRIEF satu modul (TRD-BLDR-001 FR-7): judul, Tahap berurutan, fitur, badge sumber. Yang terbaru tampil
 * penuh; BRIEF lama dilipat jadi satu baris yang bisa dibuka ([expandedByDefault] = false).
 */
@Composable
internal fun ChatBriefBubble(entry: BuilderChatEntry, expandedByDefault: Boolean, modifier: Modifier = Modifier) {
    val brief = entry.brief ?: return
    val typography = rememberClayTypography()
    var expanded by remember(entry.id, expandedByDefault) { mutableStateOf(expandedByDefault) }

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayBadge(text = "Ringkasan", tint = WeMadeColors.Info)
                ClayBadge(
                    text = briefSourceLabel(brief.source),
                    tint = if (brief.source == BriefSource.AI) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!expanded) Text(
                    text = "lama - ketuk untuk membuka",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (expanded) {
                Text(entry.text, style = typography.bodyMedium, color = WeMadeColors.OnSurface)
                if (brief.stages.isNotEmpty()) {
                    Text("Tahap", style = typography.labelSmall, fontWeight = FontWeight.Bold, color = WeMadeColors.InfoDark)
                    brief.stages.forEachIndexed { i, stage -> StageRow(i + 1, stage) }
                }
                if (brief.features.isNotEmpty()) {
                    Text("Fitur", style = typography.labelSmall, fontWeight = FontWeight.Bold, color = WeMadeColors.InfoDark)
                    brief.features.forEach { f ->
                        Text("- $f", style = typography.bodySmall, color = WeMadeColors.OnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(number: Int, stage: StageSketch) {
    val typography = rememberClayTypography()
    val tags = stageTags(stage)
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTag(text = number.toString(), tint = WeMadeColors.Primary)
            Text(
                text = stage.name,
                style = typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        // Dua tag terpisah: di kolom sempit turun baris utuh, bukan satu tag yang membungkus teksnya jadi 3 baris (temuan 10a).
        if (tags.isNotEmpty()) ClayFlowRow(spacing = ClaySpacing.Xs) {
            tags.forEach { t -> ClayTag(text = t.text, tint = if (t.rework) WeMadeColors.Warning else WeMadeColors.OnSurfaceMuted) }
        }
    }
}

/** Gelembung galat untuk pesan yang ditolak parser (kind tak dikenal / BRIEF rusak). */
@Composable
internal fun ChatParseErrorBubble(message: String, modifier: Modifier = Modifier) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Error,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Text(message, style = rememberClayTypography().bodySmall, color = WeMadeColors.Error)
    }
}
