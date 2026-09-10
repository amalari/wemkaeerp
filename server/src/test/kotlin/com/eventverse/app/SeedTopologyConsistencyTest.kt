package com.eventverse.app

import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the seeded demo topologies against drift.
 *
 * The V10 migration embeds pipeline graphs generated from `PipelinePresetFactory`. If a
 * preset later changes and the migration is not regenerated, the seeded tenants would
 * silently describe a factory flow that no longer exists in the code — which is how the
 * original seed came to contain no nodes at all.
 *
 * Regenerate with:
 *   ./gradlew :server:test --tests "com.eventverse.app.GenerateSeedTopologyTool"
 * then replace the UPDATE statements in the migration with
 * `server/build/generated-seed/seed_topology.sql`.
 */
class SeedTopologyConsistencyTest {

    private val migrationPath =
        "/db/migration/V10__fix_tenant_pipeline_seed_and_business_preset.sql"

    @Test
    fun seededTopologies_shouldMatchPresetFactoryOutput() {
        val sql = readMigration()
        val seededByPipelineId = parseSeededGraphs(sql)

        assertEquals(
            DemoTenantTopologies.all.size,
            seededByPipelineId.size,
            "Jumlah tenant yang di-seed di migrasi tidak sama dengan definisi DemoTenantTopologies"
        )

        DemoTenantTopologies.all.forEach { demo ->
            val expected = demo.build()
            val seededJson = seededByPipelineId[demo.pipelineId]
            assertNotNull(seededJson, "Migrasi tidak menyeed pipeline ${demo.pipelineId}")

            val seeded = PipelineGraphCodec.decodeGraph(seededJson)

            assertEquals(
                expected.nodes.sortedBy { it.nodeId },
                seeded.nodes.sortedBy { it.nodeId },
                "Node hasil seed untuk ${demo.companyName} tidak cocok dengan PipelinePresetFactory"
            )
            assertEquals(
                expected.edges.sortedBy { it.edgeId },
                seeded.edges.sortedBy { it.edgeId },
                "Edge hasil seed untuk ${demo.companyName} tidak cocok dengan PipelinePresetFactory"
            )
        }
    }

    @Test
    fun seededTopologies_shouldNotBeEmpty() {
        val seededByPipelineId = parseSeededGraphs(readMigration())

        seededByPipelineId.forEach { (pipelineId, json) ->
            val graph = PipelineGraphCodec.decodeGraph(json)
            assertTrue(
                graph.nodes.isNotEmpty(),
                "Pipeline $pipelineId di-seed tanpa node — inilah bug yang membuat kanvas kosong"
            )
            assertTrue(
                graph.edges.isNotEmpty(),
                "Pipeline $pipelineId di-seed tanpa edge penyambung antar modul"
            )
        }
    }

    @Test
    fun seededTopologies_shouldDemonstratePerTenantCustomisation() {
        val seeded = parseSeededGraphs(readMigration())
            .mapValues { (_, json) -> PipelineGraphCodec.decodeGraph(json) }

        // Same module, different tenant-facing name per factory.
        val inventoryNames = seeded.values.mapNotNull { graph ->
            graph.nodes.firstOrNull { it.moduleId == "inventory" }?.customDisplayName
        }
        assertEquals(
            inventoryNames.size,
            inventoryNames.distinct().size,
            "Modul inventory seharusnya dinamai berbeda di setiap tenant untuk membuktikan kustomisasi"
        )

        // At least one tenant switches modules off, and at least one carries formula params.
        assertTrue(
            seeded.values.any { graph -> graph.nodes.any { it.isBypassed } },
            "Tidak ada tenant yang menonaktifkan modul; skenario CMT makloon tidak terwakili"
        )
        assertTrue(
            seeded.values.any { graph -> graph.nodes.any { it.customFormulaParameters.isNotEmpty() } },
            "Tidak ada parameter rumus per tenant yang tersimpan"
        )
        assertTrue(
            seeded.values.any { graph -> graph.nodes.any { it.isCustomPlugin } },
            "Tidak ada modul kustom (plugin) yang di-seed"
        )
    }

    private fun readMigration(): String =
        checkNotNull(javaClass.getResourceAsStream(migrationPath)) {
            "Migrasi tidak ditemukan di classpath: $migrationPath"
        }.bufferedReader().use { it.readText() }

    /**
     * Extracts `pipelineId -> graph_data JSON` from the migration's UPDATE statements.
     * SQL string literals escape a quote by doubling it, so that is undone here.
     */
    private fun parseSeededGraphs(sql: String): Map<String, String> {
        val statementPattern = Regex(
            "graph_data\\s*=\\s*'(.*?)'::jsonb.*?WHERE\\s+id\\s*=\\s*'([^']+)'",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return statementPattern.findAll(sql).associate { match ->
            val graphJson = match.groupValues[1].replace("''", "'")
            val pipelineId = match.groupValues[2]
            pipelineId to graphJson
        }
    }
}
