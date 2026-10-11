package com.eventverse.app.domain.storage

/**
 * Pemeriksa isi minimum untuk tipe FILE yang diizinkan (TRD-FIELD-004 FR-4.1).
 *
 * Ini bukan antivirus. Tujuannya adalah menolak ketidaksesuaian paling berbahaya antara MIME yang
 * diklaim klien dan byte yang benar-benar disimpan. Tipe biner memakai signature baku; tipe teks
 * harus UTF-8 sah dan tidak boleh memuat byte NUL.
 */
object FileSignature {
    fun matches(contentType: String, bytes: ByteArray): Boolean = when (contentType) {
        "application/pdf" -> bytes.startsWith(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
        "image/png" -> bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        "image/jpeg" -> bytes.startsWith(0xFF, 0xD8, 0xFF)
        "image/webp" -> bytes.size >= 12 &&
            bytes.startsWith(0x52, 0x49, 0x46, 0x46) && // RIFF
            bytes.matchesAt(8, 0x57, 0x45, 0x42, 0x50) // WEBP
        "text/plain", "text/csv" -> bytes.none { it == 0.toByte() } &&
            runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.isSuccess
        else -> false
    }

    private fun ByteArray.startsWith(vararg signature: Int): Boolean = matchesAt(0, *signature)

    private fun ByteArray.matchesAt(offset: Int, vararg signature: Int): Boolean =
        size >= offset + signature.size && signature.indices.all { index ->
            this[offset + index].toInt() and 0xFF == signature[index]
        }
}
