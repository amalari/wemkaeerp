package com.eventverse.app.domain.sampling

import kotlinx.datetime.Instant

/**
 * Gramasi, waktu, dan program mesin untuk satu ukuran tertentu.
 *
 * Sebelum ini, ketiganya hanya ada satu set per SPK — padahal badan depan size XL jelas lebih berat
 * dan lebih lama dirajut daripada size S. Operator mesin yang memegang lembar kerja size XL menerima
 * angka target milik size acuan, lalu menyesuaikannya dari ingatan. Itu yang diperbaiki di sini.
 *
 * [derivedFromSize] bukan sekadar sisa proses penyalinan: ia membedakan angka hasil timbang sungguhan
 * dari angka yang diwarisi. Lembar kerja mencetak bedanya, sehingga tidak ada yang mengira target
 * gramasi size XL sudah pernah diverifikasi padahal ia hanya salinan size L.
 */
data class PanelSizeSpec(
    val sizeLabel: String,
    val weights: PanelWeightGrams = PanelWeightGrams(),
    val minutes: PanelKnittingMinutes = PanelKnittingMinutes(),
    /** null berarti memakai program CAM tingkat SPK — program memang lebih sering seragam daripada gramasi. */
    val programs: MachineProgram? = null,
    val derivedFromSize: String? = null
) {
    init {
        require(sizeLabel.isNotBlank()) { "Spek panel wajib menyebut ukurannya" }
        require(derivedFromSize == null || !derivedFromSize.equals(sizeLabel, ignoreCase = true)) {
            "Spek size $sizeLabel tidak bisa disalin dari dirinya sendiri"
        }
    }

    val isMeasured: Boolean get() = derivedFromSize == null && weights.total > 0.0

    val isInherited: Boolean get() = derivedFromSize != null

    /** Menyalin angka ke ukuran lain sambil meninggalkan jejak asalnya. */
    fun copyTo(targetSize: String): PanelSizeSpec = PanelSizeSpec(
        sizeLabel = targetSize,
        weights = weights,
        minutes = minutes,
        programs = programs,
        derivedFromSize = sizeLabel
    )
}

/**
 * Resolusi berjenjang: spek khusus ukuran ini, kalau tidak ada jatuh ke angka acuan tingkat SPK.
 *
 * Fallback inilah yang membuat penambahan dimensi size tidak merusak apa pun yang sudah berjalan —
 * SPK lama tanpa [YieldAndTiming.perSize] tetap menghasilkan angka yang sama persis seperti dulu,
 * dan modul costing yang membaca field datar tidak perlu tahu fitur ini ada.
 */
fun YieldAndTiming.specFor(sizeLabel: String): PanelSizeSpec =
    perSize.firstOrNull { it.sizeLabel.equals(sizeLabel, ignoreCase = true) }
        ?: PanelSizeSpec(sizeLabel = sizeLabel, weights = panelWeights, minutes = panelMinutes)

/** Menyisipkan atau menimpa spek satu ukuran, menjaga urutan masukan tetap stabil. */
fun YieldAndTiming.withSpec(spec: PanelSizeSpec): YieldAndTiming {
    val existingIndex = perSize.indexOfFirst { it.sizeLabel.equals(spec.sizeLabel, ignoreCase = true) }
    val updated = if (existingIndex >= 0) {
        perSize.toMutableList().also { it[existingIndex] = spec }
    } else {
        perSize + spec
    }
    return copy(perSize = updated)
}

// ── Operasi domain pada SPK ─────────────────────────────────────────────────────────────────
//
// Ditulis sebagai extension, bukan method di dalam `SamplingOrder`, mengikuti pola yang sudah dipakai
// `toApprovedSampleSpecification`. Alasan praktisnya: `SamplingOrder.kt` sudah 433 baris, di atas
// hard limit 400 untuk `core/**`, sehingga Aturan Ratchet melarang menambahnya.

/** Menyimpan gramasi & waktu untuk satu ukuran tertentu, tanpa menyentuh ukuran lain. */
fun SamplingOrder.updatePanelSizeSpec(spec: PanelSizeSpec, updatedAt: Instant): SamplingOrder =
    copy(yieldAndTiming = yieldAndTiming.withSpec(spec), updatedAt = updatedAt)

/**
 * Menyalin spek satu ukuran ke ukuran lain sebagai titik awal.
 *
 * Jalan pintas yang memang diminta lantai: size XL hampir selalu "seperti L tapi sedikit lebih berat",
 * dan mengetik ulang lima angka dari nol lebih rawan salah daripada menyalin lalu menyesuaikan. Jejak
 * asalnya ikut tersimpan supaya angka warisan tidak tertukar dengan angka hasil timbang.
 */
fun SamplingOrder.copyPanelSpecFrom(
    sourceSize: String,
    targetSize: String,
    updatedAt: Instant
): SamplingOrder {
    require(!sourceSize.equals(targetSize, ignoreCase = true)) {
        "Ukuran sumber dan tujuan tidak boleh sama ($sourceSize)"
    }
    return updatePanelSizeSpec(yieldAndTiming.specFor(sourceSize).copyTo(targetSize), updatedAt)
}
