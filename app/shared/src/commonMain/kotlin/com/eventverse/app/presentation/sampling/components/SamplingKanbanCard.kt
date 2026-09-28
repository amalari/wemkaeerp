package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.currentWork
import com.eventverse.app.domain.sampling.stageFrame
import com.eventverse.app.domain.sampling.storage.DealStorageReadiness
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.presentation.deal.components.rememberMockupBitmap
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.sampling.firstWorkStage
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
    nextStage: StageCode?,
    onSelectOrder: () -> Unit,
    onAdvanceStage: (StageCode) -> Unit,
    onOpenRevisionDialog: () -> Unit,
    onApproveOrder: () -> Unit,
    onDetermineFlow: (() -> Unit)? = null,
    storageReadiness: DealStorageReadiness? = null,
    /** Kerangka yang berlaku bagi SPK ini — kerangka pabrik bila SPK belum beku. */
    frame: List<StageDefinition> = order.stageFrame
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
                    Modifier.pointerInput(order.id, order.stageCode) {
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
                        frame = frame,
                        nextStage = nextStage,
                        onAdvanceStage = onAdvanceStage,
                        onOpenRevisionDialog = onOpenRevisionDialog,
                        onApproveOrder = onApproveOrder,
                        onDetermineFlow = onDetermineFlow,
                        storageReadiness = storageReadiness
                    )
                } else {
                    SamplingKanbanReadOnlyBadges(order, frame)
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

    // Body content: Info teks di kiri, pratinjau mockup thumbnail di kanan
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (order.mockupFrontKey != null) ClaySpacing.Sm else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = order.clientName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = order.styleName,
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
            SamplingKanbanMetaBadges(order)
        }

        SamplingKanbanMockupPreview(order)
    }

    if (order.isAtOperatorDesk(order.stageFrame)) SamplingRdProgressTrack(order)
}

/**
 * Pratinjau mockup di kartu kanban: thumbnail diperbesar di sisi kanan kartu dengan
 * navigasi arrow carousel di dalam gambar untuk beralih tampak Depan/Belakang.
 */
@Composable
private fun SamplingKanbanMockupPreview(order: SamplingOrder) {
    val frontKey = order.mockupFrontKey ?: return
    val backKey = order.mockupBackKey
    var showBack by remember(order.id, backKey) { mutableStateOf(false) }
    val showingBack = showBack && backKey != null
    val bitmap = rememberMockupBitmap(if (showingBack) backKey else frontKey)

    SamplingMockupThumbnail(
        bitmap = bitmap,
        isBackView = showingBack,
        canToggle = backKey != null,
        onToggle = { showBack = !showBack }
    )
}

