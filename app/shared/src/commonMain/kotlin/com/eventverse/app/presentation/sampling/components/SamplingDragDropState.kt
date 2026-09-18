package com.eventverse.app.presentation.sampling.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage

/**
 * State coordinator drag & drop kartu Pipeline Kanban Sampling — pola yang sama dengan
 * Kanban CRM (kartu melayang di tingkat Board, tidak pernah ter-clip kolom), dengan satu
 * aturan tambahan khas pipeline produksi:
 *
 * **Kartu hanya boleh di-drop ke container TAHAP BERIKUTNYA** (`order == source + 1`).
 * Loncat tahap (mis. SPK Baru langsung ke Rajut) tidak pernah menjadi drop target sah —
 * kolomnya di-dim dan drop di atasnya diabaikan.
 *
 * Zona drop yang sah juga menghormati batas ranah divisi: kolom grup Finishing & QC hanya
 * menermapa drop dari Mesin Rajut (serah terima ke Finishing), dan kolom ACC Buyer hanya
 * dari Finishing & QC (keputusan kirim ke buyer).
 */
class SamplingDragDropState {
    var isDragging by mutableStateOf(false)
        private set

    var draggedOrder by mutableStateOf<SamplingOrder?>(null)
        private set

    var cardInitialWindowOffset by mutableStateOf(Offset.Zero)
        private set

    var cardInitialSize by mutableStateOf(Size.Zero)
        private set

    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    var hoveredStage by mutableStateOf<SamplingPipelineStage?>(null)
        private set

    private var startPointerOffset = Offset.Zero
    private val stageBounds = mutableStateMapOf<SamplingPipelineStage, Rect>()

    fun registerStage(stage: SamplingPipelineStage, bounds: Rect) {
        stageBounds[stage] = bounds
    }

    fun unregisterStage(stage: SamplingPipelineStage) {
        stageBounds.remove(stage)
    }

    fun onDragStart(order: SamplingOrder, windowOffset: Offset, size: Size, pointerOffset: Offset) {
        draggedOrder = order
        cardInitialWindowOffset = windowOffset
        cardInitialSize = size
        startPointerOffset = pointerOffset
        dragOffset = Offset.Zero
        isDragging = true
        updateHoveredStage()
    }

    fun onDrag(dragAmount: Offset) {
        dragOffset += dragAmount
        updateHoveredStage()
    }

    fun onDragEnd(onCommit: (SamplingPipelineStage) -> Unit) {
        val target = hoveredStage
        val order = draggedOrder
        if (target != null && order != null && target != order.pipelineStage) {
            onCommit(target)
        }
        reset()
    }

    fun onDragCancel() {
        reset()
    }

    /** Tahap tujuan yang sah untuk kartu ini — CUMA tahap berikutnya, tidak ada loncatan. */
    fun allowedTargetsFor(order: SamplingOrder): Set<SamplingPipelineStage> = when (order.pipelineStage) {
        SamplingPipelineStage.NEW_INTAKE -> setOf(SamplingPipelineStage.CAM_PROGRAMMING)
        SamplingPipelineStage.CAM_PROGRAMMING -> setOf(SamplingPipelineStage.MACHINE_KNITTING)
        SamplingPipelineStage.MACHINE_KNITTING -> setOf(SamplingPipelineStage.LINKING_ASSEMBLY)
        SamplingPipelineStage.FINISHING_QC -> setOf(SamplingPipelineStage.IN_DELIVERY)
        else -> emptySet()
    }

    private fun reset() {
        isDragging = false
        draggedOrder = null
        dragOffset = Offset.Zero
        hoveredStage = null
        startPointerOffset = Offset.Zero
    }

    private fun updateHoveredStage() {
        val order = draggedOrder ?: return
        val allowed = allowedTargetsFor(order)
        val currentPointerWindowPos = cardInitialWindowOffset + startPointerOffset + dragOffset
        hoveredStage = stageBounds.entries.firstOrNull { (stage, rect) ->
            rect.contains(currentPointerWindowPos) && stage in allowed
        }?.key
    }

    /** Offset kartu melayang relatif terhadap root board. */
    fun floatingCardOffset(rootWindowOffset: Offset): Offset {
        val currentCardWindowPos = cardInitialWindowOffset + dragOffset
        return currentCardWindowPos - rootWindowOffset
    }
}

val LocalSamplingDragDropState = compositionLocalOf<SamplingDragDropState?> { null }

@Composable
fun rememberSamplingDragDropState(): SamplingDragDropState = remember { SamplingDragDropState() }
