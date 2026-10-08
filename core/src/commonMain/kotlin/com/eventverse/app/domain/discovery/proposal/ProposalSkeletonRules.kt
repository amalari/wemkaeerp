package com.eventverse.app.domain.discovery.proposal

/**
 * Aturan kerangka `CUSTOM_SCREEN` (Irisan 3b, D5/D6): 1..[ProposalLimits.BLOCKS] blok, label terisi, lolos batas
 * teks, tanpa label kembar. Kemurnian vertikal label diperiksa `ProposalPurityRules`. Kecocokan varian dengan
 * widget (Skeleton hanya untuk CUSTOM_SCREEN) dijaga `ProposalViewRules.matches`; width/hint tak dikenal sudah
 * ditolak codec, jadi di sini tidak ada yang perlu dicek untuk keduanya.
 */
internal object ProposalSkeletonRules {

    fun check(view: ViewProposal.Skeleton, sink: IssueSink) {
        if (view.blocks.isEmpty()) sink.add(".view.blocks", "Kerangka wajib punya minimal 1 blok")
        if (view.blocks.size > ProposalLimits.BLOCKS) {
            sink.add(".view.blocks", "Terlalu banyak blok (${view.blocks.size}); maksimum ${ProposalLimits.BLOCKS}")
        }
        view.blocks.forEachIndexed { i, b -> sink.text(".view.blocks[$i].label", b.label, "Label blok") }
        val labels = view.blocks.map { it.label.trim().lowercase() }.filter { it.isNotEmpty() }
        if (labels.distinct().size != labels.size) sink.add(".view.blocks", "Kerangka memuat label blok kembar")
    }
}
