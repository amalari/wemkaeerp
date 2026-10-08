package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.shared.json.JsonValue

/**
 * Pembaca ketat `withTime` (DATE, C6) dan `validation` (TEXT, C9) untuk field hasil penyunting modul. Tenant-variability
 * Kontrak 4 / D4: tipe JSON salah, nama tak dikenal, dan parameter pada tipe yang salah **ditolak** dengan pesan
 * berpath — tidak dibuang diam-diam, tidak jatuh ke bawaan. Galat dilempar sebagai `error(...)`, dibungkus
 * `runCatching` penyunting.
 */
internal object KoogModuleEditorFieldParams {

    data class Reading(val withTime: Boolean, val validation: TextValidation)

    fun read(o: JsonValue.Obj, type: FieldType): Reading = Reading(readWithTime(o, type), readValidation(o, type))

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
}
