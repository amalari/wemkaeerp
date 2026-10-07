package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack

/**
 * Pertanyaan berikutnya (PLAN-iv-B §6): **murni** — hanya bergantung pada keadaan sesi dan pack draf.
 * Null = selesai (langkah `DONE` atau batas giliran [InterviewLimits.TURNS] tercapai).
 *
 * Langkah yang tak perlu ditanya dilewati: peran tak ditanya bila belum ada divisi, modul tak ditanya bila belum
 * ada peran, sambungan tak ditanya bila kurang dari dua modul operasional tertaut. Tebakan isinya milik
 * [InterviewGuesser]; di sini hanya prompt netral tanpa kosakata industri apa pun.
 */
fun InterviewSession.nextQuestion(draft: DiscoveryDraft, guesses: List<Guess> = emptyList()): InterviewQuestion? {
    if (answers.size >= InterviewLimits.TURNS) return null
    val step = effectiveStep(draft.pack) ?: return null
    return InterviewQuestion("${step.code}_t${answers.size + 1}", step, promptFor(step, guesses.isNotEmpty()), guesses)
}

internal fun InterviewSession.effectiveStep(pack: DomainPack): InterviewStep? {
    var s = step
    while (true) {
        val skip = when (s) {
            InterviewStep.G2_PERAN -> divisions.isEmpty()
            InterviewStep.G3_MODUL -> roles.isEmpty()
            InterviewStep.G4_SAMBUNGAN -> links.map { it.moduleId }.distinct().count { pack.module(it)?.slot != null } < 2
            else -> false
        }
        if (!skip) return if (s == InterviewStep.DONE) null else s
        s = InterviewStep.entries[s.ordinal + 1]
    }
}

private fun promptFor(step: InterviewStep, hasGuesses: Boolean): String = when (step) {
    InterviewStep.G1_DIVISI ->
        if (hasGuesses) "Dari cerita Anda, saya menebak divisi berikut. Benar, ada yang perlu ditambah, dihapus, atau diganti nama?"
        else "Divisi atau bagian apa saja yang ada di usaha Anda?"
    InterviewStep.G2_PERAN ->
        if (hasGuesses) "Saya menebak jabatan berikut di tiap divisi. Sudah benar? Siapa kepala tiap divisi?"
        else "Jabatan apa saja di tiap divisi, dan siapa kepalanya?"
    InterviewStep.G3_MODUL ->
        if (hasGuesses) "Saya menebak modul yang dipegang tiap peran. Setuju, atau pindahkan ke modul lain?"
        else "Modul atau pekerjaan apa yang dipegang tiap peran?"
    InterviewStep.G4_SAMBUNGAN ->
        if (hasGuesses) "Saya menebak urutan serah-terima antar modul. Sudah sesuai alur kerja Anda?"
        else "Pekerjaan dari modul mana diserahkan ke modul mana?"
    InterviewStep.G5_RINGKASAN -> "Ringkasan sudah siap. Kunci sekarang, atau kembali ke giliran tertentu?"
    InterviewStep.DONE -> error("DONE tidak punya pertanyaan")
}

/**
 * "Terima semua tebakan": menandai semua `GUESSED` menjadi `SKIPPED` (dicatat jujur, bukan disamarkan `CONFIRMED`)
 * dan menutup wawancara. Hasilnya tetap **usulan** yang ditinjau, dan tetap harus lolos `InterviewValidator`.
 */
fun InterviewSession.acceptAll(): InterviewSession = copy(
    step = InterviewStep.DONE,
    links = links.map { if (it.confirmed == Confirmation.GUESSED) it.copy(confirmed = Confirmation.SKIPPED) else it },
    handoffs = handoffs.map { if (it.confirmed == Confirmation.GUESSED) it.copy(confirmed = Confirmation.SKIPPED) else it },
    answers = answers + InterviewAnswer(answers.size + 1, step, "accept_all", Confirmation.SKIPPED)
)
