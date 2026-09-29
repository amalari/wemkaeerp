package com.eventverse.app

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.DynamicModuleDescriptor
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import java.io.File
import kotlin.test.Test

/**
 * Authoring tool (not an assertion): emits the SQL that seeds demo tenant topologies.
 *
 * Generating the JSON from [CustomTenantPipeline.fromPreset] rather than hand-writing it
 * guarantees the migration agrees with `PipelinePresetFactory`. Output lands in
 * `server/build/generated-seed/seed_topology.sql`; paste it into a Flyway migration.
 *
 * [SeedTopologyConsistencyTest] then asserts the committed migration still matches, so the
 * two cannot silently drift apart later.
 */
class GenerateSeedTopologyTool {

    @Test
    fun emitSeedSql() {
        val outputFile = File("build/generated-seed/seed_topology.sql")
        outputFile.parentFile.mkdirs()

        val sql = buildString {
            DemoTenantTopologies.all.forEach { demo ->
                val pipeline = demo.build()
                val graphJson = PipelineGraphCodec.encodeGraph(pipeline)
                appendLine(
                    "-- ${demo.companyName} — ${demo.preset.shortBadge}: " +
                        "${pipeline.nodes.size} node / ${pipeline.edges.size} edge " +
                        "(${pipeline.activeNodes.size} aktif, ${pipeline.bypassedNodes.size} bypass, " +
                        "${pipeline.customPluginNodes.size} plugin kustom)"
                )
                appendLine("UPDATE tenant_pipelines")
                appendLine("SET pipeline_name = '${sqlQuote(pipeline.pipelineName)}',")
                appendLine("    base_preset = '${demo.preset.code.value}',")
                appendLine("    graph_data = '${sqlQuote(graphJson)}'::jsonb,")
                appendLine("    updated_at = CURRENT_TIMESTAMP")
                appendLine("WHERE id = '${demo.pipelineId}';")
                appendLine()
            }
        }

        outputFile.writeText(sql)
        println("Seed SQL written to ${outputFile.absolutePath} (${sql.length} chars)")
    }

    private fun sqlQuote(raw: String): String = raw.replace("'", "''")
}

/**
 * The demo tenants and the customisations that make them distinguishable.
 *
 * Deliberately not three copies of the same preset: each factory renames modules in its own
 * words, switches different modules off, carries its own costing parameters, and — for the
 * Enterprise tenant — runs a plugin module no other tenant has. That is the whole point of
 * per-tenant topologies, so the seed data has to demonstrate it.
 *
 * Shared with the consistency test so both describe the same intent once.
 */
object DemoTenantTopologies {

    data class DemoTenant(
        val tenantId: String,
        val pipelineId: String,
        val companyName: String,
        val preset: Blueprint,
        val customise: (CustomTenantPipeline) -> CustomTenantPipeline
    ) {
        fun build(): CustomTenantPipeline =
            customise(CustomTenantPipeline.fromPreset(TenantId(tenantId), preset))
                .rename("Alur Operasional $companyName")
    }

    /** Full-package exporter: owns its fabric, so it names the warehouse after imported rolls. */
    private val fobExporter = DemoTenant(
        tenantId = "ten-demo-001",
        pipelineId = "pipe-ten-demo-001",
        companyName = "PT WeMade Garmen Ekspor",
        preset = GarmentBlueprints.FOB_FULL_PACKAGE
    ) { pipeline ->
        pipeline
            .renameModuleByCode("inventory", "Gudang Kain Roll Impor & Aksesoris")
            .renameModuleByCode("fulfillment", "Ekspedisi Ekspor & Dokumen B/L")
            .setFormulaParametersByCode(
                "costing_hpp",
                mapOf(
                    "marginPercent" to "18.5",
                    "overheadPerPcsIdr" to "3500",
                    "fabricWastageTolerancePercent" to "4.0"
                )
            )
    }

