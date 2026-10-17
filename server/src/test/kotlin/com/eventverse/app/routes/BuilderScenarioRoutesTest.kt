package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.BuilderAgent
import com.eventverse.app.domain.builder.BuilderAgentReply
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioIssue
import com.eventverse.app.domain.discovery.scenario.ScenarioService
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryBuilderChatRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `POST /api/builder/draft/scenarios/{propose,confirm}` dengan `ScenarioService` PALSU (service konkret = Track A). */
class BuilderScenarioRoutesTest {

    private val slug = "wemade-demo"
    private val tenantId = "ten-wemade-demo"

    private fun sketch(id: String = "scn-1", confirmed: Boolean = false) = ScenarioSketch(
        ScenarioId(id), "Lead ke invoice",
        listOf(ScenarioStep(ModuleId("crm"), null, "Lead"), ScenarioStep(ModuleId("sales"), null, "Invoice")),
        ScenarioSource.AI, confirmed
    )

    private class FakeService(
        var onPropose: (TenantId) -> Result<List<ScenarioSketch>> = { Result.success(emptyList()) },
        var onConfirm: (TenantId, ScenarioId) -> Result<ScenarioSketch> = { _, _ -> Result.failure(IllegalArgumentException("x")) }
    ) : ScenarioService {
        val proposeCalls = mutableListOf<TenantId>()
        val confirmCalls = mutableListOf<Pair<TenantId, ScenarioId>>()
        override suspend fun propose(tenantId: TenantId): Result<List<ScenarioSketch>> { proposeCalls += tenantId; return onPropose(tenantId) }
        override suspend fun confirm(tenantId: TenantId, id: ScenarioId): Result<ScenarioSketch> { confirmCalls += tenantId to id; return onConfirm(tenantId, id) }
    }

