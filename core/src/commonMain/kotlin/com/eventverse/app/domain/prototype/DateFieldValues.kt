package com.eventverse.app.domain.prototype

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Aturan tunggal nilai field [FieldType.DATE] (A0(C6) Irisan 2). Satu tempat murni supaya `FieldSpec.accepts`,
 * validator usulan, dan `ProposalEdit` tidak bisa berbeda.
 *
 * Tanda tangan simpan:
 * - `withTime = false`: tanggal kalender ISO `TTTT-BB-HH` (`2026-10-08`), kolom SQL `DATE`.
 * - `withTime = true`: `TTTT-BB-HH'T'JJ:MM` (`2026-10-08T14:30`), **waktu dinding tanpa zona waktu**
 *   (`kotlinx.datetime.LocalDateTime`), tepat sampai menit — tanpa detik, tanpa zona; kolom SQL `TIMESTAMP`
 *   (tanpa zona). Nilai tanggal-saja pada field `withTime` ditolak, dan sebaliknya: tidak ada koersi diam-diam.
 */
object DateFieldValues {
    /** Panjang tetap `TTTT-BB-HHTJJ:MM`; menolak detik, pecahan, dan zona. */
    private const val DATE_TIME_LENGTH = 16
    private const val SEPARATOR_INDEX = 10

    fun isValid(value: String, withTime: Boolean): Boolean =
        if (withTime) isValidDateTime(value) else runCatching { LocalDate.parse(value) }.isSuccess

    private fun isValidDateTime(value: String): Boolean =
        value.length == DATE_TIME_LENGTH && value[SEPARATOR_INDEX] == 'T' && runCatching { LocalDateTime.parse(value) }.isSuccess

    /** Contoh sah untuk seed/penggantian nilai. */
    fun sample(withTime: Boolean): String = if (withTime) "2026-01-01T09:00" else "2026-01-01"
}
