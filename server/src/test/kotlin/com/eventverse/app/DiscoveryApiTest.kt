package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.get
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Bukti plan §2 A6**: funnel discovery ber-login, draf milik pemiliknya (403 untuk pengguna lain),
 * dan LOCKED immutable. Agent deterministik (kill-switch A8) sehingga tanpa jaringan & tanpa biaya.
 */
class DiscoveryApiTest {

    private val klinikSlug = "klinik-uji"
    private val garmentSlug = "garment-uji"

    @Test
    fun `tanpa login ditolak 401 dan narasi menghasilkan draf 201`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        application { app(tenants, InMemoryDiscoveryDraftRepository()) }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/api/discovery/drafts") { contentType(ContentType.Application.Json); setBody("""{"narrative":"klinik"}""") }.status
        )

        val created = client.post("/api/discovery/drafts") {
            asTenant(garmentSlug); contentType(ContentType.Application.Json)
            setBody("""{"narrative":"Kami klinik gigi dengan antrean pasien per poli dan tagihan kasir.","industryHint":"klinik"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
    }

    @Test
    fun `draf hanya bisa dibaca dan direvisi pemiliknya dan LOCKED immutable`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        val createBody = """{"id":"draft-uji-1","narrative":"Kami klinik dengan jadwal dokter dan tagihan.","industryHint":"klinik"}"""
        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/discovery/drafts") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(createBody) }.status
        )

        // Prospek lain membaca/merevisi draf itu: 403 (gerbang pemilik — T12).
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/discovery/drafts/draft-uji-1") { asTenant(klinikSlug) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/discovery/drafts/draft-uji-1") { asTenant(klinikSlug); contentType(ContentType.Application.Json); setBody(currentDocument()) }.status)

        // Pemilik boleh membaca & merevisi selama masih DRAFT.
        assertEquals(HttpStatusCode.OK, client.get("/api/discovery/drafts/draft-uji-1") { asTenant(garmentSlug) }.status)
        assertEquals(HttpStatusCode.OK, client.put("/api/discovery/drafts/draft-uji-1") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(currentDocument()) }.status)

        // Lock → update berikutnya ditolak 409 (Kontrak 5).
        assertEquals(HttpStatusCode.OK, client.post("/api/discovery/drafts/draft-uji-1/lock") { asTenant(garmentSlug) }.status)
        assertEquals(HttpStatusCode.Conflict, client.put("/api/discovery/drafts/draft-uji-1") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(currentDocument()) }.status)

        // Superadmin boleh melihat antrean seluruh draf (review platform) — via act-as workspace.
        assertEquals(HttpStatusCode.OK, client.get("/api/discovery/drafts") { asSuperadminActingAs(garmentSlug) }.status)
    }

    private fun io.ktor.server.application.Application.app(
        tenants: InMemoryTenantRepository,
        drafts: InMemoryDiscoveryDraftRepository
    ) = module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
        entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
        moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
        employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
        domainPackRepository = InMemoryDomainPackRepository(), discoveryDraftRepository = drafts)

    /** Dokumen klinik yang sama dengan keluaran agent deterministik — jadi PUT identik selalu sah. */
    private fun currentDocument() = runBlocking {
        DiscoveryDraftCodec.encodeToString(
            DeterministicDiscoveryAgent().draft(
                DiscoveryRequest("Kami klinik dengan jadwal dokter dan tagihan.", industryHint = "klinik")
            ).getOrThrow()
        )
    }

    private fun tenants() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(tenant("ten-klinik-uji", klinikSlug))
            repo.save(tenant("ten-garment-uji", garmentSlug))
        }
    }

    private fun tenant(id: String, slug: String) =
        Tenant(TenantId(id), TenantSlug(slug), TenantName(slug), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = GarmentDomainPack.CODE)
}
