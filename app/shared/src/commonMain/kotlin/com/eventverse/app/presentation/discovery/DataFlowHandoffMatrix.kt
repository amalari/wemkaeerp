package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Peta Sambungan Port Terstruktur dalam format matriks tabular berbobot kolom tetap. */
@Composable
internal fun HandoffMatrixCard(
    map: DataFlowMap,
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()

    ClayCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Peta Sambungan Port",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Rute serah-terima payload antar port input dan output modul",
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                ClayBadge(text = "${map.handoffs.size} Jalur Sambungan", tint = WeMadeColors.Primary, fontSize = 10.sp)
            }

            HandoffMatrixTable(map = map, draft = draft)
        }
    }
}

/** Tabel matriks port berbobot tetap (0.35f | 0.30f | 0.35f) yang dapat di-embed di card atau accordion. */
@Composable
internal fun HandoffMatrixTable(
    map: DataFlowMap,
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        // Header Kolom Matriks (Bobot tetap: 0.35f | 0.30f | 0.35f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DARI MODUL (SUMBER)",
                style = typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.weight(0.35f)
            )
            Text(
                text = "KONTRAK DATA / PAYLOAD",
                style = typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.30f)
            )
            Text(
                text = "MENUJU MODUL (TUJUAN)",
                style = typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.35f)
            )
        }

        // Daftar Baris Sambungan
        map.handoffs.forEachIndexed { index, handoff ->
            HandoffMatrixRow(handoff = handoff, draft = draft)
            if (index < map.handoffs.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WeMadeColors.Border.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/** Satu baris matriks dengan lebar 3 kolom berbobot tetap agar panah & tag tidak zig-zag. */
@Composable
private fun HandoffMatrixRow(handoff: PortHandoff, draft: DiscoveryDraftUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kolom 1: Sumber
        Box(modifier = Modifier.weight(0.35f), contentAlignment = Alignment.CenterStart) {
            if (handoff.from != null) {
                ClayBadge(
                    text = handoff.from.displayName,
                    tint = sectionTint(handoff.from, draft),
                    fontSize = 10.sp
                )
            } else {
                ClayBadge(text = "Luar Sistem", tint = WeMadeColors.OnSurfaceMuted, fontSize = 10.sp)
            }
        }

        // Kolom 2: Payload Kontrak Port
        Row(
            modifier = Modifier.weight(0.30f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconArrowForward(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
            Spacer(Modifier.size(4.dp))
            ClayTag(
                text = if (handoff.isReference) "${handoff.payloadLabel} · acuan" else handoff.payloadLabel,
                tint = WeMadeColors.OnSurface
            )
            Spacer(Modifier.size(4.dp))
            IconArrowForward(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
        }

        // Kolom 3: Tujuan
        Box(modifier = Modifier.weight(0.35f), contentAlignment = Alignment.CenterEnd) {
            when {
                handoff.to == null -> ClayBadge(text = "Keluaran Akhir", tint = WeMadeColors.OnSurfaceMuted, fontSize = 10.sp)
                handoff.to == handoff.from -> ClayBadge(text = "Internal Modul", tint = WeMadeColors.OnSurfaceMuted, fontSize = 10.sp)
                else -> ClayBadge(
                    text = handoff.to.displayName,
                    tint = sectionTint(handoff.to, draft),
                    fontSize = 10.sp
                )
            }
        }
    }
}
