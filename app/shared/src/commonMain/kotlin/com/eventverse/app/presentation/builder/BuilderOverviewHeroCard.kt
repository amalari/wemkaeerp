package com.eventverse.app.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.IconCheckCircle
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconZap
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kartu Utama Production Deployment ala Vercel:
 * Mockup visual kanvas mini di sisi kiri dan metadata deployment aktif di sisi kanan.
 */
@Composable
internal fun BuilderProductionDeploymentHero(
    ui: BuilderOverviewUi,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()

    ClayCard(modifier = modifier.fillMaxWidth(), shape = ClayShapes.Card, containerColor = WeMadeColors.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Box(Modifier.size(10.dp).background(WeMadeColors.Success, CircleShape))
                Text(
                    text = "Production Deployment",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(text = "READY · LIVE", tint = WeMadeColors.Success, fontSize = 10.sp)
            }
            Text(
                text = "Rev ${ui.activeRevision ?: 1} · Deployment #${ui.activeNumber ?: 1}",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kolom Kiri: Mini Browser / Canvas Preview
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                    .border(ClayBorder.Hairline, WeMadeColors.Border, ClayShapes.Tile)
                    .padding(ClaySpacing.Md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).background(WeMadeColors.Error, CircleShape))
                            Box(Modifier.size(8.dp).background(WeMadeColors.Warning, CircleShape))
                            Box(Modifier.size(8.dp).background(WeMadeColors.Success, CircleShape))
                        }
                        Box(
                            modifier = Modifier
                                .background(WeMadeColors.Surface, ClayShapes.Pill)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "https://${ui.slug}.wemakeerp.com",
                                style = typography.bodySmall,
                                fontSize = 10.sp,
                                color = WeMadeColors.OnSurfaceMuted,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WeMadeColors.Surface, ClayShapes.Tile)
                            .padding(ClaySpacing.Sm),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniStageNode("Procurement", WeMadeColors.Info)
                        IconArrowForward(Modifier.size(12.dp), color = WeMadeColors.OnSurfaceDisabled)
                        MiniStageNode("Cutting", WeMadeColors.Primary)
                        IconArrowForward(Modifier.size(12.dp), color = WeMadeColors.OnSurfaceDisabled)
                        MiniStageNode("Sewing", WeMadeColors.Accent)
                        IconArrowForward(Modifier.size(12.dp), color = WeMadeColors.OnSurfaceDisabled)
                        MiniStageNode("QC & Pack", WeMadeColors.Success)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: 100% Normal · Sinkronisasi Real-time",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.Success,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Engine: KMP + Ktor",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            // Kolom Kanan: Rincian Metadata & Tombol Aksi
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                DeploymentInfoRow(label = "Domain Aktif", value = "${ui.slug}.wemakeerp.com", isLink = true)
                DeploymentInfoRow(
                    label = "Domain Pack",
                    value = "${ui.domainPack.uppercase()} (v${ui.domainPackVersion ?: 1})",
                    sub = "Pack bawaan platform standar konveksi"
                )
                DeploymentInfoRow(
                    label = "Status Deployment",
                    value = ui.activeStatus ?: "IMPORTED",
                    badgeTint = statusTint(ui.activeStatus ?: "ACTIVE")
                )
                DeploymentInfoRow(label = "Lingkungan", value = "Production Cloud · Multiplatform")

                Spacer(Modifier.height(ClaySpacing.Xs))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Kelola Modul",
                        onClick = { onNavigate("modules") },
                        style = ClayButtonStyle.Primary,
                        leading = { IconLayers(Modifier.size(14.dp), color = Color.White) }
                    )
                    ClayButton(
                        text = "Riwayat Deploy",
                        onClick = { onNavigate("deployments") },
                        style = ClayButtonStyle.Secondary,
                        leading = { IconZap(Modifier.size(14.dp), color = WeMadeColors.OnSurface) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStageNode(name: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape)
                .border(ClayBorder.Hairline, tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconCheckCircle(Modifier.size(10.dp), color = tint)
        }
        Text(text = name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface, maxLines = 1)
    }
}

@Composable
private fun DeploymentInfoRow(
    label: String,
    value: String,
    sub: String? = null,
    isLink: Boolean = false,
    badgeTint: Color? = null
) {
    val typography = rememberClayTypography()
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = label, style = typography.bodySmall, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            Text(
                text = value,
                style = typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isLink) WeMadeColors.Primary else WeMadeColors.OnSurface
            )
            badgeTint?.let { tint -> ClayBadge(text = value, tint = tint, fontSize = 9.sp) }
        }
        sub?.let {
            Text(text = it, style = typography.bodySmall, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
        }
    }
}
