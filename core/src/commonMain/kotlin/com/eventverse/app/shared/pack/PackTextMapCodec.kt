package com.eventverse.app.shared.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kawat peta teks pack dua bahasa — `vocabulary` (kunci = [VocabularyKey]) dan `portLabels` (kunci = kode port) —
 * serta klaim `languages` (TRD-I18N-002 P5). Nilai memakai [LocalizedTextCodec]: id-saja ditulis string biasa
 * (byte-identik dengan format lama), ≥2 bahasa ditulis objek. Kunci absen = pack tak menyebutnya (bukan galat);
 * kunci/bahasa tak dikenal **ditolak** berpath, tidak dibuang diam-diam (Kontrak 4).
 */
internal object PackTextMapCodec {

    fun encodeVocabulary(map: Map<VocabularyKey, LocalizedText>): JsonValue.Obj = encode(map.mapKeys { it.key.name })

    fun encodePortLabels(map: Map<String, LocalizedText>): JsonValue.Obj = encode(map)

    private fun encode(map: Map<String, LocalizedText>): JsonValue.Obj =
        jsonObjectOf(*map.map { (k, v) -> k to LocalizedTextCodec.encode(v) }.toTypedArray())

    fun decodeVocabulary(raw: JsonValue?, at: String): Map<VocabularyKey, LocalizedText> =
        decode(raw, at).mapKeys { (key, _) ->
            VocabularyKey.entries.firstOrNull { it.name == key }
                ?: throw DomainPackDecodeException("$at.$key", "'$key' bukan istilah yang dikenal (${VocabularyKey.entries.map { it.name }})")
        }

    /** Kunci kode port tidak diverifikasi di sini — invarian `DomainPack.init` yang menolaknya. */
    fun decodePortLabels(raw: JsonValue?, at: String): Map<String, LocalizedText> = decode(raw, at)

    private fun decode(raw: JsonValue?, at: String): Map<String, LocalizedText> = when (raw) {
        null, JsonValue.Null -> emptyMap()
        is JsonValue.Obj -> raw.entries.mapValues { (k, v) -> LocalizedTextCodec.decode(v, "$at.$k") }
        else -> throw DomainPackDecodeException(at, "harus objek")
    }

    fun encodeLanguages(languages: PackLanguages): JsonValue.Arr =
        jsonArrayOf(languages.languages.map { it.code }.sorted().map { jsonOf(it) })

    /** Absen/null = pack lama, hanya Indonesia. */
    fun decodeLanguages(raw: JsonValue?, at: String): PackLanguages = when (raw) {
        null, JsonValue.Null -> PackLanguages.ID_ONLY
        is JsonValue.Arr -> {
            val codes = raw.items.mapIndexed { i, t ->
                val text = (t as? JsonValue.Str)?.value ?: throw DomainPackDecodeException("$at[$i]", "harus string")
                LanguageCode.parse(text) ?: throw DomainPackDecodeException("$at[$i]", "bahasa '$text' tidak dikenal (id, en)")
            }.toSet()
            if (LanguageCode.ID !in codes) throw DomainPackDecodeException(at, "wajib memuat 'id'")
            PackLanguages(codes)
        }
        else -> throw DomainPackDecodeException(at, "harus array")
    }
}
