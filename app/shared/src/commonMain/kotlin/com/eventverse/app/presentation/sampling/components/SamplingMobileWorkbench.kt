package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.MilestoneStep
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.sampling.SamplingMobileTab
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SamplingMobileWorkbench(
    order: SamplingOrder,
    activeTab: SamplingMobileTab,
    onTabSelected: (SamplingMobileTab) -> Unit,
    onToggleMilestone: (MilestoneStep, Boolean) -> Unit,
    onApproveOrder: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
                .padding(bottom = 70.dp) // Space for sticky bottom bar
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // 1. Mobile Header Summary
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = order.spkNumber.value,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                        Text(
                            text = "${order.clientName} — ${order.styleName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                    }

                    ClayBadge(
                        text = order.status.displayName,
                        tint = if (order.isAccApproved) WeMadeColors.Success else WeMadeColors.Accent,
                        dot = true
                    )
                }
            }

            // 2. Segmented Pill Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                SamplingMobileTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    ClayButton(
                        text = tab.displayName,
                        style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(tab) }
                    )
                }
            }

            // 3. Tab Content
            when (activeTab) {
                SamplingMobileTab.INFO -> {
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Text(text = "DETAIL PESANAN & BENANG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                        Text(text = "Benang: ${order.knitSpec.yarnType}", fontSize = 12.sp, color = WeMadeColors.OnSurface)
                        Text(text = "Rajut: ${order.knitSpec.knitType}", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = "Rib & Kerah: ${order.knitSpec.ribSpec}", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = "Plaket: ${order.knitSpec.placketSpec}", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = "Warna: ${order.knitSpec.colorwayNotes.ifBlank { "Offwhite + Hitam + Grey" }}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.Accent)
                    }

                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Text(text = "RINGKASAN ESTIMASI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Gramasi", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(text = "${order.yieldAndTiming.panelWeights.total.toString().removeSuffix(".0")} gr / pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Waktu Rajut", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(text = "${order.yieldAndTiming.panelMinutes.total} menit / pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Accent)
                        }
                    }
                }

                SamplingMobileTab.SIZE -> {
                    SizeChartComparisonTable(
                        finishedSizes = order.finishedSizeCharts,
                        rawKnitSizes = order.rawKnitSizeCharts
                    )
                }

                SamplingMobileTab.MACHINE -> {
                    FeederSequenceBar(
                        feeders = order.machineProgram.feederInstructions,
                        formulas = order.machineProgram.patternFormulas
                    )

                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Text(text = "FILE PROGRAM CAM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                        Text(text = "Depan: ${order.machineProgram.programFront.ifBlank { "BIAN-D" }}", fontSize = 12.sp, color = WeMadeColors.OnSurface)
                        Text(text = "Belakang: ${order.machineProgram.programBack.ifBlank { "BIAN-B" }}", fontSize = 12.sp, color = WeMadeColors.OnSurface)
                        Text(text = "Tangan: ${order.machineProgram.programSleeve.ifBlank { "BIAN-T" }}", fontSize = 12.sp, color = WeMadeColors.OnSurface)
                        Text(text = "Kerah: ${order.machineProgram.programCollar.ifBlank { "BIAN-KR" }}", fontSize = 12.sp, color = WeMadeColors.OnSurface)
                        Text(text = "Plaket: ${order.machineProgram.programPlacket.ifBlank { "BIAN-PL" }}", fontSize = 12.sp, color = WeMadeColors.OnSurface)
                    }
                }

                SamplingMobileTab.STATUS -> {
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        MilestoneStepTracker(
                            milestones = order.milestones,
                            onToggleMilestone = onToggleMilestone
                        )
                    }
                }
            }
        }

        // 4. Sticky Bottom Action Bar (Ramah Jempol)
        ClayCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(ClaySpacing.Sm),
            shape = ClayShapes.Pill,
            containerColor = WeMadeColors.Surface,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (order.isAccApproved) {
                    ClayButton(
                        text = "Buat Tech Pack BOM",
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(0.55f),
                        onClick = { onCreateTechPack?.invoke(order) }
                    )
                    ClayButton(
                        text = "Revisi",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(0.45f),
                        onClick = { onApproveOrder(false, "Revisi via mobile") }
                    )
                } else {
                    ClayButton(
                        text = "Revisi",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(0.35f),
                        onClick = { onApproveOrder(false, "Revisi via mobile") }
                    )

                    ClayButton(
                        text = "ACC PRODUKSI",
                        style = ClayButtonStyle.Accent,
                        modifier = Modifier.weight(0.65f),
                        onClick = { onApproveOrder(true, "ACC Produksi via mobile") }
                    )
                }
            }
        }
    }
}
