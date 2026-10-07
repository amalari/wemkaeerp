package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.brief.BriefContext
import com.eventverse.app.domain.discovery.brief.BriefDecision
import com.eventverse.app.domain.discovery.brief.BriefOpenQuestion
import com.eventverse.app.domain.discovery.brief.BriefQa

/**
 * Menyusun [BriefContext] dari riwayat chat Builder untuk brief developer (PLAN-builder-interview-chat; serah-terima).
 * Murni. [included] membatasi ke modul yang masuk brief; pesan utas Semua (`moduleId == null`) selalu ikut.
 *
 * - **Cerita**: pesan USER di utas Semua, urut waktu, tanpa balasan yang hanya berfungsi sebagai jawaban pertanyaan.
 * - **Tanya-jawab**: pertanyaan follow-up yang sudah berjawaban.
 * - **Keputusan**: pesan AGENT yang patch-nya sudah **diterapkan** (bukan usulan yang dibuang) beserta ringkasannya.
 * - **Belum jelas**: follow-up yang masih menunggu jawaban.
 *
 * Mengembalikan `null` bila tidak ada konteks sama sekali (brief tetap sama seperti sebelum fitur ini).
 */
fun briefContextOf(messages: List<ChatMessage>, included: Set<String>, narrativeLimit: Int = NARRATIVE_LIMIT): BriefContext? {
    fun inScope(moduleId: String?) = moduleId == null || moduleId in included
    val scoped = messages.filter { inScope(it.moduleId) }
    val answers = scoped.filter { it.kind == ChatMessageKind.QUESTION }
        .flatMap { m -> m.questions.mapNotNull { it.answer?.trim()?.takeIf(String::isNotEmpty) } }.toSet()
    val narrative = scoped
        .filter { it.role == ChatRole.USER && it.moduleId == null && it.text.trim() !in answers }
        .joinToString("\n\n") { it.text.trim() }
        .let { if (it.length <= narrativeLimit) it else it.take(narrativeLimit) + "..." }
        .ifBlank { null }
    val answered = scoped.filter { it.kind == ChatMessageKind.QUESTION }
        .flatMap { m -> m.questions.filter { !it.answer.isNullOrBlank() }.map { BriefQa(m.moduleId, it.question, it.answer.orEmpty().trim()) } }
    val decisions = scoped.filter { it.role == ChatRole.AGENT && it.appliedDraftId != null }
        .map { BriefDecision(it.moduleId, it.createdAt?.toString(), it.proposedSummary) }
    val open = scoped.pendingFollowUps(null).map { BriefOpenQuestion(it.moduleId, it.question.question) }
    return BriefContext(narrative, answered, decisions, open).takeUnless { it.isEmpty }
}

private const val NARRATIVE_LIMIT = 4000
