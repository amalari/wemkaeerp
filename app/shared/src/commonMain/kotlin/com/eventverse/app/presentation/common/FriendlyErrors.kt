package com.eventverse.app.presentation.common

/**
 * Pemetaan galat transport -> teks ramah yang dipakai bersama layar (Org Chart, Hak Akses). Satu
 * sumber supaya teks proxy dev / koneksi ditolak / gateway 502-504 tidak pernah bocor mentah ke
 * pengguna (Aturan Tiga Kali: dua layar sudah membutuhkannya). Buta domain: hanya `Throwable` dan `String`.
 *
 * Teks sengaja ASCII/Latin-1 (font Nunito tidak punya glyph di luar itu).
 */
internal object FriendlyErrors {
    const val UNREACHABLE = "Server tidak dapat dihubungi. Periksa koneksi Anda lalu coba lagi."

    private val noiseMarkers = listOf(
        "proxy", "econnrefused", "connection refused", "failed to fetch", "networkerror",
        "unable to resolve host", "unknownhost", "connect timed out", "timed out", "connectexception"
    )
    private val gatewayStatus = Regex("""HTTP 50[234]\b""")

    /** True bila [text] tampak seperti galat transport/proxy, bukan pesan bisnis dari server. */
    fun isTransportNoise(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        return noiseMarkers.any { it in lower } || gatewayStatus.containsMatchIn(text)
    }

    fun chainIsNoise(cause: Throwable?): Boolean {
        var current = cause
        var depth = 0
        while (current != null && depth < 6) {
            if (isTransportNoise(current.message) || isTransportNoise(current::class.simpleName)) return true
            current = current.cause
            depth++
        }
        return false
    }

    /** Teks ramah untuk galat apa pun; [fallback] dipakai bila galat tak membawa pesan. */
    fun friendly(cause: Throwable?, fallback: String = "Terjadi kesalahan tak terduga."): String = when {
        cause == null -> fallback
        chainIsNoise(cause) -> UNREACHABLE
        else -> cause.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
