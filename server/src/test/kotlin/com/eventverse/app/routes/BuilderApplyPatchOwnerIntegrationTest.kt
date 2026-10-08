package com.eventverse.app.routes

import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatRole
import com.eventverse.app.domain.discovery.DiscoveryDraftId
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
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * "Terapkan" pada tenant yang **belum punya draf kerja** (Postgres sungguhan). Draf baru butuh pemilik yang ADA di `users`
 * (FK `ops.discovery_drafts.owner_user_id`); dulu pemilik dikarang (`usr-builder-<8 karakter tenant>`) sehingga
 * FK menolaknya. Hanya jalan bila `DB_NAME` berisi `scratch`.
 */
class BuilderApplyPatchOwnerIntegrationTest {

    private val suffix = abs(System.nanoTime() % 1_000_000).toString()
    private val tenantId = TenantId("ten-own-$suffix")
    private val slug = "own-$suffix"

    @Test
    fun `Terapkan tanpa draf kerja membuat draf milik pemanggil yang nyata, bukan pemilik karangan`() = testApplication {
        if (!System.getenv("DB_NAME").orEmpty().contains("scratch")) return@testApplication
        DatabaseFactory.init()

        val tenant = Tenant(tenantId, TenantSlug(slug), TenantName("Uji Pemilik"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
        // Pemanggil = pengguna yang dipakai token uji (`usr-test-<slug>`); ia nyata di DB, drafnya BELUM ada.
        val callerId = UserId("usr-test-$slug")
        runBlocking {
            PostgresTenantRepository().save(tenant).getOrThrow()
            PostgresUserRepository().save(User(callerId, tenantId, Username("pemilik_$suffix"), EmailAddress("pemilik@$slug.com"), Role.TENANT_ADMIN)).getOrThrow()
        }
        val drafts = PostgresDiscoveryDraftRepository()
        val patch = DiscoveryDraftCodec.encodeToString(InterviewEvalPacks.draftOf(InterviewEvalPacks.klinikPack, null))
        val messageId = "own-$suffix-1"
        runBlocking {
            val chats = PostgresBuilderChatRepository()
            val conv = chats.conversationFor(tenantId)
            chats.append(ChatMessage(ChatMessageId(messageId), conv.id, tenantId, ChatRole.AGENT, "usulan", proposedDraftJson = patch, proposedSummary = listOf("Modul aktif baru: klinik_kasir")))
        }
        assertEquals(null, runBlocking { drafts.findByTenant(tenantId) }, "prasyarat: tenant belum punya draf kerja")

        application {
            module(
                tenantRepository = InMemoryTenantRepository().also { runBlocking { it.save(tenant) } },
                pipelineRepository = InMemoryTenantPipelineRepository(), entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = com.eventverse.app.infrastructure.InMemoryRoleRepository(),
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository(),
                departmentRepository = com.eventverse.app.infrastructure.InMemoryDepartmentRepository(),
                employeeRepository = com.eventverse.app.infrastructure.InMemoryEmployeeRepository(),
                domainPackRepository = com.eventverse.app.infrastructure.InMemoryDomainPackRepository(),
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                discoveryDraftRepository = drafts
            )
        }

        val res = client.post("/api/builder/chat/apply") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = tenantId.value)}")
            contentType(ContentType.Application.Json)
            setBody("""{"messageId":"$messageId"}""")
        }
        assertEquals(200, res.status.value, "Terapkan harus berhasil pada tenant tanpa draf: ${res.bodyAsText()}")
        val stored = assertNotNull(runBlocking { drafts.findByTenant(tenantId) })
        assertEquals(callerId, stored.ownerUserId, "draf baru dimiliki pemanggil yang nyata, sama seperti bootstrap GET /draft")
        assertEquals(DiscoveryDraftId("draft-${tenantId.value}"), stored.id)
    }
}
