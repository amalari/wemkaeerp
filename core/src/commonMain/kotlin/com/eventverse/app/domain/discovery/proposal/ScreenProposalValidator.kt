package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.DataBinding

/** Satu pelanggaran dengan **path** ke bagian proposal yang salah (`$.entity.fields[2].key`). */
data class ProposalIssue(val path: String, val message: String)

/** Pengumpul galat berpath bersama aturan-aturan validator; path anak dirakit dari [root]. */
internal class IssueSink(private val root: String, private val packKeys: Boolean = false) {
    val issues = mutableListOf<ProposalIssue>()

    fun add(sub: String, message: String) { issues += ProposalIssue("$root$sub", message) }

    /** Teks wajib terisi (bila [required]) dan tidak melebihi [ProposalLimits.TEXT]. */
    fun text(sub: String, value: String, name: String, required: Boolean = true) {
        if (required && value.isBlank()) add(sub, "$name wajib diisi")
        if (value.length > ProposalLimits.TEXT) add(sub, "$name terlalu panjang (${value.length} karakter; maksimum ${ProposalLimits.TEXT})")
    }

    fun key(sub: String, value: String, name: String) {
        if (packKeys) {
            if (!ProposalLimits.PACK_KEY.matches(value)) add(sub, "$name '$value' tidak sah: tidak boleh kosong atau berspasi di tepi, maksimum 41 karakter")
        } else if (!ProposalLimits.KEY.matches(value)) {
            add(sub, "$name '$value' tidak sah: huruf kecil awal, lalu huruf kecil/angka/garis bawah, maksimum 41 karakter")
        }
    }
}

/**
 * **Satu-satunya** validator usulan layar, untuk semua pembuat (manusia di pack, agent deterministik,
 * agent LLM). Keluaran LLM tidak dipercaya: usulan yang melanggar **ditolak** dengan galat berpath dan
 * dikembalikan untuk koreksi — tidak disaring, tidak diperbaiki diam-diam (tenant-variability Kontrak 4).
 *
 * Aturan (plan §2.2): kosakata tertutup (dijaga tipe `enum` + codec), koherensi entitas↔tampilan, batas
 * ukuran ([ProposalLimits]), seed sesuai skema. Kemurnian vertikal dan konsistensi lintas-layar menyusul
 * (butir B4) — keduanya butuh konteks pack/daftar layar yang belum ada di satu usulan.
 *
 * Pesan galat ditulis dalam bahasa yang bisa dipahami LLM dan manusia: menyebut apa yang salah dan apa
 * yang diharapkan.
 */
object ScreenProposalValidator {

    /**
     * @param path awalan path galat; default `$` (usulan berdiri sendiri), draf memakai `$.screens[i].proposal`.
     * @param source asal usulan bila diketahui; hanya [ProposalSource.Pack] yang boleh memilih `DataBinding.Api`.
     * @param packModuleIds modul pack bila diketahui; ubin dasbor yang menghitung modul lain wajib menunjuk modul ini.
     * @param verticalPurity true untuk pack non-garment: istilah konveksi di teks usulan ditolak ([VerticalPurity]).
     */
    fun validate(
        proposal: ScreenProposal,
        path: String = "$",
        source: ProposalSource? = null,
        packModuleIds: Set<String>? = null,
        verticalPurity: Boolean = false
    ): List<ProposalIssue> {
        val sink = IssueSink(path, packKeys = source == ProposalSource.Pack)
        sink.text(".screenId", proposal.screenId, "screenId")
        sink.text(".title", proposal.title, "title")
        sink.text(".rationale", proposal.rationale, "rationale")

        checkEntityPresence(proposal, sink)
        proposal.entity?.let { ProposalEntityRules.check(it, sink, packModuleIds) }
        ProposalViewRules.check(proposal, sink, packModuleIds)
        ProposalEntityRules.checkSeed(proposal, sink)
        if (verticalPurity) ProposalPurityRules.check(proposal, sink)

        if (proposal.binding is DataBinding.Api && source != null && source != ProposalSource.Pack) {
            sink.add(".binding", "Hanya usulan dari pack yang boleh terikat ke API; usulan ${sourceName(source)} wajib memakai binding memori")
        }
        return sink.issues
    }

    /**
     * Seluruh usulan satu dokumen sekaligus: tiap usulan lewat [validate] (path `<[basePath]>[i]`), lalu aturan
     * lintas-layar ([CrossScreenRules]: `screenId` unik, `entity.id` sama berdefinisi konsisten).
     */
    fun validateAll(
        proposals: List<ScreenProposal>,
        basePath: String = "$.proposals",
        source: ProposalSource? = null,
        packModuleIds: Set<String>? = null,
        verticalPurity: Boolean = false
    ): List<ProposalIssue> {
        val located = proposals.mapIndexed { i, p -> "$basePath[$i]" to p }
        return located.flatMap { (path, p) -> validate(p, path, source, packModuleIds, verticalPurity) } + CrossScreenRules.check(located)
    }

    private fun checkEntityPresence(p: ScreenProposal, sink: IssueSink) {
        val hasEntity = p.entity != null
        when (p.widget) {
            WidgetKind.KANBAN, WidgetKind.TABLE, WidgetKind.FORM, WidgetKind.CHECKLIST ->
                if (!hasEntity) sink.add(".entity", "Widget ${p.widget.code} wajib punya entity (jenis data yang dikelola)")
            WidgetKind.DASHBOARD, WidgetKind.CUSTOM_SCREEN ->
                if (hasEntity) sink.add(".entity", "Widget ${p.widget.code} tidak punya entity sendiri; isi null")
            WidgetKind.PRINT -> Unit
        }
    }

    private fun sourceName(source: ProposalSource): String = when (source) {
        ProposalSource.Pack -> "pack"
        ProposalSource.Deterministic -> "deterministik"
        is ProposalSource.Agent -> "agent"
    }
}
