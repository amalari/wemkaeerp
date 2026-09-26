package com.eventverse.app.presentation.crm.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.CrmLeadKpiMetrics
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconActivity
import com.eventverse.app.presentation.designsystem.IconClipboard
import com.eventverse.app.presentation.designsystem.IconReceipt
import com.eventverse.app.presentation.designsystem.IconWarning
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Baris metrik eksekutif Sales CRM (KPI Strip) bergaya Claymorphism.
 *
 * Menampilkan 4 kartu ringkasan:
 * 1. Total Pipeline Value (Rp)
 * 2. Active Leads (Count)
 * 3. Qualified Conversion (%)
 * 4. Follow-up Needed (SLA Alerts)
 */
@Composable
fun CrmKpiMetricsRow(
    metrics: CrmLeadKpiMetrics,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        // Kartu 1: Total Pipeline Value
        KpiCardItem(
            modifier = Modifier.weight(1f),
            label = "Total Pipeline Value",
            value = formatRupiah(metrics.totalPipelineValue),
            icon = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.PrimaryContainer,
                            outline = WeMadeColors.Primary,
                            borderWidth = ClayBorder.Hairline
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconReceipt(modifier = Modifier.size(20.dp), color = WeMadeColors.Primary)
                }
            }
        )

        // Kartu 2: Active Leads
        KpiCardItem(
            modifier = Modifier.weight(1f),
            label = "Active Leads",
            value = "${metrics.activeLeadsCount} Leads",
            icon = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.PrimaryContainer,
                            outline = WeMadeColors.Primary,
                            borderWidth = ClayBorder.Hairline
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconClipboard(modifier = Modifier.size(20.dp), color = WeMadeColors.Primary)
                }
            }
        )

        // Kartu 3: Qualified Conversion
        KpiCardItem(
            modifier = Modifier.weight(1f),
            label = "Qualified Conversion",
            value = "${metrics.qualifiedConversionRate}%",
            icon = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SuccessBg,
                            outline = WeMadeColors.Success,
                            borderWidth = ClayBorder.Hairline
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconActivity(modifier = Modifier.size(20.dp), color = WeMadeColors.Success)
                }
            }
        )

        // Kartu 4: Follow-up Needed / Alerts
        val hasAlerts = metrics.followUpNeededCount > 0
        KpiCardItem(
            modifier = Modifier.weight(1f),
            label = "Follow-up Needed",
            value = "${metrics.followUpNeededCount} Alerts",
            containerColor = if (hasAlerts) WeMadeColors.WarningBg else WeMadeColors.Surface,
            outlineColor = if (hasAlerts) WeMadeColors.Warning else WeMadeColors.Outline,
            icon = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = if (hasAlerts) WeMadeColors.WarningBg else WeMadeColors.SurfaceMuted,
                            outline = if (hasAlerts) WeMadeColors.Warning else WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconWarning(
                        modifier = Modifier.size(20.dp),
                        color = if (hasAlerts) WeMadeColors.Warning else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        )
    }
}

@Composable
private fun KpiCardItem(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = WeMadeColors.Surface,
    outlineColor: androidx.compose.ui.graphics.Color = WeMadeColors.Outline
) {
    ClayCard(
        modifier = modifier,
        containerColor = containerColor,
        outlineColor = outlineColor,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }
        }
    }
}
