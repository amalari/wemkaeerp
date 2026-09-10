package com.eventverse.app.shared.pipeline

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf

/**
 * Single source of truth for turning a [CustomTenantPipeline] into JSON and back.
 *
 * Used by BOTH the Ktor server (persisting `tenant_pipelines.graph_data` and serving the
 * REST payload) and the Compose Multiplatform client (parsing that payload), so the two
 * can never drift apart the way two hand-written parsers did.
 *
 * Every field of every node and edge round-trips, including `customFormulaParameters`,
 * which the previous implementation wrote but never read back.
 */
object PipelineGraphCodec {

    /** Bumped when the on-disk shape changes in a way readers must know about. */
    const val SCHEMA_VERSION: Int = 1

    private const val KEY_VERSION = "version"
    private const val KEY_TENANT_ID = "tenantId"
    private const val KEY_PIPELINE_NAME = "pipelineName"
    private const val KEY_BASE_PRESET = "baseStarterPreset"
    private const val KEY_NODES = "nodes"
    private const val KEY_EDGES = "edges"

    /** Node + edge lists decoded from a `graph_data` document. */
    data class Graph(
        val nodes: List<CustomPipelineNode>,
        val edges: List<CustomPipelineEdge>
    )

    // -----------------------------------------------------------------------
    // graph_data column (nodes + edges only; tenant identity lives in columns)
    // -----------------------------------------------------------------------

    fun encodeGraph(
        nodes: List<CustomPipelineNode>,
        edges: List<CustomPipelineEdge>
    ): String = jsonObjectOf(
        KEY_VERSION to jsonOf(SCHEMA_VERSION),
        KEY_NODES to jsonArrayOf(nodes.map(::encodeNode)),
        KEY_EDGES to jsonArrayOf(edges.map(::encodeEdge))
    ).encode()

    fun encodeGraph(pipeline: CustomTenantPipeline): String =
        encodeGraph(pipeline.nodes, pipeline.edges)

    /**
     * Decodes a `graph_data` document. Reads strictly by key, so it is immune to the key
     * reordering and whitespace normalisation PostgreSQL applies to `JSONB` values.
     *
     * Returns an empty graph for a blank or `{}` document rather than throwing, because a
     * seeded-but-unprovisioned tenant row is a legitimate state the caller handles.
     */
    fun decodeGraph(rawJson: String?): Graph {
        val root = JsonParser.parseObjectOrNull(rawJson) ?: return Graph(emptyList(), emptyList())
        return Graph(
            nodes = root.objectArray(KEY_NODES).mapNotNull(::decodeNode),
            edges = root.objectArray(KEY_EDGES).mapNotNull(::decodeEdge)
        )
    }

    // -----------------------------------------------------------------------
    // Full REST payload
    // -----------------------------------------------------------------------

    fun encodePipeline(pipeline: CustomTenantPipeline): String = jsonObjectOf(
        KEY_VERSION to jsonOf(SCHEMA_VERSION),
        KEY_TENANT_ID to jsonOf(pipeline.tenantId.value),
        KEY_PIPELINE_NAME to jsonOf(pipeline.pipelineName),
        KEY_BASE_PRESET to jsonOf(pipeline.baseStarterPreset?.code),
        KEY_NODES to jsonArrayOf(pipeline.nodes.map(::encodeNode)),
        KEY_EDGES to jsonArrayOf(pipeline.edges.map(::encodeEdge))
    ).encode()

    /**
     * Decodes a full pipeline payload. [tenantId] always wins over any `tenantId` in the
     * body so a client cannot write into another tenant's graph by editing the JSON.
     */
    fun decodePipeline(tenantId: TenantId, rawJson: String): CustomTenantPipeline {
        val root = JsonParser.parseObject(rawJson)
        val graph = Graph(
            nodes = root.objectArray(KEY_NODES).mapNotNull(::decodeNode),
            edges = root.objectArray(KEY_EDGES).mapNotNull(::decodeEdge)
        )
        return CustomTenantPipeline(
            tenantId = tenantId,
            pipelineName = root.string(KEY_PIPELINE_NAME)?.takeIf { it.isNotBlank() }
                ?: "Alur Kerja Kustom",
            baseStarterPreset = root.string(KEY_BASE_PRESET)?.let { GarmentBusinessPreset.fromCode(it) },
            nodes = graph.nodes,
            edges = graph.edges
        )
    }

