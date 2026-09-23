package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.deal.components.rememberMockupBitmap
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kartu SPK di Pipeline Kanban — hasil ekstraksi dari board supaya board tinggal merakit
 * kolom, kartu yang merender isi.
 *
 * Kartu ini *drag-aware*: membaca [LocalSamplingDragDropState]; saat digeser, seluruh isi
 * diganti placeholder "Memindahkan …" dan kartu melayang-nya dirender board (overlay Jira).
 * Tombol aksi per tahap tetap ada sebagai jalur alternatif drag.
 */
@Composable
fun SamplingKanbanCard(
    order: SamplingOrder,
    isSelected: Boolean,
    showActions: Boolean,
    nextStage: SamplingPipelineStage?,
    onSelectOrder: () -> Unit,
    onAdvanceStage: (SamplingPipelineStage) -> Unit,
    onOpenRevisionDialog: () -> Unit,
    onApproveOrder: () -> Unit,
    onDetermineFlow: (() -> Unit)? = null
) {
    val dragDropState = LocalSamplingDragDropState.current
    val canDrag = nextStage != null
    val isBeingDragged = canDrag && dragDropState?.isDragging == true && dragDropState.draggedOrder?.id == order.id
    var cardWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(Size.Zero) }

    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    cardWindowOffset = coords.positionInWindow()
                    cardSize = coords.size.toSize()
                }
            }
            .then(
                if (canDrag) {
                    Modifier.pointerInput(order.id, order.pipelineStage) {
                        detectDragGestures(
                            onDragStart = { pointerOffset ->
                                dragDropState?.onDragStart(order, cardWindowOffset, cardSize, pointerOffset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDropState?.onDrag(dragAmount)
                            },
                            onDragEnd = {
                                dragDropState?.onDragEnd { targetStage ->
                                    onAdvanceStage(targetStage)
                                }
                            },
                            onDragCancel = {
                                dragDropState?.onDragCancel()
                            }
                        )
                    }
                } else Modifier
            )
            .clickable { onSelectOrder() },
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Sm),
        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
        borderWidth = if (isBeingDragged) ClayBorder.Hairline else ClayBorder.Medium
    ) {
        if (isBeingDragged) {
            SamplingKanbanDragPlaceholder(cardHeightPx = cardSize.height)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                SamplingKanbanCardBody(order = order, isSelected = isSelected)
                if (showActions) {
                    SamplingKanbanCardActions(
                        order = order,
                        onAdvanceStage = onAdvanceStage,
                        onOpenRevisionDialog = onOpenRevisionDialog,
                        onApproveOrder = onApproveOrder,
                        onDetermineFlow = onDetermineFlow
                    )
                } else {
                    SamplingKanbanReadOnlyBadges(order)
                }
            }
        }
    }
}

@Composable
private fun SamplingKanbanDragPlaceholder(cardHeightPx: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { (cardHeightPx - 24f).coerceAtLeast(64f).toDp() }),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            IconInbox(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
            Text(
                text = "Memindahkan kartu…",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}


@Composable
private fun SamplingKanbanCardBody(order: SamplingOrder, isSelected: Boolean) {
    // Nomor SPK & revisi
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

    SamplingKanbanMockupPreview(order)
    SamplingKanbanMetaBadges(order)
}

@Composable
private fun SamplingKanbanMockupPreview(order: SamplingOrder) {
    val mockupKey = order.mockupFrontKey ?: return
    val bitmap = rememberMockupBitmap(mockupKey) ?: return
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

@Composable
private fun SamplingKanbanMetaBadges(order: SamplingOrder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayTag(
            text = "${order.sampleQuantity} Pcs",
            tint = WeMadeColors.Info
        )
        ClayTag(
            text = if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) "Makloon" else "Internal",
            tint = WeMadeColors.Success
        )
        if (order.isCustomFlow) {
            ClayTag(
                text = "Alur Kustom",
                tint = WeMadeColors.Accent
            )
        }
    }
}


@Composable
private fun SamplingKanbanCardActions(
    order: SamplingOrder,
    onAdvanceStage: (SamplingPipelineStage) -> Unit,
    onOpenRevisionDialog: () -> Unit,
    onApproveOrder: () -> Unit,
    onDetermineFlow: (() -> Unit)? = null
) {
    when (order.pipelineStage) {
        SamplingPipelineStage.NEW_INTAKE -> {
            ClayButton(
                text = "Tentukan Alur Desain ->",
                style = ClayButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDetermineFlow ?: { onAdvanceStage(SamplingPipelineStage.FLOW_REVIEW) }
            )
        }
        SamplingPipelineStage.FLOW_REVIEW -> {
            ClayButton(
                text = "Alur Siap -> Mulai CAM ->",
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
}

@Composable
private fun SamplingKanbanReadOnlyBadges(order: SamplingOrder) {
    // Sinyal visual hasil QC terakhir di kolom read-only "Di Meja Finishing & QC".
    val qcResult = order.latestQcReport?.qcResult
    if (qcResult == QcInspectionResult.REWORK || qcResult == QcInspectionResult.REJECT) {
        ClayBadge(
            text = if (qcResult == QcInspectionResult.REJECT) "QC: Rajut Ulang" else "QC: Perbaikan Ulang",
            tint = if (qcResult == QcInspectionResult.REJECT) WeMadeColors.Error else WeMadeColors.Warning,
            modifier = Modifier.fillMaxWidth()
        )
    }
    ClayBadge(
        text = order.pipelineStage.displayName,
        tint = samplingStageTint(order.pipelineStage),
        modifier = Modifier.fillMaxWidth()
    )
}

