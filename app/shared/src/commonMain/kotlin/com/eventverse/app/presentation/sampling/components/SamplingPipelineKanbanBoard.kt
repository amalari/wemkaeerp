package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.RowScope
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.presentation.sampling.effectiveFrame
import kotlin.math.roundToInt
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.storage.dealStorageReadiness
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.roundToInt

private val NARROW_BOARD_BREAKPOINT = 1100.dp

/**
 * Pipeline Kanban Sampling — full width, drag & drop ala Jira.
 *
 * Aturan main:
 * - Kartu HANYA bisa di-drop ke container tahap BERIKUTNYA ([SamplingDragDropState]).
 * - Transisi yang menuntut lembar kerja (CAM -> R&D lewat section Program di Detail SPK)
 *   diputuskan layar lewat [onAdvanceStageRequested].
 * - Klik kartu di kolom "SPK Baru" membuka dialog detail SPK ([onOpenSpkDetail]) — meja
 *   persiapan Program CAM tim sampling.
 * - Kolom yang menerima hover kartu sah di-highlight; kolom ilegal hanya redup.
 */
@Composable
fun SamplingPipelineKanbanBoard(
    orders: List<SamplingOrder>,
    selectedOrderId: SamplingOrderId?,
    onSelectOrder: (SamplingOrderId) -> Unit,
    onOpenSpkDetail: (SamplingOrder, Boolean) -> Unit,
    onAdvanceStageRequested: (SamplingOrder, StageCode) -> Unit,
    onOpenRevisionDialog: (SamplingOrder) -> Unit,
    onApproveOrder: (SamplingOrderId, String) -> Unit,
    modifier: Modifier = Modifier,
    /** Kerangka tahap pabrik — sumber kolom papan (TRD-FLOW-001). */
    stageFlow: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES,
    /** Seluruh SPK tanpa saring — kelengkapan deal di kolom Penyimpanan tidak boleh ikut tersaring. */
    allOrders: List<SamplingOrder> = orders
) {
    val dragState = rememberSamplingDragDropState()
    dragState.tenantStages = stageFlow
    val zones = remember(stageFlow) { samplingStageZones(stageFlow) }
    var rootWindowOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) rootWindowOffset = coords.positionInWindow()
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= NARROW_BOARD_BREAKPOINT
            val scrollState = rememberScrollState()
            val rowModifier = if (isWide) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().horizontalScroll(scrollState)
            }

            CompositionLocalProvider(LocalSamplingDragDropState provides dragState) {
                Row(
                    modifier = rowModifier.padding(ClaySpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    zones.forEach { zone ->
                        KanbanStageZoneColumn(
                            zone = zone,
                            orders = orders.filter { it.stageCode in zone.stages },
                            stageFlow = stageFlow,
                            selectedOrderId = selectedOrderId,
                            dragState = dragState,
                            isWide = isWide,
                            onSelectOrder = onSelectOrder,
                            onOpenSpkDetail = onOpenSpkDetail,
                            onAdvanceStageRequested = onAdvanceStageRequested,
                            onOpenRevisionDialog = onOpenRevisionDialog,
                            onApproveOrder = onApproveOrder,
                            allOrders = allOrders
                        )
                    }
                }
            }

            // Overlay kartu melayang ala Jira — dirender di atas seluruh board, tidak ter-clip kolom.
            val draggedOrder = dragState.draggedOrder
            if (dragState.isDragging && draggedOrder != null) {
                FloatingDragCard(
                    order = draggedOrder,
                    dragState = dragState,
                    rootWindowOffset = rootWindowOffset
                )
            }
        }
    }
}

