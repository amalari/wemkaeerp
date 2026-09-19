package com.eventverse.app.domain.traceability.print

import com.eventverse.app.domain.traceability.TraceCode

/**
 * Satu baris spek panel di lembar kerja: apa yang harus dirajut, seberat apa, selama apa.
 *
 * [inheritedFrom] dicetak apa adanya di lembar. Operator berhak tahu bahwa target 138 gram untuk size
 * XL sebetulnya salinan dari size L dan belum pernah ditimbang — angka warisan yang tampak sama
 * meyakinkannya dengan angka hasil timbang adalah cara paling halus untuk menyesatkan lantai produksi.
 */
data class WorksheetPanelRow(
    val panelLabel: String,
    val weightGrams: Double,
    val minutes: Int,
    val program: String,
    val inheritedFrom: String? = null
) {
    val isInherited: Boolean get() = inheritedFrom != null
}

/** Satu titik ukur dari buyer, lengkap dengan toleransinya. */
data class WorksheetMeasurementRow(
    val pomName: String,
    val finishedCm: Double?,
    val rawKnitCm: Double?,
    val toleranceCm: Double
)

/**
 * Satu halaman lembar kerja — satu ukuran.
 *
 * Dipecah per ukuran karena satu mesin mengerjakan satu ukuran dalam satu waktu, dan lembar yang
 * memuat semua ukuran memaksa operator mencari barisnya sendiri di tengah shift.
 */
data class KnitWorksheetPage(
    val sizeLabel: String,
    val orderedPcs: Int,
    val code: TraceCode,
    val panelRows: List<WorksheetPanelRow>,
    val measurementRows: List<WorksheetMeasurementRow>,
    val feederNotes: List<String> = emptyList(),
    val colorway: String = ""
) {
    val totalWeightGrams: Double get() = panelRows.sumOf { it.weightGrams }
    val totalMinutes: Int get() = panelRows.sumOf { it.minutes }
    val hasInheritedNumbers: Boolean get() = panelRows.any { it.isInherited }
}

/**
 * Lembar kerja rajut lengkap untuk satu SPK.
 *
 * Ini yang diminta dicetak sejak awal: QR, spek per bagian setelah di-breakdown, dan spek dari buyer —
 * bertiga di satu kertas, di tangan orang yang menjalankan mesinnya.
 */
data class KnitWorksheet(
    val spkNumber: String,
    val styleName: String,
    val clientName: String,
    val pages: List<KnitWorksheetPage>
) {
    val pageCount: Int get() = pages.size
}
