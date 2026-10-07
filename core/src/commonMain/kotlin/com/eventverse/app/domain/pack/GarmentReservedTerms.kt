package com.eventverse.app.domain.pack

/**
 * Kosakata **cadangan** pack garment: istilah yang hanya bermakna di konveksi dan tidak boleh merembes ke usulan
 * pack lain (PLAN-iv-B B5). Dulu daftar kode di `VerticalPurity`; kini data pack (`DomainPack.reservedTerms`),
 * tiap pack bisa menyatakan miliknya sendiri. Bukan istilah tekstil umum — "sablon", "bordir", "kain", "tekstil",
 * "potong" sengaja tidak ada (keputusan produk 2026-10-07: sablon/bordir tidak punya pack baku).
 */
object GarmentReservedTerms {
    val terms: List<String> = listOf(
        "konveksi", "penjahit", "jahit", "garmen", "garment", "buyer", "makloon", "maklon", "pcs", "hoodie", "rajut",
        "po", "spk", "bom", "hpp", "fob", "cmt", "qc"
    )
}
