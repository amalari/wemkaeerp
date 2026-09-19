package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel

/** Selisih antara yang masuk karung dan yang keluar dari bundel, untuk satu size. */
data class SizeReconciliation(
    val sizeLabel: String,
    val orderedPcs: Int,
    val bundledSets: Int,
    val consumedSets: Int,
    val sackPcs: Int,
    val leftoverPanels: List<PanelTally>
) {
    /**
     * Susut: set yang keluar dari bundel tapi tidak sampai ke karung.
     *
     * Positif berarti barang hilang di antara linking dan penyetoran — itulah angka yang selama ini
     * tidak pernah bisa dilihat siapa pun. Negatif berarti karung berisi lebih banyak daripada yang
     * tercatat masuk, yang hampir selalu berarti ada bundel yang lupa di-scan; dibiarkan negatif dan
     * tidak dijepit ke nol, justru supaya kesalahan pencatatan itu ikut terlihat.
     */
    val shrinkagePcs: Int get() = consumedSets - sackPcs

    val pendingSets: Int get() = (bundledSets - consumedSets).coerceAtLeast(0)

    val hasUnexplainedGap: Boolean get() = shrinkagePcs != 0
}

/** Rekapitulasi satu SPK. */
data class TraceReconciliation(
    val workOrder: TraceWorkOrderRef,
    val spkNumber: String,
    val perSize: List<SizeReconciliation>
) {
    val totalOrderedPcs: Int get() = perSize.sumOf { it.orderedPcs }
    val totalSackPcs: Int get() = perSize.sumOf { it.sackPcs }
    val totalShrinkagePcs: Int get() = perSize.sumOf { it.shrinkagePcs }

    /** Barang yang sudah jadi bundel tapi belum masuk karung — telemetri WIP Kontrak 6. */
    val wipPieces: Int get() = perSize.sumOf { it.pendingSets }

    val sizesWithGap: List<String> get() = perSize.filter { it.hasUnexplainedGap }.map { it.sizeLabel }

    companion object {
        fun build(
            snapshot: TraceWorkOrderSnapshot,
            containers: List<TraceContainer>,
            links: List<TraceContainerLink>
        ): TraceReconciliation {
            val consumedByBundle = links.groupBy { it.childId }
                .mapValues { (_, rows) -> rows.sumOf { it.consumedPcs } }

            val perSize = snapshot.sizes.map { line ->
                val ofSize = containers.filter { it.sizeLabel.equals(line.sizeLabel, ignoreCase = true) }
                val bundles = ofSize.filter { it.isBundle }
                val sacks = ofSize.filter { it.isSack && it.state == TraceContainerState.CLOSED }

                SizeReconciliation(
                    sizeLabel = line.sizeLabel,
                    orderedPcs = line.orderedPcs,
                    bundledSets = bundles.sumOf { it.completeSets(snapshot.panelRequirements) },
                    consumedSets = bundles.sumOf { consumedByBundle[it.id] ?: 0 },
                    sackPcs = sacks.sumOf { it.declaredPcs },
                    leftoverPanels = bundles
                        .flatMap { it.leftoverPanels(snapshot.panelRequirements) }
                        .groupBy { it.panel }
                        .map { (panel: GarmentPanel, rows) -> PanelTally(panel, rows.sumOf { it.pieces }) }
                        .filter { it.pieces > 0 }
                )
            }
            return TraceReconciliation(snapshot.ref, snapshot.spkNumber, perSize)
        }
    }
}
