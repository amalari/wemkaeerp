package com.eventverse.app

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage of per-tenant module provisioning over HTTP: the catalogue, switching
 * modules on and off, renaming, installing a custom plugin, and plan enforcement.
 */
class PipelineModuleApiTest {

    private val proSlug = "cv-berkah-makloon"
    private val proTenantId = TenantId("ten-demo-cmt")
    private val enterpriseSlug = "urbanwear-d2c"

    private fun tenantRepoWith(
        tier: SubscriptionTier = SubscriptionTier.PRO,
        preset: Blueprint = GarmentBlueprints.CMT_MAKLOON,
        slug: String = proSlug,
        tenantId: TenantId = proTenantId
    ): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(slug),
                    name = TenantName("Tenant Uji $slug"),
                    status = TenantStatus.ACTIVE,
                    tier = tier,
                    businessPreset = preset
                )
            )
        }
        return repo
    }

    @Test
    fun getModules_shouldReturnCatalogueWithPlanLimits() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        application { module(tenantRepository = tenantRepoWith(), pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()) }

        val response = client.get("/api/tenant/pipeline/modules") {
            asTenant(proSlug)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val catalog = TenantModuleCatalogCodec.decode(response.bodyAsText())

        assertEquals(SubscriptionTier.PRO, catalog.tier)
        assertEquals(9, catalog.maxActiveModules)
        assertFalse(catalog.allowsCustomPlugins)
        assertEquals(9, catalog.modules.size)
        // CMT bypasses procurement, so not every installed module is active.
        assertTrue(catalog.modules.any { !it.isActive })
    }

    @Test
    fun postActivation_shouldBypassModuleAndPersistIt() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        application { module(tenantRepository = tenantRepoWith(), pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()) }

        val response = client.post("/api/tenant/pipeline/modules/activation") {
            asTenant(proSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"operator_exec","isActive":false}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val returned = PipelineGraphCodec.decodePipelineFromPayload(response.bodyAsText())
        assertTrue(returned.nodes.first { it.moduleId == "operator_exec" }.isBypassed)

        // Verify it really persisted rather than only being echoed back.
        val stored = runBlocking { pipeRepo.findByTenantId(proTenantId) }
        assertNotNull(stored)
        assertTrue(stored.nodes.first { it.moduleId == "operator_exec" }.isBypassed)
    }

    @Test
    fun pipelineWrites_asOperatorWithoutFactoryFlowAccess_shouldBeRefused() = testApplication {
        // TRD-FLOW-002 Fase 2: sebelumnya setiap pengguna tenant bisa mengubah topologi alur.
        val pipeRepo = InMemoryTenantPipelineRepository()
        application { module(tenantRepository = tenantRepoWith(), pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()) }

        val activation = client.post("/api/tenant/pipeline/modules/activation") {
            asTenant(proSlug, role = Role.OPERATOR)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"operator_exec","isActive":false}""")
        }
        val reset = client.post("/api/tenant/pipeline/reset") {
            asTenant(proSlug, role = Role.OPERATOR)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val read = client.get("/api/tenant/pipeline") { asTenant(proSlug, role = Role.OPERATOR) }

        assertEquals(HttpStatusCode.Forbidden, activation.status)
        assertEquals(HttpStatusCode.Forbidden, reset.status)
        // B5 (2026-09-29): dulu membaca kanvas terbuka untuk semua anggota tenant (fail-open). Kini butuh
        // Factory Flow VIEW — operator tanpa jabatan/divisi tidak punya wewenang apa pun, sama seperti menunya.
        assertEquals(HttpStatusCode.Forbidden, read.status, "membaca kanvas butuh Factory Flow VIEW")
        val stored = runBlocking { pipeRepo.findByTenantId(proTenantId) }
        assertTrue(stored?.nodes?.none { it.moduleId == "operator_exec" && it.isBypassed } ?: true, "tulis yang ditolak tidak boleh tersimpan")
    }

    @Test
    fun postActivation_withMalformedBody_shouldReturn400() = testApplication {
        application {
            module(
                tenantRepository = tenantRepoWith(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        val response = client.post("/api/tenant/pipeline/modules/activation") {
            asTenant(proSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"operator_exec"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun putModule_shouldRenameForThisTenantOnly() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        application { module(tenantRepository = tenantRepoWith(), pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()) }

        val nodeId = runBlocking { pipeRepo.findByTenantId(proTenantId)!! }
            .nodes.first { it.moduleId == "inventory" }.nodeId

        val response = client.put("/api/tenant/pipeline/modules/$nodeId") {
            asTenant(proSlug)
            contentType(ContentType.Application.Json)
            setBody(
                """{"displayName":"Penerimaan Kain Titipan Buyer",
                   "formulaParameters":{"sewingTariffPerMinuteIdr":"550"}}"""
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val returned = PipelineGraphCodec.decodePipelineFromPayload(response.bodyAsText())
        val node = returned.nodes.first { it.nodeId == nodeId }
        assertEquals("Penerimaan Kain Titipan Buyer", node.customDisplayName)
        assertEquals("550", node.formulaParameter("sewingTariffPerMinuteIdr"))

        // Another tenant's copy of the same module keeps its own name.
        val otherTenant = runBlocking { pipeRepo.findByTenantId(TenantId("ten-demo-001"))!! }
        assertTrue(
            otherTenant.nodes.first { it.moduleId == "inventory" }.customDisplayName !=
                "Penerimaan Kain Titipan Buyer"
        )
    }

    @Test
    fun putModule_withBlankName_shouldReturn400() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        application { module(tenantRepository = tenantRepoWith(), pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()) }

        val nodeId = runBlocking { pipeRepo.findByTenantId(proTenantId)!! }.nodes.first().nodeId

        val response = client.put("/api/tenant/pipeline/modules/$nodeId") {
            asTenant(proSlug)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postCustomModule_onProPlan_shouldBeForbidden() = testApplication {
        application {
            module(
                tenantRepository = tenantRepoWith(tier = SubscriptionTier.PRO),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        val response = client.post("/api/tenant/pipeline/modules/custom") {
            asTenant(proSlug)
            contentType(ContentType.Application.Json)
            setBody(
                """{"moduleId":"sablon_bordir_custom","name":"Sablon & Bordir",
                   "archetype":"finishing"}"""
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("tidak mendukung"))
    }

    @Test
    fun postCustomModule_onEnterprisePlan_shouldInstallAndWireIt() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        val enterpriseTenantId = TenantId("ten-demo-d2c")
        application {
            module(
                tenantRepository = tenantRepoWith(
                    tier = SubscriptionTier.ENTERPRISE,
                    preset = GarmentBlueprints.BRAND_D2C,
                    slug = enterpriseSlug,
                    tenantId = enterpriseTenantId
                ),
                pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        val response = client.post("/api/tenant/pipeline/modules/custom") {
            asTenant(enterpriseSlug)
            contentType(ContentType.Application.Json)
            setBody(
                """{"moduleId":"sablon_bordir_custom","name":"Sablon Manual & Bordir Komputer",
                   "description":"Stasiun dekorasi kustom.","archetype":"finishing",
                   "configSchema":{"screenColorsMax":6},
                   "formulaParameters":{"screenPrintCostPerPcsIdr":"6500"}}"""
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val returned = PipelineGraphCodec.decodePipelineFromPayload(response.bodyAsText())
        val plugin = returned.nodes.firstOrNull { it.moduleId == "sablon_bordir_custom" }

        assertNotNull(plugin, "Modul kustom harus tersimpan di graf tenant")
        assertTrue(plugin.isCustomPlugin)
        assertEquals("6500", plugin.formulaParameter("screenPrintCostPerPcsIdr"))
        assertNotNull(plugin.configSchemaJson)
        assertTrue(
            returned.edges.any { it.toNodeId == plugin.nodeId },
            "Modul kustom tidak boleh menggantung tanpa sambungan"
        )
    }

    @Test
    fun getPipeline_whenStoredGraphIsEmpty_shouldProvisionFromTenantBusinessModel() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        // Reproduce the seeded-but-empty row the migration used to create.
        runBlocking {
            pipeRepo.save(
                CustomTenantPipeline(
                    tenantId = proTenantId,
                    pipelineName = "Alur Operasional",
                    baseStarterPreset = null,
                    nodes = emptyList(),
                    edges = emptyList()
                )
            )
        }
        application { module(tenantRepository = tenantRepoWith(), pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()) }

        val response = client.get("/api/tenant/pipeline") {
            asTenant(proSlug)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val returned = PipelineGraphCodec.decodePipelineFromPayload(response.bodyAsText())

        assertTrue(returned.nodes.isNotEmpty(), "Kanvas tidak boleh kosong")
        // Provisioned from the tenant's own business model, not the global default.
        assertEquals(GarmentBlueprints.CMT_MAKLOON, returned.baseStarterPreset)
    }

    @Test
    fun putPipeline_exceedingPlanLimit_shouldReturn400WithReason() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        application {
            module(
                tenantRepository = tenantRepoWith(tier = SubscriptionTier.STARTER),
                pipelineRepository = pipeRepo,
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        // Nine active modules against STARTER's five-module allowance.
        val allActive = runBlocking { pipeRepo.findByTenantId(proTenantId)!! }
            .let { pipeline ->
                pipeline.nodes.fold(pipeline) { acc, node -> acc.setNodeBypassed(node.nodeId, false) }
            }

        val response = client.put("/api/tenant/pipeline") {
            asTenant(proSlug)
            contentType(ContentType.Application.Json)
            setBody(PipelineGraphCodec.encodePipeline(allActive))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("paket langganan"))
    }

    @Test
    fun pipelineEndpoints_withoutCredentials_shouldNotLeakData() = testApplication {
        application {
            module(
                tenantRepository = tenantRepoWith(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        listOf(
            "/api/tenant/pipeline",
            "/api/tenant/pipeline/modules"
        ).forEach { path ->
            // Unauthenticated, and separately: authenticated but naming another tenant.
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get(path).status,
                "Endpoint $path harus menolak permintaan tanpa kredensial"
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client.get(path) { asTenantTargeting("cv-berkah-makloon", "wemade-demo") }.status,
                "Endpoint $path harus menolak akses lintas tenant"
            )
        }
    }
}
