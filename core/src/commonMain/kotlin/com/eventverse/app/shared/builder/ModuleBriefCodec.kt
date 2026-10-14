package com.eventverse.app.shared.builder

import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Bentuk JSON bagian BRIEF pada pesan chat (TRD-BLDR-001 §4) — **satu parser** untuk kolom DB (`features`, `stages`),
 * `GET /api/builder/chat`, dan klien. Dokumen rusak atau nilai tak dikenal = galat keras, bukan kosong/default senyap
 * (tenant-variability Kontrak 4).
 *
 * ```
 * "features": ["Lampirkan foto"]
 * "stages":   [{"name":"Intake"},{"name":"Revisi","optional":false,"reworkTo":"Intake"}]
 * "briefSource": "AI" | "DETERMINISTIC"
 * ```
 */
object ModuleBriefCodec {

    fun encodeFeatures(features: List<String>): JsonValue.Arr = jsonArrayOf(features.map(::jsonOf))

    fun decodeFeatures(raw: String): List<String> = decodeFeatures(JsonParser.parse(raw))

    fun decodeFeatures(json: JsonValue): List<String> {
        val arr = json as? JsonValue.Arr ?: error("features bukan array")
        return arr.items.mapIndexed { i, v -> (v as? JsonValue.Str)?.value ?: error("features[$i] bukan teks") }
    }

    fun encodeStages(stages: List<StageSketch>): JsonValue.Arr = jsonArrayOf(stages.map { s ->
        val base = jsonObjectOf("name" to jsonOf(s.name), "optional" to jsonOf(s.optional))
        s.reworkTo?.let { JsonValue.Obj(base.entries + ("reworkTo" to jsonOf(it))) } ?: base
    })

    fun decodeStages(raw: String): List<StageSketch> = decodeStages(JsonParser.parse(raw))

    fun decodeStages(json: JsonValue): List<StageSketch> {
        val arr = json as? JsonValue.Arr ?: error("stages bukan array")
        return arr.items.mapIndexed { i, item ->
            val o = item as? JsonValue.Obj ?: error("stages[$i] bukan objek")
            val unknown = o.entries.keys - KNOWN_STAGE_KEYS
            require(unknown.isEmpty()) { "stages[$i] memuat kunci tak dikenal: ${unknown.joinToString()}" }
            StageSketch(
                name = (o["name"] as? JsonValue.Str)?.value?.takeIf { it.isNotBlank() } ?: error("stages[$i].name wajib"),
                optional = when (val v = o["optional"]) {
                    null -> false
                    is JsonValue.Bool -> v.value
                    else -> error("stages[$i].optional bukan boolean")
                },
                reworkTo = when (val v = o["reworkTo"]) {
                    null, is JsonValue.Null -> null
                    is JsonValue.Str -> v.value
                    else -> error("stages[$i].reworkTo bukan teks")
                }
            )
        }
    }

    fun encodeSource(source: BriefSource): JsonValue = jsonOf(source.name)

    /** Parser tunggal [BriefSource]: nama tak dikenal ditolak. */
    fun parseSource(name: String): BriefSource =
        BriefSource.entries.firstOrNull { it.name == name } ?: error("briefSource tak dikenal: '$name'")

    private val KNOWN_STAGE_KEYS = setOf("name", "optional", "reworkTo")
}
