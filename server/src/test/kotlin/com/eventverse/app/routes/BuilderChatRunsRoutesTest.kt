package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatMessageKind
import com.eventverse.app.domain.builder.ChatRole
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryBuilderChatRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
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
import kotlin.test.assertTrue

/**
 * Chat Builder asinkron (PLAN-builder-interview-chat Fase A): `POST /chat/runs` → 202, `GET /runs/{id}/events` (SSE),
 * utas per modul, dan follow-up saat riwayat dimuat. Tanpa Postgres (repositori memori disuntikkan).
 */
class BuilderChatRunsRoutesTest {

    private val slug = "wemade-demo"
    private val tenantId = "ten-wemade-demo"
    private val chats = InMemoryBuilderChatRepository()
    private val story = "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir."

    private fun install(builder: ApplicationTestBuilder) = with(builder) {
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
                builderChatRepository = chats,
                builderBuildRequests = com.eventverse.app.infrastructure.InMemoryBuilderBuildRequestRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderBillingInvoices = com.eventverse.app.infrastructure.InMemorySubscriptionInvoiceRepository(),
                builderBillingPreview = com.eventverse.app.domain.builder.TenantBillingPreviewSource {
                    Result.failure(IllegalStateException("tidak dipakai di test ini"))
                },
                discoveryDraftRepository = InMemoryDiscoveryDraftRepository()
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(role: Role? = null, ownSlug: String = slug, withToken: Boolean = true) {
        header("Host", "$ownSlug.wemakeerp.com")
        if (withToken) {
            val token = if (role == null) TestAuth.tenantToken(ownSlug, tenantId = tenantId) else TestAuth.tenantToken(ownSlug, role = role, tenantId = tenantId)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun ApplicationTestBuilder.startRun(text: String, module: String? = null, role: Role? = null, withToken: Boolean = true): HttpResponse =
        client.post("/api/builder/chat/runs") {
            auth(role, withToken = withToken)
            contentType(ContentType.Application.Json)
            val m = module?.let { ""","module":"$it"""" }.orEmpty()
            setBody("""{"text":${JsonValue.Str(text).encode()}$m}""")
        }

    private fun runIdOf(r: HttpResponse) = runBlocking { (JsonParser.parse(r.bodyAsText()) as JsonValue.Obj).string("runId").orEmpty() }

    private suspend fun ApplicationTestBuilder.events(runId: String, lastEventId: String? = null): List<Pair<String?, String?>> {
        val sseClient = createClient { install(SSE) }
        val out = mutableListOf<Pair<String?, String?>>()
        sseClient.sse(urlString = "/api/builder/runs/$runId/events", request = {
            auth()
            lastEventId?.let { header("Last-Event-ID", it) }
        }) { incoming.collect { out += it.id to it.event } }
        return out
    }

    @Test
    fun `run 202 lalu SSE memutar status, message, done berurutan dan riwayat berisi pesan USER dan AGENT`() = testApplication {
        install(this)
        val accepted = startRun(story)
        assertEquals(202, accepted.status.value)
        val ev = events(runIdOf(accepted))
        assertEquals(listOf("status", "message", "done"), ev.map { it.second }, "urutan peristiwa progres")
        assertEquals(listOf("1", "2", "3"), ev.map { it.first }, "id peristiwa urut dari 1")

        val history = client.get("/api/builder/chat") { auth() }.bodyAsText()
        val roles = ((JsonParser.parse(history) as JsonValue.Obj).array("messages")).filterIsInstance<JsonValue.Obj>().map { it.string("role") }
        assertEquals(listOf("USER", "AGENT"), roles, "riwayat tetap sumber kebenaran")
    }

    @Test
    fun `Last-Event-ID melanjutkan dari peristiwa berikutnya tanpa mengulang`() = testApplication {
        install(this)
        val id = runIdOf(startRun(story))
        events(id)                                   // putar sampai selesai
        assertEquals(listOf("done"), events(id, lastEventId = "2").map { it.second })
        assertEquals(emptyList(), events(id, lastEventId = "3"), "tidak ada peristiwa baru setelah terakhir")
    }

    @Test
    fun `gerbang fail-closed, tanpa token 401, peran tak berwenang 403, run tenant lain 404, body kosong 400`() = testApplication {
        install(this)
        assertEquals(401, startRun(story, withToken = false).status.value)
        assertEquals(403, startRun(story, role = Role.SALES).status.value)
        assertEquals(400, startRun("   ").status.value)
        val id = runIdOf(startRun(story))
        val sseClient = createClient { install(SSE) }
        val denied = sseClient.get("/api/builder/runs/$id/events") { auth(role = Role.SALES) }
        assertEquals(403, denied.status.value, "peran tak berwenang tak boleh membaca aliran")
        val ghost = sseClient.get("/api/builder/runs/tidak-ada/events") { auth() }
        assertEquals(404, ghost.status.value)
    }

    @Test
    fun `pesan run bermodul masuk ke utas modul itu dan tidak muncul di utas modul lain, utas Semua memuat semuanya`() = testApplication {
        install(this)
        events(runIdOf(startRun(story)))                              // utas Semua
        events(runIdOf(startRun("Tambah tanggal kirim", module = "klinik_poli")))

        fun count(query: String): Int = runBlocking {
            val body = client.get("/api/builder/chat$query") { auth() }.bodyAsText()
            (JsonParser.parse(body) as JsonValue.Obj).array("messages").size
        }
        assertEquals(4, count(""), "utas Semua tanpa filter memuat semua pesan")
        assertEquals(2, count("?module=klinik_poli"))
        assertEquals(0, count("?module=klinik_kasir"))
    }

    @Test
    fun `follow-up dihitung setiap riwayat dimuat, utas Semua mencakup semua, utas modul hanya miliknya`() = testApplication {
        install(this)
        val conv = runBlocking { chats.conversationFor(TenantId(tenantId)) }
        runBlocking {
            chats.append(ChatMessage(ChatMessageId("q-all"), conv.id, TenantId(tenantId), ChatRole.AGENT, "Ada yang ingin saya pastikan",
                kind = ChatMessageKind.QUESTION, questions = listOf(Clarification("c1", "Satu antrean atau per poli?"), Clarification("c2", "Obat dijual?", "ya"))))
            chats.append(ChatMessage(ChatMessageId("q-poli"), conv.id, TenantId(tenantId), ChatRole.AGENT, "Soal modul poli",
                moduleId = "klinik_poli", kind = ChatMessageKind.QUESTION, questions = listOf(Clarification("c3", "Siapa yang mengisi?"))))
        }
        fun pending(query: String): List<String> = runBlocking {
            val root = JsonParser.parse(client.get("/api/builder/chat$query") { auth() }.bodyAsText()) as JsonValue.Obj
            val fu = root.obj("followUp")!!
            fu.array("questions").filterIsInstance<JsonValue.Obj>().map { it.string("id").orEmpty() } + listOf("scope=", fu.string("scope").orEmpty())
        }
        assertEquals(listOf("c1", "c3", "scope=", "all"), pending(""))
        assertEquals(listOf("c3", "scope=", "module"), pending("?module=klinik_poli"))
        assertEquals(listOf("scope=", "module"), pending("?module=klinik_kasir"))
    }

    @Test
    fun `GET chat tanpa gerbang peran 403 dan pesan lama tanpa kolom baru tetap utas Semua bertipe TEXT`() = testApplication {
        install(this)
        assertEquals(403, client.get("/api/builder/chat") { auth(role = Role.SALES) }.status.value)
        val conv = runBlocking { chats.conversationFor(TenantId(tenantId)) }
        runBlocking { chats.append(ChatMessage(ChatMessageId("lama"), conv.id, TenantId(tenantId), ChatRole.USER, "pesan lama")) }
        val m = ((JsonParser.parse(client.get("/api/builder/chat") { auth() }.bodyAsText()) as JsonValue.Obj).array("messages").single() as JsonValue.Obj)
        assertEquals("TEXT", m.string("kind"))
        assertTrue(m["moduleId"] == null || m["moduleId"] == JsonValue.Null)
    }
}
