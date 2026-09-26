package com.eventverse.app.presentation.qc.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.sampling.QcInspectorContribution
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Rekap "siapa memeriksa berapa" pada satu SPK.
 *
 * Di sampling isinya hampir selalu satu baris — tidak berguna, tapi juga tidak mengganggu.
 * Nilainya baru muncul di produksi massal, saat satu lot dibagi ke beberapa petugas dan
 * pertanyaan "sisa berapa, dikerjakan siapa" ditanyakan berkali-kali sehari.
 */
@Composable
fun QcInspectorTallyPanel(
    contributions: List<QcInspectorContribution>,
    inspectedQty: Int,
    targetQty: Int,
    modifier: Modifier = Modifier
) {
    val remaining = (targetQty - inspectedQty).coerceAtLeast(0)

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        containerColor = WeMadeColors.SurfaceMuted,
        outlineColor = WeMadeColors.OutlineSoft,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progres inspeksi SPK ini",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(ClaySpacing.Sm))
            ClayBadge(
                text = "$inspectedQty dari $targetQty pcs",
                tint = if (remaining == 0) WeMadeColors.Success else WeMadeColors.Warning,
                dot = remaining > 0
            )
        }

        if (contributions.isEmpty()) {
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))
            Text(
                text = "Belum ada yang memeriksa SPK ini.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            return@ClayCard
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            contributions.forEach { contribution ->
                QcContributionRow(contribution = contribution)
            }
        }
    }
}

@Composable
private fun QcContributionRow(contribution: QcInspectorContribution) {
    val reworkQty = (contribution.qtyPcs - contribution.passedQty).coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        QcInspectorAvatar(name = contribution.inspectorName)

        Text(
            text = contribution.inspectorName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.weight(1f))

        if (reworkQty > 0) {
            ClayBadge(text = "$reworkQty rework", tint = WeMadeColors.Error)
        }
        ClayBadge(text = "${contribution.qtyPcs} pcs", tint = WeMadeColors.Primary)
    }
}

/**
 * Inisial, bukan foto. Belum ada penyimpanan avatar di sistem ini, dan lingkaran inisial
 * berwarna sudah cukup untuk membedakan dua-tiga petugas di satu SPK secara sekilas.
 */
@Composable
private fun QcInspectorAvatar(name: String) {
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clayFlat(
                shape = ClayShapes.Pill,
                background = WeMadeColors.PrimaryContainer,
                outline = WeMadeColors.Outline
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Primary
        )
    }
}