@Composable
private fun RowScope.KanbanStageZoneColumn(
    zone: SamplingStageZone,
    orders: List<SamplingOrder>,
    stageFlow: List<StageDefinition>,
    selectedOrderId: SamplingOrderId?,
    dragState: SamplingDragDropState,
    isWide: Boolean,
    onSelectOrder: (SamplingOrderId) -> Unit,
    onOpenSpkDetail: (SamplingOrder, Boolean) -> Unit,
    onAdvanceStageRequested: (SamplingOrder, StageCode) -> Unit,
    onOpenRevisionDialog: (SamplingOrder) -> Unit,
    onApproveOrder: (SamplingOrderId, String) -> Unit,
    allOrders: List<SamplingOrder>
) {
    val columnModifier = if (isWide) {
        Modifier.weight(1f).fillMaxHeight()
    } else {
        Modifier.width(300.dp).fillMaxHeight()
    }
    val isHovered = dragState.isDragging && dragState.hoveredStage == zone.dropStage
    val isLegalTarget = dragState.isDragging && dragState.draggedOrder?.let {
        zone.dropStage in dragState.allowedTargetsFor(it)
    } == true

    var rdStageFilter by remember { mutableStateOf<StageCode?>(null) }
    val visibleOrders = rdStageFilter?.let { stage -> orders.filter { it.stageCode == stage } } ?: orders

    // Daftarkan batas zona drop setiap layout berubah — hit-test drag memakai window coordinate.
    Column(
        modifier = columnModifier
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    val origin = coords.positionInWindow()
                    dragState.registerStage(
                        zone.dropStage,
                        Rect(origin, Offset(origin.x + coords.size.width, origin.y + coords.size.height))
                    )
                }
            }
            .clayFlat(
                shape = ClayShapes.Card,
                background = if (isHovered) {
                    WeMadeColors.Primary.copy(alpha = 0.08f)
                } else {
                    WeMadeColors.SurfaceMuted
                },
                outline = when {
                    isHovered -> WeMadeColors.Primary
                    isLegalTarget -> WeMadeColors.Primary.copy(alpha = 0.45f)
                    dragState.isDragging -> WeMadeColors.Outline.copy(alpha = 0.4f)
                    else -> WeMadeColors.Outline
                }
            )
            .padding(ClaySpacing.Sm)
    ) {
        // Zona header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = zone.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = zone.subtitle,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ClayBadge(
                text = "${orders.size}",
                tint = Color(zone.tintHex)
            )
        }

        if (zone.kind == SamplingZoneKind.RND && orders.isNotEmpty()) {
            SamplingRdStageFilterRow(
                orders = orders,
                stages = stageFlow.filter { it.code in zone.stages },
                selected = rdStageFilter,
                onSelect = { rdStageFilter = it }
            )
            Spacer(Modifier.height(ClaySpacing.Sm))
        }

        // Daftar kartu
        val verticalScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(verticalScrollState),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            if (visibleOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(WeMadeColors.Surface.copy(alpha = 0.5f), ClayShapes.Tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (dragState.isDragging && isLegalTarget) "Lepas di sini" else "Kosong",
                        fontSize = 11.sp,
                        color = if (dragState.isDragging && isLegalTarget) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                visibleOrders.forEach { order ->
                    val nextStage = dragState.allowedTargetsFor(order).firstOrNull()
                    SamplingKanbanCard(
                        order = order,
                        frame = order.effectiveFrame(stageFlow),
                        isSelected = order.id == selectedOrderId,
                        showActions = zone.showActions,
                        nextStage = nextStage,
                        onAdvanceStage = { target -> onAdvanceStageRequested(order, target) },
                        onOpenRevisionDialog = { onOpenRevisionDialog(order) },
                        onApproveOrder = { onApproveOrder(order.id, "ACC Golden Sample") },
                        // Klik kartu: membuka dialog detail SPK untuk memeriksa data (detail saja).
                        onSelectOrder = {
                            onSelectOrder(order.id)
                            onOpenSpkDetail(order, false)
                        },
                        // Tombol tentukan alur: membuka dialog detail SPK langsung ke section alur proses
                        onDetermineFlow = {
                            onSelectOrder(order.id)
                            onOpenSpkDetail(order, true)
                        },
                        storageReadiness = order.dealId?.takeIf { zone.kind == SamplingZoneKind.STORAGE }
                            ?.let { dealId -> dealStorageReadiness(allOrders.filter { it.dealId == dealId }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingDragCard(
    order: SamplingOrder,
    dragState: SamplingDragDropState,
    rootWindowOffset: Offset
) {
    val density = LocalDensity.current
    val floatingOffset = dragState.floatingCardOffset(rootWindowOffset)
    val cardWidth = if (dragState.cardInitialSize.width > 0f) {
        with(density) { dragState.cardInitialSize.width.toDp() }
    } else {
        280.dp
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(floatingOffset.x.roundToInt(), floatingOffset.y.roundToInt()) }
            .width(cardWidth)
            .zIndex(999f)
            .graphicsLayer {
                rotationZ = -2.5f
                scaleX = 1.02f
                scaleY = 1.02f
                alpha = 0.95f
            }
    ) {
        SamplingKanbanCard(
            order = order,
            isSelected = false,
            showActions = false,
            nextStage = null,
            onSelectOrder = {},
            onAdvanceStage = {},
            onOpenRevisionDialog = {},
            onApproveOrder = {}
        )
    }
}

