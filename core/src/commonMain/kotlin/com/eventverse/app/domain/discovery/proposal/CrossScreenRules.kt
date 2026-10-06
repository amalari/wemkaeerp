package com.eventverse.app.domain.discovery.proposal

/**
 * Konsistensi **lintas-layar** (plan §2.2): banyak layar boleh berbagi satu `entity.id` (mis. tabel dan formulirnya),
 * tetapi tidak boleh berselisih tentang apa entitas itu. Definisi pertama menjadi acuan; layar berikutnya boleh
 * memuat **himpunan bagian** atau tambahan field, asalkan field yang sama persis identik (label, tipe, pilihan; `required` **bukan** identitas — formulir boleh mewajibkan lebih banyak daripada tabelnya),
 * serta `statusField` dan `transitions` yang sama bila keduanya menyebutnya. `screenId` wajib unik.
 *
 * [located] = pasangan (path usulan, usulan) agar galat menunjuk layar yang menyimpang dan menyebut layar acuannya.
 */
internal object CrossScreenRules {

    /** [screenIds] false bila pemanggil sudah memeriksa keunikan `screenId` atas himpunan layar yang lebih luas (draf). */
    fun check(located: List<Pair<String, ScreenProposal>>, screenIds: Boolean = true): List<ProposalIssue> {
        val issues = mutableListOf<ProposalIssue>()
        val seenScreens = mutableMapOf<String, String>()
        val reference = mutableMapOf<String, Pair<String, EntityProposal>>()
        located.forEach { (path, p) ->
            if (screenIds) seenScreens[p.screenId]?.let { first ->
                issues += ProposalIssue("$path.screenId", "screenId '${p.screenId}' sudah dipakai di $first; setiap layar wajib punya screenId unik")
            } ?: run { seenScreens[p.screenId] = path }
            val e = p.entity ?: return@forEach
            val (refPath, ref) = reference[e.id] ?: run { reference[e.id] = path to e; return@forEach }
            issues += compare(path, e, refPath, ref)
        }
        return issues
    }

    private fun compare(path: String, e: EntityProposal, refPath: String, ref: EntityProposal): List<ProposalIssue> {
        val issues = mutableListOf<ProposalIssue>()
        val where = "Entity '${e.id}' sudah didefinisikan di $refPath"
        val refFields = ref.fields.associateBy { it.key }
        e.fields.forEachIndexed { i, f ->
            val r = refFields[f.key] ?: return@forEachIndexed
            if (f.copy(required = r.required) != r) issues += ProposalIssue(
                "$path.entity.fields[$i]",
                "$where dengan field '${f.key}' berbeda (${describe(r)}); di sini ${describe(f)}. Samakan definisinya"
            )
        }
        if (e.statusField != null && ref.statusField != null && e.statusField != ref.statusField) issues += ProposalIssue(
            "$path.entity.statusField", "$where dengan statusField '${ref.statusField}', di sini '${e.statusField}'. Samakan"
        )
        if (e.transitions.isNotEmpty() && ref.transitions.isNotEmpty() && e.transitions != ref.transitions) issues += ProposalIssue(
            "$path.entity.transitions", "$where dengan transitions yang berbeda. Samakan"
        )
        return issues
    }

    private fun describe(f: FieldProposal): String =
        "tipe ${f.type.name}, label '${f.label}'${if (f.required) ", wajib" else ""}${if (f.options.isEmpty()) "" else ", pilihan ${f.options.joinToString("/")}"}"
}
