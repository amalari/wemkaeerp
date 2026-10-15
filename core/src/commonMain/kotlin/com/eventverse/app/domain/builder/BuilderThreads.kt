package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification

/**
 * Utas chat Builder (PLAN-builder-interview-chat K4): `moduleId == null` = utas **Semua** — **tanpa filter**, memuat
 * seluruh pesan; `moduleId` terisi = hanya pesan modul itu. Murni, tanpa I/O.
 */
fun List<ChatMessage>.inThread(moduleId: String?): List<ChatMessage> =
    if (moduleId == null) this else filter { it.moduleId == moduleId }

/** Satu pertanyaan follow-up yang menunggu jawaban, beserta pesan asalnya. */
data class PendingFollowUp(val messageId: ChatMessageId, val moduleId: String?, val question: Clarification)

/**
 * Follow-up yang menunggu jawaban di utas [moduleId] — dihitung **dari data** riwayat (pertanyaan di pesan QUESTION yang belum
 * berjawab), bukan dari model. Utas Semua mencakup follow-up semua utas; utas modul hanya miliknya.
 *
 * **Pertanyaan AI menggantikan pertanyaan template** (cek visual model sungguhan 2026-10-16): bila sebuah modul pernah diberi
 * pertanyaan AI (dijawab atau belum), pertanyaan template (`gap:`) modul itu yang belum dijawab tidak lagi dihitung menunggu —
 * disembunyikan, **bukan dihapus**, dan tidak muncul kembali setelah pertanyaan AI dijawab. Modul tanpa pertanyaan AI tidak berubah.
 */
fun List<ChatMessage>.pendingFollowUps(moduleId: String?): List<PendingFollowUp> {
    val questions = inThread(moduleId).filter { it.kind == ChatMessageKind.QUESTION }
    val aiModules = questions.filter { m -> m.questions.any { BriefKeys.isAiQuestionId(it.id) } }.map { it.moduleId }.toSet()
    return questions
        .flatMap { m -> m.questions.filter { it.answer.isNullOrBlank() }.map { PendingFollowUp(m.id, m.moduleId, it) } }
        .filterNot { it.moduleId != null && it.moduleId in aiModules && ModuleGapAnalyzer.isGapQuestionId(it.question.id) }
}
