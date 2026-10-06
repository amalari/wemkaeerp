package com.eventverse.app.domain.discovery.proposal

/**
 * Penjaga **kemurnian vertikal**: usulan untuk pack non-garment tidak boleh memuat kosakata konveksi. Keluaran
 * yang bocor **ditolak**, tidak disaring (tenant-variability Kontrak 4: tidak ada perbaikan senyap) — LLM yang
 * menyalin contoh garment harus tahu dan mengoreksinya.
 *
 * **Bukan** istilah tekstil umum: "sablon", "bordir", "kain", "tekstil", "potong" sengaja tidak masuk daftar
 * (keputusan produk 2026-10-07 — sablon/bordir tidak punya pack baku, packnya bergantung modul yang dihasilkan dari
 * alur pengguna, jadi usaha sablon yang menyebut "kain" bukan kebocoran). Ke depan daftar ini sebaiknya menjadi
 * **data pack** (kosakata cadangan per pack), bukan kode — lihat PLAN-discovery-interview §3.
 *
 * Daftar ini adalah kosakata **milik pack garment** yang dijaga agar tidak merembes; ia tidak dipakai untuk
 * memutuskan perilaku apa pun, hanya untuk menolak. Pencocokan per kata utuh (tak peka huruf) supaya "polimer"
 * tidak terkena "po".
 */
object VerticalPurity {
    private val LONG_TERMS = listOf(
        "konveksi", "penjahit", "jahit", "garmen", "garment", "buyer", "makloon", "maklon", "pcs", "hoodie", "rajut"
    )
    private val SHORT_TERMS = listOf("po", "spk", "bom", "hpp", "fob", "cmt", "qc")
    private val PATTERN = Regex("(?<![\\p{L}\\p{N}])(${(LONG_TERMS + SHORT_TERMS).joinToString("|")})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    /** Istilah konveksi pertama yang ditemukan di [text], atau null bila bersih. */
    fun leak(text: String): String? = PATTERN.find(text)?.value
}
