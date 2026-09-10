package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.PipelineEdge
import com.eventverse.app.domain.pipeline.PipelineGraph
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.presentation.theme.WeMadeColors

// Fixed card geometry: because every node is the same size, all port coordinates are
// computable up front — no measurement pass, no second frame, no jitter while zooming.
internal const val NODE_W = 280f
internal const val NODE_H = 176f
internal const val GAP_X = 128f
internal const val GAP_Y = 44f
internal const val CANVAS_PAD = 32f

/** Wrap into a new row of columns after this many, so a long pipeline doesn't force endless
 *  horizontal scrolling — it grows down in bands instead. */
internal const val GROUP_COLS = 5
internal const val BAND_GAP_Y = 96f

/** How far into a column's gap a routed edge turns, before running along it. */
private const val LANE_OFFSET = 24f

private const val MIN_ZOOM = 0.35f
private const val MAX_ZOOM = 1.8f
private const val OVERSCROLL = 120f

private const val VIEWPORT_H = 660f
private const val MIN_VIEWPORT_H = 360f

/** Absolute placement of one node and of every port on its edges, in dp units. */
internal data class NodeGeometry(
    val node: PipelineNode,
    val x: Float,
    val y: Float,
    /** Column within its band (0-based, left to right). */
    val col: Int,
    /** Row-of-bands index (0-based, top to bottom) — see [GROUP_COLS]. */
    val band: Int,
    /** Input port id -> y coordinate on the left edge. */
    val portY: Map<String, Float>,
    /** Landing point for edges that no declared port claims. */
    val implicitY: Float,
    val outX: Float,
    val outY: Float
)

internal data class GraphGeometry(
    val graph: PipelineGraph,
    val byNodeId: Map<String, NodeGeometry>,
    val contentW: Float,
    val contentH: Float
) {
    fun edgeStart(edge: PipelineEdge): Offset? =
        byNodeId[edge.fromNodeId]?.let { Offset(it.outX, it.outY) }

    fun edgeEnd(edge: PipelineEdge): Offset? {
        val target = byNodeId[edge.toNodeId] ?: return null
        val y = if (edge.isImplicit) target.implicitY else target.portY[edge.toPortId] ?: target.implicitY
        return Offset(target.x, y)
    }

    /**
     * Waypoints for one edge's connector, chosen so the line can never cross a card:
     * - Adjacent columns in the same band, same row: a straight line.
     * - Adjacent columns in the same band, different row: one turn through the gap between
     *   those two columns — that gap has no card in it at any height, so any detour there is
     *   safe regardless of how far apart the rows are.
     * - Anything wider (a multi-column skip, or a jump to a different band of rows): detour
     *   through the canvas's top or bottom margin. Column gaps repeat at the same x in every
     *   band, so the vertical legs are card-free for the full height of the canvas, and the
     *   margin itself is reserved empty space — so the whole path is guaranteed clear.
     */
    fun routePath(edge: PipelineEdge, start: Offset, end: Offset): List<Offset> {
        val source = byNodeId[edge.fromNodeId] ?: return listOf(start, end)
        val target = byNodeId[edge.toNodeId] ?: return listOf(start, end)

        val sameRow = kotlin.math.abs(end.y - start.y) < 1f
        val adjacent = target.band == source.band && target.col == source.col + 1
        if (adjacent && sameRow) return listOf(start, end)

        // Small per-edge jitter so edges sharing a lane or a margin don't sit perfectly on
        // top of one another.
        val jitter = ((edge.id.hashCode() % 5) - 2) * 7f

        if (adjacent) {
            val laneX = source.outX + GAP_X / 2f + jitter
            return listOf(start, Offset(laneX, start.y), Offset(laneX, end.y), end)
        }

        if (edge.isFeedback) {
            val exitLaneX = source.outX + LANE_OFFSET
            val entryLaneX = target.x - LANE_OFFSET
            val bottomMarginY = contentH - CANVAS_PAD / 2f + jitter
            return listOf(
                start,
                Offset(exitLaneX, start.y),
                Offset(exitLaneX, bottomMarginY),
                Offset(entryLaneX, bottomMarginY),
                Offset(entryLaneX, end.y),
                end
            )
        }

        val exitLaneX = source.outX + LANE_OFFSET
        val entryLaneX = target.x - LANE_OFFSET
        val useTopMargin = (edge.id.hashCode() and 1) == 0
        val marginY = (if (useTopMargin) CANVAS_PAD / 2f else contentH - CANVAS_PAD / 2f) + jitter
        return listOf(
            start,
            Offset(exitLaneX, start.y),
            Offset(exitLaneX, marginY),
            Offset(entryLaneX, marginY),
            Offset(entryLaneX, end.y),
            end
        )
    }
}

