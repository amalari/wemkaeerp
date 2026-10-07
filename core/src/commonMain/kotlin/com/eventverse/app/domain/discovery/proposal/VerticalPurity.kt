package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.pack.GarmentReservedTerms

/**
 * Penjaga **kemurnian vertikal**: usulan untuk pack non-garment tidak boleh memuat kosakata konveksi. Keluaran
 * yang bocor **ditolak**, tidak disaring (tenant-variability Kontrak 4: tidak ada perbaikan senyap) — LLM yang
 * menyalin contoh garment harus tahu dan mengoreksinya.
 *
 * **Bukan** istilah tekstil umum: "sablon", "bordir", "kain", "tekstil", "potong" sengaja tidak masuk daftar
 * (keputusan produk 2026-10-07 — sablon/bordir tidak punya pack baku, packnya bergantung modul yang dihasilkan dari
 * alur pengguna, jadi usaha sablon yang menyebut "kain" bukan kebocoran). Daftarnya kini **data pack** ([GarmentReservedTerms]); jalur wawancara memakai kosakata cadangan pack lain lewat `leak(text, reserved)`.
 *
 * Daftar ini adalah kosakata **milik pack garment** yang dijaga agar tidak merembes; ia tidak dipakai untuk
 * memutuskan perilaku apa pun, hanya untuk menolak. Pencocokan per kata utuh (tak peka huruf) supaya "polimer"
 * tidak terkena "po".
 */
object VerticalPurity {
    private val GARMENT = Regex(patternOf(GarmentReservedTerms.terms), RegexOption.IGNORE_CASE)

    private fun patternOf(terms: Collection<String>) =
        "(?<![\\p{L}\\p{N}])(${terms.joinToString("|") { Regex.escape(it) }})(?![\\p{L}\\p{N}])"

    /** Istilah konveksi pertama yang ditemukan di [text], atau null bila bersih. */
    fun leak(text: String): String? = GARMENT.find(text)?.value

    /** Istilah pertama di [text] yang termasuk [reserved] (kosakata cadangan pack lain); kosong ⇒ selalu bersih. */
    fun leak(text: String, reserved: Collection<String>): String? =
        if (reserved.isEmpty()) null else Regex(patternOf(reserved), RegexOption.IGNORE_CASE).find(text)?.value
}
