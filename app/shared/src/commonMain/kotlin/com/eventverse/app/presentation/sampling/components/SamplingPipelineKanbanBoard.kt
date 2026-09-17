package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.presentation.deal.components.rememberMockupBitmap
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SamplingPipelineKanbanBoard(
    orders: List<SamplingOrder>,
    selectedOrderId: SamplingOrderId?,
    onSelectOrder: (SamplingOrderId) -> Unit,
    onAdvanceStage: (SamplingOrderId, SamplingPipelineStage) -> Unit,
    onOpenRevisionDialog: (SamplingOrder) -> Unit,
    onApproveOrder: (SamplingOrderId, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Batas ranah divisi (planning 4-modul Sampling/Finishing/QC/Admin Tower):
    // modul Sampling berakhir di turun mesin. Linking/finishing/QC adalah ranah
    // modul OPERATOR_EXEC & QUALITY_CONTROL — di board ini hanya dipantau
    // read-only; keputusan buyer (ACC/revisi) tetap milik admin di kolom terakhir.
    val samplingStages = listOf(
        SamplingPipelineStage.NEW_INTAKE,
        SamplingPipelineStage.CAM_PROGRAMMING,
        SamplingPipelineStage.MACHINE_KNITTING
    )
    val finishingStages = listOf(
        SamplingPipelineStage.LINKING_ASSEMBLY,
        SamplingPipelineStage.FINISHING_QC
    )
    val buyerStages = listOf(
        SamplingPipelineStage.IN_DELIVERY,
        SamplingPipelineStage.ACC_APPROVED
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .padding(ClaySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        samplingStages.forEach { stage ->
            KanbanStageColumn(
                stage = stage,
                orders = orders.filter { it.pipelineStage == stage },
                selectedOrderId = selectedOrderId,
                onSelectOrder = onSelectOrder,
                onAdvanceStage = onAdvanceStage,
                onOpenRevisionDialog = onOpenRevisionDialog,
                onApproveOrder = onApproveOrder,
                modifier = Modifier.width(300.dp).fillMaxHeight()
            )
        }

        KanbanGroupColumn(
            title = "Di Meja Finishing & QC",
            subtitle = "Ranah divisi Finishing & QC — pantau saja",
            tint = WeMadeColors.Teal,
            orders = orders.filter { it.pipelineStage in finishingStages },
            selectedOrderId = selectedOrderId,
            onSelectOrder = onSelectOrder,
            onOpenRevisionDialog = onOpenRevisionDialog,
            onApproveOrder = onApproveOrder,
            showActions = false,
            modifier = Modifier.width(300.dp).fillMaxHeight()
        )

        KanbanGroupColumn(
            title = "Tunggu ACC Buyer",
            subtitle = "Keputusan buyer / admin",
            tint = WeMadeColors.Info,
            orders = orders.filter { it.pipelineStage in buyerStages },
            selectedOrderId = selectedOrderId,
            onSelectOrder = onSelectOrder,
            onOpenRevisionDialog = onOpenRevisionDialog,
            onApproveOrder = onApproveOrder,
            showActions = true,
            modifier = Modifier.width(300.dp).fillMaxHeight()
        )
    }
}

@Composable
private fun KanbanStageColumn(
    stage: SamplingPipelineStage,
    orders: List<SamplingOrder>,
    selectedOrderId: SamplingOrderId?,
    onSelectOrder: (SamplingOrderId) -> Unit,
    onAdvanceStage: (SamplingOrderId, SamplingPipelineStage) -> Unit,
    onOpenRevisionDialog: (SamplingOrder) -> Unit,
    onApproveOrder: (SamplingOrderId, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val headerTint = samplingStageTint(stage)

    Column(
        modifier = modifier
            .background(WeMadeColors.SurfaceMuted, ClayShapes.Card)
            .padding(ClaySpacing.Sm)
    ) {
        // Stage Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stage.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Tahap ${stage.order} dari 7",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            ClayBadge(
                text = "${orders.size}",
                tint = headerTint
            )
        }

        // Cards list
        val verticalScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(verticalScrollState),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(WeMadeColors.Surface.copy(alpha = 0.5f), ClayShapes.Tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kosong",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                orders.forEach { order ->
                    KanbanOrderCard(
                        order = order,
                        isSelected = order.id == selectedOrderId,
                        showActions = true,
                        onSelectOrder = { onSelectOrder(order.id) },
                        onAdvanceStage = { onAdvanceStage(order.id, it) },
                        onOpenRevisionDialog = { onOpenRevisionDialog(order) },
                        onApproveOrder = { onApproveOrder(order.id, "ACC Golden Sample") }
                    )
                }
            }
        }
    }
}

/**
 * Kolom agregat lintas-tahap: dipakai untuk ranah DI LUAR divisi Sampling
 * (linking/finishing/QC — kartu read-only, hanya dipantau) dan kelompok
 * keputusan buyer (IN_DELIVERY + ACC_APPROVED — aksi ACC/revisi tetap aktif
 * karena itu tugas admin, bukan operator sampling).
 */
