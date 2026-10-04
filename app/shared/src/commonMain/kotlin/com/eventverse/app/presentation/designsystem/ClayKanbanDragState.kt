package com.eventverse.app.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Model data kolom untuk papan kanban generic [ClayKanbanBoard].
 */
data class ClayKanbanColumnModel<ID : Any, T : Any>(
    val id: ID,
    val title: String,
    val items: List<T>,
    val tint: Color = WeMadeColors.Primary,
    val subtitle: String? = null
)

/**
 * State coordinator drag & drop papan kanban generic (pola Jira floating overlay di root board).
 * Buta domain: hanya mencatat posisi window, item yang diangkat, offset drag, dan target kolom.
 */
class ClayKanbanDragState<ID : Any, T : Any> {
    var isDragging by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<T?>(null)
        private set
    var cardInitialWindowOffset by mutableStateOf(Offset.Zero)
        private set
    var cardInitialSize by mutableStateOf(Size.Zero)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var hoveredColumnId by mutableStateOf<ID?>(null)
        private set

    private var startPointerOffset = Offset.Zero
    private val columnBounds = mutableStateMapOf<ID, Rect>()

    var canMoveCheck: (item: T, toColumnId: ID) -> Boolean = { _, _ -> true }

    fun registerColumn(columnId: ID, bounds: Rect) {
        columnBounds[columnId] = bounds
    }

    fun unregisterColumn(columnId: ID) {
        columnBounds.remove(columnId)
    }

    fun onDragStart(item: T, windowOffset: Offset, size: Size, pointerOffset: Offset) {
        draggedItem = item
        cardInitialWindowOffset = windowOffset
        cardInitialSize = size
        startPointerOffset = pointerOffset
        dragOffset = Offset.Zero
        isDragging = true
        updateHovered()
    }

    fun onDrag(amount: Offset) {
        dragOffset += amount
        updateHovered()
    }

    fun onDragEnd(onCommit: (toColumnId: ID) -> Unit) {
        val target = hoveredColumnId
        val item = draggedItem
        if (target != null && item != null) {
            onCommit(target)
        }
        reset()
    }

    fun onDragCancel() {
        reset()
    }

    fun reset() {
        isDragging = false
        draggedItem = null
        dragOffset = Offset.Zero
        hoveredColumnId = null
        startPointerOffset = Offset.Zero
    }

    private fun updateHovered() {
        val item = draggedItem ?: return
        val currentPointerWindowPos = cardInitialWindowOffset + startPointerOffset + dragOffset
        hoveredColumnId = columnBounds.entries.firstOrNull { (id, rect) ->
            rect.contains(currentPointerWindowPos) && canMoveCheck(item, id)
        }?.key
    }

    fun floatingCardOffset(rootWindowOffset: Offset): Offset {
        val currentCardWindowPos = cardInitialWindowOffset + dragOffset
        return currentCardWindowPos - rootWindowOffset
    }
}

@Composable
fun <ID : Any, T : Any> rememberClayKanbanDragState(
    canMove: (item: T, toColumnId: ID) -> Boolean = { _, _ -> true }
): ClayKanbanDragState<ID, T> {
    val state = remember { ClayKanbanDragState<ID, T>() }
    state.canMoveCheck = canMove
    return state
}
