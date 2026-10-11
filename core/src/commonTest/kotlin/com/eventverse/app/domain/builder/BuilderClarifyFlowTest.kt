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
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builder memakai perencana wawancara Discovery: satu putaran tanya, jawaban persisten, hasil tervalidasi. */
class BuilderClarifyFlowTest {

    private val tenant = TenantId("ten-uji")
    private val baseDraft = InterviewFixtures.draftOf(InterviewFixtures.klinikPack, null)

    private suspend fun InMemoryBuilderChatRepository.msgs() = messages(conversationFor(tenant).id)

    private class Drafts(draft: DiscoveryDraft) : DiscoveryDraftRepository {
        private var row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("usr-uji"), draft, tenantId = TenantId("ten-uji"))
        override suspend fun findById(id: DiscoveryDraftId) = row.takeIf { it.id == id }
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row).filter { it.ownerUserId == ownerUserId }
        override suspend fun findByTenant(tenantId: TenantId) = row.takeIf { it.tenantId == tenantId }
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    private class RecordingAgent : BuilderAgent {
        override val agentRef = "uji"
        val narratives = mutableListOf<String>()
        val currents = mutableListOf<DiscoveryDraft?>()
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String): Result<BuilderAgentReply> {
            currents += currentDraft
            narratives += userMessage
            return Result.success(BuilderAgentReply("draf ke-${narratives.size}"))
        }
    }

    private val ask = listOf(Clarification("c1", "Pasien dilayani per poli atau satu antrean?"))

    private inner class RecordingPlanner(private val fail: Boolean = false, private val noQuestions: Boolean = false) : InterviewPlanner {
        var calls = 0
        override suspend fun plan(draft: DiscoveryDraft, session: InterviewSession, narrative: String): InterviewSession {
            calls++
            if (fail) error("LLM mati")
            if (noQuestions) return InterviewFixtures.klinikSession.copy(narrative = narrative)
            if (session.clarifications.isEmpty()) return session.copy(clarifications = ask)
            return InterviewFixtures.klinikSession.copy(narrative = narrative, clarifications = session.clarifications)
        }
    }

    @Test
    fun `cerita kabur, perencana existing bertanya sekali dan giliran berhenti sebelum draf`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent(); val planner = RecordingPlanner(); val phases = mutableListOf<String>()
        val uc = SendBuilderMessageUseCase(chats, agent, Drafts(baseDraft), interviewPlanner = planner)
        uc(tenant, "Kami klinik", onProgress = { phases += it }).getOrThrow()
        assertEquals(listOf("planning"), phases)
        assertTrue(agent.narratives.isEmpty(), "draf menunggu jawaban")
        assertEquals(ChatMessageKind.QUESTION, chats.msgs().last().kind)
        assertEquals(ask, chats.msgs().last().questions)
    }

    @Test
    fun `jawaban disimpan lalu perencana dipanggil ulang tanpa putaran pertanyaan kedua`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent(); val planner = RecordingPlanner()
        val uc = SendBuilderMessageUseCase(chats, agent, Drafts(baseDraft), interviewPlanner = planner)
        uc(tenant, "Kami klinik gigi").getOrThrow()
        uc(tenant, "Satu antrean lalu dibagi ke poli").getOrThrow()
        assertEquals(2, planner.calls)
        assertEquals("Satu antrean lalu dibagi ke poli", chats.msgs().first { it.kind == ChatMessageKind.QUESTION }.questions.single().answer)
        assertTrue(agent.currents.single()?.interview?.roles?.isNotEmpty() == true, "rencana tervalidasi diteruskan sebagai konteks draf")
        assertTrue(agent.narratives.single().contains("Pertanyaan: ${ask.single().question}\nJawaban: Satu antrean lalu dibagi ke poli"))
    }

    @Test
    fun `perencana kosong atau gagal tetap menyusun draf dan revisi membaca cerita awal`() = runTest {
        val emptyAgent = RecordingAgent()
        SendBuilderMessageUseCase(InMemoryBuilderChatRepository(), emptyAgent, Drafts(baseDraft), interviewPlanner = RecordingPlanner(noQuestions = true))
            .invoke(tenant, "Kami klinik").getOrThrow()
        assertEquals(1, emptyAgent.narratives.size)

        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent()
        val uc = SendBuilderMessageUseCase(chats, agent, Drafts(baseDraft), interviewPlanner = RecordingPlanner(fail = true))
        uc(tenant, "Kami klinik").getOrThrow()
        uc(tenant, "Tambah modul pengiriman").getOrThrow()
        assertEquals(2, agent.narratives.size)
        assertTrue(agent.narratives[1].contains("Kami klinik") && agent.narratives[1].contains("Tambah modul pengiriman"))
    }

    @Test
    fun `utas modul tidak menjalankan perencana dan tidak menjawab pertanyaan utas Semua`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent(); val planner = RecordingPlanner()
        val uc = SendBuilderMessageUseCase(chats, agent, Drafts(baseDraft), interviewPlanner = planner)
        uc(tenant, "Kami klinik").getOrThrow()
        uc(tenant, "Tambah input tanggal", moduleId = "klinik_poli").getOrThrow()
        assertEquals(1, planner.calls)
        assertTrue(chats.msgs().first { it.kind == ChatMessageKind.QUESTION }.questions.single().answer == null)
    }

    @Test
    fun `narasi gabungan menjaga awal cerita saat melebihi batas`() {
        val long = "a".repeat(200)
        val msgs = listOf("m1", "m2").mapIndexed { i, id ->
            ChatMessage(ChatMessageId(id), BuilderConversationId("c"), tenant, ChatRole.USER, if (i == 0) "AWAL$long" else "AKHIR$long")
        }
        val out = composeNarrative(msgs, limit = 150)
        assertTrue(out.length <= 160)
        assertTrue(out.startsWith("AWAL") && out.endsWith("a"))
    }
}
