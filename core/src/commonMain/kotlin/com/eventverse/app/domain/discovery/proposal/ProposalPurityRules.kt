package com.eventverse.app.domain.discovery.proposal

/** Memeriksa **semua teks** usulan (judul, alasan, label, pilihan, seed, label ubin/tombol) terhadap [VerticalPurity]. */
internal object ProposalPurityRules {

    fun check(p: ScreenProposal, sink: IssueSink) {
        fun at(sub: String, text: String?) {
            val leak = text?.let(VerticalPurity::leak) ?: return
            sink.add(sub, "Memuat istilah konveksi '$leak' padahal pack ini bukan konveksi; ganti dengan istilah bisnis klien")
        }
        at(".title", p.title)
        at(".rationale", p.rationale)
        p.entity?.let { e ->
            at(".entity.label", e.label)
            e.fields.forEachIndexed { i, f ->
                at(".entity.fields[$i].label", f.label)
                f.options.forEachIndexed { j, o -> at(".entity.fields[$i].options[$j]", o) }
            }
        }
        p.seed.forEachIndexed { i, row -> row.forEach { (k, v) -> at(".seed[$i].$k", v) } }
        when (val v = p.view) {
            is ViewProposal.Form -> at(".view.submitLabel", v.submitLabel)
            is ViewProposal.Kanban -> at(".view.detailFormSubmitLabel", v.detailFormSubmitLabel)
            is ViewProposal.Dashboard -> v.tiles.forEachIndexed { i, t -> at(".view.tiles[$i].label", t.label); at(".view.tiles[$i].value", t.value) }
            is ViewProposal.Skeleton -> v.blocks.forEachIndexed { i, b -> at(".view.blocks[$i].label", b.label) }
            is ViewProposal.Table, is ViewProposal.Checklist, is ViewProposal.Print, ViewProposal.None -> Unit
        }
    }
}
