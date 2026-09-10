package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.pipeline.PipelineEdge
import com.eventverse.app.domain.pipeline.PipelineGraph

/**
 * Vertical space reserved under the stage columns as a routing corridor. Long connectors
 * (stage skips and feedback loops) run through here instead of cutting across the columns.
 */
internal val SWIMLANE_CORRIDOR_HEIGHT = 96.dp

/**
 * Card rectangles measured in the swimlane content's own coordinate space.
 *
 * The swimlane's cards have variable heights — description length, badge count, feedback
 * boxes — so unlike the node canvas their positions can't be computed up front. They're
 * measured instead, and the connector overlay draws from these.
 *
 * Everything is stored in window coordinates and rebased against the content root, so the
 * values stay correct no matter how far the swimlane is scrolled.
 */
internal class SwimlaneBounds {
    private val cardWindowRects = mutableStateMapOf<String, Rect>()

    var rootWindowOffset by mutableStateOf(Offset.Zero)
        private set
    var contentSize by mutableStateOf(Size.Zero)
        private set

    fun reportRoot(windowOffset: Offset, size: Size) {
        rootWindowOffset = windowOffset
        contentSize = size
    }

    fun reportCard(nodeId: String, windowRect: Rect) {
        cardWindowRects[nodeId] = windowRect
    }

    fun cardRect(nodeId: String): Rect? =
        cardWindowRects[nodeId]?.translate(-rootWindowOffset.x, -rootWindowOffset.y)

    fun allCardRects(): List<Rect> =
        cardWindowRects.values.map { it.translate(-rootWindowOffset.x, -rootWindowOffset.y) }

    val isReady: Boolean get() = cardWindowRects.isNotEmpty() && contentSize.height > 0f
}

/** Reports this element's bounds as the shared coordinate space for the overlay. */
internal fun Modifier.swimlaneRoot(bounds: SwimlaneBounds): Modifier =
    onGloballyPositioned { coords ->
        bounds.reportRoot(coords.positionInWindow(), coords.size.toSize())
    }

/** Reports one node card's bounds so connectors know where to attach. */
internal fun Modifier.swimlaneCard(nodeId: String, bounds: SwimlaneBounds): Modifier =
    onGloballyPositioned { coords ->
        bounds.reportCard(nodeId, Rect(coords.positionInWindow(), coords.size.toSize()))
    }

private data class RoutedEdge(
    val edge: PipelineEdge,
    val points: List<Offset>,
    val color: Color,
    val isFeedback: Boolean
)

/**
 * Draws every connector in the swimlane from resolved [PipelineGraph] edges — replacing the
 * decorative hand-off arrows, which were never tied to the data.
 *
 * Routing rules, all derived from the measured card rectangles so nothing is hardcoded per
 * stage, and none of them can cross a card:
 * - Same column, next card down: a straight vertical drop between the two cards.
 * - Neighbouring columns: out of the right edge, through the (always empty) column gap, into
 *   the target's left edge.
 * - Skipping a column, or looping backwards (a QC-failure rework route): down into the
 *   corridor reserved beneath the columns, along it, and back up into the target.
 */
