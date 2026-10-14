package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.ModuleBrief
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.infrastructure.discovery.extractJsonObject
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue

/**
 * Parser TUNGGAL balasan model untuk brief modul: `{summary, features[], stages[{name,optional,reworkTo?}], followUps[]}`.
 * Kunci atau tipe tak dikenal = galat berpath (percobaan ulang membawanya sebagai umpan balik), bukan default senyap.
 * Batas panjang/jumlah ditegakkan validator domain (`ModuleBriefValidator`), bukan di sini.
 * Hasilnya selalu bertanda [BriefSource.AI].
 */
internal object ModuleBriefReplyParser {
    private val ROOT_KEYS = setOf("summary", "features", "stages", "followUps")
    private val STAGE_KEYS = setOf("name", "optional", "reworkTo")

    fun parse(raw: String): ModuleBrief {
        val root = JsonParser.parseObject(extractJsonObject(raw))
        val unknown = root.entries.keys - ROOT_KEYS
        require(unknown.isEmpty()) { "Kunci tak dikenal: ${unknown.joinToString()}" }
        val summary = (root["summary"] as? JsonValue.Str)?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("summary wajib teks tidak kosong")
        return ModuleBrief(
            summary = summary,
            features = strings(root, "features"),
            stages = stages(root),
            followUps = strings(root, "followUps"),
            source = BriefSource.AI
        )
    }

    private fun strings(root: JsonValue.Obj, key: String): List<String> {
        val arr = root[key] as? JsonValue.Arr ?: error("$key wajib array")
        return arr.items.mapIndexed { i, v -> (v as? JsonValue.Str)?.value?.trim() ?: error("$key[$i] bukan teks") }
    }

    private fun stages(root: JsonValue.Obj): List<StageSketch> {
        val arr = root["stages"] as? JsonValue.Arr ?: error("stages wajib array")
        return arr.items.mapIndexed { i, item ->
            val o = item as? JsonValue.Obj ?: error("stages[$i] bukan objek")
            val unknown = o.entries.keys - STAGE_KEYS
            require(unknown.isEmpty()) { "stages[$i] memuat kunci tak dikenal: ${unknown.joinToString()}" }
            StageSketch(
                name = (o["name"] as? JsonValue.Str)?.value?.trim()?.takeIf { it.isNotEmpty() } ?: error("stages[$i].name wajib"),
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
}
