package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.rbac.ModuleKind

/**
 * Jenis celah pada layar sebuah modul (PLAN-builder-interview-chat Fase C). Enum sah (Uji Variabilitas): ini mekanik
 * platform — *macam kekurangan* sebuah spesifikasi layar — bukan kosakata industri; teks tanyanya dirakit dari label
 * modul/entitas milik tenant (data).
 */
enum class ModuleGapKind { WHO_FILLS, WHAT_RECORDED, WHO_SEES, DONE_WHEN, REQUIRED_UNEXPLAINED }

/**
 * Satu celah yang layak ditanyakan. [code] stabil per (jenis, layar) — dipakai sebagai `Clarification.id` sehingga celah
 * yang **sudah pernah ditanyakan** tidak ditanyakan ulang saat tab dibuka lagi.
 */
data class ModuleGap(val kind: ModuleGapKind, val code: String, val question: String)

/**
 * Menghitung celah modul **dari data draf** — tanpa model. Model hanya dipakai kemudian untuk menafsirkan jawaban
 * pengguna menjadi sunting isian. Hanya modul operasional yang punya layar; modul governance/foundation tidak ditanya.
 * Urutan hasil = urutan kepentingan (celah terbesar dulu), dan [limit] membatasi agar satu kunjungan tab tidak menjadi
 * interogasi.
 */
object ModuleGapAnalyzer {
    /** Awalan id pertanyaan template ([ModuleGap.code]). Pertanyaan AI memakai [BriefKeys.AI_ID_PREFIX] dan menggantikannya. */
    const val ID_PREFIX = "gap:"

    fun isGapQuestionId(id: String): Boolean = id.startsWith(ID_PREFIX)

    fun analyze(draft: DiscoveryDraft, moduleId: String, limit: Int = 3): List<ModuleGap> {
        val module = draft.pack.modules.firstOrNull { it.id.value == moduleId } ?: return emptyList()
        if (module.kind != ModuleKind.OPERATIONAL) return emptyList()
        val name = module.displayName
        val roleKeys = draft.interview?.links.orEmpty().filter { it.moduleId.value == moduleId }.map { it.roleKey }.toSet()
        val specs = draft.interview?.specs.orEmpty().filter { it.areaKey in roleKeys }
        fun known(value: (com.eventverse.app.domain.discovery.interview.RequirementSpec) -> String?) =
            specs.any { !value(it).isNullOrBlank() }
        val gaps = buildList {
            if (!known { it.whoFills }) add(ModuleGap(ModuleGapKind.WHO_FILLS, "gap:who_fills:$moduleId", "Siapa yang mengisi data di $name?"))
            if (!known { it.whatRecorded }) add(ModuleGap(ModuleGapKind.WHAT_RECORDED, "gap:what_recorded:$moduleId", "Apa saja yang perlu dicatat di $name?"))
            if (!known { it.whoSees }) add(ModuleGap(ModuleGapKind.WHO_SEES, "gap:who_sees:$moduleId", "Siapa yang boleh melihat data $name?"))
            if (!known { it.doneWhen }) add(ModuleGap(ModuleGapKind.DONE_WHEN, "gap:done_when:$moduleId", "Kapan pekerjaan di $name dianggap selesai?"))
            val recorded = specs.mapNotNull { it.whatRecorded }.joinToString(" ").lowercase()
            draft.screens.filter { it.moduleId.value == moduleId }.mapNotNull { it.proposal?.entity }.flatMap { it.fields }
                .filter { it.required && it.label.lowercase() !in recorded && it.key.lowercase() !in recorded }
                .forEach { field ->
                    add(ModuleGap(ModuleGapKind.REQUIRED_UNEXPLAINED, "gap:required:$moduleId:${field.key}",
                        "Mengapa isian ${field.label} wajib di $name?"))
                }
        }
        return gaps.take(limit)
    }

    /** Celah yang belum pernah ditanyakan di utas: kode yang sudah muncul sebagai id pertanyaan dilewati. */
    fun unasked(gaps: List<ModuleGap>, thread: List<ChatMessage>): List<ModuleGap> {
        val asked = thread.filter { it.kind == ChatMessageKind.QUESTION }.flatMap { m -> m.questions.map { it.id } }.toSet()
        return gaps.filter { it.code !in asked }
    }
}
