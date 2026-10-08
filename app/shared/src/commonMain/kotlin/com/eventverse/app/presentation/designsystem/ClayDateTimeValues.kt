package com.eventverse.app.presentation.designsystem

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Fungsi murni nilai tanggal-jam untuk [ClayDateTimePicker]. Buta domain, common Kotlin saja.
 *
 * Bentuk simpan: `TTTT-BB-HH'T'JJ:MM` (mis. `2026-10-08T14:30`) — waktu dinding tanpa zona, tepat menit, tanpa detik
 * dan tanpa zona. Aturannya sengaja sama ketatnya dengan aturan core (`DateFieldValues`); kesetaraannya dijaga tes
 * paritas di sisi `discovery`, bukan dengan mengimpor domain ke design system.
 */
private const val DATE_TIME_LENGTH = 16
private const val SEPARATOR_INDEX = 10
internal const val HOURS_PER_DAY = 24
internal const val MINUTES_PER_HOUR = 60

internal fun parseIsoDateTimeOrNull(value: String): LocalDateTime? {
    if (value.length != DATE_TIME_LENGTH || value[SEPARATOR_INDEX] != 'T') return null
    return runCatching { LocalDateTime.parse(value) }.getOrNull()
}

/** Kosong sah (belum diisi); selain itu harus tepat `TTTT-BB-HHTJJ:MM`. */
internal fun isValidIsoDateTime(value: String): Boolean = value.isEmpty() || parseIsoDateTimeOrNull(value) != null

/** Susun nilai simpan; [hour] 0..23 dan [minute] 0..59 (di luar itu = galat pemanggil). */
internal fun formatIsoDateTime(date: LocalDate, hour: Int, minute: Int): String {
    require(hour in 0 until HOURS_PER_DAY) { "Jam di luar 00-23: $hour" }
    require(minute in 0 until MINUTES_PER_HOUR) { "Menit di luar 00-59: $minute" }
    return "$date" + "T" + formatClock(hour, minute)
}

/** `JJ:MM` dua digit. */
internal fun formatClock(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

/** Geser nilai melingkar dalam 0 until [modulus] (23 + 1 jam = 0; 0 - 1 jam = 23). */
internal fun stepCyclic(current: Int, delta: Int, modulus: Int): Int = ((current + delta) % modulus + modulus) % modulus

/** Teks tampil: `TTTT-BB-HH JJ:MM` untuk nilai sah; nilai lain dikembalikan apa adanya (jangan disembunyikan). */
fun displayIsoDateTime(value: String): String =
    if (parseIsoDateTimeOrNull(value) == null) value
    else value.substring(0, SEPARATOR_INDEX) + " " + value.substring(SEPARATOR_INDEX + 1)
