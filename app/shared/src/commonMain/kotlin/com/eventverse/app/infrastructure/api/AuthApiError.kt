package com.eventverse.app.infrastructure.api

/**
 * Galat bertipe dari panggilan login. Pemanggil (ViewModel) wajib membedakan dua keluarga yang
 * artinya berlawanan: **server menjawab dan menolak** ([Rejected]) vs **server tidak terjangkau**
 * ([Unreachable]). Menyamakannya (semua jadi "gagal") adalah akar fallback senyap yang dulu membuat
 * sesi offline palsu setelah 403/404 dari gerbang login demo.
 *
 * Kelas, bukan `sealed interface`, karena harus turunan `Throwable` agar muat di `Result`.
 */
sealed class AuthApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Server menjawab dengan status bukan 2xx. [message] sudah berupa teks siap tampil. */
    class Rejected(val status: Int, val serverMessage: String, message: String) : AuthApiError(message)

    /** Tidak ada jawaban HTTP sama sekali (koneksi ditolak, DNS, timeout, proxy dev mati). */
    class Unreachable(cause: Throwable) : AuthApiError("Server tidak dapat dihubungi", cause)

    /** Server menjawab 2xx tetapi isinya bukan sesi yang dapat dibaca. */
    class Malformed(message: String) : AuthApiError(message)
}
