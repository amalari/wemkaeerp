package com.eventverse.app.domain.discovery

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
        draft.screens.forEachIndexed { i, s ->
            if (s.moduleId.value !in moduleIds) issues += DiscoveryValidationIssue(
                "$.screens[$i].moduleId",
                "Modul '${s.moduleId.value}' tidak ada di pack ${draft.pack.code.value}"
            )
            if (WidgetKind.fromCode(s.widget) == null) issues += DiscoveryValidationIssue(
                "$.screens[$i].widget",
                "Widget '${s.widget}' bukan kosakata tertutup: ${WidgetKind.entries.joinToString { it.code }}"
            )
        }
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
