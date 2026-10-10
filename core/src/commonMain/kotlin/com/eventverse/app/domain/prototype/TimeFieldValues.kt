package com.eventverse.app.domain.prototype

import kotlinx.datetime.LocalTime

/**
 * Aturan tunggal nilai field [FieldType.TIME] (C6, PLAN-field-component-gaps). Satu tempat murni supaya
 * `FieldSpec.accepts`, validator usulan (`ProposalEntityRules`), dan `ProposalEdit` tidak bisa berbeda —
 * pola [DateFieldValues].
 *
 * Tanda tangan simpan:
 * - `JJ:MM` 24 jam tepat menit (mis. `09:30`), **waktu dinding tanpa zona** dan tanpa tanggal; kolom SQL
 *   `TIME`. Tanggal+jam sekaligus bukan tipe ini — pakai [FieldType.DATE] dengan `withTime` (kolom
 *   `TIMESTAMP`, lihat [DateFieldValues]).
 * - Kosong = belum diisi (gerbang `FieldSpec.accepts`/validator menyaring kosong sebelum memanggil di sini);
 *   bentuk lain (`9:30`, `24:00`, `09:60`, `09:30:15`) **ditolak** — tanpa koersi, tanpa fallback senyap.
 *
 * File ini sengaja terpisah dari [EntitySpec] supaya aturan nilai tidak menambah panjang file kosakata
 * (batas core 250, pola [MultiSelectValues]).
 */
object TimeFieldValues {
    private const val TIME_LENGTH = 5
    private const val COLON_INDEX = 2

    /**
     * Bentuk `JJ:MM` dicek lokal (panjang 5, ':' di indeks 2) supaya bentuk rusak tidak pernah sampai ke
     * parser (`09:30:15` memang sah bagi ISO tetapi bukan bentuk simpan kita); keabsahan angka jam/menit
     * dipegang **tunggal** oleh `LocalTime.parse` (satu pustaka) — delegasi, bukan salinan aturan.
     */
    fun isValid(value: String): Boolean {
        if (value.length != TIME_LENGTH || value[COLON_INDEX] != ':') return false
        return runCatching { LocalTime.parse(value) }.isSuccess
    }

    /** Contoh sah untuk seed/penggantian nilai. */
    fun sample(): String = "09:30"
}
