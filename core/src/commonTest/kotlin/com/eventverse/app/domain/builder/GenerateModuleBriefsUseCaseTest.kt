package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Use case brief per modul di pack non-garment (`klinik`), tenant `ten-uji`. */
class GenerateModuleBriefsUseCaseTest {

    private val tenant = TenantId("ten-uji")
    private val codes = listOf("klinik_pendaftaran", "klinik_poli", "klinik_kasir")

    private val draft: DiscoveryDraft = draftOf(InterviewFixtures.klinikPack, InterviewFixtures.klinikSession).let {
        it.copy(blueprint = it.blueprint.copy(modules = (codes + "org_chart").map { c -> BlueprintModule(c, true) }))
    }

    private class Drafts(var stored: StoredDiscoveryDraft?) : DiscoveryDraftRepository {
        override suspend fun findById(id: DiscoveryDraftId) = stored
        override suspend fun findByOwner(ownerUserId: UserId) = listOfNotNull(stored)
        override suspend fun findByTenant(tenantId: TenantId) = stored
        override suspend fun findAll() = listOfNotNull(stored)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { this.stored = it }
    }

    private fun drafts(d: DiscoveryDraft = draft) = Drafts(StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("u-1"), d, tenantId = tenant))

    /** Briefer skrip: satu jawaban per panggilan (yang terakhir diulang); merekam permintaan. */
    private class ScriptedBriefer(private val script: List<(ModuleBriefRequest) -> Result<ModuleBrief>>) : ModuleBriefer {
        val requests = mutableListOf<ModuleBriefRequest>()
        override suspend fun brief(request: ModuleBriefRequest): Result<ModuleBrief> {
            requests += request
            return script[minOf(requests.size - 1, script.lastIndex)](request)
        }
    }

    private fun good(followUps: List<String> = emptyList(), features: List<String> = listOf("Antrean")) =
        { r: ModuleBriefRequest -> Result.success(ModuleBrief("Ringkasan ${r.moduleName}.", features, listOf(StageSketch("Daftar")), followUps, BriefSource.AI)) }

    private class FailingChats(private val d: BuilderChatRepository, private val failFor: String) : BuilderChatRepository by d {
        override suspend fun append(message: ChatMessage): ChatMessage {
            if (message.moduleId == failFor) error("penyimpanan gagal")
            return d.append(message)
        }
    }

    private suspend fun seed(chats: BuilderChatRepository, text: String = "Klinik kecil dengan antrean pasien dan kasir.") {
        val c = chats.conversationFor(tenant)
        chats.append(ChatMessage(ChatMessageId("u-${text.hashCode()}"), c.id, tenant, ChatRole.USER, text))
    }

    private fun useCase(chats: BuilderChatRepository, briefer: ModuleBriefer, d: Drafts = drafts()) =
        GenerateModuleBriefsUseCase(chats, d, briefer, ModuleBriefRules, "agent/v1")

    private suspend fun BuilderChatRepository.all() = messages(conversationFor(tenant).id)

    @Test
    fun `generate model sukses returns CREATED AI dengan pesan BRIEF per modul operasional`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val out = useCase(chats, ScriptedBriefer(listOf(good()))).generate(tenant, null).getOrThrow()
        assertEquals(codes.toSet(), out.filter { it.status == BriefStatus.CREATED }.map { it.moduleId }.toSet())
        assertEquals(BriefStatus.SKIPPED, out.first { it.moduleId == "org_chart" }.status, "governance dilewati")
        val brief = chats.all().first { it.kind == ChatMessageKind.BRIEF && it.moduleId == "klinik_poli" }
        assertEquals(BriefSource.AI, brief.briefSource)
        assertEquals(ChatRole.AGENT, brief.role)
        assertEquals(listOf("Antrean"), brief.features)
        assertNotNull(brief.basisRef)
    }

    @Test
    fun `generate gagal lalu sukses returns brief AI dan percobaan kedua membawa feedback`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val briefer = ScriptedBriefer(listOf({ Result.failure(IllegalStateException("timeout")) }, good()))
        val out = useCase(chats, briefer).generate(tenant, "klinik_poli").getOrThrow()
        assertEquals(BriefSource.AI, out.single().source)
        assertEquals(2, briefer.requests.size)
        assertNull(briefer.requests[0].feedback)
        assertTrue("timeout" in briefer.requests[1].feedback.orEmpty())
    }

    @Test
    fun `generate keluaran tak sah lalu sah returns feedback berpath dari validator`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val bad = { _: ModuleBriefRequest ->
            Result.success(ModuleBrief("x", listOf("a"), listOf(StageSketch("A", reworkTo = "B"), StageSketch("B")), source = BriefSource.AI))
        }
        val briefer = ScriptedBriefer(listOf(bad, good()))
        useCase(chats, briefer).generate(tenant, "klinik_poli").getOrThrow()
        assertTrue("stages[0].reworkTo" in briefer.requests[1].feedback.orEmpty())
    }

    @Test
    fun `generate gagal total returns fallback DETERMINISTIC tanpa stages`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val briefer = ScriptedBriefer(listOf({ Result.failure(IllegalStateException("down")) }))
        val out = useCase(chats, briefer).generate(tenant, "klinik_pendaftaran").getOrThrow()
        assertEquals(BriefStatus.CREATED, out.single().status)
        assertEquals(BriefSource.DETERMINISTIC, out.single().source)
        val brief = chats.all().single { it.kind == ChatMessageKind.BRIEF }
        assertEquals(BriefSource.DETERMINISTIC, brief.briefSource)
        assertTrue(brief.stages.isEmpty())
        assertTrue(brief.features.any { "Nomor antrean" in it })
        assertEquals(2, briefer.requests.size)
    }

    @Test
    fun `generate dua kali returns FRESH dan tidak menggandakan pesan`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val uc = useCase(chats, ScriptedBriefer(listOf(good(listOf("Berapa antrean per hari?")))))
        uc.generate(tenant, null).getOrThrow()
        val before = chats.all().size
        val second = uc.generate(tenant, null).getOrThrow()
        assertEquals(before, chats.all().size)
        assertTrue(second.filter { it.moduleId in codes }.all { it.status == BriefStatus.FRESH })
    }

    @Test
    fun `generate basis berubah returns BRIEF baru dan pertanyaan terjawab tidak ditanyakan ulang`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val uc = useCase(chats, ScriptedBriefer(listOf(good(listOf("Berapa antrean per hari?")))))
        uc.generate(tenant, "klinik_poli").getOrThrow()
        val q = chats.all().single { it.kind == ChatMessageKind.QUESTION }
        chats.markAnswered(q.id, q.questions.associate { it.id to "jawaban" })
        seed(chats, "Tambahan: ada layanan gigi.")
        val out = uc.generate(tenant, "klinik_poli").getOrThrow()
        assertEquals(BriefStatus.CREATED, out.single().status)
        assertEquals(2, chats.all().count { it.kind == ChatMessageKind.BRIEF })
        val texts = chats.all().filter { it.kind == ChatMessageKind.QUESTION }.flatMap { m -> m.questions.map { BriefKeys.normalize(it.question) } }
        assertEquals(texts.size, texts.toSet().size, "tak ada pertanyaan berulang")
    }

    @Test
    fun `generate masih ada pertanyaan menunggu returns BRIEF baru tanpa pesan QUESTION baru`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val uc = useCase(chats, ScriptedBriefer(listOf(good(listOf("Berapa antrean per hari?")))))
        uc.generate(tenant, "klinik_poli").getOrThrow()
        seed(chats, "Revisi cerita.")
        uc.generate(tenant, "klinik_poli").getOrThrow()
        assertEquals(1, chats.all().count { it.kind == ChatMessageKind.QUESTION })
        assertEquals(2, chats.all().count { it.kind == ChatMessageKind.BRIEF })
    }

    @Test
    fun `generate follow-up AI returns hanya pertanyaan AI tanpa dicampur celah template`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        // Batas 3 butir ditegakkan validator brief (FR-4); duplikat setara dibuang use case.
        val ai = listOf("Siapa pemeriksa?", "siapa pemeriksa?", "Berapa tarif?")
        useCase(chats, ScriptedBriefer(listOf(good(ai)))).generate(tenant, "klinik_poli").getOrThrow()
        val qs = chats.all().single { it.kind == ChatMessageKind.QUESTION }.questions
        assertEquals(2, qs.size, "duplikat setara dibuang; sisa kuota TIDAK diisi celah template")
        assertTrue(qs.all { it.id.startsWith("ai:klinik_poli:") }, "AI menggantikan template")
        assertEquals(BriefKeys.followUpId("klinik_poli", "Siapa pemeriksa?"), qs[0].id)
    }

    @Test
    fun `generate tanpa follow-up AI returns celah template sebagai cadangan`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        useCase(chats, ScriptedBriefer(listOf(good(emptyList())))).generate(tenant, "klinik_poli").getOrThrow()
        val qs = chats.all().single { it.kind == ChatMessageKind.QUESTION }.questions
        assertTrue(qs.isNotEmpty() && qs.all { it.id.startsWith("gap:") })
    }

    @Test
    fun `generate modul tanpa tautan dan tanpa layar returns SKIPPED tanpa pesan`() = runTest {
        val noLink = draft.copy(interview = InterviewFixtures.klinikSession.copy(links = InterviewFixtures.klinikSession.links.filter { it.moduleId.value != "klinik_kasir" }))
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        val out = useCase(chats, ScriptedBriefer(listOf(good())), drafts(noLink)).generate(tenant, "klinik_kasir").getOrThrow()
        assertEquals(BriefStatus.SKIPPED, out.single().status)
        assertTrue(chats.all().none { it.moduleId == "klinik_kasir" })
    }

    @Test
    fun `generate satu modul gagal menyimpan returns modul lain tetap CREATED`() = runTest {
        val mem = InMemoryBuilderChatRepository().also { seed(it) }
        val out = useCase(FailingChats(mem, "klinik_poli"), ScriptedBriefer(listOf(good()))).generate(tenant, null).getOrThrow()
        assertEquals(BriefStatus.SKIPPED, out.first { it.moduleId == "klinik_poli" }.status)
        assertEquals(BriefStatus.CREATED, out.first { it.moduleId == "klinik_kasir" }.status)
        assertEquals(BriefStatus.CREATED, out.first { it.moduleId == "klinik_pendaftaran" }.status)
    }

    @Test
    fun `generate tanpa draf returns daftar kosong`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val uc = GenerateModuleBriefsUseCase(chats, Drafts(null), ScriptedBriefer(listOf(good())), ModuleBriefRules, "agent/v1")
        assertTrue(uc.generate(tenant, null).getOrThrow().isEmpty())
    }

    @Test
    fun `AskModuleFollowUps setelah brief returns tidak menggandakan pertanyaan setara bertipe ai dan gap`() = runTest {
        val chats = InMemoryBuilderChatRepository().also { seed(it) }
        // Pertanyaan AI identik dengan teks celah WHO_FILLS → AskModuleFollowUps tidak boleh menanyakannya lagi.
        useCase(chats, ScriptedBriefer(listOf(good(listOf("Siapa yang mengisi data di Poli?"))))).generate(tenant, "klinik_poli").getOrThrow()
        val q = chats.all().single { it.kind == ChatMessageKind.QUESTION }
        chats.markAnswered(q.id, q.questions.associate { it.id to "ok" })
        AskModuleFollowUpsUseCase(chats, drafts())(tenant, "klinik_poli").getOrThrow()
        val texts = chats.all().filter { it.kind == ChatMessageKind.QUESTION }.flatMap { m -> m.questions.map { BriefKeys.normalize(it.question) } }
        assertEquals(texts.size, texts.toSet().size)
    }
}
