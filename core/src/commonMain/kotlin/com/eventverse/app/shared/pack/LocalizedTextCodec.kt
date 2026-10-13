package com.eventverse.app.shared.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kawat JSON [LocalizedText] (TRD-I18N-002 F4). **Satu parser** untuk semua tipe pack yang dimigrasi (P1–P6).
 *
 *  - Bentuk **lama** — string biasa `"Potong"` — dibaca sebagai `{id:"Potong"}`. Tanpa migrasi data.
 *  - Bentuk **baru** — objek `{"id":"Potong","en":"Cutting"}`.
 *  - Penulisan: hanya `id` → string biasa (dibaca juga oleh pembaca versi lama); ≥2 bahasa → objek.
 *  - Kode bahasa tak dikenal, nilai bukan string, `id` hilang, atau teks kosong → [DomainPackDecodeException]
 *    dengan path. **Tidak ada** penggantian diam-diam ke `id` (Kontrak 4).
 */
internal object LocalizedTextCodec {

    fun encode(text: LocalizedText): JsonValue =
        if (text.languages.size == 1) jsonOf(text.idText)
        else jsonObjectOf(*text.entries().map { (language, value) -> language.code to jsonOf(value) }.toTypedArray())

    /** @param path path nilai di dokumen pack, mis. `$.phases[2].displayName`. */
    fun decode(value: JsonValue?, path: String): LocalizedText = try {
        when (value) {
            is JsonValue.Str -> LocalizedText.of(id = value.value)
            is JsonValue.Obj -> LocalizedText.fromMap(
                value.entries.entries.associate { (key, item) ->
                    val language = LanguageCode.parse(key)
                        ?: throw DomainPackDecodeException("$path.$key", "bahasa '$key' tidak dikenal (id, en)")
                    val text = (item as? JsonValue.Str)?.value
                        ?: throw DomainPackDecodeException("$path.$key", "wajib string")
                    language to text
                }
            )
            else -> throw DomainPackDecodeException(path, "wajib string atau objek {id, en?}")
        }
    } catch (e: DomainPackDecodeException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw DomainPackDecodeException(path, e.message ?: "teks tidak sah")
    }
}
