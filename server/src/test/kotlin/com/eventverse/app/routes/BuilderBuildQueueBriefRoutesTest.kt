package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.builder.BriefSnapshot
import com.eventverse.app.domain.builder.BuildRequest
import com.eventverse.app.domain.builder.BuildRequestId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryBuilderBuildRequestRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Brief beku pada Antrian Pembuatan (opsi B): khusus platform, fail-closed, dan hanya yang benar-benar punya brief. */
class BuilderBuildQueueBriefRoutesTest {

    private val slug = "wemade-demo"
    private val tenantId = "ten-wemade-demo"
    private val requests = InMemoryBuilderBuildRequestRepository()

    private fun install(builder: ApplicationTestBuilder) = with(builder) {
        val tenants = InMemoryTenantRepository().also { repo ->
            runBlocking { repo.save(Tenant(TenantId(tenantId), TenantSlug(slug), TenantName("WeMade Demo"), TenantStatus.ACTIVE, SubscriptionTier.PRO)) }
        }
        runBlocking {
            requests.save(BuildRequest(BuildRequestId("br-1"), TenantId(tenantId), "klinik_poli", "Pack kustom belum diimplementasi",
                brief = BriefSnapshot("# Brief Kebutuhan — klinik\n\n## Belum jelas\n- [Poli] Siapa yang mengisi?", """{"packCode":"klinik"}""", Instant.parse("2026-10-08T01:00:00Z"))))
            requests.save(BuildRequest(BuildRequestId("br-lama"), TenantId(tenantId), "klinik_kasir", "Permintaan lama tanpa brief"))
        }
        application {
            module(
                tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = com.eventverse.app.infrastructure.InMemoryRoleRepository(),
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository(),
                departmentRepository = com.eventverse.app.infrastructure.InMemoryDepartmentRepository(),
                employeeRepository = com.eventverse.app.infrastructure.InMemoryEmployeeRepository(),
                domainPackRepository = com.eventverse.app.infrastructure.InMemoryDomainPackRepository(),
                builderDeploymentRepository = com.eventverse.app.infrastructure.InMemoryBuilderDeploymentRepository(),
                builderChatRepository = com.eventverse.app.infrastructure.InMemoryBuilderChatRepository(),
                builderBuildRequests = requests,
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderBillingInvoices = com.eventverse.app.infrastructure.InMemorySubscriptionInvoiceRepository(),
                builderBillingPreview = com.eventverse.app.domain.builder.TenantBillingPreviewSource { Result.failure(IllegalStateException("tidak dipakai")) },
                discoveryDraftRepository = com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository()
            )
        }
    }

    private suspend fun ApplicationTestBuilder.get(path: String, superadmin: Boolean = false, tenantAdmin: Boolean = false, anonymous: Boolean = false) =
        client.get(path) {
            header("Host", "$slug.wemakeerp.com")
            if (!anonymous) header(HttpHeaders.Authorization, "Bearer " + if (superadmin) TestAuth.superadminToken() else TestAuth.tenantToken(slug, tenantId = tenantId))
            if (tenantAdmin) header("X-Tenant-Slug", slug)
        }

    @Test
    fun `daftar antrean menandai hasBrief tanpa membocorkan isinya`() = testApplication {
        install(this)
        val body = JsonParser.parse(get("/api/builder/build-queue", superadmin = true).bodyAsText()) as JsonValue.Obj
        val rows = body.array("requests").filterIsInstance<JsonValue.Obj>().associateBy { it.string("id") }
        assertEquals(true, (rows.getValue("br-1")["hasBrief"] as JsonValue.Bool).value)
        assertEquals(false, (rows.getValue("br-lama")["hasBrief"] as JsonValue.Bool).value)
        assertTrue(!body.encode().contains("Belum jelas"), "daftar tidak memuat isi brief")
    }

    @Test
    fun `brief beku hanya untuk platform, tanpa token 401, tenant admin 403, tanpa brief atau tak dikenal 404`() = testApplication {
        install(this)
        assertEquals(401, get("/api/builder/build-queue/br-1/brief", anonymous = true).status.value)
        assertEquals(403, get("/api/builder/build-queue/br-1/brief", tenantAdmin = true).status.value, "isi brief developer bukan untuk tenant")
        assertEquals(404, get("/api/builder/build-queue/br-lama/brief", superadmin = true).status.value, "permintaan lama tanpa brief")
        assertEquals(404, get("/api/builder/build-queue/tidak-ada/brief", superadmin = true).status.value)

        val ok = get("/api/builder/build-queue/br-1/brief", superadmin = true)
        assertEquals(200, ok.status.value)
        val json = JsonParser.parse(ok.bodyAsText()) as JsonValue.Obj
        assertTrue(json.string("markdown").orEmpty().contains("## Belum jelas"))
        assertEquals("klinik", json.obj("brief")!!.string("packCode"))
        assertEquals("2026-10-08T01:00:00Z", json.string("takenAt"))
    }

    @Test
    fun `tenant melihat permintaannya sendiri tanpa brief`() = testApplication {
        install(this)
        val res = client.get("/api/builder/deployments") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = tenantId)}")
        }
        assertEquals(200, res.status.value)
        assertTrue(!res.bodyAsText().contains("Belum jelas") && !res.bodyAsText().contains("markdown"), "brief developer tidak bocor ke tenant")
    }
}
