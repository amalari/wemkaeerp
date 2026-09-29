package com.eventverse.app.infrastructure

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.DynamicModuleDescriptor
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.tenant.*
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for the PostgreSQL pipeline write path against the local
 * docker-compose database, following the same convention as
 * [PostgresTenantRepositoryIntegrationTest].
 *
 * This path had no test at all, which is why two defects survived in it:
 *
 *  - `graph_data` is a `JSONB` column but the Exposed table declared it as `text()`, so the
 *    driver sent a `varchar` parameter and PostgreSQL rejected the write outright.
 *  - reads reassembled the document with `substringAfter`, assuming `"nodes"` preceded
 *    `"edges"` — an assumption `JSONB` breaks, since it reorders object keys.
 *
 * Both only appear against a real server, so they can only be pinned down here.
 */
class PostgresTenantPipelineRepositoryIntegrationTest {

    private lateinit var tenantRepo: PostgresTenantRepository
    private lateinit var pipelineRepo: PostgresTenantPipelineRepository

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
        tenantRepo = PostgresTenantRepository()
        pipelineRepo = PostgresTenantPipelineRepository()
    }

    /** Pipelines reference tenants, so a tenant row has to exist first. */
    private fun createTenant(preset: Blueprint): Tenant {
        val suffix = kotlin.math.abs(System.nanoTime() % 1_000_000).toString()
        val tenant = Tenant(
            id = TenantId("ten-pipe-$suffix"),
            slug = TenantSlug("pipe-$suffix"),
            name = TenantName("PT Pabrik Pipeline $suffix"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.ENTERPRISE,
            businessPreset = preset
        )
        runBlocking { tenantRepo.save(tenant).getOrThrow() }
        return tenant
    }

    @Test
    fun savePipeline_toRealJsonbColumn_shouldSucceedAndRoundTripEveryField() = runBlocking<Unit> {
        val tenant = createTenant(GarmentBlueprints.CMT_MAKLOON)
        val plugin = DynamicModuleDescriptor(
            moduleId = "sablon_bordir_custom",
            archetype = GarmentSlots.FINISHING,
            name = "Sablon Manual & Bordir \"Premium\"",
            description = "Stasiun dekorasi kustom.",
            acceptedInputDataTypes = setOf("CutPiecesBundle"),
            producedOutputDataType = "DecoratedGarmentBundle",
            isCustomTenantPlugin = true,
            customConfigSchemaJson = """{"screenColorsMax":6}"""
        )

        val original = CustomTenantPipeline.fromPreset(tenant.id, GarmentBlueprints.CMT_MAKLOON)
            .let { pipeline ->
                val sewing = pipeline.nodes.first { it.moduleId == "operator_exec" }
                pipeline.updateNodeFormulaParameters(
                    sewing.nodeId,
                    mapOf("sewingTariffPerMinuteIdr" to "550", "reworkTolerancePercent" to "2.5")
                )
            }
            // A name containing a quote would break naive string assembly.
            .renameNode(
                CustomTenantPipeline.fromPreset(tenant.id, GarmentBlueprints.CMT_MAKLOON)
                    .nodes.first { it.moduleId == "inventory" }.nodeId,
                "Penerimaan Kain \"Titipan\" Buyer"
            )
            .addNode(plugin.toPipelineNode(nodeId = "custom-sablon"))
            .connect(
                CustomPipelineEdge(
                    edgeId = "edge-custom-sablon-rework",
                    fromNodeId = "custom-sablon",
                    toNodeId = "custom-sablon",
                    expectedDataType = "DefectReworkPayload",
                    isFeedbackReworkLoop = true
                )
            )

        // 1. The write itself is the regression: this previously failed with
        //    "column graph_data is of type jsonb but expression is of type character varying".
        val saved = pipelineRepo.save(original)
        assertTrue(saved.isSuccess, "Gagal menyimpan ke kolom JSONB: ${saved.exceptionOrNull()}")

        // 2. Read back through PostgreSQL's JSONB normalisation, which reorders keys.
        val fetched = pipelineRepo.findByTenantId(tenant.id)
        assertNotNull(fetched)

        assertEquals(original.pipelineName, fetched.pipelineName)
        assertEquals(original.baseStarterPreset, fetched.baseStarterPreset)
        assertEquals(
            original.nodes.sortedBy { it.nodeId },
            fetched.nodes.sortedBy { it.nodeId },
            "Setiap field node harus utuh setelah melewati JSONB"
        )
        assertEquals(original.edges.sortedBy { it.edgeId }, fetched.edges.sortedBy { it.edgeId })

        // 3. The specific fields that used to be silently dropped.
        val sewingNode = fetched.nodes.first { it.moduleId == "operator_exec" }
        assertEquals("550", sewingNode.formulaParameter("sewingTariffPerMinuteIdr"))
        assertEquals("2.5", sewingNode.formulaParameter("reworkTolerancePercent"))

        val pluginNode = fetched.nodes.first { it.nodeId == "custom-sablon" }
        assertTrue(pluginNode.isCustomPlugin)
        assertNotNull(pluginNode.configSchemaJson)

        assertEquals(
            "Penerimaan Kain \"Titipan\" Buyer",
            fetched.nodes.first { it.moduleId == "inventory" }.customDisplayName
        )
        assertTrue(fetched.edges.any { it.isFeedbackReworkLoop }, "Jalur rework harus ikut tersimpan")

        pipelineRepo.deleteByTenantId(tenant.id)
    }

    @Test
    fun savePipeline_calledTwice_shouldUpdateInPlaceNotDuplicate() = runBlocking<Unit> {
        val tenant = createTenant(GarmentBlueprints.FOB_FULL_PACKAGE)
        val initial = CustomTenantPipeline.fromPreset(tenant.id, GarmentBlueprints.FOB_FULL_PACKAGE)

        pipelineRepo.save(initial).getOrThrow()
        val renamedNodeId = initial.nodes.first { it.moduleId == "inventory" }.nodeId
        pipelineRepo.save(initial.renameNode(renamedNodeId, "Gudang Kain Roll Impor")).getOrThrow()

        // A duplicate row would make this fail: findByTenantId requires a single result.
        val fetched = pipelineRepo.findByTenantId(tenant.id)
        assertNotNull(fetched)
        assertEquals(
            "Gudang Kain Roll Impor",
            fetched.nodes.first { it.nodeId == renamedNodeId }.customDisplayName
        )

        pipelineRepo.deleteByTenantId(tenant.id)
    }

    @Test
    fun findByTenantId_shouldNeverReturnAnotherTenantsTopology() = runBlocking<Unit> {
        val tenantA = createTenant(GarmentBlueprints.FOB_FULL_PACKAGE)
        val tenantB = createTenant(GarmentBlueprints.CMT_MAKLOON)

        pipelineRepo.save(
            CustomTenantPipeline.fromPreset(tenantA.id, GarmentBlueprints.FOB_FULL_PACKAGE)
                .renameNode("fob-inventory", "Gudang Rahasia Tenant A")
        ).getOrThrow()

        val fetchedB = pipelineRepo.findByTenantId(tenantB.id)

        assertNull(fetchedB, "Tenant B belum punya pipeline dan tidak boleh melihat milik Tenant A")

        pipelineRepo.deleteByTenantId(tenantA.id)
    }

    @Test
    fun deleteByTenantId_shouldRemoveOnlyThatTenantsTopology() = runBlocking<Unit> {
        val tenantA = createTenant(GarmentBlueprints.FOB_FULL_PACKAGE)
        val tenantB = createTenant(GarmentBlueprints.BRAND_D2C)

        pipelineRepo.save(
            CustomTenantPipeline.fromPreset(tenantA.id, GarmentBlueprints.FOB_FULL_PACKAGE)
        ).getOrThrow()
        pipelineRepo.save(
            CustomTenantPipeline.fromPreset(tenantB.id, GarmentBlueprints.BRAND_D2C)
        ).getOrThrow()

        pipelineRepo.deleteByTenantId(tenantA.id).getOrThrow()

        assertNull(pipelineRepo.findByTenantId(tenantA.id))
        assertNotNull(
            pipelineRepo.findByTenantId(tenantB.id),
            "Menghapus alur satu tenant tidak boleh menyentuh tenant lain"
        )

        pipelineRepo.deleteByTenantId(tenantB.id)
    }

    @Test
    fun seededDemoTenants_shouldHaveNonEmptyTopologies() = runBlocking<Unit> {
        // Guards the migration defect directly: the demo tenants used to hold a row whose
        // graph_data carried no nodes, leaving the factory canvas blank.
        listOf(
            TenantId("ten-demo-001") to GarmentBlueprints.FOB_FULL_PACKAGE,
            TenantId("ten-demo-cmt") to GarmentBlueprints.CMT_MAKLOON,
            TenantId("ten-demo-d2c") to GarmentBlueprints.BRAND_D2C
        ).forEach { (tenantId, expectedPreset) ->
            val pipeline = pipelineRepo.findByTenantId(tenantId)
            assertNotNull(pipeline, "Tenant demo $tenantId harus punya pipeline hasil seed")
            assertTrue(pipeline.nodes.isNotEmpty(), "Pipeline $tenantId tidak boleh kosong")
            assertTrue(pipeline.edges.isNotEmpty(), "Pipeline $tenantId harus punya sambungan modul")
            assertEquals(expectedPreset, pipeline.baseStarterPreset)
        }
    }

    @Test
    fun seededDemoTenants_shouldCarryPerTenantCustomisation() = runBlocking<Unit> {
        val cmt = pipelineRepo.findByTenantId(TenantId("ten-demo-cmt"))
        val d2c = pipelineRepo.findByTenantId(TenantId("ten-demo-d2c"))
        assertNotNull(cmt)
        assertNotNull(d2c)

        // CMT switches procurement off; D2C runs a tenant-only plugin module.
        assertTrue(cmt.bypassedNodes.isNotEmpty(), "CMT makloon harus punya modul yang di-bypass")
        assertTrue(cmt.nodes.any { it.customFormulaParameters.isNotEmpty() })
        assertTrue(d2c.customPluginNodes.isNotEmpty(), "Tenant D2C harus punya modul kustom")

        // The same built-in module is named differently per tenant.
        assertTrue(
            cmt.nodes.first { it.moduleId == "inventory" }.customDisplayName !=
                d2c.nodes.first { it.moduleId == "inventory" }.customDisplayName
        )
    }
}
