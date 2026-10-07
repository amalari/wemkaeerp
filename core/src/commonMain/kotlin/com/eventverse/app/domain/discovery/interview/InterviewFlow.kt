package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.moduleLabel
import com.eventverse.app.domain.pack.resolveModule

/**
 * Pertanyaan berikutnya (PLAN-iv-B §6): **murni** — hanya bergantung pada keadaan sesi dan pack draf.
 * Null = selesai (langkah `DONE` atau batas giliran [InterviewLimits.TURNS] tercapai).
 *
 * Langkah yang tak perlu ditanya dilewati: peran tak ditanya bila belum ada divisi, modul tak ditanya bila belum
 * ada peran, sambungan tak ditanya bila kurang dari dua modul operasional tertaut. Tebakan isinya milik
 * [InterviewGuesser]; di sini hanya prompt netral tanpa kosakata industri apa pun.
 */
fun InterviewSession.nextQuestion(draft: DiscoveryDraft, guesses: List<Guess>? = null): InterviewQuestion? {
    val step = effectiveStep(draft.pack) ?: return null
    // Batas giliran G1–G5; fase konsultan F0–F2 satu giliran per langkah sehingga tak mungkin melampaui batasnya.
    if (!step.isConsultant && answers.count { !it.step.isConsultant } >= InterviewLimits.TURNS) return null
    val shown = guesses ?: pendingGuesses(step, draft.pack)
    return InterviewQuestion("${step.code}_t${answers.size + 1}", step, promptFor(step, shown.isNotEmpty()), shown)
}

/**
 * Tebakan sistem yang **menunggu konfirmasi** di [step], dibaca dari sesi itu sendiri (sesi usulan memuat tebakan
 * `GUESS` / `GUESSED`). Itu yang digambar klien; dikonfirmasi satu giliran, lalu langkah maju.
 */
fun InterviewSession.pendingGuesses(step: InterviewStep, pack: DomainPack): List<Guess> {
    val moduleName = { id: com.eventverse.app.domain.pack.ModuleId -> pack.moduleLabel(id) ?: id.value }
    val roleLabel = roles.associate { it.roleKey to it.label }
    return when (step) {
        InterviewStep.G1_DIVISI -> divisions.filter { it.source == ItemSource.GUESS }.map { Guess(it.code.value, it.name, 70) }
        InterviewStep.G2_PERAN -> roles.filter { it.source == ItemSource.GUESS }.map { Guess(it.roleKey.value, it.label, 70) }
        InterviewStep.G3_MODUL -> links.filter { it.confirmed == Confirmation.GUESSED }
            .map { Guess("${it.roleKey.value}:${it.moduleId.value}", "${roleLabel[it.roleKey]} → ${moduleName(it.moduleId)}", it.confidence ?: 70, it.origin) }
        InterviewStep.G4_SAMBUNGAN -> handoffs.filter { it.confirmed == Confirmation.GUESSED }
            .map { Guess("${it.from.value}>${it.to.value}", "${moduleName(it.from)} → ${moduleName(it.to)}", 60) }
        InterviewStep.F0_BISNIS, InterviewStep.F1_TUJUAN, InterviewStep.F2_SPEK,
        InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> emptyList()
    }
}

internal fun InterviewSession.effectiveStep(pack: DomainPack): InterviewStep? {
    var s = step
    while (true) {
        val skip = when (s) {
            InterviewStep.F0_BISNIS -> !profile?.summary.isNullOrBlank()
            InterviewStep.F1_TUJUAN -> profile?.let { it.goals.isNotEmpty() || it.painPoints.isNotEmpty() } == true
            // Area spesifikasi muncul dari titik sakit; tanpa titik sakit atau bila spek sudah ada, tak ada yang ditanya.
            InterviewStep.F2_SPEK -> profile?.painPoints.isNullOrEmpty() || specs.isNotEmpty()
            InterviewStep.G2_PERAN -> divisions.isEmpty()
            InterviewStep.G3_MODUL -> roles.isEmpty()
            InterviewStep.G4_SAMBUNGAN -> links.map { it.moduleId }.distinct().count { pack.resolveModule(it)?.slot != null } < 2
            else -> false
        }
        if (!skip) return if (s == InterviewStep.DONE) null else s
        s = InterviewStep.entries[s.ordinal + 1]
    }
}

private fun promptFor(step: InterviewStep, hasGuesses: Boolean): String = when (step) {
    InterviewStep.F0_BISNIS -> "Ceritakan usahanya: apa yang dijual atau dikerjakan, siapa pelanggannya, dan sebesar apa skalanya?"
    InterviewStep.F1_TUJUAN -> "Sistem seperti apa yang ingin Anda buat, dan apa yang paling merepotkan saat ini?"
    InterviewStep.F2_SPEK -> "Untuk hal yang merepotkan tadi: siapa yang mengisi, apa yang dicatat, siapa yang perlu melihat, dan kapan dianggap selesai?"
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
