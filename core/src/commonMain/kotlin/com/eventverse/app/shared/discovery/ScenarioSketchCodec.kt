package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.scenario.FieldCarry
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Bentuk JSON `DiscoveryDraft.scenarios` (TRD-BLDR-002 §4) — **satu parser**, dipakai [DiscoveryDraftCodec], klien, dan
 * keluaran agent. Ketat (tenant-variability Kontrak 4): kunci tak dikenal, tipe salah, dan nama `source` tak dikenal
 * **ditolak** dengan path ([DiscoveryDraftDecodeException]); tidak ada default senyap.
 *
 * ```
 * {"id":"scn-lead-invoice","title":"...","source":"AI","confirmed":false,
 *  "steps":[{"moduleId":"crm","screenId":"default-crm"|null,"label":"Lead",
 *            "carries":[{"fromField":"nama","toField":"pelanggan"}],"reworkTo":0?}]}
 * ```
 */
object ScenarioSketchCodec {

    fun encode(scenarios: List<ScenarioSketch>): JsonValue.Arr = jsonArrayOf(scenarios.map { sc ->
        jsonObjectOf(
            "id" to jsonOf(sc.id.value), "title" to jsonOf(sc.title),
            "source" to jsonOf(sc.source.name), "confirmed" to jsonOf(sc.confirmed),
            "steps" to jsonArrayOf(sc.steps.map { st ->
                val base = jsonObjectOf(
                    "moduleId" to jsonOf(st.moduleId.value), "screenId" to jsonOf(st.screenId), "label" to jsonOf(st.label),
                    "carries" to jsonArrayOf(st.carries.map { jsonObjectOf("fromField" to jsonOf(it.fromField), "toField" to jsonOf(it.toField)) })
                )
                // Kunci opsional ditulis hanya bila ada.
                st.reworkTo?.let { JsonValue.Obj(base.entries + ("reworkTo" to jsonOf(it))) } ?: base
            })
        )
    })

    fun decode(json: JsonValue, path: String): List<ScenarioSketch> {
        val arr = json as? JsonValue.Arr ?: fail(path, "harus array")
        return arr.items.mapIndexed { i, item -> decodeScenario(item, "$path[$i]") }
    }

    private fun decodeScenario(item: JsonValue, path: String): ScenarioSketch {
        val o = item as? JsonValue.Obj ?: fail(path, "harus objek")
        onlyKeys(o, path, setOf("id", "title", "source", "confirmed", "steps"))
        val steps = (o["steps"] as? JsonValue.Arr ?: fail("$path.steps", "wajib array")).items
            .mapIndexed { i, s -> decodeStep(s, "$path.steps[$i]") }
        return build(path) {
            ScenarioSketch(
                id = ScenarioId(str(o, "id", path)),
                title = str(o, "title", path),
                steps = steps,
                source = sourceOf(str(o, "source", path), "$path.source"),
                confirmed = when (val v = o["confirmed"]) {
                    null -> false
                    is JsonValue.Bool -> v.value
                    else -> fail("$path.confirmed", "harus boolean")
                }
            )
        }
    }

    private fun decodeStep(item: JsonValue, path: String): ScenarioStep {
        val o = item as? JsonValue.Obj ?: fail(path, "harus objek")
        onlyKeys(o, path, setOf("moduleId", "screenId", "label", "carries", "reworkTo"))
        val carries = (o["carries"] as? JsonValue.Arr ?: fail("$path.carries", "wajib array")).items.mapIndexed { i, c ->
            val co = c as? JsonValue.Obj ?: fail("$path.carries[$i]", "harus objek")
            onlyKeys(co, "$path.carries[$i]", setOf("fromField", "toField"))
            build("$path.carries[$i]") { FieldCarry(str(co, "fromField", "$path.carries[$i]"), str(co, "toField", "$path.carries[$i]")) }
        }
        return build(path) {
            ScenarioStep(
                moduleId = ModuleId(str(o, "moduleId", path)),
                screenId = when (val v = o["screenId"]) {
                    null -> fail("$path.screenId", "wajib ada (null untuk kerangka)")
                    is JsonValue.Null -> null
                    is JsonValue.Str -> v.value
                    else -> fail("$path.screenId", "harus teks atau null")
                },
                label = str(o, "label", path),
                carries = carries,
                reworkTo = when (val v = o["reworkTo"]) {
                    null, is JsonValue.Null -> null
                    is JsonValue.Num -> v.raw.toIntOrNull() ?: fail("$path.reworkTo", "harus bilangan bulat")
                    else -> fail("$path.reworkTo", "harus bilangan bulat")
                }
            )
        }
    }

    private fun sourceOf(name: String, path: String): ScenarioSource =
        ScenarioSource.entries.firstOrNull { it.name == name } ?: fail(path, "source tak dikenal: '$name'")

    private fun str(o: JsonValue.Obj, key: String, path: String): String =
        (o[key] as? JsonValue.Str)?.value ?: fail("$path.$key", "wajib teks")

    private fun onlyKeys(o: JsonValue.Obj, path: String, allowed: Set<String>) {
        val unknown = o.entries.keys - allowed
        if (unknown.isNotEmpty()) fail(path, "kunci tak dikenal: ${unknown.sorted().joinToString()}")
    }

    private fun <T> build(path: String, block: () -> T): T = try { block() } catch (e: IllegalArgumentException) {
        if (e is DiscoveryDraftDecodeException) throw e
        fail(path, e.message ?: "tidak sah")
    }

    private fun fail(path: String, message: String): Nothing = throw DiscoveryDraftDecodeException(path, message)
}
