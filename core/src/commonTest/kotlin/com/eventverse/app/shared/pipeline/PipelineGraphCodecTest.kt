package com.eventverse.app.shared.pipeline

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineGraphCodecTest {

    private val tenantId = TenantId("ten-codec-test")

    private fun sampleNode(
        nodeId: String = "node-costing",
        moduleId: String = "costing_hpp",
        parameters: Map<String, String> = emptyMap(),
        isCustomPlugin: Boolean = false,
        configSchemaJson: String? = null
    ) = CustomPipelineNode(
        nodeId = nodeId,
        moduleId = moduleId,
        customDisplayName = "Kalkulasi HPP Rahasia",
        archetype = ModuleArchetype.COSTING_HPP,
        isBypassed = false,
        stepOrderIndex = 5,
        customFormulaParameters = parameters,
        isCustomPlugin = isCustomPlugin,
        configSchemaJson = configSchemaJson
    )

    @Test
    fun encodeThenDecodeGraph_shouldPreserveEveryNodeField() {
        val node = sampleNode(
            parameters = mapOf(
                "sewingTariffPerMinuteIdr" to "550",
                "marginPercent" to "18.5"
            )
        )
        val edge = CustomPipelineEdge(
            edgeId = "edge-a-to-b",
            fromNodeId = "node-costing",
            toNodeId = "node-costing",
            expectedDataType = "CostingCalculationResult",
            isFeedbackReworkLoop = true
        )

        val decoded = PipelineGraphCodec.decodeGraph(
            PipelineGraphCodec.encodeGraph(listOf(node), listOf(edge))
        )

        assertEquals(listOf(node), decoded.nodes)
        assertEquals(listOf(edge), decoded.edges)
    }

    @Test
    fun decodeGraph_withFormulaParameters_shouldNotLoseThem() {
        // Regression: the previous serialiser wrote customFormulaParameters but never read
        // them back, so tenant-specific tariffs and margins were silently dropped.
        val parameters = mapOf(
            "sewingTariffPerMinuteIdr" to "550",
            "secretMarginPercent" to "22.75"
        )

        val decoded = PipelineGraphCodec.decodeGraph(
            PipelineGraphCodec.encodeGraph(listOf(sampleNode(parameters = parameters)), emptyList())
        )

        assertEquals(parameters, decoded.nodes.single().customFormulaParameters)
    }

    @Test
    fun decodeGraph_whenKeysAreReorderedLikeJsonb_shouldStillParse() {
        // PostgreSQL JSONB normalises whitespace and reorders object keys on read. The
        // previous parser assumed "nodes" appeared before "edges" and split the document by
        // substring, which this shape breaks.
        val jsonbStyle = """
            {"edges": [{"toNodeId": "node-b", "edgeId": "edge-a-to-b",
              "isFeedbackReworkLoop": false, "fromNodeId": "node-a",
              "expectedDataType": "StandardHandoffPayload"}],
             "nodes": [{"stepOrderIndex": 1, "moduleId": "crm_sales", "nodeId": "node-a",
              "isBypassed": false, "archetype": "order_ingestion",
              "customDisplayName": "Meja Sales", "customFormulaParameters": {"a": "1"}},
              {"stepOrderIndex": 2, "moduleId": "inventory", "nodeId": "node-b",
              "isBypassed": true, "archetype": "raw_material",
              "customDisplayName": "Gudang Kain", "customFormulaParameters": {}}],
             "version": 1}
        """.trimIndent()

        val decoded = PipelineGraphCodec.decodeGraph(jsonbStyle)

        assertEquals(2, decoded.nodes.size)
        assertEquals(1, decoded.edges.size)
        assertEquals(listOf("node-a", "node-b"), decoded.nodes.map { it.nodeId })
        assertEquals(mapOf("a" to "1"), decoded.nodes.first().customFormulaParameters)
        assertTrue(decoded.nodes.last().isBypassed)
        assertEquals("edge-a-to-b", decoded.edges.single().edgeId)
    }

    @Test
    fun decodeGraph_withBlankOrEmptyDocument_shouldReturnEmptyGraph() {
        listOf(null, "", "   ", "{}").forEach { document ->
            val decoded = PipelineGraphCodec.decodeGraph(document)
            assertTrue(decoded.nodes.isEmpty(), "Expected no nodes for document: $document")
            assertTrue(decoded.edges.isEmpty(), "Expected no edges for document: $document")
        }
    }

    @Test
    fun encodeThenDecode_withQuotesAndNewlinesInName_shouldRoundTrip() {
        val awkwardName = "Gudang \"Kain\" Roll\nLine 2 \\ Blok A"
        val node = sampleNode().copy(customDisplayName = awkwardName)

        val decoded = PipelineGraphCodec.decodeGraph(
            PipelineGraphCodec.encodeGraph(listOf(node), emptyList())
        )

        assertEquals(awkwardName, decoded.nodes.single().customDisplayName)
    }

    @Test
    fun encodeThenDecode_customPluginConfigSchema_shouldRoundTrip() {
        val schema = """{"screenColorsMax":6,"embroideryStitchRate":750}"""
        val node = sampleNode(
            moduleId = "sablon_bordir_custom",
            isCustomPlugin = true,
            configSchemaJson = schema
        )

        val decoded = PipelineGraphCodec.decodeGraph(
            PipelineGraphCodec.encodeGraph(listOf(node), emptyList())
        ).nodes.single()

        assertTrue(decoded.isCustomPlugin)
        assertEquals(6, JsonSchemaProbe.intField(decoded.configSchemaJson, "screenColorsMax"))
        assertEquals(750, JsonSchemaProbe.intField(decoded.configSchemaJson, "embroideryStitchRate"))
    }

    @Test
    fun decodeNode_withoutConfigSchema_shouldBeNull() {
        val decoded = PipelineGraphCodec.decodeGraph(
            PipelineGraphCodec.encodeGraph(listOf(sampleNode()), emptyList())
        ).nodes.single()

        assertNull(decoded.configSchemaJson)
    }

    @Test
    fun encodeThenDecodePipeline_shouldPreserveNameAndPreset() {
        val original = CustomTenantPipeline.fromPreset(tenantId, GarmentBusinessPreset.CMT_MAKLOON)

        val decoded = PipelineGraphCodec.decodePipeline(
            tenantId,
            PipelineGraphCodec.encodePipeline(original)
        )

        assertEquals(original, decoded)
    }

    @Test
    fun decodePipeline_shouldIgnoreTenantIdInBody() {
        // A client must not be able to write into another tenant's graph by editing the body.
        val payload = PipelineGraphCodec.encodePipeline(
            CustomTenantPipeline.fromPreset(TenantId("ten-attacker"), GarmentBusinessPreset.FOB_FULL_PACKAGE)
        )

        val decoded = PipelineGraphCodec.decodePipeline(tenantId, payload)

        assertEquals(tenantId, decoded.tenantId)
    }

    @Test
    fun decodePipelineFromPayload_withoutTenantId_shouldFail() {
        assertFailsWith<IllegalArgumentException> {
            PipelineGraphCodec.decodePipelineFromPayload("""{"pipelineName":"X","nodes":[],"edges":[]}""")
        }
    }

    @Test
    fun decodeGraph_shouldSkipNodesMissingIdentity() {
        val document = """
            {"nodes":[{"moduleId":"crm_sales"},{"nodeId":"ok","moduleId":"inventory"}],
             "edges":[{"edgeId":"e1","fromNodeId":"ok"}]}
        """.trimIndent()

        val decoded = PipelineGraphCodec.decodeGraph(document)

        assertEquals(listOf("ok"), decoded.nodes.map { it.nodeId })
        assertTrue(decoded.edges.isEmpty(), "An edge without a target must not be materialised")
    }
}

/** Small helper so the codec test can assert inside an opaque pass-through payload. */
private object JsonSchemaProbe {
    fun intField(json: String?, field: String): Int? =
        json?.let { com.eventverse.app.shared.json.JsonParser.parseObjectOrNull(it)?.int(field) }
}
