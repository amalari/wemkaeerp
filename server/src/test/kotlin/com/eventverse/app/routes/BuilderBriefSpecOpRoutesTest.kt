package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.prototype.PrototypeContractSamples
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C4 (`POST /api/builder/draft/brief`) dan C6 (`POST /api/builder/draft/spec-ops`).
 *
 * Gerbang dan validasi **tanpa Postgres** (repository memori disuntikkan; pola `BuilderDraftBootstrapTest`).
 * Satu test jalur sukses brief memakai katalog Postgres sungguhan lewat harga, jadi hanya jalan bila `DB_NAME`
 * berisi `scratch` (lihat `LayananChangeRequestApiIntegrationTest` untuk alasannya).
 */
class BuilderBriefSpecOpRoutesTest {

    private val slug = "wemade-demo"
    private val tenantId = "ten-wemade-demo"
    private val draftRepo = InMemoryDiscoveryDraftRepository()
    private val briefPath = "/api/builder/draft/brief"
    private val specOpsPath = "/api/builder/draft/spec-ops"

    private fun installModule(builder: ApplicationTestBuilder) = with(builder) {
        val tenants = InMemoryTenantRepository().also { repo ->
            runBlocking { repo.save(Tenant(TenantId(tenantId), TenantSlug(slug), TenantName("WeMade Demo"), TenantStatus.ACTIVE, SubscriptionTier.PRO)) }
        }
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = com.eventverse.app.infrastructure.InMemoryRoleRepository(),
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository(),
                departmentRepository = com.eventverse.app.infrastructure.InMemoryDepartmentRepository(),
                employeeRepository = com.eventverse.app.infrastructure.InMemoryEmployeeRepository(),
                domainPackRepository = com.eventverse.app.infrastructure.InMemoryDomainPackRepository(),
                builderDeploymentRepository = com.eventverse.app.infrastructure.InMemoryBuilderDeploymentRepository(),
                builderChatRepository = com.eventverse.app.infrastructure.InMemoryBuilderChatRepository(),
                builderBuildRequests = com.eventverse.app.infrastructure.InMemoryBuilderBuildRequestRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderBillingInvoices = com.eventverse.app.infrastructure.InMemorySubscriptionInvoiceRepository(),
                builderBillingPreview = com.eventverse.app.domain.builder.TenantBillingPreviewSource {
                    Result.failure(IllegalStateException("tidak dipakai di test ini"))
                },
                discoveryDraftRepository = draftRepo
            )
        }
    }

    private fun seedDraft() = runBlocking {
        val pack = GarmentDomainPack.pack
        draftRepo.save(
            StoredDiscoveryDraft(
                id = DiscoveryDraftId("draft-$tenantId"),
                ownerUserId = UserId("usr-pemilik"),
                draft = DiscoveryDraft(
                    pack, GarmentBlueprints.DEFAULT,
                    pack.screenSuggestions.map { PrototypeScreen("default-" + it.moduleId.value, it.moduleId, it.title, it.widget.code) }
                ),
                status = DiscoveryDraftStatus.DRAFT,
                tenantId = TenantId(tenantId)
            )
        )
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, role: Role? = null, auth: Boolean = true): HttpResponse =
        client.post(path) {
            header("Host", "$slug.wemakeerp.com")
            if (auth) {
                val token = if (role == null) TestAuth.tenantToken(slug, tenantId = tenantId) else TestAuth.tenantToken(slug, role = role, tenantId = tenantId)
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun specOpsBody(message: String) =
        """{"message":${com.eventverse.app.shared.json.JsonValue.Str(message).encode()},"screenId":"papan","spec":${InteractiveScreenCodec.encode(PrototypeContractSamples.ticketScreen).encode()}}"""

    // ---- spec-ops (C6) --------------------------------------------------------------------------

    @Test
    fun specOps_withoutCredentials_returns401() = testApplication {
        installModule(this)
        assertEquals(401, post(specOpsPath, specOpsBody("x"), auth = false).status.value)
    }

    @Test
    fun specOps_unauthorizedRole_returns403_beforeBodyIsRead() = testApplication {
        installModule(this)
        assertEquals(403, post(specOpsPath, "bukan json", role = Role.SALES).status.value)
    }

    @Test
    fun specOps_badBodies_return400_withMessage() = testApplication {
        installModule(this)
        assertEquals(400, post(specOpsPath, "bukan json").status.value)
        assertTrue(post(specOpsPath, """{"spec":{}}""").bodyAsText().contains("message"))
        assertTrue(post(specOpsPath, """{"message":"tambah status Revisi"}""").bodyAsText().contains("spec"))
        assertTrue(post(specOpsPath, """{"message":"x","spec":{"entities":[{"label":"tanpa id"}],"screens":[],"seed":{}}}""").bodyAsText().contains("Spec tidak sah"))
    }

    @Test
    fun specOps_recognisedSentence_returnsStructuredOps_andDoesNotApplyAnything() = testApplication {
        installModule(this)
        val res = post(specOpsPath, specOpsBody("tambah status Revisi setelah Diproses"))
        assertEquals(200, res.status.value)
        val body = JsonParser.parseObject(res.bodyAsText())
        val op = body.objectArray("ops").single()
        assertEquals("AddEnumOption", op.string("type"))
        assertEquals("Revisi", op.string("option"))
        assertEquals("Diproses", op.string("after"))
    }

    @Test
    fun specOps_unrecognisedSentence_is200WithNoOps_andAnExampleInReply() = testApplication {
        installModule(this)
        val body = JsonParser.parseObject(post(specOpsPath, specOpsBody("buatkan aplikasi kasir")).bodyAsText())
        assertTrue(body.objectArray("ops").isEmpty())
        assertTrue(body.string("reply").orEmpty().contains("Contoh:"))
    }

    // ---- brief (C4) -----------------------------------------------------------------------------

    @Test
    fun brief_withoutCredentials_returns401() = testApplication {
        installModule(this)
        assertEquals(401, post(briefPath, "{}", auth = false).status.value)
    }

    @Test
    fun brief_unauthorizedRole_returns403_andCreatesNoDraft() = testApplication {
        installModule(this)
        assertEquals(403, post(briefPath, "{}", role = Role.SALES).status.value)
        assertNull(runBlocking { draftRepo.findByTenant(TenantId(tenantId)) }, "ditolak tidak boleh memicu apa pun")
    }

    @Test
    fun brief_withoutDraft_returns404_andCreatesNothing() = testApplication {
        installModule(this)
        assertEquals(404, post(briefPath, "{}").status.value)
        assertNull(runBlocking { draftRepo.findByTenant(TenantId(tenantId)) }, "brief hanya membaca")
    }

    @Test
    fun brief_foreignModuleOrBrokenChange_return400_notIgnored() = testApplication {
        seedDraft()
        installModule(this)
        assertTrue(post(briefPath, """{"included":["modul_hantu"],"changes":[]}""").bodyAsText().contains("modul_hantu"))
        assertEquals(400, post(briefPath, """{"included":["modul_hantu"]}""").status.value)
        val broken = post(briefPath, """{"included":["${GarmentModules.SAMPLING_ORDER.value}"],"changes":[{"op":{"type":"HapusSemua"}}]}""")
        assertEquals(400, broken.status.value)
        assertTrue(broken.bodyAsText().contains("Perubahan #1"))
        assertEquals(400, post(briefPath, "bukan json").status.value)
    }

    @Test
    fun brief_successPath_hasRealCoverageAndTheClientsChanges_onScratchDatabaseOnly() = testApplication {
        if (!System.getenv("DB_NAME").orEmpty().contains("scratch")) return@testApplication
        DatabaseFactory.init()
        seedDraft()
        installModule(this)
        val sampling = GarmentModules.SAMPLING_ORDER.value
        val res = post(
            briefPath,
            """{"included":["$sampling"],"changes":[{"at":"2026-10-04T10:00:00Z","ok":true,"message":null,
                "op":{"type":"AddEnumOption","entityId":"item","field":"Kolom","option":"Revisi","after":"Dikerjakan"}}]}"""
        )
        assertEquals(200, res.status.value)
        val body = JsonParser.parseObject(res.bodyAsText())
        val markdown = body.string("markdown").orEmpty()
        assertTrue("Revisi" in markdown, "perubahan klien harus ada di brief")
        val brief = body.obj("brief")!!
        assertEquals(listOf(sampling), brief.objectArray("modules").map { it.string("moduleId") })
        assertEquals(1, brief.objectArray("changes").size)
        assertTrue(brief.objectArray("coverage").single().boolean("covered") == true, "modul katalog = sudah ada, bukan karangan klien")
    }
}
