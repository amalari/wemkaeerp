package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Giliran chat Builder dengan penanya klarifikasi (Fase B): bertanya sekali, narasi gabungan, gagal = tidak bertanya. */
class BuilderClarifyFlowTest {

    private val tenant = TenantId("ten-uji")

    private suspend fun InMemoryBuilderChatRepository.msgs() = messages(conversationFor(tenant).id)

    private object NoDrafts : DiscoveryDraftRepository {
        override suspend fun findById(id: DiscoveryDraftId): StoredDiscoveryDraft? = null
        override suspend fun findByOwner(ownerUserId: UserId) = emptyList<StoredDiscoveryDraft>()
        override suspend fun findByTenant(tenantId: TenantId): StoredDiscoveryDraft? = null
        override suspend fun findAll() = emptyList<StoredDiscoveryDraft>()
        override suspend fun save(stored: StoredDiscoveryDraft) = stored
    }

    private class RecordingAgent : BuilderAgent {
        override val agentRef = "uji"
        val narratives = mutableListOf<String>()
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String): Result<BuilderAgentReply> {
            narratives += userMessage
            return Result.success(BuilderAgentReply("draf ke-${narratives.size}"))
        }
    }

    private val ask = listOf(Clarification("c1", "Pasien dilayani per poli atau satu antrean?"))

    @Test
    fun `cerita kabur, penanya bertanya sekali, giliran berhenti sebelum draf dan fase planning dilaporkan`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent(); val phases = mutableListOf<String>()
        val uc = SendBuilderMessageUseCase(chats, agent, NoDrafts, clarifier = NarrativeClarifier { _, _ -> ask })
        uc(tenant, "Kami klinik") { phases += it }.getOrThrow()
        assertEquals(listOf("planning"), phases)
        assertTrue(agent.narratives.isEmpty(), "draf menunggu jawaban")
        val q = chats.msgs().last()
        assertEquals(ChatMessageKind.QUESTION, q.kind)
        assertEquals(ask, q.questions)
    }

    @Test
    fun `jawaban masuk ke pertanyaannya, tidak bertanya lagi, dan agent membaca narasi gabungan tanpa duplikasi jawaban`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent(); var asked = 0
        val uc = SendBuilderMessageUseCase(chats, agent, NoDrafts, clarifier = NarrativeClarifier { _, _ -> asked++; ask })
        uc(tenant, "Kami klinik gigi").getOrThrow()
        uc(tenant, "Satu antrean lalu dibagi ke poli").getOrThrow()
        assertEquals(1, asked, "satu putaran tanya")
        assertEquals("Satu antrean lalu dibagi ke poli", chats.msgs().first { it.kind == ChatMessageKind.QUESTION }.questions.single().answer)
        val narrative = agent.narratives.single()
        assertTrue(narrative.startsWith("Kami klinik gigi"))
        assertTrue(narrative.contains("Pertanyaan: ${ask.single().question}\nJawaban: Satu antrean lalu dibagi ke poli"))
        assertEquals(1, Regex("Satu antrean lalu dibagi ke poli").findAll(narrative).count(), "balasan pengguna tidak diulang")
    }

    @Test
    fun `penanya kosong atau gagal, draf langsung disusun, dan revisi dibaca bersama cerita awal`() = runTest {
        val empty = SendBuilderMessageUseCase(InMemoryBuilderChatRepository(), RecordingAgent().also { }, NoDrafts, clarifier = NarrativeClarifier { _, _ -> emptyList() })
        empty(tenant, "Kami konveksi").getOrThrow()

        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent()
        val boom = SendBuilderMessageUseCase(chats, agent, NoDrafts, clarifier = NarrativeClarifier { _, _ -> error("LLM mati") })
        boom(tenant, "Kami konveksi").getOrThrow()
        boom(tenant, "Tambah modul pengiriman").getOrThrow()
        assertEquals(2, agent.narratives.size, "gagal bertanya = tetap menyusun draf")
        assertTrue(agent.narratives[1].contains("Kami konveksi") && agent.narratives[1].contains("Tambah modul pengiriman"),
            "revisi dibaca bersama cerita awal, bukan sebagai cerita baru")
    }

    @Test
    fun `utas modul tidak pernah bertanya dan jawaban hanya mengisi pertanyaan di utasnya sendiri`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val agent = RecordingAgent(); var asked = 0
        val uc = SendBuilderMessageUseCase(chats, agent, NoDrafts, clarifier = NarrativeClarifier { _, _ -> asked++; ask })
        uc(tenant, "Kami klinik").getOrThrow()                                   // utas Semua: bertanya
        uc(tenant, "Tambah input tanggal", moduleId = "klinik_poli").getOrThrow() // utas modul: tidak bertanya, tidak menjawab pertanyaan Semua
        assertEquals(1, asked)
        assertTrue(chats.msgs().first { it.kind == ChatMessageKind.QUESTION }.questions.single().answer == null, "pertanyaan utas Semua tetap menunggu")
        assertEquals(1, agent.narratives.size)
    }

    @Test
    fun `narasi gabungan menjaga awal cerita saat melebihi batas`() {
        val long = "a".repeat(200)
        val msgs = listOf("m1", "m2").mapIndexed { i, id ->
            ChatMessage(ChatMessageId(id), BuilderConversationId("c"), tenant, ChatRole.USER, if (i == 0) "AWAL$long" else "AKHIR$long")
        }
        val out = composeNarrative(msgs, limit = 150)
        assertTrue(out.length <= 150 + 10)
        assertTrue(out.startsWith("AWAL") && out.endsWith("a"))
    }
}
