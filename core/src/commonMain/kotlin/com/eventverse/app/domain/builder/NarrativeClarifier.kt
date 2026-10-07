package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.InterviewLimits

/**
 * Penanya klarifikasi **sebelum draf disusun** (PLAN-builder-interview-chat Fase B): membaca narasi (dan modul draf
 * yang sudah ada, bila ada) lalu menjawab *apakah ada hal pokok yang tak bisa disimpulkan*. Kosong = cukup jelas,
 * langsung susun draf. Tidak kosong = maksimal [InterviewLimits.CLARIFICATIONS] pertanyaan singkat yang ditanyakan
 * dulu di chat.
 *
 * Seperti agent lain, ia hanya **mengusulkan**: kegagalan apa pun (galat, timeout, keluaran rusak) berarti "tidak
 * bertanya" — chat tidak pernah macet karena penanya.
 */
fun interface NarrativeClarifier {
    suspend fun clarify(narrative: String, existingModules: List<String>): List<Clarification>
}

/**
 * Narasi gabungan satu utas untuk agent penyusun draf. Agent menyusun draf dari narasi utuh, jadi revisi pengguna
 * ("tambah modul pengiriman") harus dibaca **bersama** cerita awalnya, bukan sebagai cerita baru yang berdiri sendiri
 * (perilaku lama: draf disusun ulang dari nol dari satu kalimat revisi).
 *
 * Urutan kronologis: pesan USER apa adanya; pesan QUESTION diikuti jawabannya sebagai "Pertanyaan/Jawaban". Pesan
 * USER yang hanya berfungsi sebagai jawaban tidak diulang. Bila melebihi batas, bagian **awal** dipertahankan
 * (cerita pokok) dan bagian akhir (revisi terbaru) dipotong paling akhir — keduanya dijaga lewat pemotongan tengah.
 */
fun composeNarrative(thread: List<ChatMessage>, limit: Int = InterviewLimits.NARRATIVE): String {
    val answers = thread.filter { it.kind == ChatMessageKind.QUESTION }
        .flatMap { m -> m.questions.mapNotNull { it.answer?.trim()?.takeIf(String::isNotEmpty) } }.toSet()
    val parts = thread.mapNotNull { m ->
        when {
            m.kind == ChatMessageKind.QUESTION ->
                m.questions.filter { !it.answer.isNullOrBlank() }
                    .joinToString("\n") { "Pertanyaan: ${it.question}\nJawaban: ${it.answer}" }
                    .ifEmpty { null }
            m.role == ChatRole.USER && m.text.trim() !in answers -> m.text.trim()
            else -> null
        }
    }
    val full = parts.joinToString("\n\n")
    if (full.length <= limit) return full
    val head = limit * 2 / 3
    return full.take(head) + "\n...\n" + full.takeLast(limit - head - 5)
}
