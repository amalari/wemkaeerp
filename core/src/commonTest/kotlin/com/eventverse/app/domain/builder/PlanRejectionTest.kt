package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rencana wawancara yang ditolak harus terlihat alasannya (cek visual model sungguhan 2026-10-16): dulu galat, rencana tak sah,
 * dan putaran kedua semuanya diam-diam menjadi "tanpa rencana". Perilaku chat TIDAK berubah — hanya alasannya yang kini dilaporkan.
 */
class PlanRejectionTest {
    private val tenant = TenantId("bordir-uji")
    private val baseDraft = InterviewFixtures.draftOf(InterviewFixtures.klinikPack, null)

    private class Drafts(draft: DiscoveryDraft) : DiscoveryDraftRepository {
        private var row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("usr-uji"), draft, tenantId = TenantId("bordir-uji"))
        override suspend fun findById(id: DiscoveryDraftId) = row
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId) = row
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    private class CountingAgent : BuilderAgent {
        override val agentRef = "uji"
        var calls = 0
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String): Result<BuilderAgentReply> {
            calls++; return Result.success(BuilderAgentReply("draf"))
        }
    }

    private class Rejections { val seen = mutableListOf<Pair<TenantId, PlanRejection>>() }

    private fun useCase(planner: InterviewPlanner, agent: BuilderAgent, rej: Rejections, chats: InMemoryBuilderChatRepository = InMemoryBuilderChatRepository()) =
        SendBuilderMessageUseCase(chats, agent, Drafts(baseDraft), interviewPlanner = planner, onPlanRejected = { t, r -> rej.seen += t to r })

    @Test
    fun `perencana melempar galat should melaporkan PLANNER_ERROR dan chat tetap menyusun draf`() = runTest {
        val rej = Rejections(); val agent = CountingAgent()
        useCase({ _, _, _ -> error("LLM timeout") }, agent, rej)(tenant, "Kami klinik").getOrThrow()
        val (t, r) = rej.seen.single()
        assertEquals(tenant, t)
        assertEquals(PlanRejectionKind.PLANNER_ERROR, r.kind)
        assertTrue("IllegalStateException" in r.detail && "LLM timeout" in r.detail, r.detail)
        assertEquals(1, agent.calls, "perilaku chat tidak berubah: draf tetap disusun")
    }

    @Test
    fun `rencana tak lolos validator should melaporkan INVALID_PLAN berisi temuan berpath`() = runTest {
        val rej = Rejections(); val agent = CountingAgent()
        val invalid = InterviewFixtures.klinikSession.let { s ->
            s.copy(links = s.links + RoleModuleLink(RoleKey("resepsionis"), ModuleId("modul_tak_ada"), ModuleOrigin.NEW))
        }
        useCase({ _, _, narrative -> invalid.copy(narrative = narrative) }, agent, rej)(tenant, "Kami klinik").getOrThrow()
        val r = rej.seen.single().second
        assertEquals(PlanRejectionKind.INVALID_PLAN, r.kind)
        assertTrue(r.detail.contains("$.interview"), "temuan harus berpath: ${r.detail}")
        assertEquals(1, agent.calls)
    }

    @Test
    fun `rencana sah should tidak melaporkan penolakan`() = runTest {
        val rej = Rejections()
        useCase({ _, _, narrative -> InterviewFixtures.klinikSession.copy(narrative = narrative) }, CountingAgent(), rej)(tenant, "Kami klinik").getOrThrow()
        assertTrue(rej.seen.isEmpty())
    }

    @Test
    fun `putaran kedua sesudah jawaban should melaporkan UNEXPECTED_SECOND_ROUND`() = runTest {
        val rej = Rejections(); val chats = InMemoryBuilderChatRepository()
        var round = 0
        val planner = InterviewPlanner { _, session, narrative ->
            round++
            // Putaran 1: bertanya. Putaran 2 (sesudah dijawab): bertanya LAGI — tidak diizinkan.
            session.copy(narrative = narrative, clarifications = session.clarifications + Clarification("c$round", "Pertanyaan $round?"))
        }
        val uc = useCase(planner, CountingAgent(), rej, chats)
        uc(tenant, "Kami klinik").getOrThrow()
        assertTrue(rej.seen.isEmpty(), "putaran pertama sah: boleh bertanya")
        uc(tenant, "Satu antrean").getOrThrow()
        assertEquals(PlanRejectionKind.UNEXPECTED_SECOND_ROUND, rej.seen.single().second.kind)
    }

    @Test
    fun `detail penolakan tidak memuat isi narasi pengguna`() = runTest {
        val rej = Rejections()
        val secret = "RAHASIA-NARASI-12345"
        useCase({ _, _, _ -> error("gagal") }, CountingAgent(), rej)(tenant, "Kami klinik. $secret").getOrThrow()
        assertTrue(rej.seen.none { secret in it.second.detail })
    }
}