    /**
     * Decodes a payload whose tenant identity comes from the document itself.
     *
     * For client use: the app knows the tenant only by slug, while the server answers with
     * the resolved id. Server-side write paths must keep using [decodePipeline], which pins
     * the tenant from the request context so a body cannot address another tenant.
     */
    fun decodePipelineFromPayload(rawJson: String): CustomTenantPipeline {
        val tenantIdValue = JsonParser.parseObject(rawJson).string(KEY_TENANT_ID)
        require(!tenantIdValue.isNullOrBlank()) { "Pipeline payload has no tenantId" }
        return decodePipeline(TenantId(tenantIdValue), rawJson)
    }

    // -----------------------------------------------------------------------
    // Nodes
    // -----------------------------------------------------------------------

    private fun encodeNode(node: CustomPipelineNode): JsonValue = jsonObjectOf(
        "nodeId" to jsonOf(node.nodeId),
        "moduleId" to jsonOf(node.moduleId),
        "customDisplayName" to jsonOf(node.customDisplayName),
        "archetype" to jsonOf(node.archetype.code),
        "isBypassed" to jsonOf(node.isBypassed),
        "stepOrderIndex" to jsonOf(node.stepOrderIndex),
        "customFormulaParameters" to jsonStringMapOf(node.customFormulaParameters),
        "isCustomPlugin" to jsonOf(node.isCustomPlugin),
        "configSchemaJson" to encodeOpaqueJson(node.configSchemaJson)
    )

    private fun decodeNode(node: JsonValue.Obj): CustomPipelineNode? {
        val nodeId = node.string("nodeId")?.takeIf { it.isNotBlank() } ?: return null
        val moduleId = node.string("moduleId")?.takeIf { it.isNotBlank() } ?: return null

        // Trust the persisted archetype, but fall back to the module's canonical slot so a
        // payload written before archetypes existed still resolves to the right one.
        val archetype = ModuleArchetype.fromCode(node.string("archetype"))
            ?: ModuleArchetype.forModuleCode(moduleId)

        return CustomPipelineNode(
            nodeId = nodeId,
            moduleId = moduleId,
            customDisplayName = node.string("customDisplayName")?.takeIf { it.isNotBlank() }
                ?: archetype.displayName,
            archetype = archetype,
            isBypassed = node.boolean("isBypassed") ?: false,
            stepOrderIndex = node.int("stepOrderIndex") ?: 0,
            customFormulaParameters = node.stringMap("customFormulaParameters"),
            isCustomPlugin = node.boolean("isCustomPlugin")
                ?: (archetype == ModuleArchetype.CUSTOM_EXTENSION),
            configSchemaJson = decodeOpaqueJson(node, "configSchemaJson")
        )
    }

    // -----------------------------------------------------------------------
    // Edges
    // -----------------------------------------------------------------------

    private fun encodeEdge(edge: CustomPipelineEdge): JsonValue = jsonObjectOf(
        "edgeId" to jsonOf(edge.edgeId),
        "fromNodeId" to jsonOf(edge.fromNodeId),
        "toNodeId" to jsonOf(edge.toNodeId),
        "expectedDataType" to jsonOf(edge.expectedDataType),
        "isFeedbackReworkLoop" to jsonOf(edge.isFeedbackReworkLoop)
    )

    private fun decodeEdge(edge: JsonValue.Obj): CustomPipelineEdge? {
        val edgeId = edge.string("edgeId")?.takeIf { it.isNotBlank() } ?: return null
        val from = edge.string("fromNodeId")?.takeIf { it.isNotBlank() } ?: return null
        val to = edge.string("toNodeId")?.takeIf { it.isNotBlank() } ?: return null
        return CustomPipelineEdge(
            edgeId = edgeId,
            fromNodeId = from,
            toNodeId = to,
            expectedDataType = edge.string("expectedDataType")?.takeIf { it.isNotBlank() }
                ?: "StandardHandoffPayload",
            isFeedbackReworkLoop = edge.boolean("isFeedbackReworkLoop") ?: false
        )
    }

    // -----------------------------------------------------------------------
    // Opaque plugin config: embedded as real JSON when valid, as a string otherwise.
    // -----------------------------------------------------------------------

    private fun encodeOpaqueJson(raw: String?): JsonValue {
        if (raw.isNullOrBlank()) return JsonValue.Null
        return runCatching { JsonParser.parse(raw) }.getOrElse { JsonValue.Str(raw) }
    }

    private fun decodeOpaqueJson(owner: JsonValue.Obj, key: String): String? =
        when (val value = owner[key]) {
            null, is JsonValue.Null -> null
            is JsonValue.Str -> value.value.takeIf { it.isNotBlank() }
            else -> value.encode()
        }
}
