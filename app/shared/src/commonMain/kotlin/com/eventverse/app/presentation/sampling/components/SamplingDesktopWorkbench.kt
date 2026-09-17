package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.MilestoneStep
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.TenselityEntry
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SamplingDesktopWorkbench(
    order: SamplingOrder,
    onToggleMilestone: (MilestoneStep, Boolean) -> Unit,
    onApproveOrder: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null,
    onOpenFinishingDialog: () -> Unit = {},
    onOpenQcDialog: () -> Unit = {},
    onOpenVendorDialog: () -> Unit = {},
    onConfirmVendorReceive: () -> Unit = {},
    onOpenRevisionDialog: () -> Unit = {},
    onUpdateTenselity: (List<TenselityEntry>) -> Unit = {}
) {
    var revisionNotes by remember(order.id) { mutableStateOf(order.accNotes) }
    var showRevisionInput by remember(order.id) { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxSize().padding(ClaySpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        // Kolom Kiri: Workbench Lembar Kerja SPK (68%)
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            // 1. Header Card SPK
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = order.spkNumber.value,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary
                            )
                            val statusColor = when (order.status) {
                                SamplingStatus.ACC_APPROVED -> WeMadeColors.Success
                                SamplingStatus.REVISION -> WeMadeColors.Warning
                                SamplingStatus.IN_PROGRESS -> WeMadeColors.Primary
                                SamplingStatus.DRAFT -> WeMadeColors.OnSurfaceMuted
                                SamplingStatus.CANCELLED -> WeMadeColors.Error
                            }
                            ClayBadge(
                                text = order.status.displayName,
                                tint = statusColor,
                                dot = true
                            )
                        }

                        Spacer(modifier = Modifier.height(ClaySpacing.Xs))

                        Text(
                            text = "${order.clientName} — ${order.styleName}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (order.isAccApproved) {
                            ClayButton(
                                text = "Buat Tech Pack BOM",
                                style = ClayButtonStyle.Primary,
                                onClick = { onCreateTechPack?.invoke(order) }
                            )
                        }

                        ClayButton(
                            text = "Setor Finishing",
                            style = ClayButtonStyle.Primary,
                            onClick = onOpenFinishingDialog
                        )

                        ClayButton(
                            text = "QC Inspeksi",
                            style = ClayButtonStyle.Success,
                            onClick = onOpenQcDialog
                        )

                        ClayButton(
                            text = "Ajukan Revisi",
                            style = ClayButtonStyle.Secondary,
                            onClick = onOpenRevisionDialog
                        )

                        ClayButton(
                            text = if (order.isAccApproved) "SUDAH DI-ACC" else "ACC PRODUKSI",
                            style = ClayButtonStyle.Accent,
                            enabled = !order.isAccApproved,
                            onClick = { onApproveOrder(true, revisionNotes) }
                        )
                    }
                }

                if (showRevisionInput || order.accNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(ClaySpacing.Md))
                    OutlinedTextField(
                        value = revisionNotes,
                        onValueChange = { revisionNotes = it },
                        label = { Text("Catatan Approval / Catatan Revisi") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ClayButton(
                            text = "Kirim Catatan Revisi",
                            style = ClayButtonStyle.Primary,
                            onClick = {
                                onApproveOrder(false, revisionNotes)
                                showRevisionInput = false
                            }
                        )
                    }
                }
            }

            // 2. Spesifikasi Benang & Desain Rajut
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Lg)
            ) {
                Text(
                    text = "SPESIFIKASI BAHAN & RAJUTAN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        Text(text = "Jenis Benang: ${order.knitSpec.yarnType}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurface)
                        Text(text = "Jenis Rajut: ${order.knitSpec.knitType}", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = "Rib: ${order.knitSpec.ribSpec}", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = WeMadeColors.OnSurfaceMuted)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        Text(text = "Kerah: ${order.knitSpec.collarSpec}", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = "Plaket: ${order.knitSpec.placketSpec}", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = "Warna: ${order.knitSpec.colorwayNotes.ifBlank { "Offwhite + Hitam + Grey" }}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.Accent)
                    }
                }
            }

            // 3. Feeder Sequence Bar
            FeederSequenceBar(
                feeders = order.machineProgram.feederInstructions,
                formulas = order.machineProgram.patternFormulas
            )

            // 4. Tenselity Matrix Table (11 Parameters)
            TenselityTable(
                entries = order.machineProgram.tenselityEntries,
                onEntriesChanged = onUpdateTenselity
            )

            // 5. Dual Size Chart Table
            SizeChartComparisonTable(
                finishedSizes = order.finishedSizeCharts,
                rawKnitSizes = order.rawKnitSizeCharts
            )

            // 6. Yield Gramasi & Cycle Time
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Lg)
            ) {
                Text(
                    text = "ESTIMASI KONSUMSI & WAKTU PRODUKSI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClayCard(
                        modifier = Modifier.weight(1f),
                        shape = ClayShapes.Tile,
                        containerColor = WeMadeColors.Primary.copy(alpha = 0.08f),
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Text(text = "TOTAL GRAMASI BENANG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
                        Text(
                            text = "${order.yieldAndTiming.panelWeights.total.toString().removeSuffix(".0")} Gram / Pcs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(text = "Depan: ${order.yieldAndTiming.panelWeights.front}g | Belakang: ${order.yieldAndTiming.panelWeights.back}g | Tangan: ${order.yieldAndTiming.panelWeights.sleeve}g", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                    }

                    ClayCard(
                        modifier = Modifier.weight(1f),
                        shape = ClayShapes.Tile,
                        containerColor = WeMadeColors.Accent.copy(alpha = 0.08f),
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Text(text = "CYCLE TIME MESIN RAJUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Accent)
                        Text(
                            text = "${order.yieldAndTiming.panelMinutes.total} Menit / Pcs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(text = "Depan: ${order.yieldAndTiming.panelMinutes.front}m | Belakang: ${order.yieldAndTiming.panelMinutes.back}m | Tangan: ${order.yieldAndTiming.panelMinutes.sleeve}m", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                    }
                }
            }

            // 7. Vendor Makloon & Jalur Finishing
            VendorMakloonCard(
                order = order,
                onOpenVendorDialog = onOpenVendorDialog,
                onConfirmReceive = onConfirmVendorReceive
            )
        }

        // Kolom Kanan: Sticky Milestone Step Tracker & Finishing/QC Summary (32%)
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            // Milestone Progress
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Lg)
            ) {
                MilestoneStepTracker(
                    milestones = order.milestones,
                    onToggleMilestone = onToggleMilestone
                )
            }

            // Ringkasan Finishing & QC
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(
                    text = "PROGRES FINISHING & QC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Setoran Finishing:", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = "${order.totalFinishedDepositedQty} / ${order.sampleQuantity} Pcs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (order.isFinishingComplete) WeMadeColors.Success else WeMadeColors.Primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Sisa Belum Selesai:", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = "${order.remainingFinishingQty} Pcs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (order.remainingFinishingQty == 0) WeMadeColors.Success else WeMadeColors.Accent
                    )
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hasil QC Terakhir:", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                    val qcStatus = order.latestQcReport?.qcResult
                    val qcBadgeTint = when (qcStatus) {
                        QcInspectionResult.PASSED -> WeMadeColors.Success
                        QcInspectionResult.REWORK -> WeMadeColors.Warning
                        QcInspectionResult.REJECT -> WeMadeColors.Error
                        null -> WeMadeColors.OnSurfaceMuted
                    }
                    ClayBadge(
                        text = qcStatus?.displayName ?: "Belum Diperiksa",
                        tint = qcBadgeTint
                    )
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayButton(
                        text = "+ Setor",
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFinishingDialog
                    )
                    ClayButton(
                        text = "QC",
                        style = ClayButtonStyle.Success,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenQcDialog
                    )
                }
            }

            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                containerColor = WeMadeColors.SurfaceMuted,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(
                    text = "RINGKASAN DEADLINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                Text(text = "Deadline Program/Rajut: ${order.deadlineProgram ?: "7-Aug-2026"}", fontSize = 11.sp, color = WeMadeColors.OnSurface)
                Text(text = "Deadline Finishing: ${order.deadlineFinishing ?: "16-Aug-2026"}", fontSize = 11.sp, color = WeMadeColors.OnSurface)
                Text(text = "Deadline Pengiriman: ${order.deadlineDelivery ?: "17-Aug-2026"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Accent)
            }
        }
    }
}
