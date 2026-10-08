package com.eventverse.app.shared.pack

import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.strictOptString

/**
 * Pembaca kawat bersama untuk parameter field yang sama di `InteractiveScreenCodec` dan `SpecOpCodec`
 * (satu parser, Kontrak 4 variability): kunci absen/`null` = bawaan, nilai tak dikenal **ditolak**, bukan jatuh ke bawaan.
 */
internal object FieldParamWire {

    /** Kunci `validation` (nama [TextValidation]); `email`, `URL`, atau kosong ditolak. */
    fun textValidation(field: JsonValue.Obj, required: Boolean = false): TextValidation {
        val name = field.strictOptString("validation") ?: if (required) throw IllegalArgumentException("Bidang 'validation' wajib diisi.") else return TextValidation.NONE
        return TextValidation.entries.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Validasi teks '$name' bukan kosakata tertutup: ${TextValidation.entries.joinToString { it.name }}")
    }

    /**
     * Kunci `format` ([NumberFormat]). Absen/`null` = PLAIN bila [required] false (dokumen lama); tipe JSON salah
     * (mis. `5`) atau nama tak dikenal ditolak, tidak jatuh ke PLAIN (D4). [required] true = kunci wajib ada.
     */
    fun numberFormat(obj: JsonValue.Obj, required: Boolean = false): NumberFormat = numberFormatName(obj.strictOptString("format"), required)

    fun numberFormatName(name: String?, required: Boolean): NumberFormat = when {
        name == null && !required -> NumberFormat.PLAIN
        else -> NumberFormat.entries.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Format angka '${name.orEmpty()}' bukan kosakata tertutup: ${NumberFormat.entries.joinToString { it.name }}")
    }

    /** Kunci `currencyCode`: string atau `null`; tipe JSON salah ditolak (bentuk kodenya divalidasi `FieldSpec`). */
    fun currencyCode(obj: JsonValue.Obj): String? = obj.strictOptString("currencyCode")
}
