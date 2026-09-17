package com.eventverse.app.presentation.production.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.ProductionLineName
import com.eventverse.app.domain.production.ProductionStage
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Panel detail satu SPK massal: acuan Golden Sample, size breakdown, alokasi lini, dan
 * realisasi per tahap.
 */
@Composable
fun BulkWorkOrderDetailPane(
    order: BulkWorkOrder,
    accessLevel: AccessLevel,
    onAllocateLine: () -> Unit,
    onRemoveLine: (ProductionLineName) -> Unit,
    onRecordProgress: (ProductionStage) -> Unit,
    modifier: Modifier = Modifier
) {
    val canOperate = accessLevel == AccessLevel.OPERATE || accessLevel == AccessLevel.MANAGE

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        HeaderCard(order)
        SizeBreakdownCard(order)
        LineAllocationCard(
            order = order,
            canOperate = canOperate,
            onAllocateLine = onAllocateLine,
            onRemoveLine = onRemoveLine
        )
        StageProgressCard(
            order = order,
            canOperate = canOperate,
            onRecordProgress = onRecordProgress
        )
    }
}

@Composable
private fun HeaderCard(order: BulkWorkOrder) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = order.spkNumber.value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${order.clientName} — ${order.styleName}",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                ProductionStatusBadge(order.status)
                ProductionHealthBadge(order.healthStatus)
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // Banner Golden Sample — acuan spesifikasi yang terkunci.
        val goldenSample = order.goldenSampleOrderId
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (goldenSample != null) WeMadeColors.SuccessBg else WeMadeColors.WarningBg,
            offset = ClayOffset.Small,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Text(
                text = if (goldenSample != null) "Mengacu pada Sampel ACC" else "Belum ada acuan Golden Sample",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (goldenSample != null) WeMadeColors.Success else WeMadeColors.Warning
            )
            Text(
                text = goldenSample?.value
                    ?: "SPK ini tidak punya sampel ber-ACC sebagai acuan spesifikasi produksi.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurface
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTag(text = "Total ${order.totalOrderedPcs} pcs", tint = WeMadeColors.Primary)
            ClayTag(text = "WIP ${order.wipPieces} pcs", tint = WeMadeColors.Warning)
            ClayTag(text = "Selesai ${order.completedPcs} pcs", tint = WeMadeColors.Success)
            if (order.totalRejectPcs > 0) {
                ClayTag(text = "Reject ${order.totalRejectPcs} pcs", tint = WeMadeColors.Defect)
            }
        }

        Spacer(Modifier.height(ClaySpacing.Sm))
        Text(
            text = "Kepemilikan bahan: ${order.stockOwnership.displayName}",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        if (order.stockOwnership.requiresWasteReconciliation) {
            Text(
                text = "Kain titipan klien — sisa kain dan perca wajib direkonsiliasi saat SPK ditutup.",
                fontSize = 11.sp,
                color = WeMadeColors.Warning
            )
        }
    }
}

@Composable
private fun SizeBreakdownCard(order: BulkWorkOrder) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Size Breakdown Massal")
        Spacer(Modifier.height(ClaySpacing.Md))

        if (order.sizeBreakdown.isEmpty()) {
            EmptyHint("Belum ada rincian ukuran — SPK ini belum bisa diturunkan ke meja potong.")
            return@ClayCard
        }

        Column(
            modifier = Modifier.fillMaxWidth().clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
        ) {
            TableRow(listOf("Ukuran / Item", "Jumlah"), header = true)
            order.sizeBreakdown.forEach { line ->
                TableRow(listOf(line.sizeLabel, "${line.orderedPcs} pcs"), header = false)
            }
            TableRow(listOf("Total", "${order.totalOrderedPcs} pcs"), header = true)
        }
    }
}

@Composable
private fun LineAllocationCard(
    order: BulkWorkOrder,
    canOperate: Boolean,
    onAllocateLine: () -> Unit,
    onRemoveLine: (ProductionLineName) -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f, fill = false)) {
                SectionTitle("Alokasi Lini & Mesin")
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayTag(
                text = if (order.unallocatedPcs > 0) "Sisa ${order.unallocatedPcs} pcs" else "Terbagi penuh",
                tint = if (order.unallocatedPcs > 0) WeMadeColors.Warning else WeMadeColors.Success
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        if (order.lineAllocations.isEmpty()) {
            EmptyHint("Belum ada lini yang dialokasikan untuk SPK ini.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                order.lineAllocations.forEach { allocation ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        ).padding(ClaySpacing.Md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = allocation.lineName.value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${allocation.assignedPcs} pcs · ${allocation.machineCount} mesin · " +
                                    "${allocation.operatorCount} operator",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(ClaySpacing.Sm))
                        if (canOperate && !order.status.isTerminal) {
                            ClayButton(
                                text = "Hapus",
                                onClick = { onRemoveLine(allocation.lineName) },
                                style = ClayButtonStyle.Danger,
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))
        ClayGuardedButton(
            text = "Alokasikan Lini Mesin",
            onClick = onAllocateLine,
            enabled = canOperate && !order.status.isTerminal && order.unallocatedPcs > 0,
            lockedHint = when {
                !canOperate -> "Butuh wewenang Input & Kerja untuk mengatur jadwal mesin."
                order.status.isTerminal -> "SPK sudah ${order.status.displayName}."
                order.unallocatedPcs <= 0 -> "Seluruh pesanan sudah terbagi ke lini."
                else -> null
            }
        )
    }
}

@Composable
private fun StageProgressCard(
    order: BulkWorkOrder,
    canOperate: Boolean,
    onRecordProgress: (ProductionStage) -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Realisasi Lantai Produksi")
        Spacer(Modifier.height(ClaySpacing.Md))

        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            ProductionStage.entries.forEach { stage ->
                val progress = order.progressFor(stage)
                val upstreamLimit = stage.previous?.let { order.progressFor(it).passedPcs }
                    ?: order.totalOrderedPcs

                Column(
                    modifier = Modifier.fillMaxWidth().clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Medium
                    ).padding(ClaySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stage.displayName,
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(ClaySpacing.Sm))
                        ClayTag(
                            text = "${progress.completedPcs} / $upstreamLimit pcs",
                            tint = if (progress.completedPcs >= upstreamLimit && upstreamLimit > 0) {
                                WeMadeColors.Success
                            } else {
                                WeMadeColors.Info
                            }
                        )
                    }

                    Text(
                        text = "Lolos ${progress.passedPcs} pcs · Rework ${progress.reworkPcs} pcs · " +
                            "Reject ${progress.rejectPcs} pcs",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

                    ClayGuardedButton(
                        text = "Catat Hasil ${stage.displayName}",
                        onClick = { onRecordProgress(stage) },
                        enabled = canOperate &&
                            order.status != BulkProductionStatus.DRAFT &&
                            order.status != BulkProductionStatus.CANCELLED,
                        style = ClayButtonStyle.Secondary,
                        lockedHint = when {
                            !canOperate -> "Butuh wewenang Input & Kerja."
                            order.status == BulkProductionStatus.DRAFT -> "SPK belum diterbitkan."
                            order.status == BulkProductionStatus.CANCELLED -> "SPK sudah dibatalkan."
                            else -> null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.OnSurface
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) WeMadeColors.SurfaceMuted else Color.Transparent)
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = cells.first(),
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 11.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(ClaySpacing.Sm))
        Text(
            text = cells.last(),
            fontSize = 11.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            color = WeMadeColors.OnSurface
        )
    }
}
