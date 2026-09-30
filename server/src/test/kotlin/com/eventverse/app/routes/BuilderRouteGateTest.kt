package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.Deployment
import com.eventverse.app.domain.builder.DeploymentId
import com.eventverse.app.domain.builder.DeploymentNumber
import com.eventverse.app.domain.builder.DeploymentStatus
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.domain.builder.BuilderAgent
import com.eventverse.app.domain.builder.BuilderAgentReply
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.infrastructure.InMemoryBuilderChatRepository
import com.eventverse.app.infrastructure.InMemoryBuilderDeploymentRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang Builder M0 (PLAN-builder-console AC-M0-1/4/5, F2, F3): overview hanya untuk peran pembawa
 * `MANAGE_BUILDER` (fail-closed), superadmin tetap masuk lewat subdomain (carve-out act-as), dan
 * aturan host-vs-JWT untuk user tenant.
 *
 * Tanpa Postgres: repositori in-memory, sama seperti `RouteGateTest` versi in-memory.
 */
class BuilderRouteGateTest {

    private val slug = "wemade-demo"

    private fun seedRepo(): InMemoryTenantRepository = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(
                Tenant(
                    TenantId("ten-wemade-demo"), TenantSlug(slug), TenantName("WeMade Demo"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
            repo.save(
                Tenant(
                    TenantId("ten-lain"), TenantSlug("pabrik-lain"), TenantName("Pabrik Lain"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
        }
    }

    private fun seedDeployments() = InMemoryBuilderDeploymentRepository().also { repo ->
        runBlocking {
            repo.save(
                Deployment(
                    id = DeploymentId("dep-ten-wemade-demo-1"),
                    tenantId = TenantId("ten-wemade-demo"),
                    number = DeploymentNumber(1),
                    packCode = DomainPackCode("garment"),
                    appBuild = "build-uji",
                    status = DeploymentStatus.IMPORTED
                )
            ).getOrThrow()
        }
    }

    /** Set repositori in-memory penuh — tanpa Postgres (pola `RouteGateTest`). */
    private fun ApplicationTestBuilder.installModule() {
        application {
            module(
                tenantRepository = seedRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository(),
                domainPackRepository = InMemoryDomainPackRepository(),
                builderDeploymentRepository = seedDeployments(),
                builderChatRepository = com.eventverse.app.infrastructure.InMemoryBuilderChatRepository(),
                builderAgent = StubBuilderAgent(),
                builderBuildRequests = com.eventverse.app.infrastructure.InMemoryBuilderBuildRequestRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                discoveryDraftRepository = com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository()
            )
        }
    }


    @Test
    fun overview_forTenantOwner_showsImportedDeployment() = testApplication {
        installModule()

        val response = client.get("/api/builder/overview") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("IMPORTED"), "Deployment #1 IMPORTED tampil di overview")
        assertTrue(response.bodyAsText().contains("build-uji"))
        assertTrue(response.bodyAsText().contains("\"domainPackVersion\":null"), "tenant lama belum pin versi (paritas B7)")
    }

    @Test
    fun overview_forTenantStaffWithoutBuilderPermission_returns403() = testApplication {
        installModule()

        val response = client.get("/api/builder/overview") {
            header("X-Tenant-Slug", slug)
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, role = Role.SALES)}")
        }

        assertEquals(403, response.status.value)
    }

    @Test
    fun overview_withoutCredentials_returns401() = testApplication {
        installModule()

        assertEquals(401, client.get("/api/builder/overview").status.value)
    }

    @Test
    fun overview_forSuperadminViaSubdomain_returns200_evenWithHostVsJwtRule() = testApplication {
        // F2 carve-out: superadmin (tenantId = null) bebas masuk subdomain tenant mana pun.
        installModule()

        val response = client.get("/api/builder/overview") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.superadminToken()}")
        }

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains(slug))
    }

    @Test
    fun tenantBoundUser_openingAnotherTenantsSubdomain_returns403() = testApplication {
        // F2: host adalah pintu tenant kedua — user tenant A membuka subdomain tenant B = 403.
        installModule()

        val response = client.get("/api/builder/overview") {
            header("Host", "pabrik-lain.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(403, response.status.value)
    }

    @Test
    fun tenantBoundUser_targetingAnotherTenantViaHeader_returns403() = testApplication {
        installModule()

        val response = client.get("/api/builder/overview") {
            header("X-Tenant-Slug", "pabrik-lain")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(403, response.status.value)
    }


    // ------------------------------------------------------- M2: deploy & rollback

    @Test
    fun deployments_deploy_shippedPack_activates() = testApplication {
        installModule()

        // Deploy butuh draf kerja tenant: bangun lewat chat lalu terapkan patch stub.
        val sent = client.post("/api/builder/chat") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"text":"Susun draf"}""")
        }
        val messageId = Regex("\"id\":\"(msg-[^\"]+)\"").findAll(sent.bodyAsText())
            .map { it.groupValues[1] }.last()
        client.post("/api/builder/chat/apply") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"messageId":"$messageId"}""")
        }

        val deployed = client.post("/api/builder/deployments") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(200, deployed.status.value, "pack shipped → ACTIVE: ${deployed.bodyAsText()}")
        assertTrue(deployed.bodyAsText().contains("\"status\":\"ACTIVE\""))

        val list = client.get("/api/builder/deployments") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }
        assertEquals(200, list.status.value)
        assertTrue(list.bodyAsText().contains("\"deployments\""))
    }

    @Test
    fun deployments_deploy_withoutPermission_returns403() = testApplication {
        installModule()

        val response = client.post("/api/builder/deployments") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo", role = Role.OPERATOR)}")
        }

        assertEquals(403, response.status.value)
    }

    @Test
    fun deployments_deploy_withoutDraft_returns409() = testApplication {
        installModule()

        val response = client.post("/api/builder/deployments") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(409, response.status.value, "tanpa draf kerja, deploy ditolak — bukan diam-diam")
    }

    @Test
    fun mayOpenBuilder_isFailClosed_forUnknownRoles() {
        assertEquals(false, mayOpenBuilder(null), "peran tidak bisa dihitung = tolak")
        assertEquals(false, mayOpenBuilder(Role.OPERATOR))
        assertEquals(true, mayOpenBuilder(Role.TENANT_ADMIN))
        assertEquals(true, mayOpenBuilder(Role.PLATFORM_SUPERADMIN))
    }

    // ------------------------------------------------------------------ M1: chat

    @Test
    fun chat_send_returnsMessages_withPendingPatch() = testApplication {
        installModule()

        val response = client.post("/api/builder/chat") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"text":"Buatkan alur produksi kaos"}""")
        }

        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"role\":\"USER\""))
        assertTrue(body.contains("\"role\":\"AGENT\""))
        assertTrue(body.contains("\"hasPendingPatch\":true"), "agent mengusulkan patch, menunggu manusia")
    }

    @Test
    fun chat_apply_persistsTenantDraft_thenDraftEndpointServesIt() = testApplication {
        installModule()

        val sent = client.post("/api/builder/chat") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"text":"Susun draf awal"}""")
        }
        assertEquals(200, sent.status.value)
        val messageId = Regex("\"id\":\"(msg-[^\"]+)\"").findAll(sent.bodyAsText())
            .map { it.groupValues[1] }.last()

        val applied = client.post("/api/builder/chat/apply") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"messageId":"$messageId"}""")
        }
        assertEquals(200, applied.status.value, "patch sah diterapkan: ${applied.bodyAsText()}")

        val draft = client.get("/api/builder/draft") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }
        assertEquals(200, draft.status.value)
        assertTrue(draft.bodyAsText().contains("\"modules\""), "draf kerja tenant tersaji untuk pane")
    }

    @Test
    fun chat_apply_twice_returnsConflict() = testApplication {
        installModule()

        val sent = client.post("/api/builder/chat") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"text":"Susun draf"}""")
        }
        val messageId = Regex("\"id\":\"(msg-[^\"]+)\"").findAll(sent.bodyAsText())
            .map { it.groupValues[1] }.last()

        client.post("/api/builder/chat/apply") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"messageId":"$messageId"}""")
        }
        val second = client.post("/api/builder/chat/apply") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
            setBody("""{"messageId":"$messageId"}""")
        }
        assertEquals(409, second.status.value, "patch yang sudah diterapkan ditolak")
    }

    @Test
    fun chat_withoutPermission_returns403() = testApplication {
        installModule()

        val response = client.post("/api/builder/chat") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo", role = Role.OPERATOR)}")
            setBody("""{"text":"halo"}""")
        }

        assertEquals(403, response.status.value)
    }

    /** Stub agent: selalu mengusulkan draf garment sah — deterministik untuk test HTTP. */
    private class StubBuilderAgent : BuilderAgent {
        override val agentRef = "stub/builder-test-v1"
        override suspend fun proposePatch(
            currentDraft: DiscoveryDraft?,
            history: List<ChatMessage>,
            userMessage: String
        ): Result<BuilderAgentReply> = Result.success(
            BuilderAgentReply(
                text = "Usulan draf dari stub.",
                proposedDraft = DiscoveryDraft(pack = GarmentDomainPack.pack, blueprint = GarmentBlueprints.DEFAULT)
            )
        )
    }
}
