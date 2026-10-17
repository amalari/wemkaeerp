package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pertanyaan AI menggantikan pertanyaan template (cek visual model sungguhan 2026-10-16): pertanyaan template yang di-seed klien
 * saat halaman dimuat dulu menahan pertanyaan AI yang lebih kontekstual agar tidak pernah muncul.
 */
class FollowUpSupersedeTest {
    private val tenant = TenantId("bordir-uji")
    private val codes = listOf("klinik_pendaftaran", "klinik_poli", "klinik_kasir")
    private val draft: DiscoveryDraft = draftOf(InterviewFixtures.klinikPack, InterviewFixtures.klinikSession).let {
        it.copy(blueprint = it.blueprint.copy(modules = codes.map { c -> BlueprintModule(c, true) }))
    }

    private class Drafts(d: DiscoveryDraft, t: TenantId) : DiscoveryDraftRepository {
        private val row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("u-1"), d, tenantId = t)
        override suspend fun findById(id: DiscoveryDraftId) = row
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId) = row
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored
    }

    private fun q(id: String, module: String, answer: String? = null) = ChatMessage(
        ChatMessageId("m-$id-$module"), BuilderConversationId("c"), tenant, ChatRole.AGENT, "tanya", moduleId = module,
        kind = ChatMessageKind.QUESTION, questions = listOf(Clarification(id, "Pertanyaan $id?", answer))
    )

    private fun ids(list: List<PendingFollowUp>) = list.map { it.question.id }

    // ── pendingFollowUps ────────────────────────────────────────────────────────

    @Test
    fun `pertanyaan AI menunggu should menyembunyikan template menunggu di modul yang sama`() {
        val thread = listOf(q("gap:who_fills:klinik_poli", "klinik_poli"), q("ai:klinik_poli:abc", "klinik_poli"))
        assertEquals(listOf("ai:klinik_poli:abc"), ids(thread.pendingFollowUps("klinik_poli")))
        assertEquals(listOf("ai:klinik_poli:abc"), ids(thread.pendingFollowUps(null)))
    }

    @Test
    fun `template tersembunyi should tidak muncul lagi setelah pertanyaan AI dijawab`() {
        val thread = listOf(q("gap:who_fills:klinik_poli", "klinik_poli"), q("ai:klinik_poli:abc", "klinik_poli", answer = "perawat"))
        assertTrue(thread.pendingFollowUps("klinik_poli").isEmpty(), "AI sudah dijawab: template yang digantikan tidak bangkit lagi")
    }

    @Test
    fun `modul tanpa pertanyaan AI should tetap menampilkan template`() {
        val thread = listOf(q("gap:who_fills:klinik_poli", "klinik_poli"))
        assertEquals(listOf("gap:who_fills:klinik_poli"), ids(thread.pendingFollowUps("klinik_poli")))
    }

    @Test
    fun `pertanyaan AI satu modul should tidak menyembunyikan template modul lain`() {
        val thread = listOf(q("ai:klinik_poli:abc", "klinik_poli"), q("gap:who_fills:klinik_kasir", "klinik_kasir"))
        assertEquals(listOf("gap:who_fills:klinik_kasir"), ids(thread.pendingFollowUps("klinik_kasir")))
        assertEquals(setOf("ai:klinik_poli:abc", "gap:who_fills:klinik_kasir"), ids(thread.pendingFollowUps(null)).toSet())
    }

    // ── use case brief ──────────────────────────────────────────────────────────

    private class AiBriefer : ModuleBriefer {
        override suspend fun brief(request: ModuleBriefRequest) = Result.success(
            ModuleBrief("Ringkasan ${request.moduleName}.", listOf(BriefFeature("Antrean")), emptyList(), listOf("Siapa pemeriksa di ${request.moduleName}?"), BriefSource.AI)
        )
    }

    @Test
    fun `pertanyaan template yang di-seed klien should tidak lagi menahan pertanyaan AI`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val drafts = Drafts(draft, tenant)
        val conv = chats.conversationFor(tenant)
        chats.append(ChatMessage(ChatMessageId("u1"), conv.id, tenant, ChatRole.USER, "Klinik kecil dengan antrean dan kasir."))
        // Seed klien saat halaman dimuat (sebelum brief): pertanyaan template menunggu jawaban.
        assertTrue(AskModuleFollowUpsUseCase(chats, drafts)(tenant, "klinik_poli").getOrThrow())
        assertTrue(chats.messages(conv.id).any { m -> m.questions.any { it.id.startsWith("gap:") } }, "prasyarat: template menunggu")

        GenerateModuleBriefsUseCase(chats, drafts, AiBriefer(), ModuleBriefRules, "agent/v1").generate(tenant, "klinik_poli").getOrThrow()

        val all = chats.messages(conv.id)
        assertTrue(all.any { m -> m.questions.any { BriefKeys.isAiQuestionId(it.id) } }, "pertanyaan AI harus tetap dibuat")
        val pending = all.pendingFollowUps("klinik_poli")
        assertTrue(pending.isNotEmpty() && pending.all { BriefKeys.isAiQuestionId(it.question.id) }, "yang menunggu hanya pertanyaan AI: ${ids(pending)}")
    }

    @Test
    fun `AskModuleFollowUps pada modul yang sudah punya brief should tidak membuat pertanyaan template`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val drafts = Drafts(draft, tenant)
        val conv = chats.conversationFor(tenant)
        chats.append(ChatMessage(ChatMessageId("u1"), conv.id, tenant, ChatRole.USER, "Klinik kecil."))
        GenerateModuleBriefsUseCase(chats, drafts, AiBriefer(), ModuleBriefRules, "agent/v1").generate(tenant, "klinik_poli").getOrThrow()
        val before = chats.messages(conv.id).size
        assertFalse(AskModuleFollowUpsUseCase(chats, drafts)(tenant, "klinik_poli").getOrThrow())
        assertEquals(before, chats.messages(conv.id).size, "tidak ada pesan baru")
    }

    // ── generasi AI terbaru menggantikan yang lama (temuan 12) ───────────────────

    private fun qm(msgId: String, module: String, vararg qs: Pair<String, String?>) = ChatMessage(
        ChatMessageId(msgId), BuilderConversationId("c"), tenant, ChatRole.AGENT, "tanya", moduleId = module,
        kind = ChatMessageKind.QUESTION, questions = qs.map { (id, ans) -> Clarification(id, "Pertanyaan $id?", ans) }
    )

    @Test
    fun `pesan pertanyaan AI lama yang belum dijawab should disembunyikan saat ada generasi baru di modul yang sama`() {
        val thread = listOf(
            qm("old", "klinik_poli", "ai:klinik_poli:a" to null, "ai:klinik_poli:b" to null),
            qm("new", "klinik_poli", "ai:klinik_poli:c" to null)
        )
        assertEquals(listOf("ai:klinik_poli:c"), ids(thread.pendingFollowUps("klinik_poli")))
        assertEquals(listOf("ai:klinik_poli:c"), ids(thread.pendingFollowUps(null)), "utas Semua: tidak menumpuk")
    }

    @Test
    fun `generasi AI satu modul should tidak menyembunyikan pertanyaan AI modul lain`() {
        val thread = listOf(qm("p1", "klinik_poli", "ai:klinik_poli:a" to null), qm("k1", "klinik_kasir", "ai:klinik_kasir:a" to null), qm("p2", "klinik_poli", "ai:klinik_poli:b" to null))
        assertEquals(setOf("ai:klinik_poli:b", "ai:klinik_kasir:a"), ids(thread.pendingFollowUps(null)).toSet())
    }

    @Test
    fun `pertanyaan AI lama yang disembunyikan should tidak bangkit setelah generasi baru dijawab`() {
        val thread = listOf(qm("old", "klinik_poli", "ai:klinik_poli:a" to null), qm("new", "klinik_poli", "ai:klinik_poli:c" to "perawat"))
        assertTrue(thread.pendingFollowUps("klinik_poli").isEmpty())
    }

    /** Briefer yang mengembalikan follow-up berbeda tiap panggilan dan merekam apa yang dikirim sebagai "sudah ditanyakan". */
    private class SequenceBriefer(private val rounds: List<List<String>>) : ModuleBriefer {
        val alreadyAsked = mutableListOf<List<Clarification>>()
        override suspend fun brief(request: ModuleBriefRequest): Result<ModuleBrief> {
            alreadyAsked += request.alreadyAsked
            val round = rounds[minOf(alreadyAsked.size - 1, rounds.lastIndex)]
            return Result.success(ModuleBrief("Ringkasan ${request.moduleName}.", listOf(BriefFeature("Antrean")), emptyList(), round, BriefSource.AI))
        }
    }

    private suspend fun story(chats: InMemoryBuilderChatRepository, text: String) {
        chats.append(ChatMessage(ChatMessageId("u-${text.hashCode()}"), chats.conversationFor(tenant).id, tenant, ChatRole.USER, text))
    }

    private fun useCase(chats: InMemoryBuilderChatRepository, briefer: ModuleBriefer) =
        GenerateModuleBriefsUseCase(chats, Drafts(draft, tenant), briefer, ModuleBriefRules, "agent/v1")

    @Test
    fun `cerita berubah dua kali should menampilkan hanya pertanyaan AI generasi terbaru bukan menumpuk`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val briefer = SequenceBriefer(listOf(listOf("Siapa pemeriksa?", "Berapa tarif?"), listOf("Apakah ada rujukan?", "Jam buka?")))
        val uc = useCase(chats, briefer)
        story(chats, "Klinik kecil."); uc.generate(tenant, "klinik_poli").getOrThrow()
        story(chats, "Klinik kecil, sekarang ada rujukan."); uc.generate(tenant, "klinik_poli").getOrThrow()

        val pending = chats.messages(chats.conversationFor(tenant).id).pendingFollowUps("klinik_poli")
        assertEquals(setOf("Apakah ada rujukan?", "Jam buka?"), pending.map { it.question.question }.toSet(), "hanya generasi terbaru yang menunggu: ${pending.map { it.question.question }}")
    }

    @Test
    fun `pertanyaan AI lama yang belum dijawab should tidak dikirim ke model sebagai sudah ditanyakan tetapi yang dijawab tetap`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val briefer = SequenceBriefer(listOf(listOf("Siapa pemeriksa?", "Berapa tarif?"), listOf("Jam buka?")))
        val uc = useCase(chats, briefer)
        story(chats, "Klinik kecil."); uc.generate(tenant, "klinik_poli").getOrThrow()
        val first = chats.messages(chats.conversationFor(tenant).id).single { it.kind == ChatMessageKind.QUESTION }
        chats.markAnswered(first.id, mapOf(first.questions[0].id to "dokter gigi"))   // satu dijawab, satu belum
        story(chats, "Klinik kecil, revisi."); uc.generate(tenant, "klinik_poli").getOrThrow()

        val texts = briefer.alreadyAsked.last().map { it.question }   // permintaan KEDUA ke model
        assertTrue("Siapa pemeriksa?" in texts, "yang dijawab tetap dikirim agar tidak diulang: $texts")
        assertTrue("Berapa tarif?" !in texts, "yang belum dijawab digantikan, jadi boleh ditanyakan ulang: $texts")
    }

    @Test
    fun `pembuatan ulang dengan pertanyaan AI yang sama should tidak menggandakan pesan pertanyaan`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val same = listOf("Siapa pemeriksa?", "Berapa tarif?")
        val uc = useCase(chats, SequenceBriefer(listOf(same, same)))
        story(chats, "Klinik kecil."); uc.generate(tenant, "klinik_poli").getOrThrow()
        story(chats, "Klinik kecil, revisi."); uc.generate(tenant, "klinik_poli").getOrThrow()
        val all = chats.messages(chats.conversationFor(tenant).id)
        assertEquals(1, all.count { it.kind == ChatMessageKind.QUESTION }, "himpunan sama sudah menunggu: tidak digandakan")
        assertEquals(2, all.count { it.kind == ChatMessageKind.BRIEF }, "brief baru tetap dibuat (basis berubah)")
    }

    // ── paritas id ──────────────────────────────────────────────────────────────

    @Test
    fun `semua kode celah template should berawalan gap dan id AI berawalan ai`() {
        val gaps = ModuleGapAnalyzer.analyze(draftOf(InterviewFixtures.klinikPack, null).let { d ->
            d.copy(blueprint = d.blueprint.copy(modules = codes.map { c -> BlueprintModule(c, true) }))
        }, "klinik_poli", limit = 10)
        assertTrue(gaps.isNotEmpty() && gaps.all { ModuleGapAnalyzer.isGapQuestionId(it.code) })
        assertTrue(BriefKeys.isAiQuestionId(BriefKeys.followUpId("klinik_poli", "Apa saja?")))
        assertFalse(ModuleGapAnalyzer.isGapQuestionId(BriefKeys.followUpId("klinik_poli", "Apa saja?")))
    }
}