@Composable
private fun KanbanGroupColumn(
    title: String,
    subtitle: String,
    tint: Color,
    orders: List<SamplingOrder>,
    selectedOrderId: SamplingOrderId?,
    onSelectOrder: (SamplingOrderId) -> Unit,
    onOpenRevisionDialog: (SamplingOrder) -> Unit,
    onApproveOrder: (SamplingOrderId, String) -> Unit,
    showActions: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(WeMadeColors.SurfaceMuted, ClayShapes.Card)
            .padding(ClaySpacing.Sm)
    ) {
        // Group Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            ClayBadge(
                text = "${orders.size}",
                tint = tint
            )
        }

        // Cards list
        val verticalScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(verticalScrollState),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(WeMadeColors.Surface.copy(alpha = 0.5f), ClayShapes.Tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kosong",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                orders.forEach { order ->
                    KanbanOrderCard(
                        order = order,
                        isSelected = order.id == selectedOrderId,
                        showActions = showActions,
                        onSelectOrder = { onSelectOrder(order.id) },
                        onAdvanceStage = { },
                        onOpenRevisionDialog = { onOpenRevisionDialog(order) },
                        onApproveOrder = { onApproveOrder(order.id, "ACC Golden Sample") }
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanOrderCard(
    order: SamplingOrder,
    isSelected: Boolean,
    showActions: Boolean,
    onSelectOrder: () -> Unit,
    onAdvanceStage: (SamplingPipelineStage) -> Unit,
    onOpenRevisionDialog: () -> Unit,
    onApproveOrder: () -> Unit
) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectOrder() },
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            // SPK number & Revisions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.spkNumber.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
                )
                if (order.revisionCount > 0) {
                    ClayBadge(
                        text = "Rev ${order.revisionCount}",
                        tint = WeMadeColors.Accent
                    )
                }
            }

            // Client & Style
            Text(
                text = order.clientName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = order.styleName,
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            // Mockup Image thumbnail preview
            val mockupKey = order.mockupFrontKey
            if (!mockupKey.isNullOrBlank()) {
                val bitmap = rememberMockupBitmap(mockupKey)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Mockup Front",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(ClayShapes.Tile),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Badges row: Qty & Finishing path
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTag(
                    text = "${order.sampleQuantity} Pcs",
                    tint = WeMadeColors.OnSurfaceMuted
                )

                if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) {
                    val vName = order.vendorInfo.vendorName.ifBlank { "Vendor" }
                    ClayTag(
                        text = "Makloon: $vName",
                        tint = WeMadeColors.Primary
                    )
                } else {
                    ClayTag(
                        text = "Internal",
                        tint = WeMadeColors.Success
                    )
                }
            }

            // Aksi tahap — hanya untuk kartu di kolom ranah Sampling dan kelompok
            // keputusan buyer. Kartu di kolom divisi lain (finishing/QC) read-only:
            // posisinya cukup ditampilkan sebagai badge, aksinya milik modul
            // OPERATOR_EXEC / QUALITY_CONTROL masing-masing.
            if (showActions) {
                when (order.pipelineStage) {
                    SamplingPipelineStage.NEW_INTAKE -> {
                        ClayButton(
                            text = "Mulai Program CAM ->",
                            style = ClayButtonStyle.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onAdvanceStage(SamplingPipelineStage.CAM_PROGRAMMING) }
                        )
                    }
                    SamplingPipelineStage.CAM_PROGRAMMING -> {
                        ClayButton(
                            text = "Masuk Mesin Rajut ->",
                            style = ClayButtonStyle.Accent,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onAdvanceStage(SamplingPipelineStage.MACHINE_KNITTING) }
                        )
                    }
                    SamplingPipelineStage.MACHINE_KNITTING -> {
                        ClayButton(
                            text = "Turun Mesin Selesai -> Serah ke Finishing",
                            style = ClayButtonStyle.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onAdvanceStage(SamplingPipelineStage.LINKING_ASSEMBLY) }
                        )
                    }
                    SamplingPipelineStage.IN_DELIVERY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            ClayButton(
                                text = "ACC Buyer",
                                style = ClayButtonStyle.Success,
                                modifier = Modifier.weight(1f),
                                onClick = onApproveOrder
                            )
                            ClayButton(
                                text = "Revisi",
                                style = ClayButtonStyle.Secondary,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenRevisionDialog
                            )
                        }
                    }
                    SamplingPipelineStage.ACC_APPROVED -> {
                        ClayBadge(
                            text = "GOLDEN SAMPLE LOCKED",
                            tint = WeMadeColors.Success,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
            } else {
                ClayBadge(
                    text = order.pipelineStage.displayName,
                    tint = samplingStageTint(order.pipelineStage),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
