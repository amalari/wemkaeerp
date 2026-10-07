package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DeterministicInterviewGuesser
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewLimits
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.InterviewStepFiller
import com.eventverse.app.domain.discovery.interview.isPlanned
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.effectiveStep
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.acceptAll
import com.eventverse.app.domain.discovery.interview.answer

/**
 * Wawancara atas satu draf (PLAN-iv-B B4). **Hanya pemilik draf** — superadmin tidak menjawab atas nama prospek
 * (berbeda dari `UpdateDiscoveryDraftUseCase`), dan kepemilikan dicek di sini, bukan hanya di route. Penyimpanan
 * lewat `UpdateDiscoveryDraftUseCase`, jadi LOCKED ditolak dan validator draf (termasuk `InterviewValidator`)
 * selalu berjalan: sesi yang gagal tidak pernah tersimpan.
 */
class InterviewDraftUseCases(
    private val repository: DiscoveryDraftRepository,
    /** Pengisi tebakan langkah (agent AI); null = hanya tebakan deterministik. Kegagalannya tidak pernah menggagalkan giliran. */
    private val filler: InterviewStepFiller? = null,
    /** Perencana alur penuh (model besar, sekali di awal); null = tidak ada. Kegagalannya tidak pernah menggagalkan giliran. */
    private val planner: InterviewPlanner? = null
) {

    private val update = UpdateDiscoveryDraftUseCase(repository)

    /**
     * Memulai wawancara dari tebakan deterministik atas [narrative]; [consultant] membuka dengan fase F0–F2 (bisnis →
     * tujuan → spesifikasi) sebelum G1. Idempoten: sesi yang sudah ada tidak ditimpa.
     */
    suspend fun start(id: DiscoveryDraftId, caller: UserId, narrative: String, consultant: Boolean = false): Result<StoredDiscoveryDraft> =
        mutate(id, caller) { draft ->
            draft.interview ?: DeterministicInterviewGuesser.propose(draft.pack, narrative)
                .let { if (consultant) it.copy(step = InterviewStep.F0_BISNIS) else it }
                .let { planWhole(draft, it, narrative) }
                .let { fillCurrentStep(draft, it, narrative) }
        }

    suspend fun answer(
        id: DiscoveryDraftId, caller: UserId, questionId: String, outcome: Confirmation, text: String?, revised: InterviewSession?,
        narrative: String = ""
    ): Result<StoredDiscoveryDraft> = mutate(id, caller) { draft ->
        val current = requireNotNull(draft.interview) { "Wawancara belum dimulai; mulai dulu" }
        require(!current.awaitingClarification) { "Jawab pertanyaan klarifikasi dulu sebelum melanjutkan giliran" }
        val advanced = current.answer(draft, questionId, outcome, text, revised).getOrThrow()
        fillCurrentStep(draft, advanced, narrative.ifBlank { advanced.narrative.orEmpty() })
    }

    /**
     * Menjawab pertanyaan klarifikasi perencana ([answers]: id → jawaban, semua yang belum terjawab wajib terisi).
     * Jawaban ditambahkan ke cerita lalu rencana disusun ulang — kali ini tanpa bertanya lagi. Perencana gagal =
     * sesi tetap berisi jawaban (tebakan deterministik berlaku), wawancara tidak pernah gagal karena AI.
     */
    suspend fun clarify(id: DiscoveryDraftId, caller: UserId, answers: Map<String, String>): Result<StoredDiscoveryDraft> =
        mutate(id, caller) { draft ->
            val current = requireNotNull(draft.interview) { "Wawancara belum dimulai; mulai dulu" }
            require(current.awaitingClarification) { "Tidak ada pertanyaan klarifikasi yang menunggu jawaban" }
            val updated = current.clarifications.map { c ->
                if (!c.answer.isNullOrBlank()) c
                else c.copy(answer = answers[c.id]?.trim()?.takeIf { it.isNotEmpty() } ?: error("Pertanyaan '${c.id}' wajib dijawab"))
            }
            val story = buildString {
                append(current.narrative.orEmpty())
                updated.forEach { append("\n\nPertanyaan: ").append(it.question).append("\nJawaban: ").append(it.answer) }
            }.take(InterviewLimits.NARRATIVE)
            val answered = current.copy(clarifications = updated, narrative = story)
            planWhole(draft, answered, story)
        }

    /** "Terima semua tebakan": menutup wawancara, tebakan dicatat `SKIPPED`. Memulai sesi dulu bila belum ada. */
    suspend fun acceptAll(id: DiscoveryDraftId, caller: UserId, narrative: String): Result<StoredDiscoveryDraft> =
        mutate(id, caller) { draft ->
            (draft.interview ?: DeterministicInterviewGuesser.propose(draft.pack, narrative)).acceptAll()
        }

    /**
     * Langkah G1–G4 yang akan ditanyakan berikutnya diisi [filler] (agent AI) bila ada. Hasil dipakai **hanya bila
     * sesi hasilnya lolos validator**; selain itu (galat, timeout, usulan tak sah) sesi dikembalikan apa adanya.
     */
    private suspend fun fillCurrentStep(draft: DiscoveryDraft, session: InterviewSession, narrative: String): InterviewSession {
        val agent = filler ?: return session
        if (session.awaitingClarification) return session           // menunggu jawaban klarifikasi dulu
        if (planner != null && session.isPlanned) return session     // rencana sudah mencakup G1–G4; pengguna meninjau
        val step = session.effectiveStep(draft.pack) ?: return session
        if (step.isConsultant || step == InterviewStep.G5_RINGKASAN) return session
        val filled = runCatching { agent.fill(draft.copy(interview = session), session, step, narrative) }.getOrNull() ?: return session
        return if (InterviewValidator.validate(filled, draft.pack).isEmpty()) filled else session
    }

    /** Rencana alur penuh sekali di awal; dipakai hanya bila lolos validator, selain itu sesi apa adanya. */
    private suspend fun planWhole(draft: DiscoveryDraft, session: InterviewSession, narrative: String): InterviewSession {
        val agent = planner ?: return session
        if (narrative.isBlank()) return session
        val planned = runCatching { agent.plan(draft.copy(interview = session), session, narrative) }.getOrNull() ?: return session
        return if (InterviewValidator.validate(planned, draft.pack).isEmpty()) planned else session
    }

    private suspend fun mutate(
        id: DiscoveryDraftId, caller: UserId, change: suspend (DiscoveryDraft) -> InterviewSession
    ): Result<StoredDiscoveryDraft> = runCatching {
        val stored = repository.findById(id) ?: error("Draf ${id.value} tidak ditemukan")
        if (stored.ownerUserId != caller) throw UpdateDiscoveryDraftUseCase.NotOwnerException("Draf ${id.value} bukan milik Anda")
        update(id, caller, false, stored.draft.copy(interview = change(stored.draft))).getOrThrow()
    }
}
