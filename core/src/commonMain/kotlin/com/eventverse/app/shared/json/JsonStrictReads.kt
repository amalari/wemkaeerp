package com.eventverse.app.shared.json

/**
 * Pembacaan **ketat** untuk kunci opsional berparameter (A0 Irisan 2): kunci absen/`null` = [default], tetapi nilai
 * bertipe salah **ditolak** — berbeda dari [JsonValue.Obj.boolean]/[JsonValue.Obj.string] yang mengembalikan `null`
 * untuk tipe salah sehingga parameter diam-diam jatuh ke bawaan (Kontrak 4: tolak, bukan fallback senyap).
 */
fun JsonValue.Obj.strictBoolean(key: String, default: Boolean): Boolean = when (val v = this[key]) {
    null, JsonValue.Null -> default
    is JsonValue.Bool -> v.value
    else -> throw IllegalArgumentException("Bidang '$key' harus boolean (true/false).")
}

/** Lihat [strictBoolean]; untuk kunci string opsional (`null` bila absen). */
fun JsonValue.Obj.strictOptString(key: String): String? = when (val v = this[key]) {
    null, JsonValue.Null -> null
    is JsonValue.Str -> v.value
    else -> throw IllegalArgumentException("Bidang '$key' harus string.")
}

/** Lihat [strictBoolean]; untuk kunci bilangan bulat opsional (`null` bila absen; tipe/pecahan salah ditolak). */
fun JsonValue.Obj.strictOptInt(key: String): Int? = when (val v = this[key]) {
    null, JsonValue.Null -> null
    is JsonValue.Num -> v.asInt ?: throw IllegalArgumentException("Bidang '$key' harus bilangan bulat.")
    else -> throw IllegalArgumentException("Bidang '$key' harus bilangan bulat.")
}

/** Boolean **wajib**: kunci absen/`null` atau bertipe salah ditolak (untuk operasi suntingan yang tak bermakna tanpa nilainya). */
fun JsonValue.Obj.strictRequiredBoolean(key: String): Boolean = when (val v = this[key]) {
    is JsonValue.Bool -> v.value
    else -> throw IllegalArgumentException("Bidang '$key' wajib diisi dengan boolean (true/false).")
}
