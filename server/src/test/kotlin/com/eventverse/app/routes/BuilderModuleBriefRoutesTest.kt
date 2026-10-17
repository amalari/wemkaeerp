package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.BriefFeature
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.BriefStatus
import com.eventverse.app.domain.builder.BuilderAgent
import com.eventverse.app.domain.builder.BuilderAgentReply
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatMessageKind
import com.eventverse.app.domain.builder.ChatRole
import com.eventverse.app.domain.builder.ModuleBriefOutcome
import com.eventverse.app.domain.builder.ModuleBriefService
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.domain.discovery.DiscoveryDraft
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `POST /api/builder/chat/briefs` dengan `ModuleBriefService` PALSU (use case nyata = Track A) + bentuk JSON pesan BRIEF. */
class BuilderModuleBriefRoutesTest {

    private val slug = "wemade-demo"
    private val tenantId = "ten-wemade-demo"
    private val chats = InMemoryBuilderChatRepository()

    /** Merekam pemanggilan; [onGenerate] menentukan hasil. */
    private class FakeService(var onGenerate: (String?) -> Result<List<ModuleBriefOutcome>>) : ModuleBriefService {
        val calls = mutableListOf<Pair<TenantId, String?>>()
        override suspend fun generate(tenantId: TenantId, moduleId: String?): Result<List<ModuleBriefOutcome>> {
            calls += tenantId to moduleId
            return onGenerate(moduleId)
        }
    }

