package com.eventverse.app.presentation.sampling.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.eventverse.app.domain.sampling.SamplingPipelineStage

/**
 * Koordinator drag-and-drop panel Adjust Flow — pola sama dengan
 * [SamplingDragDropState] (pointer position in window vs bounds zona drop).
 *
 * Item yang didrag: chip palet (template belum terpasang) atau chip proses terpasang.
 * Zona drop: celah antar tahap, diidentifikasi oleh jangkarnya (tahap di kiri celah).
 * Semua celah sah — tidak ada aturan "tahap berikutnya" seperti di kanban kartu.
 */
class ProcessFlowDragState {
    var isDragging by mutableStateOf(false)
        private set

    var draggedProcessId by mutableStateOf<String?>(null)
        private set

    var draggedTemplateCode by mutableStateOf<String?>(null)
        private set

    var hoveredGap by mutableStateOf<SamplingPipelineStage?>(null)
        private set

    private var dragPointerWindowPos = Offset.Zero
    private val gapBounds = mutableStateMapOf<SamplingPipelineStage, Rect>()

    fun registerGap(stage: SamplingPipelineStage, bounds: Rect) {
        gapBounds[stage] = bounds
    }

    fun unregisterGap(stage: SamplingPipelineStage) {
        gapBounds.remove(stage)
    }

    fun onDragStart(processId: String?, templateCode: String?, pointerWindowPos: Offset) {
        draggedProcessId = processId
        draggedTemplateCode = templateCode
        dragPointerWindowPos = pointerWindowPos
        isDragging = true
        updateHoveredGap()
    }

    fun onDrag(dragAmount: Offset) {
        if (!isDragging) return
        dragPointerWindowPos += dragAmount
        updateHoveredGap()
    }

    /** Commit drop: [onCommit](processId, templateCode, anchorStage) hanya bila drop di celah sah. */
    fun onDragEnd(onCommit: (processId: String?, templateCode: String?, anchor: SamplingPipelineStage?) -> Unit) {
        val anchor = hoveredGap
        val processId = draggedProcessId
        val templateCode = draggedTemplateCode
        if (anchor != null && (processId != null || templateCode != null)) {
            onCommit(processId, templateCode, anchor)
        }
        reset()
    }

    fun onDragCancel() = reset()

    private fun updateHoveredGap() {
        hoveredGap = gapBounds.entries
            .firstOrNull { it.value.contains(dragPointerWindowPos) }
            ?.key
    }

    private fun reset() {
        isDragging = false
        draggedProcessId = null
        draggedTemplateCode = null
        hoveredGap = null
        dragPointerWindowPos = Offset.Zero
    }
}

@Composable
fun rememberProcessFlowDragState(): ProcessFlowDragState = remember { ProcessFlowDragState() }