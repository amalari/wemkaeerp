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
import com.eventverse.app.domain.stageflow.StageCode

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

    /** Celah yang sedang dihover drag — kuncinya [slotId] unik, bukan tahap. */
    var hoveredGapId by mutableStateOf<String?>(null)
        private set

    /** Label chip yang sedang diseret — dirender ulang sebagai ghost yang mengikuti pointer. */
    var draggedLabel by mutableStateOf("")
        private set

    /** Posisi pointer (koordinat window) — state agar ghost ikut bergerak tiap frame drag. */
    var dragPointerWindowPos by mutableStateOf(Offset.Zero)
        private set

    /**
     * Zona drop dikunci per [slotId] unik, bukan per tahap: satu tahap kini bisa punya
     * beberapa celah (sebelum/di antara/sesudah proses opsionalnya) yang semuanya
     * berjangkar ke tahap yang sama. Nilainya pasangan (batas, jangkar tahap).
     */
    private val gapBounds = mutableStateMapOf<String, Pair<Rect, StageCode>>()

    fun registerGap(slotId: String, anchor: StageCode, bounds: Rect) {
        gapBounds[slotId] = bounds to anchor
    }

    fun onDragStart(processId: String?, templateCode: String?, label: String, pointerWindowPos: Offset) {
        draggedLabel = label
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
    fun onDragEnd(onCommit: (processId: String?, templateCode: String?, anchor: StageCode?) -> Unit) {
        val anchor = hoveredGapId?.let { gapBounds[it]?.second }
        val processId = draggedProcessId
        val templateCode = draggedTemplateCode
        if (anchor != null && (processId != null || templateCode != null)) {
            onCommit(processId, templateCode, anchor)
        }
        reset()
    }

    fun onDragCancel() = reset()

    private fun updateHoveredGap() {
        hoveredGapId = gapBounds.entries
            .firstOrNull { it.value.first.contains(dragPointerWindowPos) }
            ?.key
    }

    private fun reset() {
        isDragging = false
        draggedProcessId = null
        draggedTemplateCode = null
        hoveredGapId = null
        draggedLabel = ""
        dragPointerWindowPos = Offset.Zero
    }
}

@Composable
fun rememberProcessFlowDragState(): ProcessFlowDragState = remember { ProcessFlowDragState() }