    /** Agent yang menggantung sampai dilepas — menahan run chat tetap aktif untuk uji 409. */
    private class BlockingAgent(val release: CompletableDeferred<Unit>) : BuilderAgent {
        override val agentRef = "stub/blocking"
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String): Result<BuilderAgentReply> {
            release.await()
            return Result.success(BuilderAgentReply(text = "selesai"))
        }
    }

    private fun install(builder: ApplicationTestBuilder, service: ModuleBriefService?, agent: BuilderAgent? = null) = with(builder) {
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
                builderModuleBriefs = service,
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

    private fun io.ktor.client.request.HttpRequestBuilder.auth(role: Role? = null, withToken: Boolean = true) {
        header("Host", "$slug.wemakeerp.com")
        if (withToken) {
            val token = if (role == null) TestAuth.tenantToken(slug, tenantId = tenantId) else TestAuth.tenantToken(slug, role = role, tenantId = tenantId)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun ApplicationTestBuilder.briefs(body: String? = """{}""", role: Role? = null, withToken: Boolean = true): HttpResponse =
        client.post("/api/builder/chat/briefs") {
            auth(role, withToken)
            contentType(ContentType.Application.Json)
            if (body != null) setBody(body)
        }

    private fun obj(text: String) = JsonParser.parse(text) as JsonValue.Obj

    @Test
    fun `sukses memetakan hasil service ke results, modul diteruskan, tenant dari konteks`() = testApplication {
        val service = FakeService {
            Result.success(listOf(
                ModuleBriefOutcome("poli", BriefStatus.CREATED, BriefSource.AI),
                ModuleBriefOutcome("kasir", BriefStatus.FRESH),
                ModuleBriefOutcome("gudang", BriefStatus.SKIPPED)
            ))
        }
        install(this, service)
        val r = briefs("""{"module":"poli"}""")
        assertEquals(200, r.status.value)
        val results = obj(r.bodyAsText()).array("results").filterIsInstance<JsonValue.Obj>()
        assertEquals(listOf("created", "fresh", "skipped"), results.map { it.string("status") })
        assertEquals("AI", results[0].string("source"))
        assertNull(results[1].string("source"))
        assertEquals(listOf<Pair<TenantId, String?>>(TenantId(tenantId) to "poli"), service.calls)
    }

    @Test
    fun `body kosong berarti semua modul, body rusak 400, modul tak dikenal dari service 400`() = testApplication {
        val service = FakeService { m ->
            if (m == "hantu") Result.failure(IllegalArgumentException("Modul 'hantu' tidak dikenal")) else Result.success(emptyList())
        }
        install(this, service)
        assertEquals(200, briefs(body = null).status.value)
        assertNull(service.calls.last().second, "tanpa module = semua modul")
        assertEquals(400, briefs("[1,2]").status.value)
        assertEquals(400, briefs("bukan json").status.value)
        assertEquals(400, briefs("""{"module":"hantu"}""").status.value)
    }

    @Test
    fun `gerbang fail-closed - tanpa token 401, peran tak berwenang 403 dan service tak dipanggil`() = testApplication {
        val service = FakeService { Result.success(emptyList()) }
        install(this, service)
        assertEquals(401, briefs(withToken = false).status.value)
        assertEquals(403, briefs(role = Role.SALES).status.value)
        assertTrue(service.calls.isEmpty(), "peran tak berwenang tidak boleh sampai ke service")
    }

    @Test
    fun `flag mati (service null) membalas 404 bukan 500, gerbang tetap berlaku lebih dulu`() = testApplication {
        install(this, null)
        assertEquals(404, briefs().status.value)
        assertEquals(403, briefs(role = Role.SALES).status.value)
    }

    @Test
    fun `kegagalan service selain argumen tak sah menjadi 500 jujur`() = testApplication {
        install(this, FakeService { Result.failure(IllegalStateException("basis rusak")) })
        assertEquals(500, briefs().status.value)
    }

    @Test
    fun `run chat aktif untuk tenant memberi 409 dan service tak dipanggil`() = testApplication {
        val release = CompletableDeferred<Unit>()
        val service = FakeService { Result.success(emptyList()) }
        install(this, service, BlockingAgent(release))
        val started = client.post("/api/builder/chat/runs") {
            auth(); contentType(ContentType.Application.Json); setBody("""{"text":"Kami klinik gigi dengan antrean per poli dan kasir."}""")
        }
        assertEquals(202, started.status.value)
        assertEquals(409, briefs().status.value)
        assertTrue(service.calls.isEmpty())
        release.complete(Unit)
    }

    @Test
    fun `GET chat mengeluarkan BRIEF lengkap, pesan non-BRIEF membawa features-stages kosong dan briefSource null`() = testApplication {
        install(this, null)
        val conv = runBlocking { chats.conversationFor(TenantId(tenantId)) }
        runBlocking {
            chats.append(ChatMessage(ChatMessageId("t"), conv.id, TenantId(tenantId), ChatRole.USER, "halo"))
            chats.append(ChatMessage(
                ChatMessageId("b"), conv.id, TenantId(tenantId), ChatRole.AGENT, "Modul poli mengatur antrean.",
                moduleId = "poli", kind = ChatMessageKind.BRIEF,
                features = listOf(BriefFeature("Lampirkan foto")),
                stages = listOf(StageSketch("Intake"), StageSketch("Revisi", optional = true, reworkTo = "Intake")),
                inputFingerprint = "b1:abc", briefSource = BriefSource.DETERMINISTIC
            ))
        }
        val all = obj(client.get("/api/builder/chat") { auth() }.bodyAsText()).array("messages").filterIsInstance<JsonValue.Obj>()
        val plain = all.first { it.string("id")?.startsWith("t") == true }
        assertEquals("[]", plain["features"]?.encode())
        assertEquals("[]", plain["stages"]?.encode())
        assertEquals(JsonValue.Null, plain["briefSource"])

        val inModule = obj(client.get("/api/builder/chat?module=poli") { auth() }.bodyAsText()).array("messages").filterIsInstance<JsonValue.Obj>()
        val brief = inModule.first { it.string("kind") == "BRIEF" }
        // Fitur kini butir v2 (objek bertipe); tanpa bukti kunci evidence tidak ditulis.
        assertEquals("""[{"text":"Lampirkan foto"}]""", brief["features"]?.encode())
        assertEquals("DETERMINISTIC", brief.string("briefSource"))
        assertEquals("b1:abc", brief.string("inputFingerprint"))
        val stages = (brief["stages"] as JsonValue.Arr).items.filterIsInstance<JsonValue.Obj>()
        assertEquals(listOf("Intake", "Revisi"), stages.map { it.string("name") })
        assertEquals("Intake", stages[1].string("reworkTo"))
    }

    @Test
    fun `BRIEF tenant lain tidak terlihat`() = testApplication {
        install(this, null)
        val other = runBlocking { chats.conversationFor(TenantId("ten-lain")) }
        runBlocking {
            chats.append(ChatMessage(ChatMessageId("x"), other.id, TenantId("ten-lain"), ChatRole.AGENT, "rahasia lain",
                moduleId = "poli", kind = ChatMessageKind.BRIEF, inputFingerprint = "b1:x", briefSource = BriefSource.AI))
        }
        val body = client.get("/api/builder/chat?module=poli") { auth() }.bodyAsText()
        assertTrue(!body.contains("rahasia lain"))
    }
}
