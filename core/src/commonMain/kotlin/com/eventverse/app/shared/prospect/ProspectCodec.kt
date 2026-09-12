package com.eventverse.app.shared.prospect

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.CapabilityRequirement
import com.eventverse.app.domain.prospect.CoverageAnalysis
import com.eventverse.app.domain.prospect.CoverageDecision
import com.eventverse.app.domain.prospect.CoverageKind
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.moduledev.ModuleDevCodec
import com.eventverse.app.shared.pipeline.PipelineGraphCodec

/**
 * JSON encoding for the prospect flow's JSONB columns.
 *
 * Hand-rolled on the project's own JSON helpers, like every other codec here — there is no
 * kotlinx-serialization dependency in this build.
 *
 * The proposed pipeline reuses [PipelineGraphCodec] rather than getting its own format: a prospect's
 * graph is the same shape as a tenant's, and the day one is converted into the other, a second
 * encoding would be a migration instead of a copy.
 *
 * Decoding is forgiving by design. These rows are read while listing a review queue, and one
 * malformed legacy payload should cost a single field, not the whole screen.
 */
object ProspectCodec {

    // ---- Proposed pipeline ---------------------------------------------------

    fun encodePipeline(pipeline: CustomTenantPipeline): String =
        PipelineGraphCodec.encodeGraph(pipeline)

    fun decodePipeline(tenantId: TenantId, name: String, rawJson: String?): CustomTenantPipeline {
        val graph = PipelineGraphCodec.decodeGraph(rawJson)
        return CustomTenantPipeline(
            tenantId = tenantId,
            pipelineName = name,
            baseStarterPreset = null,
            nodes = graph.nodes,
            edges = graph.edges
        )
    }

    // ---- Capability requirements --------------------------------------------

    fun encodeRequirements(requirements: List<CapabilityRequirement>): String =
        jsonArrayOf(
            requirements.map { requirement ->
                jsonObjectOf(
                    "archetypeCode" to jsonOf(requirement.archetype.code),
                    "title" to jsonOf(requirement.title),
                    "description" to jsonOf(requirement.description),
                    "sourceQuote" to jsonOf(requirement.sourceQuote),
                    "features" to JsonParser.parseObject(
                        ModuleDevCodec.encodeFeatures(requirement.features)
                    )
                )
            }
        ).encode()

    fun decodeRequirements(rawJson: String?): List<CapabilityRequirement> =
        runCatching { JsonParser.parseArray(rawJson ?: "[]") }
            .getOrDefault(emptyList())
            .filterIsInstance<JsonValue.Obj>()
            .mapNotNull { obj ->
                val title = obj.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CapabilityRequirement(
                    // An unrecognised code falls back to the wildcard rather than dropping the
                    // requirement — the same rule the translator applies, so a stored row and a
                    // fresh translation behave identically.
                    archetype = ModuleArchetype.fromCode(obj.string("archetypeCode"))
                        ?: ModuleArchetype.CUSTOM_EXTENSION,
                    title = title,
                    description = obj.string("description").orEmpty(),
                    sourceQuote = obj.string("sourceQuote").orEmpty(),
                    features = ModuleDevCodec.decodeFeatures(obj.rawJson("features"))
                )
            }

    // ---- Coverage ------------------------------------------------------------

    fun encodeCoverage(analysis: CoverageAnalysis): String =
        jsonArrayOf(
            analysis.decisions.mapIndexed { index, decision ->
                jsonObjectOf(
                    "requirementIndex" to jsonOf(index),
                    "title" to jsonOf(decision.requirement.title),
                    "kind" to jsonOf(decision.kind.code),
                    "moduleId" to jsonOf(
                        when (decision) {
                            is CoverageDecision.CoveredByCatalog -> decision.entry.moduleId
                            is CoverageDecision.Gap -> decision.proposedModuleId
                        }
                    )
                )
            }
        ).encode()

    /**
     * Reads back only what the row records: which requirement, covered or not, and under which id.
     *
     * Deliberately not rebuilt into [CoverageDecision] — that carries a live `ModuleCatalogEntry`,
     * and resurrecting one from a stored id would show a reviewer the module's price as it is
     * *today* while presenting it as part of a quote made months ago.
     */
    fun decodeCoverageSummaries(rawJson: String?): List<CoverageSummary> =
        runCatching { JsonParser.parseArray(rawJson ?: "[]") }
            .getOrDefault(emptyList())
            .filterIsInstance<JsonValue.Obj>()
            .mapNotNull { obj ->
                val kind = CoverageKind.fromCode(obj.string("kind")) ?: return@mapNotNull null
                CoverageSummary(
                    requirementIndex = obj.int("requirementIndex") ?: 0,
                    title = obj.string("title").orEmpty(),
                    kind = kind,
                    moduleId = obj.string("moduleId").orEmpty()
                )
            }

    fun encodeStrings(values: List<String>): String = ModuleDevCodec.encodeStrings(values)

    fun decodeStrings(rawJson: String?): List<String> = ModuleDevCodec.decodeStrings(rawJson)
}

/** A coverage decision as stored: the facts, without the live catalogue entry behind them. */
data class CoverageSummary(
    val requirementIndex: Int,
    val title: String,
    val kind: CoverageKind,
    val moduleId: String
)
