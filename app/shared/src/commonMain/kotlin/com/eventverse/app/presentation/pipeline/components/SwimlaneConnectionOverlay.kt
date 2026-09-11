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
internal val SWIMLANE_CORRIDOR_HEIGHT = 108.dp

/**
 * Horizontal space reserved to the right of the final stage column as a routing corridor.
 * Multi-lane feedback loops and defect return lines exiting the last stage drop down
 * through here into the bottom corridor without being clipped at the canvas edge.
 */
internal val SWIMLANE_CORRIDOR_END_PADDING = 96.dp

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
    val path: Path,
    val color: Color,
    val isFeedback: Boolean,
    val startPoint: Offset,
    val tip: Offset,
    val tipPrev: Offset
)

/**
 * Draws every connector in the swimlane from resolved [PipelineGraph] edges using n8n-style
 * smooth cubic Bézier curves and multi-lane collision-free corridor routing:
 * - Same column, next card down: straight vertical drop between cards with downward arrow.
 * - Forward connections (left-to-right columns): smooth horizontal cubic Bézier curves (S-curves)
 *   that fan out gracefully so parallel links never collapse into a single vertical line.
 * - Feedback loops & column skips: multi-lane dedicated channel allocation (unique vertical drops,
 *   unique horizontal corridor tracks, and unique vertical entries) with rounded fillet corners
 *   so lines never overlap.
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
        val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))

        // Attachment points are spread down each card's edge so several connectors sharing a
        // card don't stack on the exact same pixel.
        val outCount = graph.edges.groupingBy { it.fromNodeId }.eachCount()
        val inCount = graph.edges.groupingBy { it.toNodeId }.eachCount()
        val outIndex = mutableMapOf<String, Int>()
        val inIndex = mutableMapOf<String, Int>()

        data class EdgeGeometry(
            val edge: PipelineEdge,
            val from: Rect,
            val to: Rect,
            val exitY: Float,
            val entryY: Float,
            val isSameColumn: Boolean,
            val isUnobstructedForward: Boolean
        )

        val resolved = graph.edges.mapNotNull { edge ->
            val from = bounds.cardRect(edge.fromNodeId) ?: return@mapNotNull null
            val to = bounds.cardRect(edge.toNodeId) ?: return@mapNotNull null

            val oi = outIndex.getOrElse(edge.fromNodeId) { 0 }
            val ii = inIndex.getOrElse(edge.toNodeId) { 0 }
            outIndex[edge.fromNodeId] = oi + 1
            inIndex[edge.toNodeId] = ii + 1

            val totalOut = outCount[edge.fromNodeId] ?: 1
            val totalIn = inCount[edge.toNodeId] ?: 1
            val exitY = from.top + from.height * (oi + 1f) / (totalOut + 1f)
            val entryY = to.top + to.height * (ii + 1f) / (totalIn + 1f)

            val sameCol = kotlin.math.abs(from.left - to.left) < 4f && to.top >= from.bottom - 1f
            val goesRight = to.left >= from.right - 1f

            val blocked = if (goesRight && !sameCol) {
                // Check if an actual card physically sits between the two horizontally and overlaps vertically
                allRects.any { card ->
                    card.right > from.right + 2f &&
                    card.left < to.left - 2f &&
                    card.bottom > minOf(exitY, entryY) - 10f &&
                    card.top < maxOf(exitY, entryY) + 10f
                }
            } else false

            val isUnobstructed = goesRight && !sameCol && !blocked

            EdgeGeometry(
                edge = edge,
                from = from,
                to = to,
                exitY = exitY,
                entryY = entryY,
                isSameColumn = sameCol,
                isUnobstructedForward = isUnobstructed
            )
        }

        // Corridor edges are those that loop backwards (feedback) or skip obstructed columns
        val corridorEdges = resolved.filter { !it.isSameColumn && !it.isUnobstructedForward }

        // Dynamic Column-Level Vertical Lane Allocation:
        // A) Entry lanes: group corridor edges by destination COLUMN (using to.left bucket).
        // Cards in the same stage column share the column's left edge.
        // Sort by entryY descending so lower cards (closer to corridor) get inner lanes (closer to card),
        // while higher cards get outer lanes (further to the left). This guarantees vertical runs NEVER cross or overlap!
        val entrySlotByEdgeId = mutableMapOf<String, Int>()
        corridorEdges
            .groupBy { (it.to.left / 10f).toInt() }
            .forEach { (_, colEdges) ->
                val sorted = colEdges.sortedByDescending { it.entryY }
                sorted.forEachIndexed { slot, eg ->
                    entrySlotByEdgeId[eg.edge.id] = slot
                }
            }

        // B) Exit lanes: group corridor edges by source COLUMN (using from.right bucket).
        // Sort by exitY descending so lower cards get inner lanes, higher cards get outer lanes.
        val exitSlotByEdgeId = mutableMapOf<String, Int>()
        corridorEdges
            .groupBy { (it.from.right / 10f).toInt() }
            .forEach { (_, colEdges) ->
                val sorted = colEdges.sortedByDescending { it.exitY }
                sorted.forEachIndexed { slot, eg ->
                    exitSlotByEdgeId[eg.edge.id] = slot
                }
            }

        // C) Horizontal Corridor Tracks: dedicated altitude Y per corridor edge.
        // Sort by direction (feedback loops first) and span length (shorter spans on upper tracks).
        val sortedCorridorEdges = corridorEdges.sortedWith(
            compareBy<EdgeGeometry> { !it.edge.isFeedback }
                .thenBy { kotlin.math.abs(it.to.left - it.from.right) }
                .thenBy { it.edge.id }
        )
        val corridorTrackByEdgeId = sortedCorridorEdges.mapIndexed { index, eg ->
            eg.edge.id to index
        }.toMap()

        val routed = resolved.map { eg ->
            val edge = eg.edge
            val from = eg.from
            val to = eg.to
            val exitY = eg.exitY
            val entryY = eg.entryY
            val isFeedback = edge.isFeedback

            val color = if (isFeedback) {
                Color(edge.edgeType.colorHex)
            } else {
                graph.nodes.firstOrNull { it.id == edge.fromNodeId }
                    ?.let { Color(it.stage.colorHex) } ?: Color(0xFF2563EB)
            }

            when {
                eg.isSameColumn -> {
                    // Straight vertical drop to the next card in the same stage column
                    val x = from.center.x
                    val start = Offset(x, from.bottom)
                    val end = Offset(x, to.top)
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        lineTo(end.x, end.y)
                    }
                    RoutedEdge(
                        edge = edge,
                        path = path,
                        color = color,
                        isFeedback = isFeedback,
                        startPoint = start,
                        tip = end,
                        tipPrev = Offset(x, end.y - 8.dp.toPx())
                    )
                }
                eg.isUnobstructedForward -> {
                    // n8n-style smooth cubic Bézier curve: tangent leaves horizontally to the right,
                    // and arrives horizontally from the left. Each curve has its own unique S-shape.
                    val p0 = Offset(from.right, exitY)
                    val p3 = Offset(to.left, entryY)
                    val dx = p3.x - p0.x
                    val curvature = (dx * 0.45f).coerceIn(36.dp.toPx(), 180.dp.toPx())
                    val cp1 = Offset(p0.x + curvature, p0.y)
                    val cp2 = Offset(p3.x - curvature, p3.y)

                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p3.x, p3.y)
                    }
                    RoutedEdge(
                        edge = edge,
                        path = path,
                        color = color,
                        isFeedback = isFeedback,
                        startPoint = p0,
                        tip = p3,
                        tipPrev = Offset(p3.x - 8.dp.toPx(), p3.y)
                    )
                }
                else -> {
                    // Multi-lane corridor routing: dynamically assigned vertical exit channels,
                    // dedicated horizontal altitude tracks, and dedicated vertical entry channels.
                    val exitSlot = exitSlotByEdgeId[edge.id] ?: 0
                    val exitX = from.right + 20.dp.toPx() + exitSlot * 16.dp.toPx()

                    val entrySlot = entrySlotByEdgeId[edge.id] ?: 0
                    val entryX = to.left - 20.dp.toPx() - entrySlot * 16.dp.toPx()

                    val trackIndex = corridorTrackByEdgeId[edge.id] ?: 0
                    val corridorBaseY = contentH - corridorReserve + 16.dp.toPx()
                    val corridorY = corridorBaseY + trackIndex * 16.dp.toPx()

                    val waypoints = listOf(
                        Offset(from.right, exitY),
                        Offset(exitX, exitY),
                        Offset(exitX, corridorY),
                        Offset(entryX, corridorY),
                        Offset(entryX, entryY),
                        Offset(to.left, entryY)
                    )

                    val path = Path().apply {
                        addRoundedPolyline(waypoints, radius = 10.dp.toPx())
                    }
                    val tip = Offset(to.left, entryY)
                    RoutedEdge(
                        edge = edge,
                        path = path,
                        color = color,
                        isFeedback = isFeedback,
                        startPoint = Offset(from.right, exitY),
                        tip = tip,
                        tipPrev = Offset(tip.x - 8.dp.toPx(), tip.y)
                    )
                }
            }
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
            // Dinaikkan sejalan dengan outline kartu yang kini 3dp. Garis 1.7dp di antara kartu
            // ber-outline tebal akan terbaca sebagai benang tipis yang tidak sepadan, dan arah
            // alirannya jadi sulit diikuti dari jarak pandang normal.
            val width = when {
                touchesSelection -> 3.4.dp.toPx()
                route.isFeedback -> 2.8.dp.toPx()
                else -> 2.6.dp.toPx()
            }

            drawPath(
                route.path,
                color = color,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (route.isFeedback) dash else null
                )
            )

            drawDirectionalArrow(route.tip, route.tipPrev, color, 4.6.dp.toPx())

            // Dot at the source end reads as "leaves here", mirroring n8n port sockets
            drawCircle(color = color, radius = 3.dp.toPx(), center = route.startPoint)
        }
    }
}

/**
 * Appends a polyline with smooth quadratic fillet corners of the given [radius].
 */