internal fun buildGeometry(nodes: List<PipelineNode>): GraphGeometry {
    val graph = PipelineGraph.from(nodes)

    val bandOfLayer = IntArray(graph.layers.size) { it / GROUP_COLS }
    val colOfLayer = IntArray(graph.layers.size) { it % GROUP_COLS }
    val bandCount = if (graph.layers.isEmpty()) 0 else bandOfLayer.last() + 1

    val bandRowCounts = IntArray(bandCount)
    graph.layers.forEachIndexed { layerIndex, layerNodes ->
        val b = bandOfLayer[layerIndex]
        bandRowCounts[b] = maxOf(bandRowCounts[b], layerNodes.size)
    }

    val bandYOffset = FloatArray(bandCount)
    var runningY = CANVAS_PAD
    for (b in 0 until bandCount) {
        bandYOffset[b] = runningY
        val bandHeight = bandRowCounts[b] * NODE_H + (bandRowCounts[b] - 1).coerceAtLeast(0) * GAP_Y
        runningY += bandHeight + BAND_GAP_Y
    }

    val geometries = graph.layers.flatMapIndexed { layerIndex, layerNodes ->
        val band = bandOfLayer[layerIndex]
        val col = colOfLayer[layerIndex]
        layerNodes.mapIndexed { rowIndex, node ->
            val x = CANVAS_PAD + col * (NODE_W + GAP_X)
            val y = bandYOffset[band] + rowIndex * (NODE_H + GAP_Y)

            // Ports are spread evenly down the left edge, in declaration order.
            val slots = node.inputs.size
            val portY = node.inputs.mapIndexed { slot, port ->
                port.id to y + NODE_H * (slot + 1) / (slots + 1)
            }.toMap()

            node.id to NodeGeometry(
                node = node,
                x = x,
                y = y,
                col = col,
                band = band,
                portY = portY,
                implicitY = y + NODE_H / 2f,
                outX = x + NODE_W,
                outY = y + NODE_H / 2f
            )
        }
    }.toMap()

    // Every full band uses all GROUP_COLS columns; only a single, short pipeline stays narrower.
    val usedCols = if (bandCount <= 1) graph.layers.size else GROUP_COLS
    val contentW = CANVAS_PAD * 2 + usedCols * NODE_W + (usedCols - 1).coerceAtLeast(0) * GAP_X
    val contentH = if (bandCount == 0) CANVAS_PAD * 2 else (runningY - BAND_GAP_Y) + CANVAS_PAD

    return GraphGeometry(graph, geometries, contentW, contentH)
}

