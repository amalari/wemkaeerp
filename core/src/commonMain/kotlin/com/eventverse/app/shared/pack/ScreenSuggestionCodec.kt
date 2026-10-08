package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.DashboardHints
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.domain.prototype.TableHints
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.strictBoolean

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
                "groupLabel" to jsonOf(h.groupLabel),
                "groupField" to jsonOf(h.groupField),
                "fields" to jsonArrayOf(h.fields.map(::encodeFieldHint)),
                "card" to (if (h.card.isEmpty()) JsonValue.Null else InteractiveScreenCodec.encodeCardElements(h.card)),
                "columnMeta" to (if (h.columnMeta.isEmpty()) JsonValue.Null else InteractiveScreenCodec.encodeColumnMetaMap(h.columnMeta)),
                "detailForm" to (h.detailForm?.let { f -> InteractiveScreenCodec.encodeFormConfig(f) } ?: JsonValue.Null)
            )
        } ?: JsonValue.Null),
        "tableHints" to (s.tableHints?.let { h ->
            jsonObjectOf(
                "statusColumn" to jsonOf(h.statusColumn),
                "options" to jsonArrayOf(h.options.map { jsonOf(it) }),
                "transitions" to transitionsJson(h.transitions),
                "fields" to jsonArrayOf(h.fields.map(::encodeFieldHint)),
                "inlineCreate" to jsonOf(h.inlineCreate),
                "editableFields" to jsonArrayOf(h.editableFields.map(::jsonOf))
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
        "dataBinding" to InteractiveScreenCodec.encodeBinding(s.dataBinding),
        "rationale" to jsonOf(s.rationale)
    )

    /** Parameter hanya ditulis bila bukan bawaan: pack lama tetap byte-per-byte sama setelah di-encode. */
    private fun encodeFieldHint(f: FieldHint): JsonValue.Obj = JsonValue.Obj(
        jsonObjectOf(
            "key" to jsonOf(f.key), "type" to jsonOf(f.type.name),
            "required" to jsonOf(f.required), "options" to jsonArrayOf(f.options.map(::jsonOf))
        ).entries + buildMap {
            if (f.format != NumberFormat.PLAIN) put("format", jsonOf(f.format.name))
            f.currencyCode?.let { put("currencyCode", jsonOf(it)) }
            if (f.withTime) put("withTime", jsonOf(true))
            if (f.validation != TextValidation.NONE) put("validation", jsonOf(f.validation.name))
        }
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
        fun fieldHintsIn(node: JsonValue.Obj, where: String): List<FieldHint> =
            ((node["fields"] as? JsonValue.Arr)?.items ?: emptyList()).map { item ->
                val f = item as? JsonValue.Obj ?: fail(where, "harus objek")
                val typeName = (f["type"] as? JsonValue.Str)?.value
                val type = FieldType.entries.firstOrNull { it.name == typeName }
                    ?: fail("$where.type", "'${typeName.orEmpty()}' bukan tipe field yang dikenal")
                FieldHint(
                    (f["key"] as? JsonValue.Str)?.value ?: fail("$where.key", "wajib string"),
                    type,
                    (f["required"] as? JsonValue.Bool)?.value ?: false,
                    strListIn(f, "options"),
                    // Kunci absen/null = bawaan; tipe JSON salah atau nama tak dikenal ditolak (bukan jatuh ke bawaan).
                    format = FieldParamWire.numberFormat(f),
                    currencyCode = FieldParamWire.currencyCode(f),
                    withTime = f.strictBoolean("withTime", false),
                    validation = FieldParamWire.textValidation(f)
                )
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
                    KanbanHints(
                        strListIn(h, "columns"),
                        transitionsIn(h),
                        (h["groupLabel"] as? JsonValue.Str)?.value,
                        card = InteractiveScreenCodec.decodeCardElements(h["card"]),
                        columnMeta = InteractiveScreenCodec.decodeColumnMetaMap(h["columnMeta"]),
                        detailForm = InteractiveScreenCodec.decodeFormConfig(h["detailForm"]),
                        groupField = (h["groupField"] as? JsonValue.Str)?.value,
                        fields = fieldHintsIn(h, "kanbanHints.fields")
                    )
                },
                tableHints = (o["tableHints"] as? JsonValue.Obj)?.let { h ->
                    TableHints(
                        (h["statusColumn"] as? JsonValue.Str)?.value.orEmpty(),
                        strListIn(h, "options"),
                        transitionsIn(h),
                        fields = fieldHintsIn(h, "tableHints.fields"),
                        inlineCreate = (h["inlineCreate"] as? JsonValue.Bool)?.value ?: false,
                        editableFields = strListIn(h, "editableFields")
                    )
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
                dataBinding = InteractiveScreenCodec.decodeBindingValue(o["dataBinding"]),
                rationale = (o["rationale"] as? JsonValue.Str)?.value
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
