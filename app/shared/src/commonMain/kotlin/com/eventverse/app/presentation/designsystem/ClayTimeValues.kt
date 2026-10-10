package com.eventverse.app.presentation.designsystem

/**
 * Fungsi murni nilai waktu untuk [ClayTimePicker]. Buta domain, common Kotlin saja — bisa diuji tanpa Compose.
 *
 * Bentuk nilai: `JJ:MM` 24 jam, dua digit per bagian (mis. `14:30`), atau string kosong bila belum diisi.
 * Aturannya konsisten dengan bagian jam dari `ClayDateTimeValues` (`formatClock`, `HOURS_PER_DAY`); kesetaraan itu
 * dijaga tes paritas `parity_withDateTimeClockPart`, bukan dengan berbagi state.
 *
 * Tanpa fallback senyap: input tidak sah dibiarkan tidak sah (parser mengembalikan `null`), dan pemanggil
 * yang menampilkan apa adanya — tidak pernah dikonversi diam-diam ke nilai default.
 */

/** Waktu murni presisi menit hasil parsing `JJ:MM`. */
internal data class ClayTime(val hour: Int, val minute: Int)

private const val TIME_LENGTH = 5
private const val COLON_INDEX = 2

internal fun parseClayTimeOrNull(value: String): ClayTime? {
    if (value.length != TIME_LENGTH || value[COLON_INDEX] != ':') return null
    val hour = value.twoDigitsAt(0) ?: return null
    val minute = value.twoDigitsAt(3) ?: return null
    if (hour >= HOURS_PER_DAY || minute >= MINUTES_PER_HOUR) return null
    return ClayTime(hour, minute)
}

/** Kosong sah (belum diisi); selain itu harus tepat `JJ:MM` dengan jam 00-23 dan menit 00-59. */
internal fun isValidClayTime(value: String): Boolean = value.isEmpty() || parseClayTimeOrNull(value) != null

/** Susun nilai simpan; [hour] 0..23 dan [minute] 0..59 (di luar itu = galat pemanggil). */
internal fun formatClayTime(hour: Int, minute: Int): String {
    require(hour in 0 until HOURS_PER_DAY) { "Jam di luar 00-23: $hour" }
    require(minute in 0 until MINUTES_PER_HOUR) { "Menit di luar 00-59: $minute" }
    return formatClock(hour, minute)
}

/** Dua karakter digit mulai [start] sebagai angka dua digit; salah satu bukan digit → `null`. */
private fun String.twoDigitsAt(start: Int): Int? {
    val first = this[start] - '0'
    val second = this[start + 1] - '0'
    if (first !in 0..9 || second !in 0..9) return null
    return first * 10 + second
}
