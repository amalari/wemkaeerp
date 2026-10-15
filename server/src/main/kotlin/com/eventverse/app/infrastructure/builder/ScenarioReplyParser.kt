package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.infrastructure.discovery.extractJsonObject
import com.eventverse.app.shared.discovery.ScenarioSketchCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Parser TUNGGAL balasan model untuk skenario: `{"scenarios":[{id?, title, steps:[...]}]}`. Bentuk skenario dibentuk oleh
 * [ScenarioSketchCodec] (core) — tidak ada parser skenario kedua. Di sini hanya pembungkus akar dan penegakan kepemilikan:
 * model TIDAK boleh menentukan `source` maupun `confirmed`; mengirim salah satunya **ditolak** dengan galat jelas
 * (bukan diabaikan diam-diam), lalu hasil dipaksa `source = AI`, `confirmed = false`. `id` opsional (bila tak ada dibuat
 * `scn-ai-N`). Daftar kosong adalah jawaban sah. Batas jumlah/panjang urusan validator domain.
 */
internal object ScenarioReplyParser {
    private const val ROOT_KEY = "scenarios"
    private val FORBIDDEN_KEYS = listOf("source", "confirmed")

    fun parse(raw: String): List<ScenarioSketch> {
        val root = JsonParser.parseObject(extractJsonObject(raw))
        val unknown = root.entries.keys - ROOT_KEY
        require(unknown.isEmpty()) { "Kunci akar tak dikenal: ${unknown.sorted().joinToString()}" }
        val arr = root[ROOT_KEY] as? JsonValue.Arr ?: error("$ROOT_KEY wajib array")
        val forced = arr.items.mapIndexed { i, item ->
            val o = item as? JsonValue.Obj ?: error("$ROOT_KEY[$i] bukan objek")
            val forbidden = FORBIDDEN_KEYS.filter { it in o.entries }
            require(forbidden.isEmpty()) { "$ROOT_KEY[$i] memuat kunci yang hanya boleh ditentukan sistem: ${forbidden.joinToString()}" }
            val withId = if ("id" in o.entries) o.entries else o.entries + ("id" to jsonOf("scn-ai-${i + 1}"))
            JsonValue.Obj(withId + mapOf("source" to jsonOf("AI"), "confirmed" to jsonOf(false)))
        }
        return ScenarioSketchCodec.decode(jsonArrayOf(forced), "$.$ROOT_KEY")
    }
}
