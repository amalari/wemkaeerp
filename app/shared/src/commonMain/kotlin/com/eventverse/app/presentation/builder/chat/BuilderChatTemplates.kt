package com.eventverse.app.presentation.builder.chat

/**
 * Pesan pembuka untuk alur wizard template (`/builder/chat?template=<kode>`).
 *
 * Murni & deterministik — supaya bisa diuji tanpa Compose maupun browser. Kode yang **tidak
 * dikenal** menghasilkan `null`: tidak ada fallback senyap ke teks generik yang menyamar sebagai
 * template (`tenant-variability-rules.md` Kontrak 4). Pemanggil (efek di `BuilderChatPane`)
 * melewati giliran alih-alih mengirim pesan yang keliru.
 */
internal fun templateSeedPrompt(code: String): String? = when (code) {
    "wizard" -> "Saya ingin membuat pack baru dengan metode Template-based. Bantui saya memilih template garment yang sesuai."
    "fob" -> "Saya ingin membuat pack untuk konveksi FOB (Full Operation). Pabrik punya divisi Cutting, Sewing, Finishing, dan QC. Bantui saya configure modulnya."
    "cmt" -> "Saya ingin membuat pack untuk konveksi CMT (Cut, Make, Trim). Bahan dari buyer. Bantui saya."
    "sablon" -> "Saya ingin membuat pack untuk bisnis sablon garment. Bantui saya setup modulnya."
    "bordir" -> "Saya ingin membuat pack untuk bisnis bordir embroidery. Bantui saya."
    "rajut" -> "Saya ingin membuat pack untuk bisnis rajut knitting. Bantui saya."
    "custom" -> "Saya ingin membuat pack custom dari awal. Tanpa template. Bantui saya."
    else -> null
}
