package com.eventverse.app.routes

import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.builder.BuilderConversation
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatRole
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.infrastructure.PostgresBuilderChatRepository
import com.eventverse.app.infrastructure.PostgresDiscoveryDraftRepository
import com.eventverse.app.infrastructure.PostgresTenantRepository
import com.eventverse.app.infrastructure.PostgresUserRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Uji ujung ke ujung dengan Postgres sungguhan**: deploy pack KUSTOM (non-garment) lewat rute HTTP, lalu Antrian Pembuatan
 * dan brief beku. Ini jalur yang dulu selalu gagal (deployment BLOCKED_ON_BUILD tanpa versi pack ditolak domain *dan* CHECK SQL V81)
 * dan tidak pernah dites pada DB. Hanya jalan bila `DB_NAME` berisi `scratch`.
 */
class BuilderDeployCustomPackIntegrationTest {

    private val suffix = abs(System.nanoTime() % 1_000_000).toString()
    private val tenantId = TenantId("ten-e2e-$suffix")
    private val slug = "e2e-$suffix"

    @Test
    fun `deploy pack kustom lewat HTTP menyimpan deployment BLOCKED_ON_BUILD bervers pack, permintaan pembuatan, dan brief beku`() = testApplication {
        if (!System.getenv("DB_NAME").orEmpty().contains("scratch")) return@testApplication
        DatabaseFactory.init()

        // Tenant nyata di Postgres (FK tenants) + padanan memori untuk resolusi tenant di rute.
        val tenant = Tenant(tenantId, TenantSlug(slug), TenantName("Klinik Uji E2E"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
        runBlocking {
            PostgresTenantRepository().save(tenant).getOrThrow()
            // Pemilik draf harus pengguna nyata (FK ops.discovery_drafts.owner_user_id -> users).
            PostgresUserRepository().save(User(UserId("usr-$suffix"), tenantId, Username("owner_$suffix"), EmailAddress("owner@$slug.com"), Role.TENANT_ADMIN)).getOrThrow()
        }
        val memTenants = InMemoryTenantRepository().also { runBlocking { it.save(tenant) } }

        // Draf kerja = pack klinik (kustom, bukan bawaan platform) dengan dua modul aktif; chat nyata di Postgres.
        val drafts = PostgresDiscoveryDraftRepository()
        val pack = InterviewEvalPacks.klinikPack
        val base = InterviewEvalPacks.draftOf(pack, null)
        val draft = base.copy(blueprint = base.blueprint.copy(modules = listOf(BlueprintModule("klinik_poli", true), BlueprintModule("klinik_kasir", true))))
        runBlocking {
            drafts.save(StoredDiscoveryDraft(DiscoveryDraftId("draft-$tenantId"), UserId("usr-$suffix"), draft, tenantId = tenantId))
            val chats = PostgresBuilderChatRepository()
            val conv: BuilderConversation = chats.conversationFor(tenantId)
            chats.append(ChatMessage(ChatMessageId("e2e-$suffix-1"), conv.id, tenantId, ChatRole.USER, "Kami klinik gigi, pasien antre per poli."))
        }

        application {
            module(
                tenantRepository = memTenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = com.eventverse.app.infrastructure.InMemoryRoleRepository(),
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository(),
                departmentRepository = com.eventverse.app.infrastructure.InMemoryDepartmentRepository(),
                employeeRepository = com.eventverse.app.infrastructure.InMemoryEmployeeRepository(),
                domainPackRepository = com.eventverse.app.infrastructure.InMemoryDomainPackRepository(),
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                discoveryDraftRepository = drafts
                // sengaja TIDAK menyuntik: builderDeploymentRepository, builderBuildRequests, builderChatRepository -> Postgres
            )
        }

        val deploy = client.post("/api/builder/deployments") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = tenantId.value)}")
        }
        assertEquals(200, deploy.status.value, "deploy pack kustom harus berhasil, bukan 409: ${deploy.bodyAsText()}")
        val dep = JsonParser.parse(deploy.bodyAsText()) as JsonValue.Obj
        assertEquals("BLOCKED_ON_BUILD", dep.string("status"))
        assertEquals(1, (dep["packVersion"] as JsonValue.Num).asInt, "deployment BLOCKED_ON_BUILD membawa versi pack terkunci")

        val queue = client.get("/api/builder/build-queue") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.superadminToken()}")
        }
        assertEquals(200, queue.status.value)
        val mine = ((JsonParser.parse(queue.bodyAsText()) as JsonValue.Obj).array("requests")).filterIsInstance<JsonValue.Obj>()
            .filter { it.string("tenantId") == tenantId.value }
        assertEquals(setOf("klinik_poli", "klinik_kasir"), mine.map { it.string("moduleId").orEmpty() }.toSet())
        assertTrue(mine.all { (it["hasBrief"] as JsonValue.Bool).value }, "tiap permintaan membawa brief beku")

        val poli = mine.first { it.string("moduleId") == "klinik_poli" }
        val brief = client.get("/api/builder/build-queue/${poli.string("id")}/brief") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.superadminToken()}")
        }
        assertEquals(200, brief.status.value)
        val md = (JsonParser.parse(brief.bodyAsText()) as JsonValue.Obj).string("markdown").orEmpty()
        assertTrue("Kami klinik gigi, pasien antre per poli." in md, "cerita dari chat Postgres ikut ke brief")
        assertTrue("Poli" in md && "perlu dibangun" in md)

        // Id bersih (dulu `...-DeploymentNumber(value=1)-...`: bocoran toString kelas bernilai, dan berisiko melampaui varchar(140)).
        assertTrue(mine.all { !it.string("id").orEmpty().contains("DeploymentNumber") }, "id permintaan bersih: ${mine.map { it.string("id") }}")
        val stored = runBlocking { com.eventverse.app.infrastructure.PostgresBuilderDeploymentRepository().findByTenant(tenantId) }
        assertEquals(listOf("dep-${tenantId.value}-1"), stored.map { it.id.value }, "id deployment tersimpan bersih di Postgres")
    }
}
