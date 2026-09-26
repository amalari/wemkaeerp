package com.eventverse.app.presentation.crm.components

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
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadStage

/**
 * State coordinator untuk interaksi Drag & Drop kartu Kanban CRM ala Jira.
 *
 * Mengelola kartu yang sedang melayang di tingkat Board sehingga tidak terpotong (clipped)
 * oleh LazyColumn atau batas kolom, serta mendeteksi drop target per kolom stage.
 */
class CrmDragDropState {
    var isDragging by mutableStateOf(false)
        private set

    var draggedLead by mutableStateOf<CrmLead?>(null)
        private set

    var cardInitialWindowOffset by mutableStateOf(Offset.Zero)
        private set

    var cardInitialSize by mutableStateOf(Size.Zero)
        private set

    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    var hoveredStage by mutableStateOf<LeadStage?>(null)
        private set

    private var startPointerOffset = Offset.Zero
    private val columnBounds = mutableStateMapOf<LeadStage, Rect>()

    fun registerColumn(stage: LeadStage, bounds: Rect) {
        columnBounds[stage] = bounds
    }

    fun unregisterColumn(stage: LeadStage) {
        columnBounds.remove(stage)
    }

    fun onDragStart(lead: CrmLead, windowOffset: Offset, size: Size, pointerOffset: Offset) {
        draggedLead = lead
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

    fun onDragEnd(onCommit: (LeadStage) -> Unit) {
        val target = hoveredStage
        val lead = draggedLead
        if (target != null && lead != null && target != lead.stage) {
            onCommit(target)
        }
        reset()
    }

    fun onDragCancel() {
        reset()
    }

    private fun reset() {
        isDragging = false
        draggedLead = null
        dragOffset = Offset.Zero
        hoveredStage = null
        startPointerOffset = Offset.Zero
    }

    private fun updateHoveredStage() {
        val currentPointerWindowPos = cardInitialWindowOffset + startPointerOffset + dragOffset
        val hit = columnBounds.entries.firstOrNull { (_, rect) ->
            rect.contains(currentPointerWindowPos)
        }?.key
        hoveredStage = hit
    }

    /**
     * Menghitung offset relatif kartu yang melayang terhadap root board window offset.
     */
    fun floatingCardOffset(rootWindowOffset: Offset): Offset {
        val currentCardWindowPos = cardInitialWindowOffset + dragOffset
        return currentCardWindowPos - rootWindowOffset
    }
}

val LocalCrmDragDropState = compositionLocalOf<CrmDragDropState?> { null }

@Composable
fun rememberCrmDragDropState(): CrmDragDropState = remember { CrmDragDropState() }
