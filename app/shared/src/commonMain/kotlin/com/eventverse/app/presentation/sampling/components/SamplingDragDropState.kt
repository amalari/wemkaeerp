package com.eventverse.app.presentation.sampling.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.presentation.designsystem.ClayKanbanDragState
import com.eventverse.app.presentation.sampling.effectiveFrame
import com.eventverse.app.presentation.sampling.firstWorkStage
import com.eventverse.app.presentation.sampling.routeOn

/**
 * State coordinator drag & drop kartu Pipeline Kanban Sampling.
 * Mengadopsi koordinator generic [ClayKanbanDragState] dari design system untuk paritas
 * dan mengurangi duplikasi logika drag koordinat root, dengan aturan transisi pipeline produksi.
 */
class SamplingDragDropState {
    private val coordinator = ClayKanbanDragState<StageCode, SamplingOrder>()

    val isDragging: Boolean get() = coordinator.isDragging
    val draggedOrder: SamplingOrder? get() = coordinator.draggedItem
    val cardInitialWindowOffset: Offset get() = coordinator.cardInitialWindowOffset
    val cardInitialSize: Size get() = coordinator.cardInitialSize
    val dragOffset: Offset get() = coordinator.dragOffset
    val hoveredStage: StageCode? get() = coordinator.hoveredColumnId

    fun registerStage(stage: StageCode, bounds: Rect) {
        coordinator.registerColumn(stage, bounds)
    }

    fun unregisterStage(stage: StageCode) {
        coordinator.unregisterColumn(stage)
    }

    fun onDragStart(order: SamplingOrder, windowOffset: Offset, size: Size, pointerOffset: Offset) {
        coordinator.canMoveCheck = { ord, target -> target in allowedTargetsFor(ord) }
        coordinator.onDragStart(order, windowOffset, size, pointerOffset)
    }

    fun onDrag(dragAmount: Offset) {
        coordinator.onDrag(dragAmount)
    }

    fun onDragEnd(onCommit: (StageCode) -> Unit) {
        coordinator.onDragEnd { target ->
            val order = draggedOrder
            if (order != null && target != order.stageCode) {
                onCommit(target)
            }
        }
    }

    fun onDragCancel() {
        coordinator.onDragCancel()
    }

    fun allowedTargetsFor(order: SamplingOrder): Set<StageCode> {
        val frame = order.effectiveFrame(tenantStages)
        return when (order.stageCode) {
            INTAKE -> setOfNotNull(FLOW_REVIEW, frame.firstWorkStage()?.code)
            ExitStages.DELIVERY, ExitStages.APPROVED -> emptySet()
            else -> setOfNotNull(order.routeOn(frame).nextAfter(order.stageCode))
        }
    }

    var tenantStages: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES

    fun floatingCardOffset(rootWindowOffset: Offset): Offset =
        coordinator.floatingCardOffset(rootWindowOffset)
}

val LocalSamplingDragDropState = compositionLocalOf<SamplingDragDropState?> { null }

@Composable
fun rememberSamplingDragDropState(): SamplingDragDropState = remember { SamplingDragDropState() }

private val INTAKE = SamplingPipelineStage.NEW_INTAKE.toStageCode()
private val FLOW_REVIEW = SamplingPipelineStage.FLOW_REVIEW.toStageCode()
