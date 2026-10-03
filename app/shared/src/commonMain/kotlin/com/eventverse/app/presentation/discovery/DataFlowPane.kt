package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.jetbrains.compose.resources.painterResource

/**
 * `DataFlowPane` (plan §4): peta aliran data antar modul aktif — input tiap modul **dari modul
 * mana**, outputnya **apa** dan **masuk ke modul mana**. Sambungan dihitung dari kontrak port
 * domain lewat [buildDataFlowMap]; pane ini hanya menggambar.
 */
@Composable
fun DataFlowPane(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val map = remember(draft) { buildDataFlowMap(draft) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayBadge("${map.flows.size} Modul Aktif", WeMadeColors.Success, dot = true)
            ClayBadge("${map.connectionCount} Sambungan Port", WeMadeColors.Primary)
            ClayBadge("${map.externalInputCount} Input Eksternal", WeMadeColors.Info)
            ClayBadge("${map.endOutputCount} Keluaran Akhir", WeMadeColors.OnSurfaceMuted)
        }

        ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Text(
                    text = "Peta Sambungan Port",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                map.handoffs.forEach { handoff -> HandoffRow(handoff, draft) }
            }
        }

        Text(
            text = "Rincian per Modul",
            style = typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        map.flows.forEach { flow -> ModuleFlowCard(flow, draft) }
    }
}

/** Satu baris peta: [modul sumber] →(payload)→ [modul tujuan]. */
@Composable
private fun HandoffRow(handoff: PortHandoff, draft: DiscoveryDraftUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (handoff.from != null) {
            ClayBadge(
                text = handoff.from.displayName,
                tint = sectionTint(handoff.from, draft),
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 10.sp
            )
        } else {
            ClayBadge(text = "Luar Sistem", tint = WeMadeColors.OnSurfaceMuted, fontSize = 10.sp)
        }
        IconArrowForward(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
        ClayTag(
            text = if (handoff.isReference) "${handoff.payloadLabel} · rujukan" else handoff.payloadLabel,
            tint = WeMadeColors.OnSurface
        )
        IconArrowForward(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
        if (handoff.to != null) {
            ClayBadge(
                text = handoff.to.displayName,
                tint = sectionTint(handoff.to, draft),
                fontSize = 10.sp
            )
        } else {
            ClayBadge(text = "Keluaran Akhir", tint = WeMadeColors.OnSurfaceMuted, fontSize = 10.sp)
        }
    }
}

/** Kartu rincian satu modul: header identitas + baris Masuk (dari mana) dan Keluar (ke mana). */
@Composable
private fun ModuleFlowCard(flow: ModuleDataFlow, draft: DiscoveryDraftUi) {
    val typography = rememberClayTypography()
    val style = resolveSectionStyle(flow.module.section, draft)
    val iconRenderer = resolveModuleIcon(flow.module)
    val clayRes = resolveClayAsset(flow.module)

    ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(style.background, ClayShapes.Tile),
                    contentAlignment = Alignment.Center
                ) {
                    if (clayRes != null && flow.module.active) {
                        Image(
                            painter = painterResource(clayRes),
                            contentDescription = flow.module.displayName,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        iconRenderer(Modifier.size(18.dp), style.color)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = flow.module.displayName,
                        style = typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = flow.module.slot ?: flow.module.section,
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ClayBadge(
                    text = flow.slotLabel ?: flow.module.slot ?: flow.module.section,
                    tint = style.color,
                    fontSize = 10.sp
                )
            }

            if (flow.incoming.isEmpty()) {
                MutedIoRow(label = "Masuk", text = "titik masuk alur — tanpa port masuk")
            } else {
                flow.incoming.forEach { IoRow(label = "Masuk", handoff = it, tint = style.color, draft = draft) }
            }
            if (flow.outgoing.isEmpty()) {
                MutedIoRow(label = "Keluar", text = "tanpa port keluar")
            } else {
                flow.outgoing.forEach { IoRow(label = "Keluar", handoff = it, tint = style.color, draft = draft) }
            }
        }
    }
}


/** Satu baris port pada kartu modul: label + payload + relasi ke modul pasangannya. */
@Composable
private fun IoRow(label: String, handoff: PortHandoff, tint: Color, draft: DiscoveryDraftUi) {
    val typography = rememberClayTypography()
    val isOutgoing = label == "Keluar"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(46.dp),
            style = typography.bodySmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted
        )
        ClayTag(
            text = if (handoff.isReference) "${handoff.payloadLabel} · rujukan" else handoff.payloadLabel,
            tint = tint
        )

        when {
            !isOutgoing && handoff.from != null -> {
                Text(
                    text = "dari",
                    style = typography.bodySmall,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1
                )
                ClayBadge(
                    text = handoff.from.displayName,
                    tint = sectionTint(handoff.from, draft),
                    fontSize = 10.sp
                )
            }
            !isOutgoing -> ClayBadge(text = "Luar Sistem", tint = WeMadeColors.OnSurfaceMuted, fontSize = 10.sp)
            handoff.to == null -> Text(
                text = "keluaran akhir — belum dipakai modul lain",
                style = typography.bodySmall,
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            handoff.to == handoff.from -> ClayBadge(
                text = "dipakai internal modul ini",
                tint = WeMadeColors.OnSurfaceMuted,
                fontSize = 10.sp
            )
            else -> {
                Text(
                    text = "ke",
                    style = typography.bodySmall,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1
                )
                ClayBadge(
                    text = handoff.to.displayName,
                    tint = sectionTint(handoff.to, draft),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/** Baris IO tersederhana untuk kondisi tanpa port (titik masuk alur / tanpa keluaran). */
@Composable
private fun MutedIoRow(label: String, text: String) {
    val typography = rememberClayTypography()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(46.dp),
            style = typography.bodySmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Text(
            text = text,
            style = typography.bodySmall,
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun sectionTint(module: DiscoveryModuleUi, draft: DiscoveryDraftUi) =
    resolveSectionStyle(module.section, draft).color

