package com.eventverse.app.presentation.discovery.fields

import androidx.compose.ui.text.input.KeyboardType
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TextValidation

/** Keyboard untuk field TEXT menurut [TextValidation] (cabang eksplisit; tanpa `else`). */
fun keyboardTypeFor(validation: TextValidation): KeyboardType = when (validation) {
    TextValidation.NONE -> KeyboardType.Text
    TextValidation.EMAIL -> KeyboardType.Email
    TextValidation.PHONE -> KeyboardType.Phone
}

/** Pesan galat singkat per jenis validasi; `null` untuk [TextValidation.NONE]. */
fun validationErrorText(validation: TextValidation): String? = when (validation) {
    TextValidation.NONE -> null
    TextValidation.EMAIL -> "Alamat email tidak sah (contoh: nama@contoh.id)"
    TextValidation.PHONE -> "Nomor telepon tidak sah (8-15 digit, boleh diawali +)"
}

/**
 * Galat tampil untuk [value] pada field TEXT ber-validasi, memakai aturan tunggal `FieldSpec.accepts` (core).
 * Nilai kosong tidak dianggap galat; nilai tidak dinormalisasi. Tipe selain TEXT -> `null`.
 */
fun FieldSpec.validationMessageFor(value: String): String? =
    if (type != FieldType.TEXT || value.isEmpty() || accepts(value)) null else validationErrorText(validation)
