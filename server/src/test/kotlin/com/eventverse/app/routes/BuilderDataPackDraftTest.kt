package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.LayananPilotPack
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pack **data** (modul pilot `layanan`) lewat jalur nyata: plugin tenant memuat pack dari repository (bukan dari kode),
 * lalu `GET /api/builder/draft` mengembalikan draf yang sudah disemai. Dulu `null` ("Belum ada draf kerja") karena
 * bootstrap mencari blueprint garmen sebelum mengecek draf tersimpan. Tanpa Postgres (repository memori).
 */
class BuilderDataPackDraftTest {
    private val slug = "layanan-demo"
    private val tenantId = "ten-layanan-demo"

    @AfterTest
    fun cleanRegistry() { DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    @Test
    fun tenantOfADataPack_withSeededDraft_getsItFromTheBuilderDraftEndpoint() = testApplication {
        DomainPackRegistry.unregister(LayananPilotPack.CODE) // dimuat malas oleh plugin tenant, bukan didaftarkan manual
        val packs = InMemoryDomainPackRepository()
        runBlocking { packs.save(StoredDomainPack(LayananPilotPack.pack, 1, DomainPackStatus.LOCKED, null)) }
        val drafts = InMemoryDiscoveryDraftRepository()
        runBlocking {
            drafts.save(
                StoredDiscoveryDraft(
                    id = DiscoveryDraftId("draft-$tenantId"), ownerUserId = UserId("usr-pemilik"),
                    draft = DiscoveryDraft(
                        LayananPilotPack.pack,
                        Blueprint(
                            BlueprintCode("layanan_starter"), LayananPilotPack.CODE, "Starter Layanan", "Pilot", "Draf pilot", "Tim layanan",
                            listOf(BlueprintModule(LayananPilotPack.CHANGE_REQUEST.value, active = true))
                        ),
                        listOf(PrototypeScreen("default-${LayananPilotPack.CHANGE_REQUEST.value}", LayananPilotPack.CHANGE_REQUEST, "Papan Permintaan", "KANBAN"))
                    ),
                    tenantId = TenantId(tenantId)
                )
            )
        }
        val tenants = InMemoryTenantRepository().also { repo ->
            runBlocking {
                repo.save(Tenant(TenantId(tenantId), TenantSlug(slug), TenantName("Layanan Demo"), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = LayananPilotPack.CODE))
            }
        }
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                domainPackRepository = packs,
                discoveryDraftRepository = drafts
            )
        }

        val response = client.get("/api/builder/draft") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = tenantId)}")
        }

        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue("Papan Permintaan" in body, "draf pilot harus tersaji, bukan 'null': $body")
        assertTrue("\"packCode\":\"layanan\"" in body)
        // C3: ikatan data layar pilot sampai ke klien sebagai {"type":"api","basePath":…}.
        assertTrue("\"binding\":{\"type\":\"api\",\"basePath\":\"${LayananPilotPack.API_BASE_PATH}\"}" in body, "binding Api harus tersaji: $body")
    }
}
