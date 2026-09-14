package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.TechPackUiState
import com.eventverse.app.presentation.techpack.toFormattedString
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TechPackDetailHeader(
    techPack: TechPack,
    state: TechPackUiState,
    canManage: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = techPack.styleName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayBadge(
                        text = techPack.styleCode.value,
                        tint = WeMadeColors.Primary
                    )
                    ClayBadge(
                        text = "Versi ${techPack.version}",
                        tint = WeMadeColors.Secondary
                    )
                    StatusBadge(status = techPack.status)
                }

                // Action Buttons
                if (canManage) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (techPack.isEditable) {
                            if (techPack.unresolvedLines.isNotEmpty()) {
                                ClayButton(
                                    text = "Cocokkan Bahan (${techPack.unresolvedLines.size})",
                                    onClick = { onEvent(TechPackUiEvent.ResolveMaterials) },
                                    style = ClayButtonStyle.Secondary
                                )
                            }

                            ClayButton(
                                text = "Rilis Tech Pack",
                                onClick = { onEvent(TechPackUiEvent.ReleaseTechPack) },
                                style = ClayButtonStyle.Accent,
                                enabled = techPack.bomLines.isNotEmpty() && techPack.blockingUnresolvedLines.isEmpty()
                            )
                        } else if (techPack.status == TechPackStatus.RELEASED) {
                            ClayButton(
                                text = "Buat Revisi (v${techPack.version + 1})",
                                onClick = { onEvent(TechPackUiEvent.ReviseTechPack) },
                                style = ClayButtonStyle.Primary
                            )
                        }

                        ClayButton(
                            text = "Arsipkan",
                            onClick = { onEvent(TechPackUiEvent.ArchiveTechPack(techPack.id)) },
                            style = ClayButtonStyle.Danger
                        )
                    }
                }
            }

            // Subtitle info row (Client, Source SPK, Total SAM, Total Ordered Qty)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (techPack.clientName.isNotBlank()) {
                        Text(
                            text = "Klien: ${techPack.clientName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    if (techPack.sourceSpkNumber.isNotBlank()) {
                        Text(
                            text = "Asal Sampling: ${techPack.sourceSpkNumber}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.Teal
                        )
                    }
                    Text(
                        text = "Total SAM: ${techPack.totalSamMinutes.toFormattedString()} menit",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                }

                // Version switcher if style has multiple versions
                if (state.versions.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Riwayat Versi:",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        state.versions.forEach { ver ->
                            val isCurrent = ver.id == techPack.id
                            Box(
                                modifier = Modifier
                                    .clickable { onEvent(TechPackUiEvent.SelectTechPack(ver.id)) }
                                    .claySurface(
                                        shape = ClayShapes.Pill,
                                        background = if (isCurrent) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                        outline = if (isCurrent) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                        offset = if (isCurrent) ClayOffset.Small else ClayOffset.Flat,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = ClaySpacing.Sm, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${ver.version}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }
            }

            // Warning Banner for blocking unresolved lines
            if (techPack.blockingUnresolvedLines.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .claySurface(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.WarningBg,
                            outline = WeMadeColors.Warning,
                            offset = ClayOffset.Small,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
                ) {
                    Text(
                        text = "⚠️ Terdapat ${techPack.blockingUnresolvedLines.size} bahan baku berstatus aset finansial yang belum dipetakan ke katalog Master Data. Petakan bahan sebelum merilis Tech Pack.",
                        fontSize = 11.sp,
                        color = WeMadeColors.Warning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
