package com.eventverse.app.presentation.crm.components

import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.presentation.designsystem.parseIsoDateTimeOrNull
import com.eventverse.app.shared.common.DateTimeCodec

/**
 * Jenis kontrol yang dipakai CRM untuk mengedit satu [FieldType] di inspektur lead.
 *
 * Pemeta murni (tanpa Compose) supaya "tiap tipe punya kontrol" bisa dites tanpa rendering. `when` di
 * [leadFieldControl] **tanpa `else`**: varian `FieldType` baru gagal kompilasi di sini dan di
 * `LeadCustomField` (field-component-rules Kontrak 6).
 */
internal enum class LeadFieldControl {
    TEXT,
    /** `ClayTextArea` / isian teks multi-baris (FieldType.LongText). */
    LONG_TEXT,
    NUMBER,
    CHECKBOX,

    /** `ClayDatePicker` — nilai `TTTT-BB-HH` atau kosong. */
    DATE_PICKER,

    /** `ClayDateTimePicker` — nilai `TTTT-BB-HH'T'JJ:MM` atau kosong (C6, Irisan 2). */
    DATE_TIME_PICKER,
    SINGLE_SELECT,
    USER_REF,

    /**
     * Rujukan record lain (C7, TRD-FIELD-001). Dirender `ClayRelationPicker` di
     * `LeadCustomField` saat editable (opsi dari route `relation-options`); baca-saja menampilkan
     * label/fallback id. Tidak pernah dipalsukan jadi kolom teks.
     */
    RELATION,

    /**
     * Berkas terunggah (C8, TRD-FIELD-002). Nilai sel = key `fields/...` (byte di ObjectStorage).
     * Dirender `ClayFileField` di `LeadCustomField` — unggah/ganti/hapus bila [LeadFieldFileActions]
     * tersedia (lead sudah ada); tidak pernah dipalsukan jadi kolom teks.
     */
    FILE
}

internal fun leadFieldControl(type: FieldType): LeadFieldControl = when (type) {
    is FieldType.Text -> LeadFieldControl.TEXT
    is FieldType.LongText -> LeadFieldControl.LONG_TEXT
    is FieldType.Number -> LeadFieldControl.NUMBER
    is FieldType.Checkbox -> LeadFieldControl.CHECKBOX
    is FieldType.DateField -> if (type.withTime) LeadFieldControl.DATE_TIME_PICKER else LeadFieldControl.DATE_PICKER
    is FieldType.SingleSelect -> LeadFieldControl.SINGLE_SELECT
    is FieldType.UserRef -> LeadFieldControl.USER_REF
    is FieldType.Relation -> LeadFieldControl.RELATION
    is FieldType.File -> LeadFieldControl.FILE
}

/**
 * Nilai field tanggal sah untuk disimpan/dikirim: kosong berarti *belum diisi* (sah untuk field
 * opsional), selain itu harus tanggal kalender ISO `YYYY-MM-DD` yang nyata — bukan sekadar bentuk.
 * Parsing lewat `DateTimeCodec` yang aman di Wasm/JS (tanpa boxing `Result`).
 */
internal fun isBlankOrIsoDate(value: String): Boolean =
    value.isBlank() || DateTimeCodec.parseLocalDateOrNull(value) != null

/**
 * Nilai field tanggal **berwaktu** sah untuk disimpan/dikirim: kosong berarti *belum diisi*, selain itu
 * harus `TTTT-BB-HH'T'JJ:MM`. Parser yang sama dengan `ClayDateTimePicker` (satu aturan simpan, C6);
 * nilai lama `TTTT-BB-HH` tidak sah di sini dan ditolak, bukan didiamkan.
 */
internal fun isBlankOrIsoDateTime(value: String): Boolean =
    value.isBlank() || parseIsoDateTimeOrNull(value) != null
