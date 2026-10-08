package com.eventverse.app.presentation.crm.components

import com.eventverse.app.domain.customfield.FieldType

/**
 * Jenis kontrol yang dipakai CRM untuk mengedit satu [FieldType] di inspektur lead.
 *
 * Pemeta murni (tanpa Compose) supaya "tiap tipe punya kontrol" bisa dites tanpa rendering. `when` di
 * [leadFieldControl] **tanpa `else`**: varian `FieldType` baru gagal kompilasi di sini dan di
 * `LeadCustomField` (field-component-rules Kontrak 6).
 */
internal enum class LeadFieldControl {
    TEXT,
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
    USER_REF
}

internal fun leadFieldControl(type: FieldType): LeadFieldControl = when (type) {
    is FieldType.Text, is FieldType.LongText -> LeadFieldControl.TEXT
    is FieldType.Number -> LeadFieldControl.NUMBER
    is FieldType.Checkbox -> LeadFieldControl.CHECKBOX
    is FieldType.DateField -> if (type.withTime) LeadFieldControl.DATE_TIME_TEXT else LeadFieldControl.DATE_PICKER
    is FieldType.SingleSelect -> LeadFieldControl.SINGLE_SELECT
    is FieldType.UserRef -> LeadFieldControl.USER_REF
}
