package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.prototype.CurrencyCode
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.shared.json.JsonValue

/**
 * Pembaca ketat `format` + `currencyCode` untuk field hasil penyunting modul (C4). Tenant-variability Kontrak 4:
 * nilai tak dikenal **ditolak** dengan pesan jelas — tidak jatuh diam-diam ke PLAIN atau IDR. Galat dilempar
 * sebagai `error(...)` lalu dibungkus `runCatching` penyunting, jadi pengguna melihat alasannya.
 */
internal object KoogModuleEditorNumberFormat {

    /** Hasil baca: pasangan `format` dan `currencyCode` yang sudah konsisten dengan [FieldType]. */
    data class Reading(val format: NumberFormat, val currencyCode: String?)

    fun read(o: JsonValue.Obj, type: FieldType): Reading {
        val raw = o["format"]
        val format = when {
            raw == null || raw is JsonValue.Null -> NumberFormat.PLAIN
            raw is JsonValue.Str -> NumberFormat.entries.firstOrNull { it.name == raw.value.uppercase() }
                ?: error("field.format '${raw.value}' tidak dikenal; wajib salah satu ${NumberFormat.entries.joinToString { it.name }}")
            else -> error("field.format harus teks, salah satu ${NumberFormat.entries.joinToString { it.name }}")
        }
        if (format != NumberFormat.PLAIN && type != FieldType.NUMBER) {
            error("field.format ${format.name} hanya untuk type NUMBER, bukan ${type.name}")
        }
        val rawCode = o["currencyCode"]
        val code = when (rawCode) {
            null, is JsonValue.Null -> null
            is JsonValue.Str -> rawCode.value
            else -> error("field.currencyCode harus teks tiga huruf besar (mis. IDR) atau null")
        }
        if (format == NumberFormat.CURRENCY) {
            if (code == null || !CurrencyCode.isValid(code)) {
                error("field.currencyCode wajib untuk format CURRENCY: tiga huruf besar seperti IDR atau USD (diterima: ${code ?: "kosong"})")
            }
        } else if (code != null) {
            error("field.currencyCode hanya untuk format CURRENCY; hilangkan atau isi null")
        }
        return Reading(format, code)
    }
}
