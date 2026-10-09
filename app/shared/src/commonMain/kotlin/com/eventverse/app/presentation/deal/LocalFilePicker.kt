package com.eventverse.app.presentation.deal

/** Berkas lokal yang dipilih pengguna dari picker platform (mis. foto mockup desain). */
class PickedLocalFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * Membuka picker berkas platform dengan filter [accept] (mis. `image/png,image/jpeg`).
 *
 * `null` berarti pengguna membatalkan ATAU platform belum menyediakan picker-nya
 * (Android/iOS menyusul — keduanya butuh wiring Activity/UIDocumentPicker dari lapisan app).
 */
expect suspend fun pickLocalFile(accept: String): PickedLocalFile?

/** Filter MIME untuk foto mockup desain. */
const val MOCKUP_IMAGE_ACCEPT = "image/png,image/jpeg,image/webp"

/**
 * Mengurai data URL (`data:image/png;base64,AAAA…`) menjadi berkas.
 *
 * Picker web sengaja mengembalikan data URL, bukan biner: membaca `File.arrayBuffer()`
 * dari DOM memerlukan interop typed-array yang berbeda antara JS dan Wasm, sedangkan
 * data URL hanyalah `String` — satu bentuk yang jalan di kedua target web.
 *
 * [fileName] = nama asli berkas dari input DOM; dipakai apa adanya bila ada (penting untuk
 * field `FILE` yang menyimpan nama di ref). Bila null/kosong, nama disintesis dari MIME
 * seperti sebelumnya (pemanggil lama mockup tidak berubah perilaku bila picker tak memberi nama).
 */
internal fun decodeDataUrlToPickedFile(dataUrl: String, fileName: String? = null): PickedLocalFile? {
    if (!dataUrl.startsWith("data:")) return null
    val comma = dataUrl.indexOf(',')
    if (comma <= 0) return null
    val header = dataUrl.substring(5, comma)
    if (!header.contains("base64")) return null

    val mime = header.substringBefore(';').takeIf { it.isNotBlank() } ?: "image/png"
    val bytes = decodeBase64(dataUrl.substring(comma + 1)) ?: return null
    if (bytes.isEmpty()) return null

    val extension = when {
        mime.contains("png") -> "png"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("webp") -> "webp"
        else -> "img"
    }
    return PickedLocalFile(
        fileName = fileName?.takeIf { it.isNotBlank() } ?: "design-mockup.$extension",
        mimeType = mime,
        bytes = bytes
    )
}

/**
 * Dekoder base64 minimal tanpa dependency — dipakai picker web dan pemuat foto mockup.
 * Mengembalikan `null` untuk masukan yang bukan base64 valid (bukan exception: pemanggilnya
 * adalah jalur UI yang cukup menampilkan placeholder).
 */
internal fun decodeBase64(input: String): ByteArray? {
    val cleaned = input.filterNot { it.isWhitespace() }
    if (cleaned.isEmpty()) return null

    val output = ByteArray((cleaned.length * 3) / 4)
    var buffer = 0
    var bits = 0
    var index = 0

    for (ch in cleaned) {
        if (ch == '=') break
        val value = when (ch) {
            in 'A'..'Z' -> ch - 'A'
            in 'a'..'z' -> ch - 'a' + 26
            in '0'..'9' -> ch - '0' + 52
            '+' -> 62
            '/' -> 63
            else -> return null
        }
        buffer = (buffer shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            output[index++] = ((buffer shr bits) and 0xFF).toByte()
        }
    }
    return output.copyOf(index)
}
