package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft

/**
 * Menerapkan **satu giliran jawaban** (murni). [outcome] ∈ {CONFIRMED, CHANGED, SKIPPED}; [revised] opsional =
 * sesi hasil suntingan klien (tambah/hapus/ganti nama/pindah modul, dengan tandanya sendiri `CHANGED`). Yang
 * diambil dari [revised] hanya isinya — langkah dan jejak giliran selalu milik server, supaya klien tidak bisa
 * melompati langkah atau memalsukan riwayat.
 *
 * Aturan: [questionId] harus pertanyaan yang sedang berlaku (jawaban basi ditolak, bukan diterapkan ke
 * pertanyaan lain); tebakan `GUESSED` pada langkah ini menjadi `SKIPPED` bila dilewati, selain itu `CONFIRMED`.
 * Hasilnya tetap harus lolos `InterviewValidator` — itu dilakukan pemanggil sebelum menyimpan.
 */
fun InterviewSession.answer(
    draft: DiscoveryDraft,
    questionId: String,
    outcome: Confirmation,
    text: String? = null,
    revised: InterviewSession? = null
): Result<InterviewSession> = runCatching {
    require(outcome != Confirmation.GUESSED) { "Jawaban tidak boleh GUESSED; pakai CONFIRMED, CHANGED, atau SKIPPED" }
    val question = requireNotNull(nextQuestion(draft)) { "Wawancara sudah selesai" }
    require(question.id == questionId) { "Pertanyaan sudah berganti (sekarang '${question.id}', bukan '$questionId'); ambil ringkasan terbaru" }
    val base = revised ?: this
    val settle = if (outcome == Confirmation.SKIPPED) Confirmation.SKIPPED else Confirmation.CONFIRMED
    val settled = when (question.step) {
        InterviewStep.G3_MODUL -> base.copy(links = base.links.map { if (it.confirmed == Confirmation.GUESSED) it.copy(confirmed = settle) else it })
        InterviewStep.G4_SAMBUNGAN -> base.copy(handoffs = base.handoffs.map { if (it.confirmed == Confirmation.GUESSED) it.copy(confirmed = settle) else it })
        else -> base
    }
    val next = if (question.step == InterviewStep.G5_RINGKASAN) InterviewStep.DONE else InterviewStep.entries[question.step.ordinal + 1]
    settled.copy(
        step = next,
        answers = answers + InterviewAnswer(answers.size + 1, question.step, question.id, outcome, text)
    )
}
