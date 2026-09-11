package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.FactoryPipelineSnapshot
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun ExecutiveKpiHeader(
    snapshot: FactoryPipelineSnapshot,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // KPI 1: Overall Factory Health
        KpiStatCard(
            modifier = Modifier.weight(1f),
            title = "Kesehatan Alur Pabrik",
            metricValue = "${snapshot.overallHealthScore}%",
            subtitle = if (snapshot.overallHealthScore >= 90) "Sangat Sehat & Terkendali" else "Perlu Pengawasan Supervisor",
            accentColor = if (snapshot.overallHealthScore >= 90) WeMadeColors.Success else WeMadeColors.Warning,
            bgColor = if (snapshot.overallHealthScore >= 90) WeMadeColors.SuccessBg else WeMadeColors.WarningBg,
            iconContent = {
                IconHealth(
                    modifier = Modifier.size(16.dp),
                    color = if (snapshot.overallHealthScore >= 90) WeMadeColors.Success else WeMadeColors.Warning
                )
            }
        )

        // KPI 2: Total Work In Progress (WIP)
        KpiStatCard(
            modifier = Modifier.weight(1f),
            title = "Total WIP di Produksi",
            metricValue = "${snapshot.totalWipPieces} Pcs",
            subtitle = "Pakaian aktif di seluruh line",
            accentColor = WeMadeColors.Primary,
            bgColor = WeMadeColors.PrimaryContainer,
            iconContent = {
                IconWip(
                    modifier = Modifier.size(16.dp),
                    color = WeMadeColors.Primary
                )
            }
        )

        // KPI 3: Active Bottlenecks
        KpiStatCard(
            modifier = Modifier.weight(1f),
            title = "Titik Hambatan (Bottleneck)",
            metricValue = if (snapshot.activeBottlenecks == 0) "Nol (Lancar)" else "${snapshot.activeBottlenecks} Modul",
            subtitle = if (snapshot.activeBottlenecks == 0) "Tidak ada antrean tertahan" else "Ada akumulasi antrean kerja",
            accentColor = if (snapshot.activeBottlenecks == 0) WeMadeColors.Success else WeMadeColors.Error,
            bgColor = if (snapshot.activeBottlenecks == 0) WeMadeColors.SuccessBg else WeMadeColors.ErrorBg,
            iconContent = {
                if (snapshot.activeBottlenecks == 0) {
                    IconCheck(
                        modifier = Modifier.size(15.dp),
                        color = WeMadeColors.Success
                    )
                } else {
                    IconWarning(
                        modifier = Modifier.size(15.dp),
                        color = WeMadeColors.Error
                    )
                }
            }
        )

        // KPI 4: Avg Lead Time
        KpiStatCard(
            modifier = Modifier.weight(1f),
            title = "Estimasi Lead Time",
            metricValue = "${snapshot.avgLeadTimeDays} Hari",
            subtitle = "Dari PO masuk ke siap kirim",
            accentColor = WeMadeColors.Purple,
            bgColor = WeMadeColors.PurpleBg,
            iconContent = {
                IconClock(
                    modifier = Modifier.size(16.dp),
                    color = WeMadeColors.Purple
                )
            }
        )
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    metricValue: String,
    subtitle: String,
    accentColor: Color,
    bgColor: Color,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier,
        // Outline mengambil warna aksen KPI-nya, jadi kartu bottleneck merah langsung terbaca
        // dari jauh tanpa perlu membaca angkanya.
        outlineColor = accentColor,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clayFlat(
                            shape = CircleShape,
                            background = bgColor,
                            outline = accentColor.copy(alpha = 0.45f),
                            borderWidth = ClayBorder.Medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
            }

            Text(
                text = metricValue,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1
                )
            }
        }
    }
}
