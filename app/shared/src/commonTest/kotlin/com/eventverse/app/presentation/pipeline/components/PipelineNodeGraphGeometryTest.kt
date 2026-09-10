package com.eventverse.app.presentation.pipeline.components

import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineEdgeKind
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The node canvas computes every coordinate up front from fixed card sizes, so the whole
 * layout is verifiable without rendering anything.
 */
class PipelineNodeGraphGeometryTest {

    private fun nodesOf(preset: GarmentBusinessPreset): List<PipelineNode> =
        PipelinePresetFactory.createSnapshot(preset).nodes

    @Test
    fun buildGeometry_allPresets_placesEveryNodeExactlyOnce() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val nodes = nodesOf(preset)
            val geometry = buildGeometry(nodes)

            assertEquals(
                nodes.size,
                geometry.byNodeId.size,
                "Preset $preset lost nodes while placing them"
            )
            nodes.forEach { node ->
                assertNotNull(geometry.byNodeId[node.id], "No placement for ${node.id} in $preset")
            }
        }
    }

    @Test
    fun buildGeometry_allPresets_keepsEveryCardInsideContentBounds() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val geometry = buildGeometry(nodesOf(preset))

            geometry.byNodeId.values.forEach { placement ->
                assertTrue(
                    placement.x >= 0f && placement.x + NODE_W <= geometry.contentW,
                    "Card ${placement.node.id} overflows content width in $preset"
                )
                assertTrue(
                    placement.y >= 0f && placement.y + NODE_H <= geometry.contentH,
                    "Card ${placement.node.id} overflows content height in $preset"
                )
            }
        }
    }

    @Test
    fun buildGeometry_cardsInSameColumn_doNotOverlapVertically() {
        val geometry = buildGeometry(nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE))

        geometry.byNodeId.values
            .groupBy { it.x }
            .forEach { (_, column) ->
                val sorted = column.sortedBy { it.y }
                sorted.zipWithNext { upper, lower ->
                    assertTrue(
                        lower.y - (upper.y + NODE_H) >= GAP_Y - 0.01f,
                        "Cards ${upper.node.id} and ${lower.node.id} overlap or crowd"
                    )
                }
            }
    }

    @Test
    fun buildGeometry_columns_advanceLeftToRightWithinABand() {
        val geometry = buildGeometry(nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE))
        val graph = geometry.graph

        graph.edges.forEach { edge ->
            val from = geometry.byNodeId.getValue(edge.fromNodeId)
            val to = geometry.byNodeId.getValue(edge.toNodeId)
            // Only the forward flow is required to advance rightwards: a band wrap resets the
            // column to the left, and a conditional/feedback route (QC reject going back for
            // rework) is a deliberate backward edge.
            if (from.band == to.band && edge.kind == PipelineEdgeKind.FORWARD) {
                assertTrue(
                    to.x > from.x,
                    "Forward edge ${edge.id} should flow rightwards within its band, got ${from.x} -> ${to.x}"
                )
            }
        }

        val distinctX = geometry.byNodeId.values.map { it.x }.distinct().sorted()
        distinctX.zipWithNext { left, right ->
            assertEquals(NODE_W + GAP_X, right - left, 0.01f, "Unexpected column pitch")
        }
    }

    @Test
    fun buildGeometry_columns_neverExceedGroupCols() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val geometry = buildGeometry(nodesOf(preset))
            geometry.byNodeId.values.forEach { placement ->
                assertTrue(
                    placement.col in 0 until GROUP_COLS,
                    "Node ${placement.node.id} in $preset has out-of-range column ${placement.col}"
                )
            }
        }
    }

    @Test
    fun routePath_adjacentSameBandEdges_neverCrossAnotherCard() {
        // Any waypoint x that falls strictly inside another card's column range, at a y that
        // falls inside that card's row range, would mean the line is drawn through it.
        GarmentBusinessPreset.entries.forEach { preset ->
            val geometry = buildGeometry(nodesOf(preset))
            val graph = geometry.graph

            graph.edges.forEach { edge ->
                val start = geometry.edgeStart(edge) ?: return@forEach
                val end = geometry.edgeEnd(edge) ?: return@forEach
                val waypoints = geometry.routePath(edge, start, end)

                waypoints.zipWithNext { a, b ->
                    // Sample along each segment, including endpoints, and check against every
                    // OTHER node's card rectangle (a route may legitimately touch its own
                    // source/target card at the attachment point).
                    val steps = 20
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = a.x + (b.x - a.x) * t
                        val y = a.y + (b.y - a.y) * t

                        geometry.byNodeId.values.forEach inner@{ card ->
                            if (card.node.id == edge.fromNodeId || card.node.id == edge.toNodeId) return@inner
                            val insideX = x > card.x + 1f && x < card.x + NODE_W - 1f
                            val insideY = y > card.y + 1f && y < card.y + NODE_H - 1f
                            assertTrue(
                                !(insideX && insideY),
                                "Edge ${edge.id} in $preset passes through card ${card.node.id} at ($x,$y)"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun buildGeometry_inputPorts_sitOnLeftEdgeInDeclarationOrder() {
        val nodes = nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val geometry = buildGeometry(nodes)

        nodes.filter { it.inputs.isNotEmpty() }.forEach { node ->
            val placement = geometry.byNodeId.getValue(node.id)
            val ys = node.inputs.map { port ->
                val y = placement.portY[port.id]
                assertNotNull(y, "Port ${port.id} has no coordinate")
                assertTrue(
                    y > placement.y && y < placement.y + NODE_H,
                    "Port ${port.id} sits outside its card"
                )
                y
            }
            assertEquals(ys.sorted(), ys, "Ports on ${node.id} are not in declaration order")
            assertEquals(ys.distinct().size, ys.size, "Ports on ${node.id} share a coordinate")
        }
    }

    @Test
    fun edgeEndpoints_allPresets_resolveToConcreteCoordinates() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val geometry = buildGeometry(nodesOf(preset))

            geometry.graph.edges.forEach { edge ->
                val start = geometry.edgeStart(edge)
                val end = geometry.edgeEnd(edge)
                assertNotNull(start, "Edge ${edge.id} has no start in $preset")
                assertNotNull(end, "Edge ${edge.id} has no end in $preset")

                val source = geometry.byNodeId.getValue(edge.fromNodeId)
                assertEquals(source.x + NODE_W, start.x, 0.01f, "Edge must leave the right edge")
                assertEquals(
                    geometry.byNodeId.getValue(edge.toNodeId).x,
                    end.x,
                    0.01f,
                    "Edge must land on the left edge"
                )
            }
        }
    }

    @Test
    fun edgeEndpoints_fanOutNode_sharesOutletAndLandsOnDistinctPorts() {
        val nodes = nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val geometry = buildGeometry(nodes)

        val fanOutId = geometry.graph.edgesByFromNodeId
            .entries.first { it.value.size > 1 }.key
        val outgoing = geometry.graph.edgesByFromNodeId.getValue(fanOutId)

        val starts = outgoing.mapNotNull { geometry.edgeStart(it) }.distinct()
        assertEquals(1, starts.size, "All outgoing edges must leave the same OUT socket")

        val ends = outgoing.mapNotNull { geometry.edgeEnd(it) }
        assertEquals(ends.distinct().size, ends.size, "Fan-out edges collapsed onto one point")
    }

    @Test
    fun buildGeometry_emptyNodeSet_producesPaddingOnlyCanvas() {
        val geometry = buildGeometry(emptyList())

        assertTrue(geometry.byNodeId.isEmpty())
        assertEquals(CANVAS_PAD * 2, geometry.contentW, 0.01f)
        assertEquals(CANVAS_PAD * 2, geometry.contentH, 0.01f)
    }
}
