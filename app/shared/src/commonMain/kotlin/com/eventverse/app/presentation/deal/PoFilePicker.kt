package com.eventverse.app.presentation.deal

/**
 * Berkas PO yang dipilih pengguna dari platform file picker.
 */
data class PickedPoFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    /** Nomor PO awal yang disarankan — diturunkan dari nama berkas. */
    val suggestedPoNumber: String
)

/**
 * Platform file picker untuk berkas PO (PDF/gambar). Null berarti platform tidak
 * mendukung picker (atau pengguna membatalkan) — UI lalu menawarkan input manual.
 */
expect suspend fun pickPoFile(): PickedPoFile?

/** Membuka [url] (presigned download) di browser/aplikasi platform. */
expect fun openInBrowser(url: String)