private fun Path.addRoundedPolyline(points: List<Offset>, radius: Float) {
    if (points.size < 2) return
    if (points.size == 2 || radius <= 0f) {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
        return
    }

    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size - 1) {
        val pPrev = points[i - 1]
        val pCurr = points[i]
        val pNext = points[i + 1]

        val vIn = pCurr - pPrev
        val lenIn = kotlin.math.hypot(vIn.x.toDouble(), vIn.y.toDouble()).toFloat()
        val vOut = pNext - pCurr
        val lenOut = kotlin.math.hypot(vOut.x.toDouble(), vOut.y.toDouble()).toFloat()

        if (lenIn < 0.01f || lenOut < 0.01f) {
            lineTo(pCurr.x, pCurr.y)
            continue
        }

        val uIn = Offset(vIn.x / lenIn, vIn.y / lenIn)
        val uOut = Offset(vOut.x / lenOut, vOut.y / lenOut)

        val r = minOf(radius, lenIn / 2f, lenOut / 2f)
        val pBefore = pCurr - uIn * r
        val pAfter = pCurr + uOut * r

        lineTo(pBefore.x, pBefore.y)
        quadraticTo(pCurr.x, pCurr.y, pAfter.x, pAfter.y)
    }
    lineTo(points.last().x, points.last().y)
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
