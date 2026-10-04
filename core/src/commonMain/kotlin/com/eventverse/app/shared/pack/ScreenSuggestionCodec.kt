package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.DashboardHints
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.TableHints
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kawat JSON daftar [ScreenSuggestion] — dipisah dari `DomainPackCodec` karena tanggung
 * jawabnya berbeda (usulan layar prototype); pemisahan ini membuat `DomainPackCodec` kembali di
 * bawah batas lunak ukuran file. Decode **ketat**: widget tak dikenal ditolak dengan path, tidak
 * ditebak. Kunci baru `dataBinding` (kontrak v2, plan induk §3.4) **opsional**: pack JSON lama
 * tanpa kunci tetap terbaca (= memori).
 */
internal object ScreenSuggestionCodec {

    fun encode(suggestions: List<ScreenSuggestion>): JsonValue.Arr = jsonArrayOf(suggestions.map(::encodeOne))

    /** `null`/`Null` = pack lama yang sama sekali belum mengusulkan layar — sah, bukan galat. */
    fun decode(node: JsonValue?): List<ScreenSuggestion> = when (node) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> node.items.mapIndexed { i, v ->
            val o = v as? JsonValue.Obj ?: throw DomainPackDecodeException("$.screenSuggestions[$i]", "harus objek")
            decodeOne(o, "$.screenSuggestions[$i]")
        }
        else -> throw DomainPackDecodeException("$.screenSuggestions", "harus array")
    }

    private fun encodeOne(s: ScreenSuggestion): JsonValue.Obj = jsonObjectOf(
        "moduleId" to jsonOf(s.moduleId.value), "title" to jsonOf(s.title), "widget" to jsonOf(s.widget.code),
        "sampleRows" to jsonArrayOf(s.sampleRows.map { row ->
            jsonObjectOf(*row.map { (k, v) -> k to jsonOf(v) }.toTypedArray())
        }),
        "kanbanHints" to (s.kanbanHints?.let { h ->
            jsonObjectOf(
                "columns" to jsonArrayOf(h.columns.map { jsonOf(it) }),
                "transitions" to transitionsJson(h.transitions),
                "groupLabel" to jsonOf(h.groupLabel)
            )
        } ?: JsonValue.Null),
        "tableHints" to (s.tableHints?.let { h ->
            jsonObjectOf(
                "statusColumn" to jsonOf(h.statusColumn),
                "options" to jsonArrayOf(h.options.map { jsonOf(it) }),
                "transitions" to transitionsJson(h.transitions)
            )
        } ?: JsonValue.Null),
        "dashboardHints" to (s.dashboardHints?.let { h ->
            jsonObjectOf("counts" to jsonObjectOf(*h.counts.map { (label, c) -> label to InteractiveScreenCodec.encodeCount(c) }.toTypedArray()))
        } ?: JsonValue.Null),
        "formHints" to (s.formHints?.let { h ->
            jsonObjectOf(
                "fields" to jsonArrayOf(h.fields.map(::jsonOf)),
                "required" to jsonArrayOf(h.required.map(::jsonOf)),
                "options" to jsonObjectOf(*h.options.map { (k, v) -> k to jsonArrayOf(v.map(::jsonOf)) }.toTypedArray()),
                "submitLabel" to jsonOf(h.submitLabel)
            )
        } ?: JsonValue.Null),
        "dataBinding" to InteractiveScreenCodec.encodeBinding(s.dataBinding)
    )

    private fun decodeOne(o: JsonValue.Obj, path: String): ScreenSuggestion {
        fun fail(key: String?, message: String): Nothing =
            throw DomainPackDecodeException(if (key == null) path else "$path.$key", message)
        fun str(key: String): String = (o[key] as? JsonValue.Str)?.value ?: fail(key, "wajib string")
        fun strListIn(node: JsonValue.Obj, key: String): List<String> =
            ((node[key] as? JsonValue.Arr)?.items ?: emptyList()).map { s -> (s as? JsonValue.Str)?.value ?: fail(key, "harus string") }
        fun transitionsIn(node: JsonValue.Obj): Map<String, Set<String>> =
            ((node["transitions"] as? JsonValue.Obj)?.entries ?: emptyMap()).mapValues { (_, v) ->
                ((v as? JsonValue.Arr)?.items.orEmpty()).filterIsInstance<JsonValue.Str>().map { it.value }.toSet()
            }
        val moduleId = try {
            ModuleId(str("moduleId"))
        } catch (e: IllegalArgumentException) {
            fail("moduleId", e.message ?: "tidak sah")
        }
        val widgetRaw = str("widget")
        val widget = WidgetKind.entries.firstOrNull { it.name == widgetRaw }
            ?: fail("widget", "'$widgetRaw' bukan salah satu dari ${WidgetKind.entries.map { it.name }}")
        return try {
            ScreenSuggestion(
                moduleId = moduleId,
                title = str("title"),
                widget = widget,
                sampleRows = ((o["sampleRows"] as? JsonValue.Arr)?.items ?: emptyList()).mapIndexed { i, row ->
                    val r = row as? JsonValue.Obj ?: fail("sampleRows[$i]", "harus objek")
                    r.entries.mapValues { (k, v) -> (v as? JsonValue.Str)?.value ?: fail("sampleRows[$i].$k", "harus string") }
                },
                kanbanHints = (o["kanbanHints"] as? JsonValue.Obj)?.let { h ->
                    KanbanHints(strListIn(h, "columns"), transitionsIn(h), (h["groupLabel"] as? JsonValue.Str)?.value)
                },
                tableHints = (o["tableHints"] as? JsonValue.Obj)?.let { h ->
                    TableHints((h["statusColumn"] as? JsonValue.Str)?.value.orEmpty(), strListIn(h, "options"), transitionsIn(h))
                },
                dashboardHints = (o["dashboardHints"] as? JsonValue.Obj)?.let { h ->
                    DashboardHints(((h["counts"] as? JsonValue.Obj)?.entries ?: emptyMap()).mapNotNull { (label, v) ->
                        (v as? JsonValue.Obj)?.let { label to InteractiveScreenCodec.decodeCount(it) }
                    }.toMap())
                },
                formHints = (o["formHints"] as? JsonValue.Obj)?.let { h ->
                    FormHints(
                        strListIn(h, "fields"),
                        strListIn(h, "required"),
                        ((h["options"] as? JsonValue.Obj)?.entries ?: emptyMap()).mapValues { (_, v) ->
                            ((v as? JsonValue.Arr)?.items.orEmpty()).filterIsInstance<JsonValue.Str>().map { it.value }
                        },
                        (h["submitLabel"] as? JsonValue.Str)?.value
                    )
                },
                dataBinding = InteractiveScreenCodec.decodeBindingValue(o["dataBinding"])
            )
        } catch (e: DomainPackDecodeException) {
            throw e
        } catch (e: IllegalArgumentException) {
            fail(null, e.message ?: "tidak sah")
        } catch (e: IllegalStateException) {
            fail(null, e.message ?: "tidak sah")
        }
    }

    private fun transitionsJson(transitions: Map<String, Set<String>>): JsonValue.Obj =
        jsonObjectOf(*transitions.map { (from, tos) -> from to jsonArrayOf(tos.map { jsonOf(it) }) }.toTypedArray())
}
