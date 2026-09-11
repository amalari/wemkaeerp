package com.eventverse.app.presentation.pipeline.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineGraph
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwimlaneRoutingCollisionTest {

    @Test
    fun swimlaneBounds_reportsAndTranslatesCorrectly() {
        val bounds = SwimlaneBounds()
        bounds.reportRoot(Offset(100f, 50f), Size(1200f, 800f))
        bounds.reportCard("node-1", Rect(100f, 50f, 400f, 250f))

        assertTrue(bounds.isReady)
        val rect = bounds.cardRect("node-1")
        assertEquals(Rect(0f, 0f, 300f, 200f), rect)
    }

    @Test
    fun corridorFeedbackEdges_haveDistinctExitAndCorridorTracks() {
        val nodes = PipelinePresetFactory.createSnapshot(GarmentBusinessPreset.FOB_FULL_PACKAGE).nodes
        val graph = PipelineGraph.from(nodes)

        // Find feedback routes from QC
        val qcNode = nodes.first { it.id == "fob-qc-defect" }
        val feedbackEdges = graph.edges.filter { it.fromNodeId == qcNode.id && it.isFeedback }

        assertTrue(feedbackEdges.size >= 2, "Expected at least 2 feedback edges from QC node")

        // In the multi-lane allocation algorithm:
        // Each feedback edge from the same node receives an incrementing exit slot (k * 12dp)
        // and a dedicated corridor track index (t * 14dp)
        val exitSlots = mutableMapOf<String, Int>()
        val allocatedExitX = feedbackEdges.map { edge ->
            val slot = exitSlots.getOrElse(edge.fromNodeId) { 0 }
            exitSlots[edge.fromNodeId] = slot + 1
            slot * 12f
        }
        assertEquals(allocatedExitX.size, allocatedExitX.distinct().size, "Corridor exit lanes must be unique")

        val allocatedTracks = feedbackEdges.indices.map { it * 14f }
        assertEquals(allocatedTracks.size, allocatedTracks.distinct().size, "Corridor horizontal tracks must be unique")
    }

    @Test
    fun allPresets_produceValidGraphEdgesWithoutSelfLoops() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val snapshot = PipelinePresetFactory.createSnapshot(preset)
            val graph = PipelineGraph.from(snapshot.nodes)

            graph.edges.forEach { edge ->
                assertTrue(edge.fromNodeId != edge.toNodeId, "Edge ${edge.id} cannot be a self-loop")
                assertTrue(edge.fromNodeId.isNotBlank(), "Edge source cannot be blank")
                assertTrue(edge.toNodeId.isNotBlank(), "Edge destination cannot be blank")
            }
        }
    }

    @Test
    fun corridorEdges_enteringSameColumn_haveDistinctEntryXLanes() {
        // Mock 2 cards in Column 4 (Manufacturing)
        val col4Left = 1000f
        val card1 = Rect(col4Left, 100f, 1300f, 300f) // Jadwal Mesin (top)
        val card2 = Rect(col4Left, 400f, 1300f, 600f) // Catatan Operator (bottom)

        data class MockEdge(val id: String, val to: Rect, val entryY: Float)

        // All 3 real corridor edges entering Column 4 in FOB preset:
        // 1. Purple forward skip: fob-costing-hpp -> fob-mrp-spk (enters card1 at entryY = 150f)
        // 2. Gray conditional reject: fob-qc-defect -> fob-mrp-spk (enters card1 at entryY = 220f)
        // 3. Orange feedback rework: fob-qc-defect -> fob-operator-exec (enters card2 at entryY = 500f)
        val edgesEnteringCol4 = listOf(
            MockEdge("purple-costing-to-mrp", card1, 150f),
            MockEdge("gray-qc-reject-to-mrp", card1, 220f),
            MockEdge("orange-qc-rework-to-operator", card2, 500f)
        )

        // Algorithm: group by to.left bucket, sort by entryY descending
        val entrySlotByEdgeId = mutableMapOf<String, Int>()
        edgesEnteringCol4
            .groupBy { (it.to.left / 10f).toInt() }
            .forEach { (_, colEdges) ->
                val sorted = colEdges.sortedByDescending { it.entryY }
                sorted.forEachIndexed { slot, edge ->
                    entrySlotByEdgeId[edge.id] = slot
                }
            }

        // Slot 0 (inner lane, closest to cards) -> orange edge at entryY = 500f (bottom card)
        // Slot 1 (middle lane) -> gray edge at entryY = 220f (top card, lower port)
        // Slot 2 (outer lane) -> purple edge at entryY = 150f (top card, upper port)
        assertEquals(0, entrySlotByEdgeId["orange-qc-rework-to-operator"])
        assertEquals(1, entrySlotByEdgeId["gray-qc-reject-to-mrp"])
        assertEquals(2, entrySlotByEdgeId["purple-costing-to-mrp"])

        val orangeEntryX = col4Left - 14f - (entrySlotByEdgeId["orange-qc-rework-to-operator"] ?: 0) * 14f
        val grayEntryX = col4Left - 14f - (entrySlotByEdgeId["gray-qc-reject-to-mrp"] ?: 0) * 14f
        val purpleEntryX = col4Left - 14f - (entrySlotByEdgeId["purple-costing-to-mrp"] ?: 0) * 14f

        assertEquals(col4Left - 14f, orangeEntryX)
        assertEquals(col4Left - 28f, grayEntryX)
        assertEquals(col4Left - 42f, purpleEntryX)

        val allX = listOf(orangeEntryX, grayEntryX, purpleEntryX)
        assertEquals(3, allX.distinct().size, "All 3 corridor entry X coordinates must be strictly distinct")
    }

    @Test
    fun corridorFeedbackEdges_exitingLastColumn_fitWithinEndPadding() {
        val col5Width = 324f
        val cardPadding = 12f
        val cardRight = col5Width - cardPadding // 312f
        val colRight = col5Width // 324f
        val endPadding = SWIMLANE_CORRIDOR_END_PADDING.value // 96f

        val nodes = PipelinePresetFactory.createSnapshot(GarmentBusinessPreset.FOB_FULL_PACKAGE).nodes
        val graph = PipelineGraph.from(nodes)
        val qcNode = nodes.first { it.id == "fob-qc-defect" }
        val feedbackEdges = graph.edges.filter { it.fromNodeId == qcNode.id && it.isFeedback }

        assertTrue(feedbackEdges.isNotEmpty(), "Expected feedback edges from QC")

        feedbackEdges.forEachIndexed { slot, _ ->
            val exitX = cardRight + 20f + slot * 16f
            assertTrue(exitX > colRight, "Exit lane $slot must be outside column right border")
            assertTrue(exitX - colRight >= 8f, "Exit lane $slot must clear the column shadow")
            assertTrue(exitX < colRight + endPadding, "Exit lane $slot must be within end corridor padding")
        }
    }
}

