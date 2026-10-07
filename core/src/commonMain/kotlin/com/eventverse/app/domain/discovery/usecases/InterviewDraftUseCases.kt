package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DeterministicInterviewGuesser
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.acceptAll
import com.eventverse.app.domain.discovery.interview.answer

/**
 * Wawancara atas satu draf (PLAN-iv-B B4). **Hanya pemilik draf** — superadmin tidak menjawab atas nama prospek
 * (berbeda dari `UpdateDiscoveryDraftUseCase`), dan kepemilikan dicek di sini, bukan hanya di route. Penyimpanan
 * lewat `UpdateDiscoveryDraftUseCase`, jadi LOCKED ditolak dan validator draf (termasuk `InterviewValidator`)
 * selalu berjalan: sesi yang gagal tidak pernah tersimpan.
 */
class InterviewDraftUseCases(private val repository: DiscoveryDraftRepository) {

    private val update = UpdateDiscoveryDraftUseCase(repository)

    /**
     * Memulai wawancara dari tebakan deterministik atas [narrative]; [consultant] membuka dengan fase F0–F2 (bisnis →
     * tujuan → spesifikasi) sebelum G1. Idempoten: sesi yang sudah ada tidak ditimpa.
     */
    suspend fun start(id: DiscoveryDraftId, caller: UserId, narrative: String, consultant: Boolean = false): Result<StoredDiscoveryDraft> =
        mutate(id, caller) { draft ->
            draft.interview ?: DeterministicInterviewGuesser.propose(draft.pack, narrative)
                .let { if (consultant) it.copy(step = InterviewStep.F0_BISNIS) else it }
        }

    suspend fun answer(
        id: DiscoveryDraftId, caller: UserId, questionId: String, outcome: Confirmation, text: String?, revised: InterviewSession?
    ): Result<StoredDiscoveryDraft> = mutate(id, caller) { draft ->
        val current = requireNotNull(draft.interview) { "Wawancara belum dimulai; mulai dulu" }
        current.answer(draft, questionId, outcome, text, revised).getOrThrow()
    }

    /** "Terima semua tebakan": menutup wawancara, tebakan dicatat `SKIPPED`. Memulai sesi dulu bila belum ada. */
    suspend fun acceptAll(id: DiscoveryDraftId, caller: UserId, narrative: String): Result<StoredDiscoveryDraft> =
        mutate(id, caller) { draft ->
            (draft.interview ?: DeterministicInterviewGuesser.propose(draft.pack, narrative)).acceptAll()
        }

    private suspend fun mutate(
        id: DiscoveryDraftId, caller: UserId, change: (DiscoveryDraft) -> InterviewSession
    ): Result<StoredDiscoveryDraft> = runCatching {
        val stored = repository.findById(id) ?: error("Draf ${id.value} tidak ditemukan")
        if (stored.ownerUserId != caller) throw UpdateDiscoveryDraftUseCase.NotOwnerException("Draf ${id.value} bukan milik Anda")
        update(id, caller, false, stored.draft.copy(interview = change(stored.draft))).getOrThrow()
    }
}
