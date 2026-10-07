package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.rbac.ModuleKind

/**
 * Jenis celah pada layar sebuah modul (PLAN-builder-interview-chat Fase C). Enum sah (Uji Variabilitas): ini mekanik
 * platform — *macam kekurangan* sebuah spesifikasi layar — bukan kosakata industri; teks tanyanya dirakit dari label
 * modul/entitas milik tenant (data).
 */
enum class ModuleGapKind { NO_SCREEN, FEW_FIELDS, NO_REQUIRED, NO_STATUS_FLOW, GENERIC_SOURCE }

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

    const val MIN_FIELDS = 4

    fun analyze(draft: DiscoveryDraft, moduleId: String, limit: Int = 3): List<ModuleGap> {
        val module = draft.pack.modules.firstOrNull { it.id.value == moduleId } ?: return emptyList()
        if (module.kind != ModuleKind.OPERATIONAL) return emptyList()
        val name = module.displayName
        val screens = draft.screens.filter { it.moduleId.value == moduleId }
        val withProposal = screens.filter { it.proposal != null }
        if (withProposal.isEmpty()) {
            return listOf(ModuleGap(ModuleGapKind.NO_SCREEN, "gap:no_screen:$moduleId",
                "Apa saja yang perlu dicatat di $name? Sebutkan isian utamanya."))
        }
        return withProposal.flatMap { s -> gapsOf(s.proposal!!, s.source, name) }.take(limit)
    }

    private fun gapsOf(p: ScreenProposal, source: ProposalSource?, moduleName: String): List<ModuleGap> = buildList {
        val e = p.entity
        val what = e?.label ?: p.title
        if (e != null && e.fields.size < MIN_FIELDS) add(ModuleGap(ModuleGapKind.FEW_FIELDS, "gap:few_fields:${p.screenId}",
            "Isian $what baru ${e.fields.size}. Apa lagi yang perlu dicatat?"))
        if (e != null && e.fields.isNotEmpty() && e.fields.none { it.required }) add(ModuleGap(ModuleGapKind.NO_REQUIRED, "gap:no_required:${p.screenId}",
            "Dari isian $what, mana yang wajib diisi?"))
        if (e?.statusField != null && p.widget == WidgetKind.KANBAN && e.transitions.isEmpty()) add(ModuleGap(ModuleGapKind.NO_STATUS_FLOW, "gap:no_status_flow:${p.screenId}",
            "Status $what boleh berpindah ke mana saja, atau ada urutan tertentu?"))
        if (source == ProposalSource.Deterministic) add(ModuleGap(ModuleGapKind.GENERIC_SOURCE, "gap:generic:${p.screenId}",
            "Isian $moduleName masih standar. Adakah yang perlu disesuaikan dengan cara kerja Anda?"))
    }.sortedBy { it.kind.ordinal }

    /** Celah yang belum pernah ditanyakan di utas: kode yang sudah muncul sebagai id pertanyaan dilewati. */
    fun unasked(gaps: List<ModuleGap>, thread: List<ChatMessage>): List<ModuleGap> {
        val asked = thread.filter { it.kind == ChatMessageKind.QUESTION }.flatMap { m -> m.questions.map { it.id } }.toSet()
        return gaps.filter { it.code !in asked }
    }
}
