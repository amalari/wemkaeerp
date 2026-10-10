package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import org.jetbrains.compose.resources.painterResource

/** Kartu modul dengan layout split 2-seksi internal (Port Masukan vs Port Keluaran). */
@Composable
internal fun ModuleFlowCard(
    flow: ModuleDataFlow,
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val style = resolveSectionStyle(flow.module.section, draft)
    val iconRenderer = resolveModuleIcon(flow.module)
    val clayRes = resolveClayAsset(flow.module)

    ClayCard(modifier = modifier, contentPadding = PaddingValues(ClaySpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Header Modul
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

            // Garis pembatas halus
            Box(Modifier.fillMaxWidth().height(1.dp).background(WeMadeColors.Border.copy(alpha = 0.35f)))

            // Layout Split: Masukan (Kiri) & Keluaran (Kanan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Kolom Masukan
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "MASUKAN (${flow.incoming.size})",
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    if (flow.incoming.isEmpty()) {
                        Text(
                            text = "Titik awal alur - tanpa port masuk",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    } else {
                        flow.incoming.forEach { handoff ->
                            FlowItemPill(
                                payload = handoff.payloadLabel,
                                isReference = handoff.isReference,
                                directionLabel = "dari",
                                counterpart = handoff.from?.displayName ?: "Luar Sistem",
                                counterpartTint = handoff.from?.let { sectionTint(it, draft) } ?: WeMadeColors.OnSurfaceMuted,
                                tagTint = style.color
                            )
                        }
                    }
                }

                // Kolom Keluaran
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "KELUARAN (${flow.outgoing.size})",
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    if (flow.outgoing.isEmpty()) {
                        Text(
                            text = "Tanpa port keluar",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    } else {
                        flow.outgoing.forEach { handoff ->
                            val isEnd = handoff.to == null
                            val isInternal = handoff.to == handoff.from
                            FlowItemPill(
                                payload = handoff.payloadLabel,
                                isReference = false,
                                directionLabel = "ke",
                                counterpart = when {
                                    isEnd -> "Keluaran Akhir"
                                    isInternal -> "Internal Modul"
                                    else -> handoff.to?.displayName ?: ""
                                },
                                counterpartTint = when {
                                    isEnd || isInternal -> WeMadeColors.OnSurfaceMuted
                                    else -> handoff.to?.let { sectionTint(it, draft) } ?: WeMadeColors.OnSurfaceMuted
                                },
                                tagTint = style.color
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Item baris port di dalam kartu modul (Payload Tag + arah + lawan modul). */
@Composable
private fun FlowItemPill(
    payload: String,
    isReference: Boolean,
    directionLabel: String,
    counterpart: String,
    counterpartTint: Color,
    tagTint: Color
) {
    val typography = rememberClayTypography()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayTag(
            text = if (isReference) "$payload · acuan" else payload,
            tint = tagTint
        )
        Text(
            text = directionLabel,
            style = typography.bodySmall,
            fontSize = 9.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        ClayBadge(
            text = counterpart,
            tint = counterpartTint,
            fontSize = 9.sp
        )
    }
}
