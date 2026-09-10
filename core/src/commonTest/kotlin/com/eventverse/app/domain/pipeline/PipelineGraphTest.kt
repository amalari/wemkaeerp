package com.eventverse.app.domain.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineGraphTest {

    private fun nodesOf(preset: GarmentBusinessPreset) =
        PipelinePresetFactory.createSnapshot(preset).nodes

    @Test
    fun buildGraph_allPresets_resolvesEveryAutomatedInputPort() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val graph = PipelineGraph.from(nodesOf(preset))
            assertTrue(
                graph.danglingInputPortIds.isEmpty(),
                "Preset $preset has unresolvable automated input ports: ${graph.danglingInputPortIds}"
            )
        }
    }

    @Test
    fun buildGraph_allPresets_producesNoSelfEdgeAndNoDuplicatePair() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val graph = PipelineGraph.from(nodesOf(preset))
            assertTrue(
                graph.edges.none { it.fromNodeId == it.toNodeId },
                "Preset $preset produced a self edge"
            )
            val ids = graph.edges.map { it.id }
            assertEquals(ids.size, ids.distinct().size, "Preset $preset produced duplicate edge ids")
        }
    }

    @Test
    fun buildGraph_allPresets_edgeEndpointsExistInNodeSet() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val graph = PipelineGraph.from(nodesOf(preset))
            val ids = graph.nodes.map { it.id }.toSet()
            graph.edges.forEach { edge ->
                assertTrue(edge.fromNodeId in ids, "Dangling source ${edge.fromNodeId} in $preset")
                assertTrue(edge.toNodeId in ids, "Dangling target ${edge.toNodeId} in $preset")
            }
        }
    }

    @Test
    fun buildGraph_nodeWithFanOut_producesEdgePerDownstreamModule() {
        val nodes = nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val graph = PipelineGraph.from(nodes)

        val fanOutNode = nodes.first { it.downstreamModuleCodes.size > 1 }
        val targets = graph.edgesByFromNodeId[fanOutNode.id].orEmpty().map { it.toNodeId }.toSet()

        val expected = fanOutNode.downstreamModuleCodes
            .mapNotNull { code -> nodes.firstOrNull { it.module.code == code }?.id }
            .toSet()

        assertTrue(expected.size > 1, "Expected a real fan-out node in the FOB preset")
        assertTrue(
            targets.containsAll(expected),
            "Fan-out from ${fanOutNode.id} lost targets: expected $expected, drew $targets"
        )
    }

    @Test
    fun buildGraph_filteredNodeSet_marksMissingSourceAsDangling() {
        val nodes = nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val fullGraph = PipelineGraph.from(nodes)

        // Drop a node that something downstream depends on.
        val consumedEdge = fullGraph.edges.first { !it.isImplicit }
        val without = nodes.filterNot { it.id == consumedEdge.fromNodeId }

        val graph = PipelineGraph.from(without)

        assertTrue(
            consumedEdge.toPortId in graph.danglingInputPortIds,
            "Port ${consumedEdge.toPortId} should be dangling once its source node is filtered out"
        )
        assertTrue(
            graph.edges.none { it.fromNodeId == consumedEdge.fromNodeId },
            "No edge may reference a node that is not in the set"
        )
    }

    @Test
    fun layering_skipEdge_placesTargetAfterFarthestPredecessor() {
        val a = stubNode(id = "a", stepNumber = 1, downstream = listOf("b", "d"))
        val b = stubNode(id = "b", stepNumber = 2, downstream = listOf("c"))
        val c = stubNode(id = "c", stepNumber = 3, downstream = listOf("d"))
        val d = stubNode(id = "d", stepNumber = 4)

        val graph = PipelineGraph.from(listOf(a, b, c, d))

        assertFalse(graph.hasCycle)
        assertEquals(0, graph.layerOf("a"))
        assertEquals(1, graph.layerOf("b"))
        assertEquals(2, graph.layerOf("c"))
        // d is reachable from a directly, but the longest path a->b->c->d wins.
        assertEquals(3, graph.layerOf("d"))
    }

    @Test
    fun layering_allPresets_producesNoEmptyLayer() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val graph = PipelineGraph.from(nodesOf(preset))
            assertTrue(graph.layers.isNotEmpty(), "Preset $preset produced no layers")
            assertTrue(graph.layers.none { it.isEmpty() }, "Preset $preset produced an empty layer")
            assertEquals(
                graph.nodes.size,
                graph.layers.sumOf { it.size },
                "Preset $preset lost nodes during layering"
            )
        }
    }

    @Test
    fun layering_cyclicGraph_fallsBackToStageOrderWithoutHanging() {
        val a = stubNode(id = "a", stepNumber = 1, stage = PipelineStage.COMMERCIAL, downstream = listOf("b"))
        val b = stubNode(id = "b", stepNumber = 2, stage = PipelineStage.ENGINEERING, downstream = listOf("a"))

        val graph = PipelineGraph.from(listOf(a, b))

        assertTrue(graph.hasCycle, "A -> B -> A should be detected as a cycle")
        assertEquals(2, graph.layers.sumOf { it.size })
        assertEquals(0, graph.layerOf("a"))
        assertEquals(1, graph.layerOf("b"))
    }

    @Test
    fun conditionalPaths_reworkLoop_isDrawnButNeverDistortsLayering() {
        val nodes = nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val graph = PipelineGraph.from(nodes)

        val conditional = graph.edges.filter { it.kind == PipelineEdgeKind.CONDITIONAL }
        assertTrue(conditional.isNotEmpty(), "FOB preset should declare at least one rework route")

        // A rework route points back upstream — that is the whole point of it.
        assertTrue(
            conditional.any { graph.layerOf(it.toNodeId) < graph.layerOf(it.fromNodeId) },
            "Expected at least one conditional route pointing back to an earlier layer"
        )

        // ...but it must not make the graph look cyclic, which would collapse the whole
        // column layout back to plain stage order.
        assertFalse(
            graph.hasCycle,
            "A conditional rework loop must not trigger the cycle fallback"
        )
    }

    @Test
    fun conditionalPaths_areExcludedFromForwardOnlyLayering() {
        val nodes = nodesOf(GarmentBusinessPreset.FOB_FULL_PACKAGE)
        val graph = PipelineGraph.from(nodes)

        // Layering must match a graph built from the forward edges alone.
        graph.edges.filter { it.kind == PipelineEdgeKind.FORWARD }.forEach { edge ->
            assertTrue(
                graph.layerOf(edge.toNodeId) > graph.layerOf(edge.fromNodeId),
                "Forward edge ${edge.id} should advance the layer"
            )
        }
    }

    @Test
    fun buildGraph_emptyNodeSet_returnsEmptyGraph() {
        val graph = PipelineGraph.from(emptyList())

        assertTrue(graph.edges.isEmpty())
        assertTrue(graph.layers.isEmpty())
        assertFalse(graph.hasCycle)
        assertEquals(0, graph.layerOf("anything"))
    }

    @Test
    fun neighborIdsOf_node_includesBothDirections() {
        val a = stubNode(id = "a", stepNumber = 1, downstream = listOf("b"))
        val b = stubNode(id = "b", stepNumber = 2, downstream = listOf("c"))
        val c = stubNode(id = "c", stepNumber = 3)

        val graph = PipelineGraph.from(listOf(a, b, c))

        assertEquals(setOf("a", "c"), graph.neighborIdsOf("b"))
    }

    @Test
    fun buildGraph_withFeedbackEdge_doesNotBreakTopologicalLayers() {
        val a = stubNode(id = "a", stepNumber = 1, downstream = listOf("b"))
        val b = stubNode(id = "b", stepNumber = 2, downstream = listOf("c"))
        val c = stubNode(
            id = "c",
            stepNumber = 3,
            feedbackRoutes = listOf(
                PipelineFeedbackRoute(
                    id = "c-fb-a",
                    targetModuleCode = moduleForStubId("a").code,
                    targetModuleName = "Module A",
                    edgeType = PipelineEdgeType.FEEDBACK_DEFECT,
                    triggerReason = "Defect in C",
                    actionContract = "Klaim Retur Bahan",
                    isActive = true
                )
            )
        )

        val graph = PipelineGraph.from(listOf(a, b, c))

        assertFalse(graph.hasCycle, "Feedback edge should NOT be treated as a cycle in layer calculation")
        assertEquals(0, graph.layerOf("a"))
        assertEquals(1, graph.layerOf("b"))
        assertEquals(2, graph.layerOf("c"))

        val feedbackEdge = graph.edges.firstOrNull { it.isFeedback }
        assertTrue(feedbackEdge != null, "Feedback edge should be resolved in the graph")
        assertEquals("c", feedbackEdge.fromNodeId)
        assertEquals("a", feedbackEdge.toNodeId)
        assertEquals(PipelineEdgeType.FEEDBACK_DEFECT, feedbackEdge.edgeType)
    }

    @Test
    fun buildGraph_qcFabricDefectScenario_resolvesFeedbackEdgeToInventory() {
        val snapshot = PipelinePresetFactory.createSnapshot(
            preset = GarmentBusinessPreset.FOB_FULL_PACKAGE,
            scenario = PipelineSimulationScenario.QC_FABRIC_DEFECT
        )
        val graph = PipelineGraph.from(snapshot.nodes)

        val feedbackEdge = graph.edges.firstOrNull { it.edgeType == PipelineEdgeType.FEEDBACK_DEFECT }
        assertTrue(feedbackEdge != null, "FOB QC Fabric Defect scenario must resolve a feedback edge")
        assertEquals("fob-qc-defect", feedbackEdge.fromNodeId)
        assertEquals("fob-inventory", feedbackEdge.toNodeId)
        assertFalse(graph.hasCycle)

        val qcNode = snapshot.nodes.first { it.id == "fob-qc-defect" }
        assertEquals(FlowHealthStatus.CRITICAL, qcNode.healthStatus)
        assertTrue(qcNode.hasActiveFeedback)

        val invNode = snapshot.nodes.first { it.id == "fob-inventory" }
        assertEquals(2280, invNode.wipPieces) // 2100 + 180 shortage
    }

    @Test
    fun buildGraph_qcWorkmanshipScenario_resolvesFeedbackEdgeToOperator() {
        val snapshot = PipelinePresetFactory.createSnapshot(
            preset = GarmentBusinessPreset.FOB_FULL_PACKAGE,
            scenario = PipelineSimulationScenario.QC_WORKMANSHIP_DEFECT
        )
        val graph = PipelineGraph.from(snapshot.nodes)

        val feedbackEdge = graph.edges.firstOrNull { it.edgeType == PipelineEdgeType.FEEDBACK_REWORK }
        assertTrue(feedbackEdge != null, "FOB QC Workmanship scenario must resolve a rework edge")
        assertEquals("fob-qc-defect", feedbackEdge.fromNodeId)
        assertEquals("fob-operator-exec", feedbackEdge.toNodeId)
        assertFalse(graph.hasCycle)

        val opNode = snapshot.nodes.first { it.id == "fob-operator-exec" }
        assertEquals(1445, opNode.wipPieces) // 1350 + 95 rework
    }

    /** Stable, distinct module per stub id, so ids and module codes stay in sync. */
    private val stubModules = PipelinePresetFactory
        .createSnapshot(GarmentBusinessPreset.FOB_FULL_PACKAGE).nodes
        .map { it.module }

    private fun moduleForStubId(id: String) = stubModules["abcdefgh".indexOf(id)]

    /**
     * Builds a node with a distinct module per id, translating [downstream] stub ids into the
     * module codes that [PipelineGraph.from] actually resolves against.
     */
    private fun stubNode(
        id: String,
        stepNumber: Int,
        stage: PipelineStage = PipelineStage.COMMERCIAL,
        downstream: List<String> = emptyList(),
        feedbackRoutes: List<PipelineFeedbackRoute> = emptyList()
    ): PipelineNode {
        val module = moduleForStubId(id)
        return PipelineNode(
            id = id,
            module = module,
            stage = stage,
            stepNumber = stepNumber,
            title = id,
            description = "",
            assignedDepartment = "",
            deptColorHex = 0xFF000000,
            inputContract = "",
            outputContract = "out-$id",
            wipPieces = 0,
            cycleTimeHours = 0.0,
            healthStatus = FlowHealthStatus.HEALTHY,
            healthMessage = "",
            downstreamModuleCodes = downstream.map { moduleForStubId(it).code },
            feedbackRoutes = feedbackRoutes,
            inputs = emptyList()
        )
    }
}
