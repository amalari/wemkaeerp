package com.eventverse.app.presentation.crm.components

import com.eventverse.app.domain.customfield.FieldType
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

    /**
     * Tanggal **dengan waktu**: `ClayDatePicker` belum mendukung waktu, jadi tetap kolom teks seperti sebelumnya
     * (tidak dipalsukan menjadi tanggal saja).
     */
    DATE_TIME_TEXT,
    SINGLE_SELECT,
    USER_REF,

    /**
     * Rujukan record lain (C7, TRD-FIELD-001). Kontrol pemilih (`ClayRelationPicker`) menyusul di
     * Track C — untuk sementara varian ini tidak punya editor aktif (render baca-saja), tidak
     * dipalsukan jadi kolom teks.
     */
    RELATION,

    /**
     * Berkas terunggah (C8, TRD-FIELD-002). Nilai sel = key `fields/...` (byte di ObjectStorage).
     * Kontrol unggah (`ClayFileField`) menyusul di Track C — dilarang dipalsukan jadi kolom teks.
     */
    FILE
}

internal fun leadFieldControl(type: FieldType): LeadFieldControl = when (type) {
    is FieldType.Text -> LeadFieldControl.TEXT
    is FieldType.LongText -> LeadFieldControl.LONG_TEXT
    is FieldType.Number -> LeadFieldControl.NUMBER
    is FieldType.Checkbox -> LeadFieldControl.CHECKBOX
    is FieldType.DateField -> if (type.withTime) LeadFieldControl.DATE_TIME_TEXT else LeadFieldControl.DATE_PICKER
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
