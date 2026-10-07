package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.interview.InterviewLimits

/** Satu pelanggaran dengan path ke bagian dokumen pack yang salah. */
data class PackIssue(val path: String, val message: String)

/**
 * Aturan rujukan modul bersama (B6). Fungsi murni; galat berpath dan berpesan supaya bisa dipahami LLM.
 *
 * - **R1** hanya modul yang **ditawarkan** (`sharedModules` pack bawaan) yang bisa dirujuk; modul tata kelola/fondasi
 *   tetap lewat salinan identik.
 * - **R2** `portMapping` lengkap: semua port masuk dan keluar slot modul terpetakan.
 * - **R3** kunci = port pack, nilai = port slot modul; satu-satu.
 * - **R4** tidak sekaligus didefinisikan di `pack.modules`, tidak dirujuk dua kali; [ModuleReference.label] terisi.
 * - **R5** pack bawaan platform tidak boleh merujuk.
 */
object ModuleReferenceRules {

    /** Modul bersama yang ditawarkan platform beserta slot-nya, atau null bila [id] bukan penawaran. */
    fun offered(id: ModuleId): Pair<ModuleDefinition, SlotDefinition>? =
        DomainPackRegistry.shipped.firstNotNullOfOrNull { p ->
            if (id !in p.sharedModules) null
            else p.module(id)?.let { m -> m.slot?.let(p::slot)?.let { m to it } }
        }

    fun validate(pack: DomainPack): List<PackIssue> {
        val out = mutableListOf<PackIssue>()
        if (pack.moduleReferences.isNotEmpty() && DomainPackRegistry.isShipped(pack.code))
            out += PackIssue("$.pack.moduleReferences", "Pack bawaan platform tidak boleh merujuk modul lain; dokumennya wajib identik")
        val seen = mutableSetOf<ModuleId>()
        pack.moduleReferences.forEachIndexed { i, ref ->
            val at = "$.pack.moduleReferences[$i]"
            val offer = offered(ref.platformModuleId)
            if (!seen.add(ref.platformModuleId)) out += PackIssue("$at.platformModuleId", "Modul '${ref.platformModuleId.value}' dirujuk dua kali")
            if (ref.label.isBlank() || ref.label.length > InterviewLimits.TEXT)
                out += PackIssue("$at.label", "Label modul di pack ini wajib terisi dan maksimum ${InterviewLimits.TEXT} karakter")
            when {
                offer == null -> out += PackIssue("$at.platformModuleId",
                    "Modul '${ref.platformModuleId.value}' bukan modul bersama yang ditawarkan platform; hanya modul bersama berslot yang bisa dirujuk (tata kelola: salin identik)")
                pack.module(ref.platformModuleId) != null -> out += PackIssue("$at.platformModuleId",
                    "Modul '${ref.platformModuleId.value}' sudah didefinisikan di pack; pilih salah satu: definisi sendiri atau rujukan")
                else -> portIssues(pack, ref, offer.second, at, out)
            }
        }
        return out
    }

    private fun portIssues(pack: DomainPack, ref: ModuleReference, slot: SlotDefinition, at: String, out: MutableList<PackIssue>) {
        val needed = setOf(slot.defaultInput, slot.defaultOutput)
        val list = needed.joinToString { it.value }
        ref.portMapping.forEach { (own, theirs) ->
            if (own !in pack.portTypes) out += PackIssue("$at.portMapping.${own.value}", "Port '${own.value}' tidak ada di kosakata pack ${pack.code.value}")
            if (theirs !in needed) out += PackIssue("$at.portMapping.${own.value}", "Port platform '${theirs.value}' bukan port modul ini; port modul: $list")
        }
        (needed - ref.portMapping.values.toSet()).forEach {
            out += PackIssue("$at.portMapping", "Port platform '${it.value}' belum dipetakan; petakan semua port modul ($list)")
        }
        if (ref.portMapping.values.toSet().size != ref.portMapping.size)
            out += PackIssue("$at.portMapping", "Dua port pack tidak boleh dipetakan ke port platform yang sama")
    }
}

/** Definisi modul untuk pack ini: miliknya sendiri, atau modul bersama yang dirujuk (definisi dari platform). */
fun DomainPack.resolveModule(id: ModuleId): ModuleDefinition? =
    module(id) ?: moduleReferences.firstOrNull { it.platformModuleId == id }?.let { ModuleReferenceRules.offered(id)?.first }

/** Nama modul untuk pengguna pack ini: label pack bagi modul rujukan, nama definisinya bagi modul sendiri. */
fun DomainPack.moduleLabel(id: ModuleId): String? =
    moduleReferences.firstOrNull { it.platformModuleId == id && module(id) == null }?.label ?: module(id)?.displayName

fun DomainPack.isReferenced(id: ModuleId): Boolean = module(id) == null && moduleReferences.any { it.platformModuleId == id }

/** Port masuk dan keluar slot modul **dalam kosakata pack** (modul rujukan: lewat `portMapping` terbalik); null bila tak berslot. */
fun DomainPack.slotPorts(id: ModuleId): Pair<PortType, PortType>? {
    module(id)?.slot?.let { s -> slot(s)?.let { return it.defaultInput to it.defaultOutput } }
    val ref = moduleReferences.firstOrNull { it.platformModuleId == id } ?: return null
    val slot = ModuleReferenceRules.offered(id)?.second ?: return null
    val inverse = ref.portMapping.entries.associate { it.value to it.key }
    return (inverse[slot.defaultInput] ?: return null) to (inverse[slot.defaultOutput] ?: return null)
}