/** Point at half the total arc length along a polyline — a routing-agnostic "middle". */
private fun polylineMidpoint(points: List<Offset>): Offset {
    if (points.size < 2) return points.firstOrNull() ?: Offset.Zero
    val lengths = points.zipWithNext { a, b ->
        kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
    }
    val total = lengths.sum()
    if (total <= 0f) return points.first()

    var remaining = total / 2f
    for (i in lengths.indices) {
        val segLen = lengths[i]
        if (remaining <= segLen) {
            val t = if (segLen > 0f) remaining / segLen else 0f
            val a = points[i]
            val b = points[i + 1]
            return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        remaining -= segLen
    }
    return points.last()
}

/**
 * n8n-style node canvas. Unlike the swimlane and linear layouts, every connector here is
 * drawn from a resolved [PipelineEdge], so fan-out and stage-skipping links are visible.
 */
@Composable
fun PipelineNodeGraphLayout(
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    isPresentationMode: Boolean,
    onSelectNode: (PipelineNode) -> Unit,
    onInspectInputs: (PipelineNode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Whole graph — edges, layering and every coordinate — derived once per node set.
    val geometry = remember(nodes) { buildGeometry(nodes) }
    val graph = geometry.graph

    val highlightedEdgeIds = remember(geometry, selectedNode?.id) {
        selectedNode?.id?.let { graph.edgesTouching(it) } ?: emptySet()
    }
    val neighborIds = remember(geometry, selectedNode?.id) {
        selectedNode?.id?.let { graph.neighborIdsOf(it) + it } ?: emptySet()
    }

    var zoom by remember(geometry) { mutableStateOf(1f) }
    var pan by remember(geometry) { mutableStateOf(Offset.Zero) }

    val canvasBg = if (isPresentationMode) Color(0xFF020617) else Color(0xFFF8FAFC)
    val gridBorder = if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GraphLegendBar(
            edgeCount = graph.edges.size,
            feedbackEdgeCount = graph.edges.count { it.isFeedback },
            danglingCount = graph.danglingInputPortIds.size,
            hasCycle = graph.hasCycle,
            isPresentationMode = isPresentationMode
        )

        // A short graph should not sit in a tall, mostly empty frame.
        val viewportHeight = geometry.contentH.coerceIn(MIN_VIEWPORT_H, VIEWPORT_H).dp

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(canvasBg)
                .border(1.dp, gridBorder, RoundedCornerShape(12.dp))
                .clipToBounds()
        ) {
            val viewportW = maxWidth.value
            val viewportH = maxHeight.value

            fun clampPan(candidate: Offset, atZoom: Float): Offset {
                val scaledW = geometry.contentW * atZoom
                val scaledH = geometry.contentH * atZoom
                val minX = minOf(0f, viewportW - scaledW) - OVERSCROLL
                val minY = minOf(0f, viewportH - scaledH) - OVERSCROLL
                return Offset(
                    x = candidate.x.coerceIn(minX, OVERSCROLL),
                    y = candidate.y.coerceIn(minY, OVERSCROLL)
                )
            }

            /** Zooms around [focus] so the point under it stays put. */
            fun applyZoom(target: Float, focus: Offset) {
                val next = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
                val ratio = next / zoom
                pan = clampPan(focus - (focus - pan) * ratio, next)
                zoom = next
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
                    .pointerInput(geometry) {
                        detectTransformGestures { centroid, panDelta, gestureZoom, _ ->
                            if (gestureZoom != 1f) {
                                applyZoom(zoom * gestureZoom, centroid / density)
                            }
                            pan = clampPan(pan + panDelta / density, zoom)
                        }
                    }
            ) {
                Box(
                    // The graph is usually wider than the viewport. wrapContentSize with
                    // unbounded = true lets it measure at full size and pins it to the top
                    // start; required* would instead centre the overflow and shift the
                    // whole canvas left.
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(width = geometry.contentW.dp, height = geometry.contentH.dp)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x * this.density
                            translationY = pan.y * this.density
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    GraphEdgeCanvas(
                        geometry = geometry,
                        highlightedEdgeIds = highlightedEdgeIds,
                        hasSelection = selectedNode != null,
                        isPresentationMode = isPresentationMode
                    )

                    geometry.byNodeId.values.forEach { placement ->
                        val node = placement.node
                        val isDimmed = selectedNode != null && node.id !in neighborIds

                        Box(
                            modifier = Modifier
                                .offset(x = placement.x.dp, y = placement.y.dp)
                                .alpha(if (isDimmed) 0.35f else 1f)
                        ) {
                            GraphNodeCard(
                                node = node,
                                isSelected = selectedNode?.id == node.id,
                                isPresentationMode = isPresentationMode,
                                onClick = { onSelectNode(node) },
                                onInspectInputs = { onInspectInputs(node) }
                            )
                        }

                        GraphPortHandles(
                            placement = placement,
                            danglingPortIds = graph.danglingInputPortIds,
                            isDimmed = isDimmed,
                            isPresentationMode = isPresentationMode,
                            onInspectInputs = { onInspectInputs(node) }
                        )
                    }

                    if (selectedNode != null) {
                        SelectedEdgeLabels(
                            geometry = geometry,
                            edgeIds = highlightedEdgeIds,
                            isPresentationMode = isPresentationMode
                        )
                    }
                }
            }

            ZoomControls(
                zoom = zoom,
                isPresentationMode = isPresentationMode,
                onZoomIn = { applyZoom(zoom * 1.25f, Offset(viewportW / 2f, viewportH / 2f)) },
                onZoomOut = { applyZoom(zoom / 1.25f, Offset(viewportW / 2f, viewportH / 2f)) },
                onFit = {
                    val fitted = minOf(
                        viewportW / geometry.contentW,
                        viewportH / geometry.contentH
                    ).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    zoom = fitted
                    pan = clampPan(Offset.Zero, fitted)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Draws every resolved edge as a horizontal cubic bezier, plus stubs for the two kinds of
 * input that have no upstream node on the canvas: manual entry and filtered-out sources.
 */
@Composable
private fun GraphEdgeCanvas(
    geometry: GraphGeometry,
    highlightedEdgeIds: Set<String>,
    hasSelection: Boolean,
    isPresentationMode: Boolean
) {
    val manualColor = Color(0xFFEA580C)
    val danglingColor = if (isPresentationMode) Color(0xFF475569) else Color(0xFF94A3B8)

    Canvas(
        modifier = Modifier.size(width = geometry.contentW.dp, height = geometry.contentH.dp)
    ) {
        val dashed = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))

        geometry.graph.edges.forEach { edge ->
            val start = geometry.edgeStart(edge) ?: return@forEach
            val end = geometry.edgeEnd(edge) ?: return@forEach
            val source = geometry.byNodeId[edge.fromNodeId]?.node ?: return@forEach

            val isHighlighted = edge.id in highlightedEdgeIds
            val alpha = when {
                !hasSelection -> 0.85f
                isHighlighted -> 1f
                else -> 0.18f
            }
            val edgeColor = if (edge.isFeedback) Color(edge.edgeType.colorHex) else Color(source.stage.colorHex)
            val color = edgeColor.copy(alpha = alpha)
            val width = if (isHighlighted) 2.6.dp.toPx() else if (edge.isFeedback) 2.2.dp.toPx() else 1.8.dp.toPx()

            val waypoints = geometry.routePath(edge, start, end)
            val pxPoints = waypoints.map { Offset(it.x.dp.toPx(), it.y.dp.toPx()) }
            val p3 = pxPoints.last()

            val path = Path().apply {
                moveTo(pxPoints[0].x, pxPoints[0].y)
                for (i in 1 until pxPoints.size) lineTo(pxPoints[i].x, pxPoints[i].y)
            }
            drawPath(
                path,
                color = color,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (edge.isFeedback) dashed else null
                )
            )

            // Solid filled arrowhead at the landing port. Every routed path's final leg
            // approaches from the left (ports live on the card's left edge), so the arrow
            // always points right regardless of how the rest of the path bent to get there.
            val headLen = 9.dp.toPx()
            val headHalfWidth = 5.dp.toPx()
            val head = Path().apply {
                moveTo(p3.x, p3.y)
                lineTo(p3.x - headLen, p3.y - headHalfWidth)
                lineTo(p3.x - headLen, p3.y + headHalfWidth)
                close()
            }
            drawPath(head, color = color)
        }

        // Stubs for inputs with no node to come from.
        geometry.byNodeId.values.forEach { placement ->
            placement.node.inputs.forEach { port ->
                val isDangling = port.id in geometry.graph.danglingInputPortIds
                if (!port.isManual && !isDangling) return@forEach

                val y = (placement.portY[port.id] ?: placement.implicitY).dp.toPx()
                val x = placement.x.dp.toPx()
                val stub = 26.dp.toPx()
                drawLine(
                    color = if (port.isManual) manualColor else danglingColor,
                    start = Offset(x - stub, y),
                    end = Offset(x, y),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dashed
                )
                drawCircle(
                    color = if (port.isManual) manualColor else danglingColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x - stub, y)
                )
            }
        }
    }
}

/** Clickable port sockets sitting on the card edges, above the cards. */
@Composable
private fun GraphPortHandles(
    placement: NodeGeometry,
    danglingPortIds: Set<String>,
    isDimmed: Boolean,
    isPresentationMode: Boolean,
    onInspectInputs: () -> Unit
) {
    val socketRing = if (isPresentationMode) Color(0xFF0F172A) else Color.White
    val alpha = if (isDimmed) 0.35f else 1f

    placement.node.inputs.forEach { port ->
        val y = placement.portY[port.id] ?: placement.implicitY
        val color = when {
            port.isManual -> Color(0xFFEA580C)
            port.id in danglingPortIds -> if (isPresentationMode) Color(0xFF475569) else Color(0xFF94A3B8)
            else -> Color(0xFF0284C7)
        }

        Box(
            modifier = Modifier
                .offset(x = (placement.x - 6f).dp, y = (y - 6f).dp)
                .alpha(alpha)
                .size(12.dp)
                .clip(CircleShape)
                .background(socketRing)
                .border(2.dp, color, CircleShape)
                .clickable { onInspectInputs() }
        )
    }

    Box(
        modifier = Modifier
            .offset(x = (placement.outX - 5f).dp, y = (placement.outY - 5f).dp)
            .alpha(alpha)
            .size(10.dp)
            .clip(CircleShape)
            .background(WeMadeColors.Success)
    )
}

/** Contract labels for the selected node's edges only — enough context without clutter. */
@Composable
private fun SelectedEdgeLabels(
    geometry: GraphGeometry,
    edgeIds: Set<String>,
    isPresentationMode: Boolean
) {
    geometry.graph.edges.filter { it.id in edgeIds }.forEach { edge ->
        val start = geometry.edgeStart(edge) ?: return@forEach
        val end = geometry.edgeEnd(edge) ?: return@forEach
        if (edge.label.isBlank()) return@forEach

        // Halfway along the actual routed path, so the label sits on the line drawn —
        // whether that's a straight shot or a detour via the canvas margin.
        val mid = polylineMidpoint(geometry.routePath(edge, start, end))

        val isFeedback = edge.isFeedback
        val badgeBg = when {
            isFeedback -> if (isPresentationMode) Color(0xFF450A0A) else Color(0xFFFEF2F2)
            isPresentationMode -> Color(0xFF1E293B)
            else -> Color.White
        }
        val badgeBorder = when {
            isFeedback -> Color(edge.edgeType.colorHex)
            isPresentationMode -> Color(0xFF334155)
            else -> Color(0xFFE2E8F0)
        }
        val textColor = when {
            isFeedback -> Color(edge.edgeType.colorHex)
            isPresentationMode -> Color(0xFFCBD5E1)
            else -> WeMadeColors.OnSurface
        }

        Box(
            modifier = Modifier
                .offset(x = (mid.x - 90f).dp, y = (mid.y - 11f).dp)
                .width(180.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(badgeBg)
                    .border(
                        if (isFeedback) 1.5.dp else 1.dp,
                        badgeBorder,
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isFeedback) "↩ ${edge.label}" else edge.label,
                    fontSize = 9.sp,
                    fontWeight = if (isFeedback) FontWeight.Bold else FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

/** Compact fixed-size node card: everything must fit [NODE_H] so geometry stays predictable. */
@Composable
private fun GraphNodeCard(
    node: PipelineNode,
    isSelected: Boolean,
    isPresentationMode: Boolean,
    onClick: () -> Unit,
    onInspectInputs: () -> Unit
) {
    val isBottleneck = node.isBottleneck

    val border = when {
        isSelected -> BorderStroke(2.dp, WeMadeColors.Primary)
        isBottleneck -> BorderStroke(1.5.dp, Color(node.healthStatus.badgeColorHex))
        isPresentationMode -> BorderStroke(1.dp, Color(0xFF334155))
        else -> BorderStroke(1.dp, WeMadeColors.Border)
    }
    val cardBg = when {
        isPresentationMode && isSelected -> Color(0xFF1E293B)
        isPresentationMode -> Color(0xFF0F172A)
        isBottleneck -> Color(node.healthStatus.bgTintHex)
        else -> WeMadeColors.Surface
    }
    val titleColor = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
    val mutedColor = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted

    Card(
        modifier = Modifier
            .width(NODE_W.dp)
            .height(NODE_H.dp)
            .alpha(if (node.isBypassed) 0.55f else 1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(node.stage.colorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${node.stepNumber}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = node.stage.displayName.substringAfter(". ").uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(node.stage.colorHex),
                        letterSpacing = 0.4.sp,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(node.healthStatus.badgeColorHex))
                )
            }

            Text(
                text = node.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                lineHeight = 17.sp,
                maxLines = 2
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconUsers(modifier = Modifier.size(10.dp), color = Color(node.deptColorHex))
                Text(
                    text = node.assignedDepartment,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(node.deptColorHex),
                    maxLines = 1
                )
            }

            // IN summary doubles as the entry point to the existing inspection modal.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFEFF6FF))
                    .clickable { onInspectInputs() }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconNodePort(modifier = Modifier.size(10.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "IN ${node.inputs.size}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WeMadeColors.Primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (node.automatedInputCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconZap(modifier = Modifier.size(9.dp), color = Color(0xFF0284C7))
                            Text(
                                text = "${node.automatedInputCount}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                    if (node.manualInputCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconPerson(modifier = Modifier.size(9.dp), color = Color(0xFFEA580C))
                            Text(
                                text = "${node.manualInputCount}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }
                }
            }

            if (node.activeFeedbackBadge != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPresentationMode) Color(0xFF450A0A) else Color(0xFFFEF2F2))
                        .border(0.5.dp, Color(0xFFFCA5A5), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "↩ ${node.activeFeedbackBadge}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626),
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconWip(
                        modifier = Modifier.size(10.dp),
                        color = if (isBottleneck) WeMadeColors.Warning else WeMadeColors.Primary
                    )
                    Text(
                        text = if (node.isBypassed) "0 Pcs" else "${node.wipPieces} Pcs",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBottleneck) WeMadeColors.Warning else WeMadeColors.Primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconClock(modifier = Modifier.size(10.dp), color = mutedColor)
                    Text(
                        text = if (node.isBypassed) "Bypassed" else "${node.cycleTimeHours}h",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = mutedColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphLegendBar(
    edgeCount: Int,
    feedbackEdgeCount: Int = 0,
    danglingCount: Int,
    hasCycle: Boolean,
    isPresentationMode: Boolean
) {
    val mutedColor = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$edgeCount koneksi antar-modul",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = mutedColor
        )
        LegendDot(Color(0xFF0284C7), "Input otomatis", mutedColor)
        LegendDot(Color(0xFFEA580C), "Input manual", mutedColor)
        if (feedbackEdgeCount > 0) {
            LegendDot(Color(0xFFDC2626), "$feedbackEdgeCount feedback defect/rework", Color(0xFFDC2626))
        }
        if (danglingCount > 0) {
            LegendDot(
                if (isPresentationMode) Color(0xFF475569) else Color(0xFF94A3B8),
                "$danglingCount input sumbernya tersembunyi",
                mutedColor
            )
        }
        if (hasCycle) {
            Text(
                text = "Siklus terdeteksi — kolom memakai urutan tahap",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Warning
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text = label, fontSize = 11.sp, color = textColor)
    }
}

/**
 * Explicit zoom buttons: the web build runs in a Skiko canvas with browser scrolling
 * disabled, so there is no wheel-zoom for mouse users to fall back on.
 */
@Composable
private fun ZoomControls(
    zoom: Float,
    isPresentationMode: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isPresentationMode) Color(0xFF1E293B) else Color.White
    val borderColor = if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border
    val fg = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ZoomButton("−", fg, onZoomOut)
        Text(
            text = "${(zoom * 100).toInt()}%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.Center
        )
        ZoomButton("+", fg, onZoomIn)
        Box(modifier = Modifier.width(1.dp).height(16.dp).background(borderColor))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onFit() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = "Pas", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
private fun ZoomButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
