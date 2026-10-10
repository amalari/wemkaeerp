package com.eventverse.app.presentation.orgchart

import com.eventverse.app.infrastructure.api.OrgChartRestoreException

/** Tingkat keparahan pesan toast Org Chart; menentukan warna banner. */
internal enum class OrgChartToastSeverity { SUCCESS, WARNING, ERROR }

/**
 * Satu-satunya pemetaan galat -> teks untuk Org Chart (T1 poles). Dua tugas:
 *
 *  1. [friendly]: galat transport (proxy dev, koneksi ditolak, gateway 502/503/504) tidak boleh bocor
 *     sebagai teks mentah ("Error occurred while trying to proxy ...") ke pengguna.
 *  2. [severityOf]: toast hanya membawa teks, jadi tingkat keparahan dibaca dari awalan pesan. Awalan itu
 *     dipasang di sini dan di titik penulisan pesan galat; pesan lain dianggap sukses.
 *
 * Teks sengaja ASCII/Latin-1 (font Nunito tidak punya glyph di luar itu).
 */
internal object OrgChartErrorMessages {
    const val UNREACHABLE = "Server tidak dapat dihubungi. Periksa koneksi Anda lalu coba lagi."
    private const val NOT_AUTHORIZED = "Anda tidak berwenang memuat contoh struktur organisasi."

    private val noiseMarkers = listOf(
        "proxy", "econnrefused", "connection refused", "failed to fetch", "networkerror",
        "unable to resolve host", "unknownhost", "connect timed out", "timed out", "connectexception"
    )
    private val gatewayStatus = Regex("""HTTP 50[234]\b""")
    private val errorPrefixes = listOf("Gagal", "Error", "Tidak terhubung", "Anda tidak berwenang", "Server tidak dapat")

    /** True bila [text] tampak seperti galat transport/proxy, bukan pesan bisnis dari server. */
    fun isTransportNoise(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        return noiseMarkers.any { it in lower } || gatewayStatus.containsMatchIn(text)
    }

    private fun chainIsNoise(cause: Throwable?): Boolean {
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

    /** Toast gagal "Muat/Pulihkan contoh": 403 galat, 409 peringatan (bukan sukses), selain itu galat. */
    fun restoreFailure(cause: Throwable): String {
        val restore = cause as? OrgChartRestoreException
        if (restore == null) {
            return if (chainIsNoise(cause)) UNREACHABLE else "Gagal memuat contoh struktur organisasi: ${friendly(cause)}"
        }
        if (restore.status in 502..504 || isTransportNoise(restore.serverMessage)) return UNREACHABLE
        val server = restore.serverMessage.takeIf { it.isNotBlank() }
        return when (restore.status) {
            403 -> server?.let { "Gagal memuat contoh: $it" } ?: NOT_AUTHORIZED
            409 -> "Peringatan: ${server ?: "Jenis usaha ini tidak menyediakan contoh struktur organisasi."}"
            else -> "Gagal memuat contoh struktur organisasi: ${server ?: "HTTP ${restore.status}"}"
        }
    }

    fun severityOf(message: String): OrgChartToastSeverity = when {
        errorPrefixes.any { message.startsWith(it) } -> OrgChartToastSeverity.ERROR
        message.startsWith("Peringatan") || message.contains("tidak boleh kosong") -> OrgChartToastSeverity.WARNING
        else -> OrgChartToastSeverity.SUCCESS
    }
}