@Composable
private fun SamplingMockupThumbnail(
    bitmap: ImageBitmap?,
    isBackView: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit
) {
    val imageSize = 90.dp
    Box(
        modifier = Modifier
            .size(imageSize)
            .clip(ClayShapes.Tile)
            .background(WeMadeColors.SurfaceMuted)
            .clickable(enabled = canToggle, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = if (isBackView) "Mockup Tampak Belakang" else "Mockup Tampak Depan",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            IconInbox(modifier = Modifier.size(24.dp), color = WeMadeColors.OnSurfaceMuted)
        }

        if (canToggle) {
            // Tombol Arrow Kiri (Previous) di dalam gambar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .size(22.dp)
                    .clip(ClayShapes.Pill)
                    .background(WeMadeColors.SurfaceDark.copy(alpha = 0.65f))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                CarouselChevron(
                    isNext = false,
                    modifier = Modifier.size(12.dp),
                    color = WeMadeColors.Surface
                )
            }

            // Tombol Arrow Kanan (Next) di dalam gambar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 3.dp)
                    .size(22.dp)
                    .clip(ClayShapes.Pill)
                    .background(WeMadeColors.SurfaceDark.copy(alpha = 0.65f))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                CarouselChevron(
                    isNext = true,
                    modifier = Modifier.size(12.dp),
                    color = WeMadeColors.Surface
                )
            }

            // Indikator titik carousel di bawah gambar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(WeMadeColors.SurfaceDark.copy(alpha = 0.55f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (!isBackView) 10.dp else 4.dp, height = 4.dp)
                            .background(
                                color = if (!isBackView) WeMadeColors.Primary else WeMadeColors.Surface.copy(alpha = 0.6f),
                                shape = ClayShapes.Pill
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(width = if (isBackView) 10.dp else 4.dp, height = 4.dp)
                            .background(
                                color = if (isBackView) WeMadeColors.Primary else WeMadeColors.Surface.copy(alpha = 0.6f),
                                shape = ClayShapes.Pill
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselChevron(isNext: Boolean, modifier: Modifier = Modifier, color: Color = WeMadeColors.Surface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val path = Path().apply {
            if (isNext) {
                moveTo(w * 0.35f, h * 0.22f)
                lineTo(w * 0.65f, h * 0.50f)
                lineTo(w * 0.35f, h * 0.78f)
            } else {
                moveTo(w * 0.65f, h * 0.22f)
                lineTo(w * 0.35f, h * 0.50f)
                lineTo(w * 0.65f, h * 0.78f)
            }
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SamplingKanbanMetaBadges(order: SamplingOrder) {
    ClayFlowRow(spacing = ClaySpacing.Xs) {
        val sizePrefix = if (!order.sizeLabel.isNullOrBlank()) "${order.sizeLabel} • " else ""
        ClayTag(
            text = "$sizePrefix${order.sampleQuantity} Pcs",
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
        order.currentWork?.let { claim ->
            ClayTag(text = "Dikerjakan: ${claim.operatorName}", tint = WeMadeColors.Accent)
        }
    }
}

@Composable
private fun SamplingKanbanCardActions(
    order: SamplingOrder,
    frame: List<StageDefinition>,
    nextStage: StageCode?,
    onAdvanceStage: (StageCode) -> Unit,
    onOpenRevisionDialog: () -> Unit,
    onApproveOrder: () -> Unit,
    onDetermineFlow: (() -> Unit)? = null,
    storageReadiness: DealStorageReadiness? = null
) {
    val current = frame.firstOrNull { it.code == order.stageCode }
    val firstWork = frame.firstWorkStage()
    when {
        order.stageCode == INTAKE -> {
            ClayButton(
                text = "Tentukan Alur Desain ->",
                style = ClayButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDetermineFlow ?: { onAdvanceStage(FLOW_REVIEW) }
            )
        }
        order.stageCode == FLOW_REVIEW && firstWork != null -> {
            ClayButton(
                text = "Alur Siap -> Mulai ${firstWork.shortLabel} ->",
                style = ClayButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAdvanceStage(firstWork.code) }
            )
        }
        // Tahap persiapan (kerja tanpa meja operator; rajut: Program CAM) → ke lantai produksi.
        current != null && current.kind == StageKind.WORK && !current.has(StageTrait.OPERATOR_DESK) && nextStage != null -> {
            ClayButton(
                text = "Mulai Pembuatan ->",
                style = ClayButtonStyle.Accent,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAdvanceStage(nextStage) }
            )
        }
        order.stageCode == ExitStages.STORAGE -> SamplingStorageCardSection(order, storageReadiness) {
            onAdvanceStage(ExitStages.DELIVERY)
        }
        order.stageCode == ExitStages.DELIVERY -> {
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
        order.stageCode == ExitStages.APPROVED -> {
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
private fun SamplingKanbanReadOnlyBadges(order: SamplingOrder, frame: List<StageDefinition>) {
    // Sinyal visual hasil QC terakhir di kolom read-only "Di Meja Finishing & QC".
    val qcResult = order.latestQcReport?.qcResult
    if (qcResult == QcInspectionResult.REWORK || qcResult == QcInspectionResult.REJECT) {
        ClayBadge(
            text = if (qcResult == QcInspectionResult.REJECT) "QC: Rajut Ulang" else "QC: Perbaikan Ulang",
            tint = if (qcResult == QcInspectionResult.REJECT) WeMadeColors.Error else WeMadeColors.Warning,
            modifier = Modifier.fillMaxWidth()
        )
    }
    // Kartu R&D sudah menyebut tahapnya di jejak progres; badge tahap di sini jadi dobel.
    val stage = frame.firstOrNull { it.code == order.stageCode }
    if (stage != null && !stage.has(StageTrait.OPERATOR_DESK)) {
        ClayBadge(
            text = stage.displayName,
            tint = Color(stage.colorHex),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** SPK sedang di meja operator (kolom R&D) pada [frame]. */
private fun SamplingOrder.isAtOperatorDesk(frame: List<StageDefinition>): Boolean =
    frame.firstOrNull { it.code == stageCode }?.has(StageTrait.OPERATOR_DESK) == true

private val INTAKE = SamplingPipelineStage.NEW_INTAKE.toStageCode()
private val FLOW_REVIEW = SamplingPipelineStage.FLOW_REVIEW.toStageCode()
