package com.eventverse.app.presentation.orgchart

import com.eventverse.app.infrastructure.api.OrgChartRestoreException
import com.eventverse.app.presentation.common.FriendlyErrors

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
    const val UNREACHABLE = FriendlyErrors.UNREACHABLE
    private const val NOT_AUTHORIZED = "Anda tidak berwenang memuat contoh struktur organisasi."

    private val errorPrefixes = listOf("Gagal", "Error", "Tidak terhubung", "Anda tidak berwenang", "Server tidak dapat")

    /** Pemetaan transport kini bersama ([FriendlyErrors]); delegasi ini menjaga pemanggil Org Chart tak berubah. */
    fun isTransportNoise(text: String?): Boolean = FriendlyErrors.isTransportNoise(text)

    private fun chainIsNoise(cause: Throwable?): Boolean = FriendlyErrors.chainIsNoise(cause)

    fun friendly(cause: Throwable?, fallback: String = "Terjadi kesalahan tak terduga."): String =
        FriendlyErrors.friendly(cause, fallback)

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
