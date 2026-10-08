package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.proposal.SkeletonHint

/** Rupa sketsa satu blok kerangka. [NEUTRAL] = kotak berlabel lama (draf tanpa petunjuk). */
enum class SketchShape { NEUTRAL, TABLE, FORM, METRIC_CARDS, ACTIONS }

/** Satu blok kerangka siap gambar; [full] false = separuh lebar. */
data class SketchBlock(val label: String, val shape: SketchShape, val full: Boolean)

/** Teks `Petunjuk` pada baris sampel → [SkeletonHint]; kosong/tak dikenal → null (tanpa crash). */
fun skeletonHintOf(text: String?): SkeletonHint? = when (text?.trim()?.lowercase()) {
    "tabel" -> SkeletonHint.TABLE
    "formulir" -> SkeletonHint.FORM
    "kartu angka" -> SkeletonHint.METRIC_CARDS
    "aksi" -> SkeletonHint.ACTIONS
    else -> null
}

fun sketchShapeOf(hint: SkeletonHint?): SketchShape = when (hint) {
    null -> SketchShape.NEUTRAL
    SkeletonHint.TABLE -> SketchShape.TABLE
    SkeletonHint.FORM -> SketchShape.FORM
    SkeletonHint.METRIC_CARDS -> SketchShape.METRIC_CARDS
    SkeletonHint.ACTIONS -> SketchShape.ACTIONS
}

/**
 * Baris sampel `CUSTOM_SCREEN` → baris tata letak. Blok "separuh" dipasangkan dengan blok separuh
 * berikutnya; separuh ganjil berdiri sendiri (tetap setengah lebar). Blok `penuh` selalu sendiri.
 * Draf lama (hanya `Blok` + `Lebar`, tanpa `Petunjuk`) menghasilkan rupa [SketchShape.NEUTRAL].
 */
fun planSketchRows(rows: List<Map<String, String>>): List<List<SketchBlock>> {
    val blocks = rows.map {
        SketchBlock(
            label = it["Blok"].orEmpty(),
            shape = sketchShapeOf(skeletonHintOf(it["Petunjuk"])),
            full = it["Lebar"] == "penuh"
        )
    }
    val result = mutableListOf<List<SketchBlock>>()
    var i = 0
    while (i < blocks.size) {
        val b = blocks[i]
        val next = blocks.getOrNull(i + 1)?.takeIf { !b.full && !it.full }
        if (next != null) {
            result += listOf(b, next)
            i += 2
        } else {
            result += listOf(b)
            i++
        }
    }
    return result
}
