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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterviewStepFillerTest {

    private val pack = InterviewFixtures.klinikPack.copy(roleHints = listOf(
        RoleHint("resepsionis", "Resepsionis", ModuleId("klinik_pendaftaran")), RoleHint("perawat", "Perawat", ModuleId("klinik_poli"))
    ))
    private val owner = UserId("u-1")
    private val story = "Resepsionis mendaftarkan pasien lalu perawat memeriksa."

    private class MemRepo(initial: StoredDiscoveryDraft) : DiscoveryDraftRepository {
        var row = initial
        override suspend fun findById(id: DiscoveryDraftId) = row.takeIf { it.id == id }
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId): StoredDiscoveryDraft? = null
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft { row = stored; return stored }
    }

    private fun repo() = MemRepo(StoredDiscoveryDraft(DiscoveryDraftId("d1"), owner, draftOf(pack, null)))
    private val id = DiscoveryDraftId("d1")

    private fun agentDivisions() = listOf(
        DivisionDraft(DivisionCode("pelayanan_pasien"), "Pelayanan Pasien", ItemSource.GUESS, BasisRef(Basis.NARASI, quote = "pasien"))
    )

    @Test
    fun `agent mengganti tebakan G1, memangkas yang menggantung, dan sesi tetap sah`() = runTest {
        val calls = mutableListOf<InterviewStep>()
        val filler = InterviewStepFiller { _, session, step, _ -> calls += step; session.replacingGuessesOf(step, newDivisions = agentDivisions()) }
        val r = repo()
        InterviewDraftUseCases(r, filler).start(id, owner, story).getOrThrow()
        val s = requireNotNull(r.row.draft.interview)
        assertEquals(listOf(InterviewStep.G1_DIVISI), calls)
        assertEquals(listOf("pelayanan_pasien"), s.divisions.map { it.code.value })
        assertTrue(s.roles.isEmpty() && s.links.isEmpty() && s.handoffs.isEmpty(), "tebakan deterministik yang menggantung dipangkas")
        assertEquals(emptyList(), InterviewValidator.validate(s, pack))
    }

    @Test
    fun `agent gagal atau mengembalikan sesi tak sah, tebakan deterministik tetap berlaku`() = runTest {
        val baseline = DeterministicInterviewGuesser.propose(pack, story)
        val boom = repo()
        InterviewDraftUseCases(boom, InterviewStepFiller { _, _, _, _ -> error("LLM mati") }).start(id, owner, story).getOrThrow()
        assertEquals(baseline, boom.row.draft.interview)
        val invalid = repo()
        InterviewDraftUseCases(invalid, InterviewStepFiller { _, s, _, _ -> s.copy(roles = s.roles + RoleDraft(RoleKey("hantu"), "Hantu", DivisionCode("tidak_ada"), ItemSource.GUESS)) })
            .start(id, owner, story).getOrThrow()
        assertEquals(baseline, invalid.row.draft.interview)
    }

    @Test
    fun `tanpa agent perilaku tidak berubah`() = runTest {
        val r = repo()
        InterviewDraftUseCases(r).start(id, owner, story).getOrThrow()
        assertEquals(DeterministicInterviewGuesser.propose(pack, story), r.row.draft.interview)
    }

    @Test
    fun `mode konsultan, agent tidak dipanggil di F0-F2 dan baru saat masuk G1`() = runTest {
        val calls = mutableListOf<InterviewStep>()
        val filler = InterviewStepFiller { _, session, step, _ -> calls += step; session }
        val r = repo()
        val uc = InterviewDraftUseCases(r, filler)
        uc.start(id, owner, story, consultant = true).getOrThrow()
        assertTrue(calls.isEmpty())
        fun q() = requireNotNull(r.row.draft.interview!!.nextQuestion(r.row.draft)).id
        uc.answer(id, owner, q(), Confirmation.CONFIRMED, "Klinik umum", null, story).getOrThrow()
        assertTrue(calls.isEmpty(), "F1 bukan langkah tebakan")
        val s = r.row.draft.interview!!.copy(profile = r.row.draft.interview!!.profile?.copy(painPoints = emptyList()))
        r.row = r.row.copy(draft = r.row.draft.copy(interview = s))
        uc.answer(id, owner, q(), Confirmation.CONFIRMED, "antrean rapi", null, story).getOrThrow()
        assertEquals(listOf(InterviewStep.G1_DIVISI), calls)
    }

    @Test
    fun `butir milik pengguna dipertahankan saat tebakan diganti`() {
        val mine = DivisionDraft(DivisionCode("farmasi"), "Farmasi", ItemSource.ANSWER, BasisRef(Basis.JAWABAN, answerId = "q"))
        val s = InterviewSession(InterviewStep.G1_DIVISI, divisions = listOf(mine, DivisionDraft(DivisionCode("lama"), "Lama", ItemSource.GUESS)))
        val out = s.replacingGuessesOf(InterviewStep.G1_DIVISI, newDivisions = agentDivisions())
        assertEquals(listOf("farmasi", "pelayanan_pasien"), out.divisions.map { it.code.value })
        assertFalse(out.divisions.any { it.code.value == "lama" })
    }

    @Test
    fun `pemangkasan menyisakan satu kepala per divisi`() {
        val d = DivisionDraft(DivisionCode("a"), "A", ItemSource.GUESS)
        val s = InterviewSession(InterviewStep.G2_PERAN, divisions = listOf(d), roles = listOf(
            RoleDraft(RoleKey("r1"), "R1", d.code, ItemSource.GUESS, isHead = true),
            RoleDraft(RoleKey("r2"), "R2", d.code, ItemSource.GUESS, isHead = true)
        ))
        assertEquals(listOf(true, false), s.withoutDangling().roles.map { it.isHead })
    }
}
