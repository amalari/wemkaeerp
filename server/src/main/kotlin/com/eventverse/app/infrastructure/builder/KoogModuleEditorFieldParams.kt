package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.shared.json.JsonValue

/**
 * Pembaca ketat `withTime` (DATE, C6), `validation` (TEXT, C9), dan `maxSelections` (MULTI_SELECT, TRD-FIELD-003)
 * untuk field hasil penyunting modul. Tenant-variability Kontrak 4 / D4: tipe JSON salah, nama tak dikenal, dan
 * parameter pada tipe yang salah **ditolak** dengan pesan berpath — tidak dibuang diam-diam, tidak jatuh ke bawaan.
 * Galat dilempar sebagai `error(...)`, dibungkus `runCatching` penyunting.
 */
internal object KoogModuleEditorFieldParams {

    data class Reading(val withTime: Boolean, val validation: TextValidation, val maxSelections: Int?)

    fun read(o: JsonValue.Obj, type: FieldType, options: List<String>): Reading =
        Reading(readWithTime(o, type), readValidation(o, type), readMaxSelections(o, type, options))

    private fun readWithTime(o: JsonValue.Obj, type: FieldType): Boolean {
        val withTime = when (val raw = o["withTime"]) {
            null, is JsonValue.Null -> false
            is JsonValue.Bool -> raw.value
            else -> error("field.withTime harus boolean (true atau false)")
        }
        if (withTime && type != FieldType.DATE) error("field.withTime hanya untuk type DATE, bukan ${type.name}")
        return withTime
    }

    private fun readValidation(o: JsonValue.Obj, type: FieldType): TextValidation {
        val names = TextValidation.entries.joinToString { it.name }
        val validation = when (val raw = o["validation"]) {
            null, is JsonValue.Null -> TextValidation.NONE
            is JsonValue.Str -> TextValidation.entries.firstOrNull { it.name == raw.value.uppercase() }
                ?: error("field.validation '${raw.value}' tidak dikenal; wajib salah satu $names")
            else -> error("field.validation harus teks, salah satu $names")
        }
        if (validation != TextValidation.NONE && type != FieldType.TEXT) {
            error("field.validation ${validation.name} hanya untuk type TEXT, bukan ${type.name}")
        }
        return validation
    }

    /**
     * `maxSelections` (TRD-FIELD-003 FR-4): hanya sah untuk `MULTI_SELECT`; bila diisi harus `1..jumlah opsi`.
     * Pecahan (`2.5`), string (`"2"`), atau tipe JSON lain **ditolak** — bukan dipotong diam-diam jadi bilangan bulat.
     */
    private fun readMaxSelections(o: JsonValue.Obj, type: FieldType, options: List<String>): Int? {
        val raw = o["maxSelections"]
        if (raw == null || raw is JsonValue.Null) return null
        val value = (raw as? JsonValue.Num)?.raw?.toIntOrNull()
            ?: error("field.maxSelections harus bilangan bulat atau null")
        if (type != FieldType.MULTI_SELECT) {
            error("field.maxSelections hanya untuk type MULTI_SELECT, bukan ${type.name}")
        }
        if (value !in 1..options.size) {
            error("field.maxSelections harus 1..${options.size} (jumlah opsi MULTI_SELECT), dapat $value")
        }
        return value
    }
}
