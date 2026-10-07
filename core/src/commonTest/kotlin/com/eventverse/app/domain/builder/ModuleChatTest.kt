package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Follow-up per modul dan sunting isian lewat chat (Fase C), di pack non-garment (`klinik`). */
class ModuleChatTest {

    private val tenant = TenantId("ten-uji")
    private fun f(key: String, type: FieldType = FieldType.TEXT, required: Boolean = false) = FieldProposal(key, key.replaceFirstChar { it.uppercase() }, type, required)

    private val screen = run {
        val fields = listOf(f("nama"), f("keluhan"))
        val p = ScreenProposal("s_poli", ModuleId("klinik_poli"), "Antrean Poli", WidgetKind.TABLE, "karena uji",
            EntityProposal("pasien", "Pasien", fields), ViewProposal.Table(fields.map { it.key }))
        PrototypeScreen("s_poli", ModuleId("klinik_poli"), "Antrean Poli", "TABLE", p, ProposalSource.Deterministic)
    }
    private val draft: DiscoveryDraft = draftOf(InterviewFixtures.klinikPack, null).copy(screens = listOf(screen))

    private class Drafts(var stored: StoredDiscoveryDraft?) : DiscoveryDraftRepository {
        override suspend fun findById(id: DiscoveryDraftId) = stored
        override suspend fun findByOwner(ownerUserId: UserId) = listOfNotNull(stored)
        override suspend fun findByTenant(tenantId: TenantId) = stored
        override suspend fun findAll() = listOfNotNull(stored)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { this.stored = it }
    }
    private fun drafts(d: DiscoveryDraft? = draft) = Drafts(d?.let { StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("u-1"), it, tenantId = tenant) })

    private class RecordingAgent : BuilderAgent {
        override val agentRef = "uji"
        var calls = 0
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String): Result<BuilderAgentReply> {
            calls++; return Result.success(BuilderAgentReply("draf umum"))
        }
    }

    private suspend fun InMemoryBuilderChatRepository.msgs() = messages(conversationFor(tenant).id)

    // ---- follow-up saat tab modul dibuka ----------------------------------------------------------

    @Test
    fun `tab modul dibuka, celah ditanyakan sekali di utas modul dan tidak digandakan`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val ask = AskModuleFollowUpsUseCase(chats, drafts())
        assertTrue(ask(tenant, "klinik_poli").getOrThrow())
        val q = chats.msgs().single()
        assertEquals(ChatMessageKind.QUESTION, q.kind)
        assertEquals("klinik_poli", q.moduleId)
        assertTrue(q.questions.all { it.id.startsWith("gap:") })
        assertFalse(ask(tenant, "klinik_poli").getOrThrow(), "masih menunggu jawaban → tidak menggandakan")
        assertEquals(1, chats.msgs().size)
        assertEquals(1, chats.msgs().pendingFollowUps(null).map { it.messageId }.toSet().size, "utas Semua melihat follow-up modul")
    }

    @Test
    fun `tanpa draf atau modul tanpa celah tidak membuat pesan, dan celah yang sudah dijawab tidak ditanyakan lagi`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        assertFalse(AskModuleFollowUpsUseCase(chats, drafts(null))(tenant, "klinik_poli").getOrThrow())
        assertFalse(AskModuleFollowUpsUseCase(chats, drafts())(tenant, "org_chart").getOrThrow(), "modul governance")
        val ask = AskModuleFollowUpsUseCase(chats, drafts())
        ask(tenant, "klinik_poli").getOrThrow()
        val q = chats.msgs().single()
        chats.markAnswered(q.id, q.questions.associate { it.id to "sudah" })
        assertFalse(ask(tenant, "klinik_poli").getOrThrow(), "semua celah sudah pernah ditanyakan")
    }

    // ---- sunting isian lewat chat -------------------------------------------------------------------

    private fun useCase(chats: InMemoryBuilderChatRepository, d: Drafts, editor: ModuleEditor, agent: RecordingAgent = RecordingAgent()) =
        SendBuilderMessageUseCase(chats, agent, d, moduleEditing = EditModuleFromChat(chats, d, editor)) to agent

    @Test
    fun `permintaan di utas modul menjadi patch usulan isian, bukan menyusun ulang seluruh draf`() = runTest {
        val chats = InMemoryBuilderChatRepository(); var seen: ModuleEditRequest? = null
        val (uc, agent) = useCase(chats, drafts(), ModuleEditor { r ->
            seen = r
            Result.success(ModuleEditReply("", listOf(ProposalEdit.AddField(f("tanggal_kirim", FieldType.DATE, true)))))
        })
        val phases = mutableListOf<String>()
        uc(tenant, "tambah input tanggal kirim", moduleId = "klinik_poli") { phases += it }.getOrThrow()
        assertEquals(0, agent.calls, "agent penyusun draf tidak dipanggil")
        assertEquals(listOf("editing"), phases)
        assertEquals("Poli", seen!!.moduleName)
        val reply = chats.msgs().last()
        assertEquals("klinik_poli", reply.moduleId)
        assertTrue(reply.hasPendingPatch)
        assertEquals(listOf("Tambah isian: Tanggal_kirim (date, wajib)"), reply.proposedSummary)
        val patched = DiscoveryDraftCodec.decode(requireNotNull(reply.proposedDraftJson))
        assertEquals(listOf("nama", "keluhan", "tanggal_kirim"), patched.screens.single().proposal!!.entity!!.fields.map { it.key })
        assertTrue(patched.screens.single().source is ProposalSource.Agent)
    }

    @Test
    fun `jawaban atas follow-up diteruskan ke penyunting bersama pertanyaannya`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val d = drafts(); var seen: ModuleEditRequest? = null
        AskModuleFollowUpsUseCase(chats, d)(tenant, "klinik_poli").getOrThrow()
        val firstQuestion = chats.msgs().single().questions.first().question
        val (uc, _) = useCase(chats, d, ModuleEditor { r -> seen = r; Result.success(ModuleEditReply("Tidak ada yang berubah.", emptyList())) })
        uc(tenant, "Perlu nomor rekam medis", moduleId = "klinik_poli").getOrThrow()
        assertEquals(listOf(firstQuestion to "Perlu nomor rekam medis"), seen!!.answered.take(1))
        assertTrue(chats.msgs().first { it.kind == ChatMessageKind.QUESTION }.questions.all { it.answer == "Perlu nomor rekam medis" })
        val reply = chats.msgs().last()
        assertEquals("Tidak ada yang berubah.", reply.text)
        assertNull(reply.proposedDraftJson, "tanpa sunting = tanpa patch")
    }

    @Test
    fun `sunting tak sah dicoba ulang dengan galat berpath, tetap gagal dijawab jujur tanpa patch`() = runTest {
        val chats = InMemoryBuilderChatRepository(); val feedbacks = mutableListOf<String?>()
        val (uc, _) = useCase(chats, drafts(), ModuleEditor { r ->
            feedbacks += r.feedback
            Result.success(ModuleEditReply("", listOf(ProposalEdit.AddField(f("nama")))))   // kunci ganda → ditolak
        })
        uc(tenant, "tambah nama", moduleId = "klinik_poli").getOrThrow()
        assertEquals(2, feedbacks.size)
        assertNull(feedbacks[0]); assertTrue(feedbacks[1]!!.contains("sudah ada"), "percobaan kedua membawa galat")
        val reply = chats.msgs().last()
        assertNull(reply.proposedDraftJson)
        assertTrue(reply.text.startsWith("Belum bisa menerapkan"))

        val ok = InMemoryBuilderChatRepository(); var n = 0
        val (uc2, _) = useCase(ok, drafts(), ModuleEditor { _ ->
            n++; Result.success(ModuleEditReply("", listOf(if (n == 1) ProposalEdit.RemoveField("hantu") else ProposalEdit.AddField(f("usia", FieldType.NUMBER)))))
        })
        uc2(tenant, "tambah usia", moduleId = "klinik_poli").getOrThrow()
        assertTrue(ok.msgs().last().hasPendingPatch, "percobaan kedua berhasil setelah umpan balik")
    }

    @Test
    fun `penyunting gagal atau modul tanpa layar, balasan jujur atau jatuh ke agent penyusun draf`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val (uc, agent) = useCase(chats, drafts(), ModuleEditor { Result.failure(IllegalStateException("model mati")) })
        uc(tenant, "tambah usia", moduleId = "klinik_poli").getOrThrow()
        assertTrue(chats.msgs().last().text.contains("model mati"))
        assertEquals(0, agent.calls)

        val noScreen = InMemoryBuilderChatRepository()
        val (uc2, agent2) = useCase(noScreen, drafts(draftOf(InterviewFixtures.klinikPack, null)), ModuleEditor { error("tidak boleh dipanggil") })
        uc2(tenant, "tambah usia", moduleId = "klinik_poli").getOrThrow()
        assertEquals(1, agent2.calls, "modul tanpa layar berproposal memakai agent penyusun draf")
        assertNotNull(noScreen.msgs().last().text)
    }
}
