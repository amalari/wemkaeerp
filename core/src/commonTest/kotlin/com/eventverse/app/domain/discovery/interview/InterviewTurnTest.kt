package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.RoleHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterviewTurnTest {

    private val pack = InterviewFixtures.klinikPack.copy(roleHints = listOf(
        RoleHint("resepsionis", "Resepsionis", ModuleId("klinik_pendaftaran")), RoleHint("perawat", "Perawat", ModuleId("klinik_poli"))
    ))
    private val start = DeterministicInterviewGuesser.propose(pack, "resepsionis mendaftarkan lalu perawat memeriksa")

    private fun step(s: InterviewSession, outcome: Confirmation = Confirmation.CONFIRMED): InterviewSession {
        val q = requireNotNull(s.nextQuestion(draftOf(pack, s)))
        return s.answer(draftOf(pack, s), q.id, outcome).getOrThrow()
    }

    @Test
    fun `jawaban maju satu langkah dan mencatat jejak giliran`() {
        val after = step(start)
        assertEquals(InterviewStep.G2_PERAN, after.step)
        assertEquals(listOf("g1_divisi_t1"), after.answers.map { it.questionId })
    }

    @Test
    fun `menjawab sampai selesai mengonfirmasi tautan dan sambungan, dan tetap sah`() {
        var s = start
        var turns = 0
        while (s.nextQuestion(draftOf(pack, s)) != null) { s = step(s); turns++ }
        assertEquals(5, turns)
        assertEquals(InterviewStep.DONE, s.step)
        assertTrue(s.links.all { it.confirmed == Confirmation.CONFIRMED } && s.handoffs.all { it.confirmed == Confirmation.CONFIRMED })
        assertEquals(emptyList(), InterviewValidator.validate(s, pack))
    }

    @Test
    fun `dilewati menandai tebakan SKIPPED`() {
        var s = start
        repeat(2) { s = step(s) }
        val skipped = step(s, Confirmation.SKIPPED)
        assertTrue(skipped.links.all { it.confirmed == Confirmation.SKIPPED })
    }

    @Test
    fun `jawaban basi, GUESSED, dan setelah selesai ditolak`() {
        val d = draftOf(pack, start)
        assertTrue(start.answer(d, "g3_modul_t1", Confirmation.CONFIRMED).isFailure)
        assertTrue(start.answer(d, "g1_divisi_t1", Confirmation.GUESSED).isFailure)
        assertTrue(start.acceptAll().answer(d, "x", Confirmation.CONFIRMED).isFailure)
    }

    @Test
    fun `sesi suntingan klien dipakai isinya tetapi langkah dan jejak tetap milik server`() {
        val revised = start.copy(step = InterviewStep.DONE, answers = emptyList(),
            divisions = start.divisions + DivisionDraft(DivisionCode("farmasi"), "Farmasi", ItemSource.ANSWER))
        val after = start.answer(draftOf(pack, start), "g1_divisi_t1", Confirmation.CHANGED, revised = revised).getOrThrow()
        assertEquals(InterviewStep.G2_PERAN, after.step)
        assertEquals(1, after.answers.size)
        assertTrue(after.divisions.any { it.code.value == "farmasi" })
    }

    @Test
    fun `pertanyaan memuat tebakan yang menunggu dari sesi itu sendiri`() {
        assertEquals(start.divisions.map { it.name }, start.nextQuestion(draftOf(pack, start))!!.guesses.map { it.label })
        val g3 = start.copy(step = InterviewStep.G3_MODUL).nextQuestion(draftOf(pack, start))!!
        assertTrue(g3.guesses.all { it.origin == ModuleOrigin.NEW })
    }
}