    private class BlockingAgent(val release: CompletableDeferred<Unit>) : BuilderAgent {
        override val agentRef = "stub/blocking"
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String): Result<BuilderAgentReply> {
            release.await()
            return Result.success(BuilderAgentReply(text = "selesai"))
        }
    }

    private fun install(
        builder: ApplicationTestBuilder, service: ScenarioService?, agent: BuilderAgent? = null,
        chats: com.eventverse.app.domain.builder.BuilderChatRepository = InMemoryBuilderChatRepository()
    ) = with(builder) {
        val tenants = InMemoryTenantRepository().also { repo ->
            runBlocking {
                repo.save(Tenant(TenantId(tenantId), TenantSlug(slug), TenantName("WeMade Demo"), TenantStatus.ACTIVE, SubscriptionTier.PRO))
                repo.save(Tenant(TenantId("ten-lain"), TenantSlug("pabrik-lain"), TenantName("Pabrik Lain"), TenantStatus.ACTIVE, SubscriptionTier.PRO))
            }
        }
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository(),
                entitlementRepository = com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository(),
                roleRepository = com.eventverse.app.infrastructure.InMemoryRoleRepository(),
                moduleAssignmentRepository = com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository(),
                departmentRepository = com.eventverse.app.infrastructure.InMemoryDepartmentRepository(),
                employeeRepository = com.eventverse.app.infrastructure.InMemoryEmployeeRepository(),
                domainPackRepository = com.eventverse.app.infrastructure.InMemoryDomainPackRepository(),
                builderDeploymentRepository = com.eventverse.app.infrastructure.InMemoryBuilderDeploymentRepository(),
                builderChatRepository = chats,
                builderAgent = agent,
                builderInterviewPlanner = null,
                builderModuleEditor = null,
                builderScenarios = service,
                builderBuildRequests = com.eventverse.app.infrastructure.InMemoryBuilderBuildRequestRepository(),
                builderProbe = com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe { false },
                builderAuditLog = com.eventverse.app.infrastructure.InMemoryAuditLogRepository(),
                builderBillingInvoices = com.eventverse.app.infrastructure.InMemorySubscriptionInvoiceRepository(),
                builderBillingPreview = com.eventverse.app.domain.builder.TenantBillingPreviewSource {
                    Result.failure(IllegalStateException("tidak dipakai di test ini"))
                },
                discoveryDraftRepository = com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository()
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(role: Role? = null, withToken: Boolean = true, host: String = slug, tid: String = tenantId) {
        header("Host", "$host.wemakeerp.com")
        if (withToken) {
            val token = if (role == null) TestAuth.tenantToken(host, tenantId = tid) else TestAuth.tenantToken(host, role = role, tenantId = tid)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun ApplicationTestBuilder.call(
        path: String, body: String? = "{}", role: Role? = null, withToken: Boolean = true, host: String = slug, tid: String = tenantId
    ): HttpResponse = client.post("/api/builder/draft/scenarios/$path") {
        auth(role, withToken, host, tid)
        contentType(ContentType.Application.Json)
        if (body != null) setBody(body)
    }

    private fun obj(text: String) = JsonParser.parse(text) as JsonValue.Obj

    @Test
    fun `propose sukses mengeluarkan bentuk codec dan memakai tenant dari konteks`() = testApplication {
        val svc = FakeService(onPropose = { Result.success(listOf(sketch())) })
        install(this, svc)
        val r = call("propose")
        assertEquals(200, r.status.value)
        val sc = obj(r.bodyAsText()).array("scenarios").filterIsInstance<JsonValue.Obj>().single()
        assertEquals("scn-1", sc.string("id"))
        assertEquals("AI", sc.string("source"))
        assertEquals(2, (sc["steps"] as JsonValue.Arr).items.size)
        assertEquals(listOf(TenantId(tenantId)), svc.proposeCalls)
        assertEquals(200, call("propose", body = null).status.value, "body kosong diterima")
    }

    @Test
    fun `confirm sukses mengembalikan skenario terkonfirmasi`() = testApplication {
        val svc = FakeService(onConfirm = { _, id -> Result.success(sketch(id.value, confirmed = true)) })
        install(this, svc)
        val r = call("confirm", """{"id":"scn-1"}""")
        assertEquals(200, r.status.value)
        val sc = obj(r.bodyAsText())["scenario"] as JsonValue.Obj
        assertEquals("scn-1", sc.string("id"))
        assertEquals("true", sc["confirmed"]?.encode())
        assertEquals(listOf(TenantId(tenantId) to ScenarioId("scn-1")), svc.confirmCalls)
    }

    @Test
    fun `peran tak berwenang 403 dan tanpa token 401, service tidak pernah dipanggil`() = testApplication {
        val svc = FakeService()
        install(this, svc)
        assertEquals(403, call("propose", role = Role.SALES).status.value)
        assertEquals(403, call("confirm", """{"id":"scn-1"}""", role = Role.SALES).status.value)
        assertEquals(401, call("propose", withToken = false).status.value)
        assertEquals(401, call("confirm", """{"id":"scn-1"}""", withToken = false).status.value)
        assertTrue(svc.proposeCalls.isEmpty() && svc.confirmCalls.isEmpty(), "peran tak berwenang tidak boleh sampai ke service")
    }

    @Test
    fun `body rusak atau id kosong 400`() = testApplication {
        val svc = FakeService()
        install(this, svc)
        assertEquals(400, call("propose", "[1]").status.value)
        assertEquals(400, call("propose", "bukan json").status.value)
        assertEquals(400, call("confirm", "bukan json").status.value)
        assertEquals(400, call("confirm", "{}").status.value)
        assertEquals(400, call("confirm", """{"id":"  "}""").status.value)
        assertTrue(svc.proposeCalls.isEmpty() && svc.confirmCalls.isEmpty())
    }

    @Test
    fun `confirm id tak ada 404 dan galat validasi berpath 422 dengan issues`() = testApplication {
        val svc = FakeService(onConfirm = { _, id ->
            when (id.value) {
                "hilang" -> Result.failure(IllegalArgumentException("Skenario 'hilang' tidak ada"))
                "typed" -> Result.failure(com.eventverse.app.domain.discovery.scenario.ScenarioInvalidException(listOf(ScenarioIssue("steps[1].screenId", "bukan milik modul"))))
                "terkunci" -> Result.failure(com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase.LockedException("Draf d1 sudah terkunci"))
                else -> Result.failure(IllegalArgumentException("steps[0].carries[0].toField: field tidak ada"))
            }
        })
        install(this, svc)
        val gone = call("confirm", """{"id":"hilang"}""")
        assertEquals(404, gone.status.value)
        assertEquals("Skenario 'hilang' tidak ada", obj(gone.bodyAsText()).string("error"), "404 id tak ada membawa badan JSON {error}")
        assertEquals(423, call("confirm", """{"id":"terkunci"}""").status.value)
        val typed = call("confirm", """{"id":"typed"}""")
        assertEquals(422, typed.status.value)
        val issue = obj(typed.bodyAsText()).array("issues").filterIsInstance<JsonValue.Obj>().single()
        assertEquals("steps[1].screenId", issue.string("path"))
        assertEquals("bukan milik modul", issue.string("message"))
        // Hanya tipe galat resmi yang berarti "tak sah": pesan berisi path pada IllegalArgumentException biasa TIDAK ditebak jadi 422.
        assertEquals(404, call("confirm", """{"id":"berpath"}""").status.value)
    }

    @Test
    fun `kegagalan lain menjadi 500 jujur`() = testApplication {
        install(this, FakeService(onPropose = { Result.failure(IllegalStateException("basis rusak")) }))
        assertEquals(500, call("propose").status.value)
    }

    @Test
    fun `run chat aktif memberi 409 dan service tak dipanggil`() = testApplication {
        val release = CompletableDeferred<Unit>()
        val svc = FakeService()
        install(this, svc, BlockingAgent(release))
        val started = client.post("/api/builder/chat/runs") {
            auth(); contentType(ContentType.Application.Json); setBody("""{"text":"Kami klinik gigi dengan antrean per poli dan kasir."}""")
        }
        assertEquals(202, started.status.value)
        assertEquals(409, call("propose").status.value)
        assertEquals(409, call("confirm", """{"id":"scn-1"}""").status.value)
        assertTrue(svc.proposeCalls.isEmpty() && svc.confirmCalls.isEmpty())
        release.complete(Unit)
    }

    @Test
    fun `flag mati (service null) 404 setelah gerbang`() = testApplication {
        install(this, null)
        assertEquals(404, call("propose").status.value)
        assertEquals(404, call("confirm", """{"id":"x"}""").status.value)
        assertEquals(403, call("propose", role = Role.SALES).status.value)
    }

    @Test
    fun `tenant lain memanggil dengan tenant-nya sendiri, bukan tenant pertama`() = testApplication {
        val svc = FakeService(onPropose = { Result.success(emptyList()) })
        install(this, svc)
        assertEquals(200, call("propose", host = "pabrik-lain", tid = "ten-lain").status.value)
        assertEquals(listOf(TenantId("ten-lain")), svc.proposeCalls)
    }

    // --- SC3 / FR-9: pertanyaan skenario menjadi pesan QUESTION utas Semua ---

    private fun withQuestions(vararg q: String) = sketch().copy(questions = q.toList())
    private suspend fun com.eventverse.app.domain.builder.BuilderChatRepository.of(t: String) = messages(conversationFor(TenantId(t)).id)

    @Test
    fun `propose dengan pertanyaan membuat pesan QUESTION utas Semua hanya untuk tenant pemanggil`() = testApplication {
        val chats = InMemoryBuilderChatRepository()
        install(this, FakeService(onPropose = { Result.success(listOf(withQuestions("DP harus lunas dulu?"))) }), chats = chats)
        assertEquals(200, call("propose").status.value)
        val m = runBlocking { chats.of(tenantId) }.single()
        assertEquals(com.eventverse.app.domain.builder.ChatMessageKind.QUESTION, m.kind)
        assertEquals(null, m.moduleId)
        assertTrue(m.questions.single().id.startsWith("scn:scn-1:"))
        assertTrue(runBlocking { chats.of("ten-lain") }.isEmpty(), "tenant lain tidak ikut")
        assertEquals(200, call("propose").status.value)
        assertEquals(1, runBlocking { chats.of(tenantId) }.size, "propose berulang tidak menggandakan pesan")
    }

    @Test
    fun `peran tak berwenang 403 tidak membuat pesan pertanyaan`() = testApplication {
        val chats = InMemoryBuilderChatRepository()
        install(this, FakeService(onPropose = { Result.success(listOf(withQuestions("DP harus lunas dulu?"))) }), chats = chats)
        assertEquals(403, call("propose", role = Role.SALES).status.value)
        assertEquals(401, call("propose", withToken = false).status.value)
        assertTrue(runBlocking { chats.of(tenantId) }.isEmpty())
    }

    @Test
    fun `propose gagal atau draf terkunci tidak membuat pesan pertanyaan`() = testApplication {
        val chats = InMemoryBuilderChatRepository()
        val svc = FakeService(onPropose = { Result.failure(com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase.LockedException("terkunci")) })
        install(this, svc, chats = chats)
        assertEquals(423, call("propose").status.value)
        svc.onPropose = { Result.failure(IllegalStateException("basis rusak")) }
        assertEquals(500, call("propose").status.value)
        assertTrue(runBlocking { chats.of(tenantId) }.isEmpty())
    }

    @Test
    fun `skenario tanpa pertanyaan tidak membuat pesan`() = testApplication {
        val chats = InMemoryBuilderChatRepository()
        install(this, FakeService(onPropose = { Result.success(listOf(sketch())) }), chats = chats)
        assertEquals(200, call("propose").status.value)
        assertTrue(runBlocking { chats.of(tenantId) }.isEmpty())
    }
}
