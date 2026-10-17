package com.eventverse.app.shared.builder

import com.eventverse.app.domain.builder.BriefFeature
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.shared.evidence.EvidenceRefCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Bentuk JSON bagian BRIEF pada pesan chat (TRD-BLDR-001 §4 + TRD-BLDR-003 FR-3/FR-6) — **satu parser** untuk kolom DB
 * (`features`, `stages`), `GET /api/builder/chat`, dan klien. Dokumen rusak atau nilai tak dikenal = galat keras,
 * bukan kosong/default senyap (tenant-variability Kontrak 4).
 *
 * Versi kontrak dibedakan **dari bentuknya, tanpa heuristik** (keputusan Track 0; kolom fitur dan tahap tersimpan
 * terpisah sehingga tidak ada amplop untuk kunci versi):
 *
 * - v1 (legacy, dibaca apa adanya): `"features": ["Lampirkan foto"]`, stage tanpa kunci `evidence`.
 * - v2: `"features": [{"text":"Lampirkan foto","evidence":{"source":"NARRATIVE","quote":"..."}}]`; stage boleh
 *   membawa `evidence`. Encoder kini selalu menulis v2; butir tanpa bukti ditulis tanpa kunci `evidence`.
 * - Campuran string dan objek dalam satu array = korup → ditolak.
 *
 * `"stages": [{"name":"Intake"},{"name":"Revisi","optional":false,"reworkTo":"Intake"}]`,
 * `"briefSource": "AI" | "DETERMINISTIC"`.
 */
object ModuleBriefCodec {

    fun encodeFeatures(features: List<BriefFeature>): JsonValue.Arr = jsonArrayOf(features.map { f ->
        EvidenceRefCodec.withEvidence(jsonObjectOf("text" to jsonOf(f.text)), f.evidence)
    })

    fun decodeFeatures(raw: String): List<BriefFeature> = decodeFeatures(JsonParser.parse(raw))

    fun decodeFeatures(json: JsonValue): List<BriefFeature> {
        val arr = json as? JsonValue.Arr ?: error("features bukan array")
        // Bentuk = penanda versi: campuran string (v1) dan objek (v2) tidak pernah ditulis encoder mana pun → korup.
        val shapes = arr.items.map { it::class }.distinct()
        require(shapes.size <= 1) { "features mencampur bentuk v1 (teks) dan v2 (objek)" }
        return arr.items.mapIndexed { i, v ->
            when (v) {
                is JsonValue.Str -> BriefFeature(v.value)
                is JsonValue.Obj -> {
                    val p = "features[$i]"
                    val unknown = v.entries.keys - KNOWN_FEATURE_KEYS
                    require(unknown.isEmpty()) { "$p memuat kunci tak dikenal: ${unknown.sorted().joinToString()}" }
                    val text = (v["text"] as? JsonValue.Str)?.value?.takeIf { it.isNotBlank() } ?: error("$p.text wajib teks")
                    BriefFeature(text, optEvidence(v, p))
                }
                else -> error("features[$i] bukan teks (v1) atau objek (v2)")
            }
        }
    }

    fun encodeStages(stages: List<StageSketch>): JsonValue.Arr = jsonArrayOf(stages.map { s ->
        val base = jsonObjectOf("name" to jsonOf(s.name), "optional" to jsonOf(s.optional))
        val withRework = s.reworkTo?.let { JsonValue.Obj(base.entries + ("reworkTo" to jsonOf(it))) } ?: base
        EvidenceRefCodec.withEvidence(withRework, s.evidence)
    })

    fun decodeStages(raw: String): List<StageSketch> = decodeStages(JsonParser.parse(raw))

    fun decodeStages(json: JsonValue): List<StageSketch> {
        val arr = json as? JsonValue.Arr ?: error("stages bukan array")
        return arr.items.mapIndexed { i, item ->
            val o = item as? JsonValue.Obj ?: error("stages[$i] bukan objek")
            val unknown = o.entries.keys - KNOWN_STAGE_KEYS
            require(unknown.isEmpty()) { "stages[$i] memuat kunci tak dikenal: ${unknown.sorted().joinToString()}" }
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
                },
                evidence = optEvidence(o, "stages[$i]"),
            )
        }
    }

    fun encodeSource(source: BriefSource): JsonValue = jsonOf(source.name)

    /** Parser tunggal [BriefSource]: nama tak dikenal ditolak. */
    fun parseSource(name: String): BriefSource =
        BriefSource.entries.firstOrNull { it.name == name } ?: error("briefSource tak dikenal: '$name'")

    private val KNOWN_STAGE_KEYS = setOf("name", "optional", "reworkTo", "evidence")
    private val KNOWN_FEATURE_KEYS = setOf("text", "evidence")

    private fun optEvidence(o: JsonValue.Obj, path: String) = when (val v = o["evidence"]) {
        null, JsonValue.Null -> null
        else -> EvidenceRefCodec.decode(v, "$path.evidence")
    }
}
