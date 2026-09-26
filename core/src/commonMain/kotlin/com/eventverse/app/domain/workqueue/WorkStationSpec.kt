package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.ModuleArchetype

/**
 * Spesifikasi kontrak stasiun kerja di lantai produksi.
 *
 * Stasiun diperlakukan sebagai baris data (bukan enum kaku), sehingga tiap tenant atau
 * jenis konveksi dapat menambah stasiun kustom tanpa merusak core engine.
 */
data class WorkStationSpec(
    val code: WorkStationCode,
    val displayName: String,
    val archetype: ModuleArchetype,
    val inputTrackingUnit: WorkTrackingUnit,
    val outputTrackingUnit: WorkTrackingUnit,
    val piecerateTariffIdr: Long = 0L,
    val standardMinutesPerPiece: Double = 0.0,
    val isMergeGate: Boolean = false,
    val isBatchStation: Boolean = false,
    /**
     * Jangkar penyisipan untuk stasiun tambahan: stasiun ini diposisikan SETELAH stasiun
     * dengan kode jangkar (bukan di-append di ujung barisan). `null` pada stasiun kustom
     * berarti perilaku lama (append di akhir). Diabaikan untuk stasiun bawaan.
     */
    val insertAfterCode: WorkStationCode? = null,
    val description: String = ""
) {
    init {
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(piecerateTariffIdr >= 0L) { "piecerateTariffIdr cannot be negative" }
        require(standardMinutesPerPiece >= 0.0) { "standardMinutesPerPiece cannot be negative" }
        if (isMergeGate) {
            require(inputTrackingUnit == WorkTrackingUnit.BUNDLE && outputTrackingUnit == WorkTrackingUnit.LOT_ACCUMULATION) {
                "Merge gate station must transition from BUNDLE to LOT_ACCUMULATION"
            }
        }
    }
}
