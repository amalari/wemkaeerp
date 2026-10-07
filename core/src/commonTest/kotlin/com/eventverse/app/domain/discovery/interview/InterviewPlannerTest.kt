package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.usecases.InterviewDraftUseCases
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.RoleHint
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Perencana alur penuh: sekali di awal, divalidasi, gagal = sesi apa adanya, dan pengisi per langkah dilewati. */
class InterviewPlannerTest {

    private val pack = InterviewFixtures.klinikPack.copy(roleHints = listOf(
        RoleHint("resepsionis", "Resepsionis", ModuleId("klinik_pendaftaran"))
    ))
    private val owner = UserId("u-1")
    private val id = DiscoveryDraftId("d1")
    private val story = "Resepsionis mendaftarkan pasien lalu perawat memeriksa."

    private class MemRepo(initial: StoredDiscoveryDraft) : DiscoveryDraftRepository {
        var row = initial
        override suspend fun findById(id: DiscoveryDraftId) = row.takeIf { it.id == id }
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId): StoredDiscoveryDraft? = null
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft { row = stored; return stored }
    }

    private fun repo() = MemRepo(StoredDiscoveryDraft(id, owner, draftOf(pack, null)))

    private val planned = DivisionDraft(DivisionCode("pemeriksaan"), "Pemeriksaan", ItemSource.GUESS, BasisRef(Basis.NARASI, quote = "perawat memeriksa"))

    @Test
    fun `perencana mengisi rencana sekali di awal dan pengisi per langkah dilewati`() = runTest {
        var planCalls = 0
        val fillCalls = mutableListOf<InterviewStep>()
        val planner = InterviewPlanner { _, s, _ -> planCalls++; s.mergingPlan(listOf(planned), emptyList(), emptyList(), emptyList()) }
        val filler = InterviewStepFiller { _, s, step, _ -> fillCalls += step; s }
        val r = repo()
        InterviewDraftUseCases(r, filler, planner).start(id, owner, story).getOrThrow()
        val s = requireNotNull(r.row.draft.interview)
        assertEquals(1, planCalls)
        assertTrue(s.divisions.any { it.code.value == "pemeriksaan" }, "usulan perencana masuk")
        assertTrue(s.divisions.any { it.code.value == "pendaftaran" }, "tebakan deterministik tidak hilang")
        assertTrue(fillCalls.isEmpty(), "rencana sudah mencakup G1–G4, model kecil tidak dipanggil lagi")
        assertEquals(emptyList(), InterviewValidator.validate(s, pack))
    }

    @Test
    fun `perencana gagal atau tak sah, sesi tetap tebakan deterministik`() = runTest {
        val baseline = DeterministicInterviewGuesser.propose(pack, story)
        val boom = repo()
        InterviewDraftUseCases(boom, null, InterviewPlanner { _, _, _ -> error("LLM mati") }).start(id, owner, story).getOrThrow()
        assertEquals(baseline, boom.row.draft.interview)
        val invalid = repo()
        InterviewDraftUseCases(invalid, null, InterviewPlanner { _, s, _ ->
            s.copy(roles = s.roles + RoleDraft(RoleKey("hantu"), "Hantu", DivisionCode("tidak_ada"), ItemSource.GUESS))
        }).start(id, owner, story).getOrThrow()
        assertEquals(baseline, invalid.row.draft.interview)
    }

    @Test
    fun `tanpa narasi perencana tidak dipanggil`() = runTest {
        var calls = 0
        val r = repo()
        InterviewDraftUseCases(r, null, InterviewPlanner { _, s, _ -> calls++; s }).start(id, owner, "  ").getOrThrow()
        assertEquals(0, calls)
    }

    private val ask = listOf(Clarification("c1", "Pasien dilayani per poli atau satu antrean?"))

    @Test
    fun `perencana boleh bertanya dulu, wawancara menunggu jawaban dan pengisi per langkah tidak jalan`() = runTest {
        val fills = mutableListOf<InterviewStep>()
        val planner = InterviewPlanner { _, s, _ -> s.copy(clarifications = ask) }
        val r = repo()
        val uc = InterviewDraftUseCases(r, InterviewStepFiller { _, s, st, _ -> fills += st; s }, planner)
        uc.start(id, owner, story).getOrThrow()
        val s = requireNotNull(r.row.draft.interview)
        assertTrue(s.awaitingClarification)
        assertEquals(ask, s.clarifications)
        assertTrue(fills.isEmpty())
        val q = requireNotNull(s.nextQuestion(r.row.draft)).id
        assertTrue(uc.answer(id, owner, q, Confirmation.CONFIRMED, null, null).isFailure, "giliran diblokir sampai klarifikasi dijawab")
    }

    @Test
    fun `jawaban klarifikasi masuk ke cerita, rencana disusun ulang tanpa bertanya lagi`() = runTest {
        var calls = 0
        val seen = mutableListOf<List<Clarification>>()
        val planner = InterviewPlanner { _, s, _ ->
            calls++; seen += s.clarifications
            if (s.clarifications.isEmpty()) s.copy(clarifications = ask) else s.mergingPlan(listOf(planned), emptyList(), emptyList(), emptyList())
        }
        val r = repo()
        val uc = InterviewDraftUseCases(r, null, planner)
        uc.start(id, owner, story).getOrThrow()
        uc.clarify(id, owner, mapOf("c1" to "Satu antrean, lalu dibagi ke poli")).getOrThrow()
        val s = requireNotNull(r.row.draft.interview)
        assertEquals(2, calls)
        assertEquals("Satu antrean, lalu dibagi ke poli", seen.last().single().answer)
        assertTrue(!s.awaitingClarification)
        assertTrue(s.narrative.orEmpty().contains("Jawaban: Satu antrean, lalu dibagi ke poli"))
        assertTrue(s.narrative.orEmpty().startsWith(story), "cerita asli tetap, supaya kutipan lama masih sah")
        assertTrue(s.divisions.any { it.code.value == "pemeriksaan" })
    }

    @Test
    fun `klarifikasi tanpa jawaban atau tanpa pertanyaan ditolak`() = runTest {
        val r = repo()
        val uc = InterviewDraftUseCases(r, null, InterviewPlanner { _, s, _ -> if (s.clarifications.isEmpty()) s.copy(clarifications = ask) else s })
        assertTrue(uc.clarify(id, owner, mapOf("c1" to "x")).isFailure, "belum dimulai")
        uc.start(id, owner, story).getOrThrow()
        assertTrue(uc.clarify(id, owner, mapOf("c1" to "  ")).isFailure, "jawaban kosong")
        assertTrue(uc.clarify(id, UserId("lain"), mapOf("c1" to "ok")).isFailure, "bukan pemilik")
    }

    @Test
    fun `validator menolak lebih dari tiga klarifikasi dan id ganda`() {
        val many = (1..4).map { Clarification("c$it", "Tanya $it") }
        val s = InterviewSession(InterviewStep.F0_BISNIS, clarifications = many + Clarification("c1", "ganda"))
        val paths = InterviewValidator.validate(s, pack).map { it.path }
        assertTrue(paths.count { it == "$.interview.clarifications" } >= 2)
    }
}
