package com.eventverse.app

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression coverage for custom-module grants.
 *
 * A custom plugin grant cannot be derived from the subscription tier — the plugin is
 * licensed to one specific factory. When the grant lived only in memory for the duration of
 * the install request, the tenant that installed a plugin had *every subsequent edit* to its
 * own pipeline rejected, because the entitlement rebuilt on the next request no longer knew
 * the plugin was allowed. The failure surfaced as an unrelated 400 on rename.
 */
class TenantEntitlementPersistenceTest {

    private val slug = "urbanwear-d2c"
    private val tenantId = TenantId("ten-demo-d2c")

    private fun enterpriseTenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(slug),
                    name = TenantName("UrbanWear Studio Apparel"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.ENTERPRISE,
                    businessPreset = GarmentBlueprints.BRAND_D2C
                )
            )
        }
        return repo
    }

    private suspend fun HttpResponse.assertStatus(expected: HttpStatusCode) {
        assertEquals(expected, status, "Body: ${bodyAsText()}")
    }

    @Test
    fun afterInstallingCustomModule_unrelatedEditsShouldStillSucceed() = testApplication {
        val pipeRepo = InMemoryTenantPipelineRepository()
        val entitlementRepo = InMemoryTenantEntitlementRepository()
        application {
            module(
                tenantRepository = enterpriseTenantRepo(),
                pipelineRepository = pipeRepo,
                entitlementRepository = entitlementRepo
            )
        }

        // 1. Install a tenant-only plugin module.
        client.post("/api/tenant/pipeline/modules/custom") {
            asTenant(slug)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"sablon_bordir_custom","name":"Sablon","archetype":"finishing"}""")
        }.assertStatus(HttpStatusCode.Created)

        // 2. The grant must have been persisted, not just held for that one request.
        val grants = runBlocking { entitlementRepo.findByTenantId(tenantId) }
        assertNotNull(grants, "Grant modul kustom harus tersimpan")
        assertTrue(grants.grantedCustomModuleIds.contains("sablon_bordir_custom"))

        // 3. An edit that has nothing to do with the plugin must not be rejected.
        val nodeId = runBlocking { pipeRepo.findByTenantId(tenantId)!! }
            .nodes.first { it.moduleId == "inventory" }.nodeId

        client.put("/api/tenant/pipeline/modules/$nodeId") {
            asTenant(slug)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Stok Katalog SKU & Bahan Brand"}""")
        }.assertStatus(HttpStatusCode.OK)

        // 4. Switching another module off must also still work.
        client.post("/api/tenant/pipeline/modules/activation") {
            asTenant(slug)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"sampling_order","isActive":false}""")
        }.assertStatus(HttpStatusCode.OK)
    }

    @Test
    fun catalog_shouldReportInstalledPluginAsGranted() = testApplication {
        val entitlementRepo = InMemoryTenantEntitlementRepository()
        application {
            module(
                tenantRepository = enterpriseTenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = entitlementRepo
            )
        }

        client.post("/api/tenant/pipeline/modules/custom") {
            asTenant(slug)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"sablon_bordir_custom","name":"Sablon","archetype":"finishing"}""")
        }.assertStatus(HttpStatusCode.Created)

        val catalog = client.get("/api/tenant/pipeline/modules") {
            asTenant(slug)
        }
        catalog.assertStatus(HttpStatusCode.OK)

        val decoded = com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec
            .decode(catalog.bodyAsText())
        val plugin = decoded.modules.firstOrNull { it.isCustomPlugin }

        assertNotNull(plugin, "Katalog harus memuat plugin terpasang")
        assertTrue(
            plugin.isGrantedByPlan,
            "Plugin yang sudah di-grant tidak boleh tampil sebagai perlu upgrade"
        )
    }

    @Test
    fun pipelineFromServer_shouldRemainReadableAfterPluginInstall() = testApplication {
        application {
            module(
                tenantRepository = enterpriseTenantRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
        }

        client.post("/api/tenant/pipeline/modules/custom") {
            asTenant(slug)
            contentType(ContentType.Application.Json)
            setBody("""{"moduleId":"sablon_bordir_custom","name":"Sablon","archetype":"finishing"}""")
        }.assertStatus(HttpStatusCode.Created)

        val response = client.get("/api/tenant/pipeline") { asTenant(slug) }
        response.assertStatus(HttpStatusCode.OK)

        val pipeline = PipelineGraphCodec.decodePipelineFromPayload(response.bodyAsText())
        assertEquals(1, pipeline.customPluginNodes.size)
    }
}
