package com.eventverse.app.domain.discovery.proposal

/**
 * Penjaga **kemurnian vertikal**: usulan untuk pack non-garment tidak boleh memuat kosakata konveksi. Keluaran
 * yang bocor **ditolak**, tidak disaring (tenant-variability Kontrak 4: tidak ada perbaikan senyap) — LLM yang
 * menyalin contoh garment harus tahu dan mengoreksinya.
 *
 * Daftar ini adalah kosakata **milik pack garment** yang dijaga agar tidak merembes; ia tidak dipakai untuk
 * memutuskan perilaku apa pun, hanya untuk menolak. Pencocokan per kata utuh (tak peka huruf) supaya "polimer"
 * tidak terkena "po".
 */
object VerticalPurity {
    private val LONG_TERMS = listOf(
        "konveksi", "penjahit", "jahit", "kain", "garmen", "garment", "buyer", "tekstil", "sablon", "bordir",
        "makloon", "maklon", "potong", "pcs", "hoodie", "rajut"
    )
    private val SHORT_TERMS = listOf("po", "spk", "bom", "hpp", "fob", "cmt", "qc")
    private val PATTERN = Regex("(?<![\\p{L}\\p{N}])(${(LONG_TERMS + SHORT_TERMS).joinToString("|")})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    /** Istilah konveksi pertama yang ditemukan di [text], atau null bila bersih. */
    fun leak(text: String): String? = PATTERN.find(text)?.value
}
