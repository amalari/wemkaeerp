package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.builder.EnsureTenantWorkingDraftUseCase
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bootstrap draf kerja Builder dari keadaan tenant (impor M0): `GET /api/builder/draft` pada tenant
 * tanpa draf membangun draf dari pipeline aktif tenant — pane Modules menampilkan modul tenant yang
 * nyata. Tenant yang sudah punya draf tidak pernah disentuh; gerbang fail-closed tetap di depan.
 * Tanpa Postgres (pola `BuilderRouteGateTest`).
 */
class BuilderDraftBootstrapTest {

    private val slug = "wemade-demo"
    private val tenantId = "ten-wemade-demo"

    private val draftRepo = InMemoryDiscoveryDraftRepository()
    private val pipelineRepo = InMemoryTenantPipelineRepository()

    private fun seedTenant() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(
                Tenant(
                    TenantId(tenantId), TenantSlug(slug), TenantName("WeMade Demo"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
        }
    }

    /** Seed pipeline tenant dari preset FOB, dengan modul pada [bypassedCodes] di-bypass. */
    private fun seedPipeline(vararg bypassedCodes: String) {
        runBlocking {
            var pipeline = CustomTenantPipeline.fromPreset(TenantId(tenantId), GarmentBlueprints.FOB_FULL_PACKAGE)
            bypassedCodes.forEach { code ->
                val nodeId = pipeline.nodes.first { it.moduleId == code }.nodeId
                pipeline = pipeline.setNodeBypassed(nodeId, true)
            }
            pipelineRepo.save(pipeline).getOrThrow()
        }
    }

    private fun ApplicationTestBuilder.installModule() {
        application {
            module(
                tenantRepository = seedTenant(),
                pipelineRepository = pipelineRepo,
                entitlementRepository = com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository(),
                roleRepository = com.eventverse.app.infrastructure.InMemoryRoleRepository(),
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository(),
                departmentRepository = com.eventverse.app.infrastructure.InMemoryDepartmentRepository(),
                employeeRepository = com.eventverse.app.infrastructure.InMemoryEmployeeRepository(),
                domainPackRepository = com.eventverse.app.infrastructure.InMemoryDomainPackRepository(),
                builderDeploymentRepository = com.eventverse.app.infrastructure.InMemoryBuilderDeploymentRepository(),
                builderChatRepository = com.eventverse.app.infrastructure.InMemoryBuilderChatRepository(),
                builderAgent = StubBuilderAgent(),
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

    private suspend fun ApplicationTestBuilder.getDraft(): io.ktor.client.statement.HttpResponse =
        client.get("/api/builder/draft") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = tenantId)}")
        }

    @Test
    fun draft_bootstrap_fromTenantPipeline_excludesBypassedModule() = testApplication {
        // Tenant punya alur nyata: FOB dengan Gudang di-bypass — draf harus mengikuti data itu,
        // bukan blueprint penuh (9 modul aktif).
        seedPipeline("inventory")
        installModule()

        val response = getDraft()

        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"modules\""), "draf bootstrap tersaji untuk pane")
        assertEquals(
            "\"activeModuleCount\":8",
            Regex("\"activeModuleCount\":\\d+").find(body)?.value,
            "9 modul FOB dikurangi 1 bypass = 8 aktif"
        )
        assertTrue(body.contains("\"blueprintCode\":\"fob_full_package\""))
    }

    @Test
    fun draft_bootstrap_isIdempotent_sameDraftIdOnSecondCall() = testApplication {
        seedPipeline()
        installModule()

        val first = getDraft()
        val second = getDraft()

        val firstId = Regex("\"id\":\"([^\"]+)\"").find(first.bodyAsText())?.groupValues?.get(1)
        val secondId = Regex("\"id\":\"([^\"]+)\"").find(second.bodyAsText())?.groupValues?.get(1)
        assertEquals(firstId, secondId, "GET kedua tidak membuat draf baru")
        assertEquals("draft-$tenantId", firstId, "id mengikuti konvensi draf kerja tenant")
    }

    @Test
    fun draft_existingDraft_isNeverReplacedByBootstrap() = testApplication {
        runBlocking {
            draftRepo.save(
                StoredDiscoveryDraft(
                    id = DiscoveryDraftId("draft-$tenantId"),
                    ownerUserId = UserId("usr-pemilik"),
                    draft = DiscoveryDraft(pack = GarmentDomainPack.pack, blueprint = GarmentBlueprints.CMT_MAKLOON),
                    status = DiscoveryDraftStatus.DRAFT,
                    tenantId = TenantId(tenantId)
                )
            )
        }
        seedPipeline()
        installModule()

        val response = getDraft()

        assertEquals(200, response.status.value)
        assertTrue(
            response.bodyAsText().contains("\"blueprintCode\":\"cmt_makloon\""),
            "draf existing tetap disajikan — bootstrap tidak pernah menimpa"
        )
    }

    @Test
    fun draft_unauthorizedRole_returns403_andCreatesNothing() = testApplication {
        seedPipeline()
        installModule()

        val response = client.get("/api/builder/draft") {
            header("X-Tenant-Slug", slug)
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, role = Role.SALES)}")
        }

        assertEquals(403, response.status.value)
        assertNull(
            runBlocking { draftRepo.findByTenant(TenantId(tenantId)) },
            "permintaan ditolak tidak boleh memicu bootstrap"
        )
    }

    @Test
    fun bootstrap_unknownPack_returnsNull_withoutSaving() {
        val useCase = EnsureTenantWorkingDraftUseCase(draftRepo, pipelineRepo)
        val result = runBlocking {
            useCase(TenantId("ten-lain"), DomainPackCode("klinik"), UserId("usr-uji"))
        }
        assertNull(result, "pack tak dikenal = fail-soft, bukan error baru")
    }

    /** Stub agent deterministik (pola `BuilderRouteGateTest`): selalu usulkan draf garment sah. */
    private class StubBuilderAgent : com.eventverse.app.domain.builder.BuilderAgent {
        override val agentRef = "stub/builder-bootstrap-test-v1"
        override suspend fun proposePatch(
            currentDraft: DiscoveryDraft?,
            history: List<com.eventverse.app.domain.builder.ChatMessage>,
            userMessage: String
        ): Result<com.eventverse.app.domain.builder.BuilderAgentReply> = Result.success(
            com.eventverse.app.domain.builder.BuilderAgentReply(
                text = "Usulan draf dari stub.",
                proposedDraft = DiscoveryDraft(pack = GarmentDomainPack.pack, blueprint = GarmentBlueprints.DEFAULT)
            )
        )
    }
}