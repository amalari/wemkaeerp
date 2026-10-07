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
 * Follow-up yang menunggu jawaban di utas [moduleId] — dihitung **dari data** riwayat (pertanyaan di pesan QUESTION
 * yang belum berjawaban), bukan dari model. Utas Semua mencakup follow-up semua utas; utas modul hanya miliknya.
 * Dipanggil setiap kali riwayat dimuat (K3), jadi hasilnya selalu mengikuti keadaan terkini.
 */
fun List<ChatMessage>.pendingFollowUps(moduleId: String?): List<PendingFollowUp> =
    inThread(moduleId)
        .filter { it.kind == ChatMessageKind.QUESTION }
        .flatMap { m -> m.questions.filter { it.answer.isNullOrBlank() }.map { PendingFollowUp(m.id, m.moduleId, it) } }
