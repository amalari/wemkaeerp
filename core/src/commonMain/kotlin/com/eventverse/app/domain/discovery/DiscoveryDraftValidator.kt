package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.discovery.proposal.CrossScreenRules
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry

/** Satu pelanggaran dengan **path** ke bagian dokumen yang salah (`$.pack.modules[2].id`). */
data class DiscoveryValidationIssue(val path: String, val message: String)

/**
 * Validator draf discovery (plan §2 A2, T7): gabungan tiga sumber aturan yang sudah ada, **tanpa** menduplikasi
 * salah satunya —
 *
 * 1. invarian struktur `DomainPack` / `Blueprint` / `DiscoveryDraft` (sudah melempar saat konstruksi);
 * 2. identitas global [DomainPackRegistry.violations] (prefiks `<kode pack>_`, id bersama wajib identik) —
 *    pesannya dipetakan ke path berdasar id yang disebut, supaya agent AI tahu *bagian mana* yang dikoreksi;
 * 3. blueprint hanya menyebut modul pack (ditonton `DiscoveryDraft.init`, dilaporkan di sini dengan path).
 *
 * Galat **berpath** inilah yang dikembalikan ke agent untuk koreksi diri; draf yang gagal tidak pernah tersimpan.
 */
object DiscoveryDraftValidator {

    fun validate(draft: DiscoveryDraft): List<DiscoveryValidationIssue> {
        val issues = mutableListOf<DiscoveryValidationIssue>()

        val shipped = DomainPackRegistry.shipped.firstOrNull { it.code == draft.pack.code }
        issues += when {
            shipped == null -> DomainPackRegistry.violations(draft.pack).map { it.toPathedIssue(draft) }
            // Pack bawaan platform (garment) tidak boleh "draft" ulang dengan isi berbeda: draf garment
            // sah hanya bila dokumennya identik dengan pack yang dikirim.
            shipped != draft.pack -> listOf(
                DiscoveryValidationIssue(
                    "$.pack",
                    "Kode ${draft.pack.code.value} milik pack bawaan platform; dokumen wajib identik, bukan ditulis ulang"
                )
            )
            else -> emptyList()
        }

        val moduleIds = draft.pack.modules.map { it.id.value }.toSet()
        draft.blueprint.modules.forEachIndexed { i, m ->
            if (m.moduleCode !in moduleIds) issues += DiscoveryValidationIssue(
                "$.blueprint.modules[$i].moduleCode",
                "Modul '${m.moduleCode}' tidak ada di pack ${draft.pack.code.value}"
            )
        }
        if (draft.screens.size > ProposalLimits.SCREENS) issues += DiscoveryValidationIssue(
            "$.screens", "Terlalu banyak layar (${draft.screens.size}); maksimum ${ProposalLimits.SCREENS}. Pilih layar untuk modul utama saja"
        )
        // Murni = pack non-garment: kosakata konveksi di layar usulannya ditolak (B4).
        val purity = draft.pack.code != GarmentDomainPack.CODE
        draft.screens.forEachIndexed { i, s ->
            if (s.moduleId.value !in moduleIds) issues += DiscoveryValidationIssue(
                "$.screens[$i].moduleId",
                "Modul '${s.moduleId.value}' tidak ada di pack ${draft.pack.code.value}"
            )
            if (WidgetKind.fromCode(s.widget) == null) issues += DiscoveryValidationIssue(
                "$.screens[$i].widget",
                "Widget '${s.widget}' bukan kosakata tertutup: ${WidgetKind.entries.joinToString { it.code }}"
            )
            issues += proposalIssues(i, s, moduleIds, purity)
        }
        // Konsistensi lintas-layar: screenId unik (semua layar) dan entity.id sama berdefinisi konsisten (yang berproposal).
        val ids = mutableMapOf<String, Int>()
        draft.screens.forEachIndexed { i, s ->
            ids[s.screenId]?.let { first ->
                issues += DiscoveryValidationIssue("$.screens[$i].screenId", "screenId '${s.screenId}' sudah dipakai di $.screens[$first]; setiap layar wajib punya screenId unik")
            } ?: run { ids[s.screenId] = i }
        }
        issues += CrossScreenRules.check(
            draft.screens.mapIndexedNotNull { i, s -> s.proposal?.let { "$.screens[$i].proposal" to it } },
            screenIds = false
        ).map { DiscoveryValidationIssue(it.path, it.message) }
        return issues
    }

    /**
     * Usulan isi layar: identitas harus sama dengan deskriptor layarnya (satu kebenaran), asal wajib ada, lalu
     * seluruh aturan [ScreenProposalValidator] dengan path `$.screens[i].proposal…`. Satu validator untuk
     * semua pembuat — draf tidak punya aturan usulan sendiri.
     */
    private fun proposalIssues(i: Int, s: PrototypeScreen, moduleIds: Set<String>, purity: Boolean): List<DiscoveryValidationIssue> {
        val p = s.proposal ?: return if (s.source != null) {
            listOf(DiscoveryValidationIssue("$.screens[$i].source", "source hanya bermakna bila layar punya proposal"))
        } else emptyList()
        val at = "$.screens[$i].proposal"
        val issues = mutableListOf<DiscoveryValidationIssue>()
        if (p.screenId != s.screenId) issues += DiscoveryValidationIssue("$at.screenId", "Harus sama dengan screenId layar '${s.screenId}', dapat '${p.screenId}'")
        if (p.moduleId != s.moduleId) issues += DiscoveryValidationIssue("$at.moduleId", "Harus sama dengan moduleId layar '${s.moduleId.value}', dapat '${p.moduleId.value}'")
        if (p.widget.code != s.widget) issues += DiscoveryValidationIssue("$at.widget", "Harus sama dengan widget layar '${s.widget}', dapat '${p.widget.code}'")
        if (s.source == null) issues += DiscoveryValidationIssue("$.screens[$i].source", "Layar ber-proposal wajib menyebut source (PACK, DETERMINISTIC, atau AGENT)")
        issues += ScreenProposalValidator.validate(p, at, s.source, moduleIds, purity).map { DiscoveryValidationIssue(it.path, it.message) }
        return issues
    }

    /**
     * Pesan registry dipetakan ke path: "Modul x…" → `$.pack.modules[i].id`, "Slot x…" → `$.pack.slots[i].code`.
     * Registry tetap satu-satunya penulis aturan identitas; di sini hanya penerjemah lokasi.
     */
    private fun String.toPathedIssue(draft: DiscoveryDraft): DiscoveryValidationIssue {
        val moduleId = removePrefix("Modul ").substringBefore(' ')
        val slotCode = removePrefix("Slot ").substringBefore(' ')
        val moduleIndex = draft.pack.modules.indexOfFirst { it.id.value == moduleId }
        if (moduleIndex >= 0) return DiscoveryValidationIssue("$.pack.modules[$moduleIndex].id", this)
        val slotIndex = draft.pack.slots.indexOfFirst { it.code.value == slotCode }
        if (slotIndex >= 0) return DiscoveryValidationIssue("$.pack.slots[$slotIndex].code", this)
        return DiscoveryValidationIssue("$.pack", this)
    }
}
