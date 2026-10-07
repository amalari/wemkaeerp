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

    private fun install(
        builder: ApplicationTestBuilder,
        clarifier: com.eventverse.app.domain.builder.NarrativeClarifier? = null,
        moduleEditor: com.eventverse.app.domain.builder.ModuleEditor? = null,
        drafts: InMemoryDiscoveryDraftRepository = InMemoryDiscoveryDraftRepository()
    ) = with(builder) {
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
                builderClarifier = clarifier,
                builderModuleEditor = moduleEditor,
                builderBuildRequests = com.eventverse.app.infrastructure.InMemoryBuilderBuildRequestRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderBillingInvoices = com.eventverse.app.infrastructure.InMemorySubscriptionInvoiceRepository(),
                builderBillingPreview = com.eventverse.app.domain.builder.TenantBillingPreviewSource {
                    Result.failure(IllegalStateException("tidak dipakai di test ini"))
                },
                discoveryDraftRepository = drafts
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

    @Test
    fun `cerita kabur, run mengirim status planning lalu peristiwa question, dan jawaban berikutnya menyusun draf`() = testApplication {
        install(this, clarifier = com.eventverse.app.domain.builder.NarrativeClarifier { _, _ ->
            listOf(Clarification("c1", "Pasien dilayani per poli atau satu antrean?"))
        })
        val first = events(runIdOf(startRun("Kami klinik")))
        assertEquals(listOf("status", "question", "done"), first.map { it.second })

        val history = (JsonParser.parse(client.get("/api/builder/chat") { auth() }.bodyAsText()) as JsonValue.Obj)
        val messages = history.array("messages").filterIsInstance<JsonValue.Obj>()
        assertEquals(listOf("USER", "AGENT"), messages.map { it.string("role") })
        assertEquals("QUESTION", messages.last().string("kind"))
        assertEquals(listOf("c1"), history.obj("followUp")!!.array("questions").filterIsInstance<JsonValue.Obj>().map { it.string("id").orEmpty() })

        // Jawaban pengguna: pertanyaan terjawab, tidak bertanya lagi, draf disusun.
        val second = events(runIdOf(startRun("Satu antrean lalu dibagi ke poli")))
        assertEquals(listOf("status", "message", "done"), second.map { it.second })
        val after = (JsonParser.parse(client.get("/api/builder/chat") { auth() }.bodyAsText()) as JsonValue.Obj)
        assertEquals(emptyList(), after.obj("followUp")!!.array("questions").toList(), "follow-up sudah terjawab")
    }

    // ---- Fase C: follow-up per modul + sunting isian ---------------------------------------------

    private fun seedDraft(withProposal: Boolean): InMemoryDiscoveryDraftRepository {
        val pack = com.eventverse.app.domain.pack.GarmentDomainPack.pack
        val moduleId = com.eventverse.app.domain.pack.ModuleId("sampling_order")
        val screens = if (!withProposal) emptyList() else {
            val fields = listOf(
                com.eventverse.app.domain.discovery.proposal.FieldProposal("nama", "Nama", com.eventverse.app.domain.prototype.FieldType.TEXT, true),
                com.eventverse.app.domain.discovery.proposal.FieldProposal("warna", "Warna", com.eventverse.app.domain.prototype.FieldType.TEXT)
            )
            val p = com.eventverse.app.domain.discovery.proposal.ScreenProposal(
                "s_sampling", moduleId, "Sampling", com.eventverse.app.domain.discovery.WidgetKind.TABLE, "karena uji",
                com.eventverse.app.domain.discovery.proposal.EntityProposal("sampel", "Sampel", fields),
                com.eventverse.app.domain.discovery.proposal.ViewProposal.Table(fields.map { it.key })
            )
            listOf(com.eventverse.app.domain.discovery.PrototypeScreen("s_sampling", moduleId, "Sampling", "TABLE", p, com.eventverse.app.domain.discovery.proposal.ProposalSource.Deterministic))
        }
        return InMemoryDiscoveryDraftRepository().also { repo ->
            runBlocking {
                repo.save(com.eventverse.app.domain.discovery.StoredDiscoveryDraft(
                    com.eventverse.app.domain.discovery.DiscoveryDraftId("draft-$tenantId"), com.eventverse.app.domain.auth.UserId("usr-pemilik"),
                    com.eventverse.app.domain.discovery.DiscoveryDraft(pack, com.eventverse.app.domain.pack.GarmentBlueprints.DEFAULT, screens),
                    tenantId = TenantId(tenantId)
                ))
            }
        }
    }

    private suspend fun ApplicationTestBuilder.followUps(module: String?, role: Role? = null, withToken: Boolean = true): HttpResponse =
        client.post("/api/builder/chat/followups") {
            auth(role, withToken = withToken)
            contentType(ContentType.Application.Json)
            setBody(if (module == null) "{}" else """{"module":"$module"}""")
        }

    @Test
    fun `tab modul dibuka, follow-up dibuat sekali di utas modul dan gerbang fail-closed`() = testApplication {
        install(this, drafts = seedDraft(withProposal = true))
        assertEquals(401, followUps("sampling_order", withToken = false).status.value)
        assertEquals(403, followUps("sampling_order", role = Role.SALES).status.value)
        assertEquals(400, followUps(null).status.value)

        val first = JsonParser.parse(followUps("sampling_order").bodyAsText()) as JsonValue.Obj
        assertEquals(true, (first["created"] as JsonValue.Bool).value)
        val again = JsonParser.parse(followUps("sampling_order").bodyAsText()) as JsonValue.Obj
        assertEquals(false, (again["created"] as JsonValue.Bool).value, "idempoten: masih menunggu jawaban")

        val module = JsonParser.parse(client.get("/api/builder/chat?module=sampling_order") { auth() }.bodyAsText()) as JsonValue.Obj
        assertEquals(listOf("QUESTION"), module.array("messages").filterIsInstance<JsonValue.Obj>().map { it.string("kind") })
        assertTrue(module.obj("followUp")!!.array("questions").size >= 1)
        val other = JsonParser.parse(client.get("/api/builder/chat?module=quality_control") { auth() }.bodyAsText()) as JsonValue.Obj
        assertEquals(0, other.array("messages").size, "utas modul lain tidak melihatnya")
    }

    @Test
    fun `permintaan di utas modul lewat run menjadi patch isian dari penyunting, tanpa menyusun ulang draf`() = testApplication {
        val editor = com.eventverse.app.domain.builder.ModuleEditor { _ ->
            Result.success(com.eventverse.app.domain.builder.ModuleEditReply("", listOf(
                com.eventverse.app.domain.discovery.proposal.ProposalEdit.AddField(
                    com.eventverse.app.domain.discovery.proposal.FieldProposal("tanggal_kirim", "Tanggal Kirim", com.eventverse.app.domain.prototype.FieldType.DATE)))))
        }
        install(this, moduleEditor = editor, drafts = seedDraft(withProposal = true))
        val ev = events(runIdOf(startRun("tambah input tanggal kirim", module = "sampling_order")))
        assertEquals(listOf("status", "message", "done"), ev.map { it.second })
        val msgs = ((JsonParser.parse(client.get("/api/builder/chat?module=sampling_order") { auth() }.bodyAsText()) as JsonValue.Obj).array("messages")).filterIsInstance<JsonValue.Obj>()
        assertEquals(listOf("USER", "AGENT"), msgs.map { it.string("role") })
        assertEquals(true, (msgs.last()["hasPendingPatch"] as JsonValue.Bool).value)
        assertTrue((msgs.last().array("summary").first() as JsonValue.Str).value.startsWith("Tambah isian"))
    }
}
