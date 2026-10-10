package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import org.jetbrains.compose.resources.painterResource

/** Kartu satu stasiun kerja pada rel alur nilai. */
@Composable
internal fun StationNodeCard(
    step: Int,
    flow: ModuleDataFlow,
    isSelected: Boolean,
    style: ModuleSectionStyle,
    clayRes: org.jetbrains.compose.resources.DrawableResource?,
    iconRenderer: @Composable (Modifier, Color) -> Unit,
    onClick: () -> Unit
) {
    val typography = rememberClayTypography()
    val stepLabel = if (step < 10) "0$step" else "$step"

    Box(
        modifier = Modifier
            .width(170.dp)
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) style.background else WeMadeColors.Surface,
                shape = ClayShapes.Tile
            )
            .padding(ClaySpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Baris Atas: Nomor Tahap & Indikator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayBadge(
                    text = "TAHAP $stepLabel",
                    tint = if (isSelected) style.color else WeMadeColors.OnSurfaceMuted,
                    fontSize = 9.sp
                )
                Box(Modifier.size(8.dp).background(style.color, CircleShape))
            }

            // Tengah: Miniatur 3D / Vektor + Nama
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(style.background, ClayShapes.Tile),
                    contentAlignment = Alignment.Center
                ) {
                    if (clayRes != null && flow.module.active) {
                        Image(
                            painter = painterResource(clayRes),
                            contentDescription = flow.module.displayName,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        iconRenderer(Modifier.size(16.dp), style.color)
                    }
                }

                Text(
                    text = flow.module.displayName,
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bawah: Label Seksi
            Text(
                text = style.title,
                style = typography.bodySmall,
                fontSize = 9.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Kabel penghubung visual antar stasiun alur. */
@Composable
internal fun StationConnectorBridge(
    payload: String,
    isReference: Boolean
) {
    Column(
        modifier = Modifier.padding(horizontal = ClaySpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ClayTag(
            text = if (isReference) "$payload · acuan" else payload,
            tint = WeMadeColors.OnSurface
        )
        IconArrowForward(
            modifier = Modifier.size(14.dp),
            color = WeMadeColors.Primary
        )
    }
}

/** Kartu inspeksi interaktif yang membedah masukan dan keluaran stasiun yang sedang dipilih. */
@Composable
internal fun StationInspectorCard(
    flow: ModuleDataFlow,
    stageIndex: Int,
    totalStages: Int,
    draft: DiscoveryDraftUi
) {
    val typography = rememberClayTypography()
    val style = resolveSectionStyle(flow.module.section, draft)
    val clayRes = resolveClayAsset(flow.module)
    val iconRenderer = resolveModuleIcon(flow.module)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.5f), ClayShapes.Tile)
            .padding(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Header Inspector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(style.background, ClayShapes.Tile),
                        contentAlignment = Alignment.Center
                    ) {
                        if (clayRes != null && flow.module.active) {
                            Image(
                                painter = painterResource(clayRes),
                                contentDescription = flow.module.displayName,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            iconRenderer(Modifier.size(18.dp), style.color)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = flow.module.displayName,
                            style = typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${style.title} · Slot: ${flow.slotLabel ?: flow.module.slot ?: "-"}",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                ClayBadge(
                    text = "Tahap $stageIndex dari $totalStages di Rantai Alur",
                    tint = style.color,
                    fontSize = 10.sp
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(WeMadeColors.Border.copy(alpha = 0.35f)))

            // 2 Kolom: Sumber Masukan vs Tujuan Keluaran Stasiun
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                // Kolom Masukan
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DATA DITERIMA (UPSTREAM INPUT)",
                        style = typography.bodySmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    if (flow.incoming.isEmpty()) {
                        Text(
                            text = "Titik inisiasi awal - menerima pesanan/draf dari luar sistem.",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    } else {
                        flow.incoming.forEach { h ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ClayTag(h.payloadLabel, tint = style.color)
                                Text("dari", fontSize = 9.sp, color = WeMadeColors.OnSurfaceMuted)
                                ClayBadge(
                                    h.from?.displayName ?: "Luar Sistem",
                                    tint = h.from?.let { sectionTint(it, draft) } ?: WeMadeColors.OnSurfaceMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                // Kolom Keluaran
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DATA DISALURKAN (DOWNSTREAM OUTPUT)",
                        style = typography.bodySmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    if (flow.outgoing.isEmpty()) {
                        Text(
                            text = "Keluaran akhir rantai alur.",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    } else {
                        flow.outgoing.forEach { h ->
                            val isEnd = h.to == null
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ClayTag(h.payloadLabel, tint = style.color)
                                Text("ke", fontSize = 9.sp, color = WeMadeColors.OnSurfaceMuted)
                                ClayBadge(
                                    if (isEnd) "Keluaran Akhir" else h.to?.displayName ?: "",
                                    tint = if (isEnd) WeMadeColors.OnSurfaceMuted else h.to?.let { sectionTint(it, draft) } ?: WeMadeColors.OnSurfaceMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