@Composable
internal fun SwimlaneConnectionCanvas(
    graph: PipelineGraph,
    bounds: SwimlaneBounds,
    selectedNodeId: String?,
    isPresentationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (!bounds.isReady) return@Canvas

        val allRects = bounds.allCardRects()
        val contentH = bounds.contentSize.height
        val corridorReserve = SWIMLANE_CORRIDOR_HEIGHT.toPx()
        val laneOffset = 13.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))

        // Attachment points are spread down each card's edge so several connectors sharing a
        // card don't stack on the exact same pixel.
        val outIndex = mutableMapOf<String, Int>()
        val inIndex = mutableMapOf<String, Int>()
        val outCount = graph.edges.groupingBy { it.fromNodeId }.eachCount()
        val inCount = graph.edges.groupingBy { it.toNodeId }.eachCount()

        val routed = graph.edges.mapIndexedNotNull { edgeIndex, edge ->
            val from = bounds.cardRect(edge.fromNodeId) ?: return@mapIndexedNotNull null
            val to = bounds.cardRect(edge.toNodeId) ?: return@mapIndexedNotNull null

            val oi = outIndex.getOrElse(edge.fromNodeId) { 0 }
            val ii = inIndex.getOrElse(edge.toNodeId) { 0 }
            outIndex[edge.fromNodeId] = oi + 1
            inIndex[edge.toNodeId] = ii + 1

            val exitY = from.top + from.height * (oi + 1f) / ((outCount[edge.fromNodeId] ?: 1) + 1f)
            val entryY = to.top + to.height * (ii + 1f) / ((inCount[edge.toNodeId] ?: 1) + 1f)
            val jitter = ((edgeIndex % 5) - 2) * 5.dp.toPx()

            val isFeedback = edge.isFeedback
            val corridorY = if (isFeedback) {
                contentH - corridorReserve * 0.34f + jitter
            } else {
                contentH - corridorReserve * 0.74f + jitter
            }

            val points = routeSwimlaneEdge(
                from = from,
                to = to,
                exitY = exitY,
                entryY = entryY,
                allRects = allRects,
                corridorY = corridorY,
                laneOffset = laneOffset,
                jitter = jitter
            )

            val color = if (isFeedback) {
                Color(edge.edgeType.colorHex)
            } else {
                graph.nodes.firstOrNull { it.id == edge.fromNodeId }
                    ?.let { Color(it.stage.colorHex) } ?: Color(0xFF2563EB)
            }

            RoutedEdge(edge, points, color, isFeedback)
        }

        routed.forEach { route ->
            val touchesSelection = selectedNodeId != null &&
                (route.edge.fromNodeId == selectedNodeId || route.edge.toNodeId == selectedNodeId)
            val alpha = when {
                selectedNodeId == null -> if (route.isFeedback) 0.9f else 0.75f
                touchesSelection -> 1f
                else -> 0.15f
            }
            val color = route.color.copy(alpha = alpha)
            val width = when {
                touchesSelection -> 2.6.dp.toPx()
                route.isFeedback -> 1.9.dp.toPx()
                else -> 1.7.dp.toPx()
            }

            val path = Path().apply {
                moveTo(route.points[0].x, route.points[0].y)
                for (i in 1 until route.points.size) lineTo(route.points[i].x, route.points[i].y)
            }
            drawPath(
                path,
                color = color,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (route.isFeedback) dash else null
                )
            )

            val tip = route.points.last()
            val prev = route.points[route.points.size - 2]
            drawDirectionalArrow(tip, prev, color, 4.6.dp.toPx())

            // A dot at the source end reads as "leaves here", mirroring the node canvas.
            drawCircle(color = color, radius = 3.dp.toPx(), center = route.points.first())
        }
    }
}

/**
 * Waypoints for one swimlane connector. Vertical runs stay inside column gaps and horizontal
 * runs stay inside the corridor beneath the columns, so no segment can land on a card.
 */
private fun routeSwimlaneEdge(
    from: Rect,
    to: Rect,
    exitY: Float,
    entryY: Float,
    allRects: List<Rect>,
    corridorY: Float,
    laneOffset: Float,
    jitter: Float
): List<Offset> {
    val sameColumn = kotlin.math.abs(from.left - to.left) < 4f

    // Straight drop to the next card in the same stage column.
    if (sameColumn && to.top >= from.bottom - 1f) {
        val x = from.center.x + jitter * 0.4f
        return listOf(Offset(x, from.bottom), Offset(x, to.top))
    }

    val goesRight = to.left >= from.right - 1f
    if (goesRight) {
        // Any card sitting horizontally between the two means a column is being skipped.
        val blocked = allRects.any { it.right > from.right + 1f && it.left < to.left - 1f }
        if (!blocked) {
            val laneX = (from.right + to.left) / 2f + jitter * 0.5f
            return listOf(
                Offset(from.right, exitY),
                Offset(laneX, exitY),
                Offset(laneX, entryY),
                Offset(to.left, entryY)
            )
        }
    }

    // Column skip or a backward feedback loop: drop into the corridor and travel there.
    val exitX = from.right + laneOffset
    val entryX = to.left - laneOffset
    return listOf(
        Offset(from.right, exitY),
        Offset(exitX, exitY),
        Offset(exitX, corridorY),
        Offset(entryX, corridorY),
        Offset(entryX, entryY),
        Offset(to.left, entryY)
    )
}

/** Filled arrowhead pointing along the connector's final segment, whatever its direction. */
private fun DrawScope.drawDirectionalArrow(tip: Offset, from: Offset, color: Color, size: Float) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (len < 0.01f) return

    val ux = dx / len
    val uy = dy / len
    val perpX = -uy
    val perpY = ux
    val baseX = tip.x - ux * size * 1.9f
    val baseY = tip.y - uy * size * 1.9f

    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + perpX * size, baseY + perpY * size)
        lineTo(baseX - perpX * size, baseY - perpY * size)
        close()
    }
    drawPath(head, color = color)
}
