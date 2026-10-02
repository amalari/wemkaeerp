package com.eventverse.app.presentation.builder

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconActivity
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconDatabase
import com.eventverse.app.presentation.designsystem.IconGlobe
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Grid 4 Kartu KPI Ringkas Vercel-style. */
@Composable
internal fun BuilderMetricsGrid(ui: BuilderOverviewUi, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        MetricCard(
            title = "Modul Operasional",
            value = "8 Modul",
            subtitle = "Procurement, Cutting, Sewing, QC",
            badge = "100% Aktif",
            badgeColor = WeMadeColors.Success,
            icon = { IconLayers(Modifier.size(18.dp), color = WeMadeColors.Primary) },
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Kesehatan Pipeline",
            value = "100% Optimal",
            subtitle = "Nol hambatan alur produksi",
            badge = "HEALTHY",
            badgeColor = WeMadeColors.Success,
            icon = { IconActivity(Modifier.size(18.dp), color = WeMadeColors.Success) },
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Domain Pack",
            value = "${ui.domainPack.uppercase()} v${ui.domainPackVersion ?: 1}",
            subtitle = "Pola garmen standar pabrik",
            badge = "Standard Pack",
            badgeColor = WeMadeColors.Info,
            icon = { IconPackage(Modifier.size(18.dp), color = WeMadeColors.Info) },
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Paket Tenant",
            value = ui.tier.uppercase(),
            subtitle = "Akses penuh platform builder",
            badge = ui.status.uppercase(),
            badgeColor = statusTint(ui.status),
            icon = { IconShield(Modifier.size(18.dp), color = WeMadeColors.Accent) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    ClayCard(modifier = modifier, shape = ClayShapes.Card, containerColor = WeMadeColors.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier.size(32.dp).background(WeMadeColors.SurfaceMuted, ClayShapes.Tile),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(ClaySpacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subtitle,
                style = typography.bodySmall,
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            ClayBadge(text = badge, tint = badgeColor, fontSize = 9.sp)
        }
    }
}

/** 4 Kartu Fitur Interaktif Cepat (Hub Kapabilitas Builder). */
@Composable
internal fun BuilderQuickActionHub(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(
            text = "Eksplorasi & Modifikasi Alur",
            style = typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
            FeatureActionCard(
                title = "AI Chat Architect",
                description = "Konsultasikan perubahan alur kerja via percakapan AI untuk menyiapkan draft modifikasi otomatis.",
                actionLabel = "Buka Chat AI",
                icon = { IconChat(Modifier.size(20.dp), color = WeMadeColors.Primary) },
                onClick = { onNavigate("chat") },
                modifier = Modifier.weight(1f)
            )
            FeatureActionCard(
                title = "Modul & Stasiun Kerja",
                description = "Konfigurasi stasiun kerja lantai produksi, estimasi WIP, dan alokasi mesin konveksi.",
                actionLabel = "Kelola Modul",
                icon = { IconLayers(Modifier.size(20.dp), color = WeMadeColors.Accent) },
                onClick = { onNavigate("modules") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
            FeatureActionCard(
                title = "Aliran Data & Skema Port",
                description = "Inspeksi kompatibilitas port input/output antar modul agar alur SPK dan QC terhubung mulus.",
                actionLabel = "Inspeksi Port",
                icon = { IconDatabase(Modifier.size(20.dp), color = WeMadeColors.Info) },
                onClick = { onNavigate("dataflow") },
                modifier = Modifier.weight(1f)
            )
            FeatureActionCard(
                title = "Studio Prototype",
                description = "Uji coba simulasi alur transaksi pesanan konveksi secara interaktif sebelum rilis ke produksi.",
                actionLabel = "Uji Prototype",
                icon = { IconGlobe(Modifier.size(20.dp), color = WeMadeColors.Purple) },
                onClick = { onNavigate("prototype") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeatureActionCard(
    title: String,
    description: String,
    actionLabel: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    ClayCard(modifier = modifier, shape = ClayShapes.Card, containerColor = WeMadeColors.Surface, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(40.dp).background(WeMadeColors.SurfaceMuted, ClayShapes.Tile),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Text(text = title, style = typography.titleSmall, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                Text(
                    text = description,
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(ClaySpacing.Xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary
                    )
                    IconArrowForward(Modifier.size(12.dp), color = WeMadeColors.Primary)
                }
            }
        }
    }
}

/** Kartu Riwayat Deployment Terbaru ala Vercel. */
@Composable
internal fun BuilderRecentDeploymentsCard(
    deployments: List<BuilderDeploymentItemUi>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()

    ClayCard(modifier = modifier.fillMaxWidth(), shape = ClayShapes.Card, containerColor = WeMadeColors.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Aktivitas Deployment Terbaru",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Catatan audit rilis alur kerja dan konfigurasi modul",
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            ClayButton(text = "Lihat Semua", onClick = onViewAll, style = ClayButtonStyle.Secondary, fontSize = 11.sp)
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        if (deployments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                    .padding(ClaySpacing.Lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada riwayat deployment tambahan.",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                deployments.take(4).forEach { dep ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                            .padding(ClaySpacing.Md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Box(Modifier.size(8.dp).background(statusTint(dep.status), CircleShape))
                            Text(
                                text = "Deployment #${dep.number}",
                                style = typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            ClayBadge(text = dep.status, tint = statusTint(dep.status), fontSize = 9.sp)
                        }
                        Text(
                            text = "Pack: ${dep.packCode} (v${dep.packVersion ?: 1}) · Rev ${dep.blueprintRevision}",
                            style = typography.bodySmall,
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}
