package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Komponen UI untuk usulan layar (Jalur A, plan §4):
 * - [ProposalSourceBadge]: lencana asal usulan (Pack, Deterministik, Agent)
 * - [ProposalRationaleRow]: baris alasan pemilihan jenis tampilan ("Dipilih karena …")
 * - [ProposalIncompleteWarning]: jalur aman bila interactive belum tersedia / proposal hilang
 * - [ModuleProposalSummaryCard]: kartu pratinjau proposal per modul sebelum draf dikunci di wizard
 *
 * Murni bahasa Clay, nol literal warna — semua warna dari [WeMadeColors].
 */

@Composable
fun ProposalSourceBadge(
    source: ProposalSource,
    modifier: Modifier = Modifier
) {
    val tint = when (source) {
        is ProposalSource.Pack -> WeMadeColors.Primary
        is ProposalSource.Deterministic -> WeMadeColors.Teal
        is ProposalSource.Agent -> WeMadeColors.Purple
    }
    ClayTag(
        text = source.displayName,
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun ProposalRationaleRow(
    rationale: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    Text(
        text = rationale,
        style = MaterialTheme.typography.labelSmall,
        color = WeMadeColors.OnSurfaceMuted,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun ProposalIncompleteWarning(
    widget: String,
    rationale: String? = null,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.SurfaceMuted,
        outlineColor = WeMadeColors.OutlineSoft,
        borderWidth = ClayBorder.Hairline,
        shape = ClayShapes.Card
    ) {
        Column(
            modifier = Modifier.padding(ClaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pratinjau Belum Lengkap",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = widget,
                    tint = WeMadeColors.Warning,
                    dot = true
                )
            }
            Text(
                text = "Layar untuk modul ini belum memiliki tata letak interaktif (jenis '$widget').",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            if (!rationale.isNullOrBlank()) {
                Text(
                    text = "Alasan perancangan: \"$rationale\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ModuleProposalSummaryCard(
    screen: DiscoveryScreenUi,
    moduleName: String,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.OutlineSoft,
        borderWidth = ClayBorder.Hairline,
        shape = ClayShapes.Card
    ) {
        Column(
            modifier = Modifier.padding(ClaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = moduleName,
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    screen.source?.let { src ->
                        ProposalSourceBadge(source = src)
                    }
                    val kind = WidgetKind.fromCode(screen.widget)
                    ClayBadge(
                        text = kind?.displayName ?: screen.widget,
                        tint = when (kind) {
                            WidgetKind.FORM, WidgetKind.TABLE -> WeMadeColors.Primary
                            WidgetKind.KANBAN -> WeMadeColors.Accent
                            WidgetKind.DASHBOARD -> WeMadeColors.Success
                            WidgetKind.CUSTOM_SCREEN -> WeMadeColors.Purple
                            else -> WeMadeColors.OnSurfaceMuted
                        },
                        dot = true
                    )
                }
            }

            screen.rationale?.takeIf { it.isNotBlank() }?.let { r ->
                ProposalRationaleRow(rationale = r, maxLines = 3)
            }

            // Ringkasan entitas & status (bila ada usulan entitas)
            val entity = screen.proposal?.entity
            if (entity != null) {
                ClayFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    spacing = ClaySpacing.Xs
                ) {
                    ClayTag(
                        text = "${entity.fields.size} field",
                        tint = WeMadeColors.Secondary
                    )
                    val statusField = entity.statusField?.let { sf ->
                        entity.fields.firstOrNull { it.key == sf }
                    }
                    if (statusField != null && statusField.options.isNotEmpty()) {
                        val flow = statusField.options.take(4).joinToString(" → ") +
                            if (statusField.options.size > 4) " (+${statusField.options.size - 4})" else ""
                        ClayTag(
                            text = "Status: $flow",
                            tint = WeMadeColors.Teal
                        )
                    }
                    if (screen.interactive != null) {
                        ClayTag(
                            text = "Siap dimainkan",
                            tint = WeMadeColors.Success
                        )
                    }
                }
            }
        }
    }
}
