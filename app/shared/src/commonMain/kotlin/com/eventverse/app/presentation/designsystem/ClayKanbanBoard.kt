package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.roundToInt


/**
 * Papan kanban generic Claymorphism (designsystem-rules & Aturan Tiga Kali).
 *
 * Fitur:
 * - Buta domain: hanya mengelola kolom, item kartu, dan gestur pemindahan.
 * - Drag & drop dengan floating overlay ala Jira di root koordinat (tidak pernah ter-clip kolom).
 * - Jalur aksesibilitas & mobile: ketuk kartu membuka menu "Pindah ke…".
 * - Highlight kolom drop target yang valid saat hover.
 * - Kolom kosong tetap bisa menerima drop target.
 */
@Composable
fun <ID : Any, T : Any> ClayKanbanBoard(
    columns: List<ClayKanbanColumnModel<ID, T>>,
    itemId: (T) -> String,
    onMove: (item: T, toColumnId: ID) -> Unit,
    canMove: (item: T, toColumnId: ID) -> Boolean,
    modifier: Modifier = Modifier,
    dragState: ClayKanbanDragState<ID, T> = rememberClayKanbanDragState(canMove),
    emptyText: String = "Kosong",
    columnWidth: Dp = 280.dp,
    onCardClick: ((T) -> Unit)? = null,
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit
) {
    dragState.canMoveCheck = canMove
    var rootWindowOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) rootWindowOffset = coords.positionInWindow()
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalColumnsWidth = columnWidth * columns.size + ClaySpacing.Md * (columns.size - 1)
            val needsHorizontalScroll = maxWidth < totalColumnsWidth
            val scrollState = rememberScrollState()

            // Papan dirancang untuk wadah bertinggi terbatas (layar penuh). Di wadah tak terbatas — mis. bingkai
            // prototype di dalam kartu yang bisa di-scroll — `fillMaxHeight`/`weight(1f)`/`LazyColumn` runtuh jadi
            // tinggi nol dan hanya header kolom yang terlihat. Karena itu layout mengikuti constraint yang diterima.
            val bounded = constraints.hasBoundedHeight
            val sized = if (bounded) Modifier.fillMaxSize() else Modifier
            val rowModifier = if (needsHorizontalScroll) sized.horizontalScroll(scrollState) else sized

            Row(
                modifier = rowModifier.padding(ClaySpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                columns.forEach { column ->
                    val isHovered = dragState.isDragging && dragState.hoveredColumnId == column.id
                    val isLegalTarget = dragState.isDragging && dragState.draggedItem?.let {
                        canMove(it, column.id)
                    } == true
                    val holdsDragged = column.items.any { itemId(it) == dragState.draggedItem?.let(itemId) }

                    val colBase = if (needsHorizontalScroll) Modifier.width(columnWidth) else Modifier.weight(1f)
                    val colModifier = if (bounded) colBase.fillMaxHeight() else colBase

                    Column(
                        modifier = colModifier
                            .zIndex(if (holdsDragged) 1f else 0f)
                            .onGloballyPositioned { coords ->
                                if (coords.isAttached) {
                                    dragState.registerColumn(column.id, coords.boundsInWindow())
                                }
                            }
                            .clayFlat(
                                shape = ClayShapes.Panel,
                                background = if (isHovered) {
                                    column.tint.copy(alpha = 0.16f)
                                } else if (isLegalTarget) {
                                    column.tint.copy(alpha = 0.08f)
                                } else {
                                    column.tint.copy(alpha = 0.04f)
                                },
                                outline = when {
                                    isHovered -> column.tint
                                    isLegalTarget -> column.tint.copy(alpha = 0.6f)
                                    dragState.isDragging -> WeMadeColors.Outline.copy(alpha = 0.35f)
                                    else -> column.tint.copy(alpha = 0.25f)
                                },
                                borderWidth = if (isHovered) ClayBorder.Thick else ClayBorder.Medium
                            )
                            .padding(ClaySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        // Header kolom
                        Text(
                            text = "${column.title} · ${column.items.size}",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            color = WeMadeColors.OnSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        column.subtitle?.let { sub ->
                            Text(
                                text = sub,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = WeMadeColors.OnSurfaceMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        // Isi daftar kartu
                        if (column.items.isEmpty()) {
                            Box(
                                modifier = if (bounded) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().padding(ClaySpacing.Lg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emptyText,
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            if (bounded) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                    contentPadding = PaddingValues(bottom = ClaySpacing.Md)
                                ) {
                                    items(items = column.items, key = itemId) { item ->
                                        KanbanCardWrapper(
                                            item = item,
                                            itemId = itemId(item),
                                            columns = columns,
                                            canMove = canMove,
                                            onMove = onMove,
                                            dragState = dragState,
                                            onCardClick = onCardClick,
                                            content = itemContent
                                        )
                                    }
                                }
                            } else {
                                // Tinggi tak terbatas: daftar biasa (tinggi mengikuti isi), bukan LazyColumn.
                                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                                    column.items.forEach { item ->
                                        key(itemId(item)) {
                                            KanbanCardWrapper(
                                                item = item,
                                                itemId = itemId(item),
                                                columns = columns,
                                                canMove = canMove,
                                                onMove = onMove,
                                                dragState = dragState,
                                                onCardClick = onCardClick,
                                                content = itemContent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating drag card overlay di root koordinat board
            val dragged = dragState.draggedItem
            if (dragState.isDragging && dragged != null) {
                FloatingDragCardOverlay(
                    item = dragged,
                    dragState = dragState,
                    rootWindowOffset = rootWindowOffset,
                    content = itemContent
                )
            }
        }
    }
}

@Composable
private fun <ID : Any, T : Any> KanbanCardWrapper(
    item: T,
    itemId: String,
    columns: List<ClayKanbanColumnModel<ID, T>>,
    canMove: (item: T, toColumnId: ID) -> Boolean,
    onMove: (item: T, toColumnId: ID) -> Unit,
    dragState: ClayKanbanDragState<ID, T>,
    onCardClick: ((T) -> Unit)?,
    content: @Composable (item: T, isDragging: Boolean) -> Unit
) {
    var cardWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(Size.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    val isBeingDragged = dragState.isDragging && dragState.draggedItem?.let { it == item } == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    cardWindowOffset = coords.positionInWindow()
                    cardSize = coords.size.toSize()
                }
            }
            .alpha(if (isBeingDragged) 0.35f else 1f)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(itemId) {
                detectTapGestures(
                    onTap = {
                        if (onCardClick != null) onCardClick(item) else menuOpen = true
                    },
                    onLongPress = { menuOpen = true }
                )
            }
            .pointerInput(itemId) {
                detectDragGestures(
                    onDragStart = { grabOffset ->
                        menuOpen = false
                        dragState.onDragStart(item, cardWindowOffset, cardSize, grabOffset)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragState.onDrag(amount)
                    },
                    onDragEnd = {
                        dragState.onDragEnd { targetColId -> onMove(item, targetColId) }
                    },
                    onDragCancel = {
                        dragState.onDragCancel()
                    }
                )
            }
    ) {
        content(item, isBeingDragged)

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val allowedColumns = columns.filter { canMove(item, it.id) }
            if (allowedColumns.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Tidak ada perpindahan yang diizinkan") },
                    onClick = { menuOpen = false },
                    enabled = false
                )
            } else {
                allowedColumns.forEach { col ->
                    DropdownMenuItem(
                        text = { Text("Pindah ke ${col.title}") },
                        onClick = {
                            menuOpen = false
                            onMove(item, col.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun <ID : Any, T : Any> FloatingDragCardOverlay(
    item: T,
    dragState: ClayKanbanDragState<ID, T>,
    rootWindowOffset: Offset,
    content: @Composable (item: T, isDragging: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val floatingOffset = dragState.floatingCardOffset(rootWindowOffset)
    val cardWidth = if (dragState.cardInitialSize.width > 0f) {
        with(density) { dragState.cardInitialSize.width.toDp() }
    } else {
        260.dp
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
        content(item, true)
    }
}
