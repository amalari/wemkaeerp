package com.eventverse.app.presentation.common

/** Berkas yang dipilih pengguna lewat file picker platform. */
data class PickedFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && fileName == other.fileName && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
}

/**
 * File picker generik lintas platform.
 *
 * Mengembalikan null bila pengguna membatalkan **atau** platformnya belum mendukung picker —
 * UI wajib memperlakukan keduanya sama: tidak ada berkas, tampilkan jalan keluar lain
 * (impor massal lewat task Gradle) alih-alih menggantung.
 *
 * [extensions] tanpa titik, huruf kecil, mis. `listOf("xlsx")`.
 */
expect suspend fun pickFile(extensions: List<String>): PickedFile?
