package com.eventverse.app.infrastructure.pdf

/**
 * Konversi satuan kanvas (1/10 mm) ke titik PDF.
 *
 * Diangkat ke sini karena kini ada tiga renderer yang memakainya — invoice, kartu telusur, dan lembar
 * kerja rajut. Menyalinnya ke tiap renderer berarti suatu saat salah satunya akan diperbaiki sendirian.
 */
object PdfUnits {
    const val MM10_TO_PT: Float = 72f / 254f

    fun toPoints(mm10: Int): Float = mm10 * MM10_TO_PT
}