    /**
     * CMT makloon: the buyer supplies the fabric, so procurement is switched off and the
     * receiving station is named after consigned material.
     */
    private val cmtMakloon = DemoTenant(
        tenantId = "ten-demo-cmt",
        pipelineId = "pipe-ten-demo-cmt",
        companyName = "CV Berkah Makloon Jahit",
        preset = GarmentBlueprints.CMT_MAKLOON
    ) { pipeline ->
        pipeline
            .renameModuleByCode("inventory", "Penerimaan Kain Titipan Buyer")
            .renameModuleByCode("operator_exec", "Catatan Jahit Harian Operator")
            .setFormulaParametersByCode(
                "operator_exec",
                mapOf(
                    "sewingTariffPerMinuteIdr" to "550",
                    "reworkTolerancePercent" to "2.5"
                )
            )
            .setFormulaParametersByCode(
                "costing_hpp",
                mapOf(
                    "serviceFeePerPcsIdr" to "14500",
                    "includeFabricCost" to "false"
                )
            )
    }

    /**
     * Own-brand D2C on an Enterprise plan: runs an extra printing/embroidery station that
     * exists for this tenant only, installed as a plugin module.
     */
    private val brandD2c = DemoTenant(
        tenantId = "ten-demo-d2c",
        pipelineId = "pipe-ten-demo-d2c",
        companyName = "UrbanWear Studio Apparel",
        preset = GarmentBlueprints.BRAND_D2C
    ) { pipeline ->
        val sablonPlugin = DynamicModuleDescriptor(
            moduleId = "sablon_bordir_custom",
            archetype = GarmentSlots.FINISHING,
            name = "Sablon Manual & Bordir Komputer",
            description = "Stasiun sablon plastisol dan bordir komputer khusus brand sendiri.",
            acceptedInputDataTypes = setOf("CutPiecesBundle"),
            producedOutputDataType = "DecoratedGarmentBundle",
            isCustomTenantPlugin = true,
            customConfigSchemaJson = """{"screenColorsMax":6,"embroideryStitchRate":750}"""
        )

        pipeline
            .renameModuleByCode("inventory", "Stok Katalog SKU & Bahan Brand")
            .renameModuleByCode("fulfillment", "Packing Marketplace & Kurir Instan")
            .setFormulaParametersByCode(
                "costing_hpp",
                mapOf(
                    "retailMarkupPercent" to "62.0",
                    "marketplaceFeePercent" to "6.5",
                    "packingCostPerOrderIdr" to "4200"
                )
            )
            .addNode(
                sablonPlugin.toPipelineNode(
                    nodeId = "custom-sablon-bordir",
                    formulaParameters = mapOf(
                        "screenPrintCostPerPcsIdr" to "6500",
                        "embroideryCostPer1000StitchIdr" to "1800"
                    )
                )
            )
            .wireAfterModule("operator_exec", "custom-sablon-bordir", "CutPiecesBundle")
    }

    val all: List<DemoTenant> = listOf(fobExporter, cmtMakloon, brandD2c)
}

// ---------------------------------------------------------------------------
// Seed-authoring helpers: address nodes by module code rather than node id, since the
// seed describes intent ("rename the inventory module") not preset-internal ids.
// ---------------------------------------------------------------------------

private fun CustomTenantPipeline.renameModuleByCode(
    moduleCode: String,
    displayName: String
): CustomTenantPipeline {
    val node = nodes.firstOrNull { it.moduleId == moduleCode }
        ?: error("Preset ${baseStarterPreset?.code?.value} has no module '$moduleCode' to rename")
    return renameNode(node.nodeId, displayName)
}

private fun CustomTenantPipeline.setFormulaParametersByCode(
    moduleCode: String,
    parameters: Map<String, String>
): CustomTenantPipeline {
    val node = nodes.firstOrNull { it.moduleId == moduleCode }
        ?: error("Preset ${baseStarterPreset?.code?.value} has no module '$moduleCode' to parameterise")
    return updateNodeFormulaParameters(node.nodeId, parameters)
}

private fun CustomTenantPipeline.wireAfterModule(
    upstreamModuleCode: String,
    targetNodeId: String,
    expectedDataType: String
): CustomTenantPipeline {
    val upstream = nodes.firstOrNull { it.moduleId == upstreamModuleCode }
        ?: error("Preset ${baseStarterPreset?.code?.value} has no module '$upstreamModuleCode' to wire from")
    return connect(
        com.eventverse.app.domain.pipeline.CustomPipelineEdge(
            edgeId = "edge-${upstream.nodeId}-to-$targetNodeId",
            fromNodeId = upstream.nodeId,
            toNodeId = targetNodeId,
            expectedDataType = expectedDataType
        )
    )
}
