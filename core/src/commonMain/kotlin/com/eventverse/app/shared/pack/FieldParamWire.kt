package com.eventverse.app.shared.pack

import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.strictOptString

/**
 * Pembaca kawat bersama untuk parameter field yang sama di `InteractiveScreenCodec` dan `SpecOpCodec`
 * (satu parser, Kontrak 4 variability): kunci absen/`null` = bawaan, nilai tak dikenal **ditolak**, bukan jatuh ke bawaan.
 */
internal object FieldParamWire {

    /** Kunci `validation` (nama [TextValidation]); `email`, `URL`, atau kosong ditolak. */
    fun textValidation(field: JsonValue.Obj): TextValidation {
        val name = field.strictOptString("validation") ?: return TextValidation.NONE
        return TextValidation.entries.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Validasi teks '$name' bukan kosakata tertutup: ${TextValidation.entries.joinToString { it.name }}")
    }
}
