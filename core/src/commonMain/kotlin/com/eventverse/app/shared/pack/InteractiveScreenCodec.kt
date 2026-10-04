package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.ChecklistConfig
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.CountSpec
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.DashboardConfig
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.KanbanConfig
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.StateMachine
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TileSpec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf

/** Kawat JSON [InteractiveScreen] (server → klien). Decode ketat: spec tak koheren melempar. */
object InteractiveScreenCodec {

    fun encode(s: InteractiveScreen): JsonValue.Obj = jsonObjectOf(
        "entities" to jsonArrayOf(s.spec.entities.map { e ->
            jsonObjectOf(
                "id" to jsonOf(e.id), "label" to jsonOf(e.label),
                "fields" to jsonArrayOf(e.fields.map { f ->
                    jsonObjectOf(
                        "key" to jsonOf(f.key), "label" to jsonOf(f.label), "type" to jsonOf(f.type.name),
                        "options" to jsonArrayOf(f.options.map(::jsonOf)), "required" to jsonOf(f.required)
                    )
                }),
                "stateMachine" to (e.stateMachine?.let { sm ->
                    jsonObjectOf(
                        "field" to jsonOf(sm.field),
                        "transitions" to jsonObjectOf(*sm.transitions.map { (k, v) -> k to jsonArrayOf(v.map(::jsonOf)) }.toTypedArray())
                    )
                } ?: JsonValue.Null)
            )
        }),
        "screens" to jsonArrayOf(s.spec.screens.map { sc ->
            jsonObjectOf(
                "screenId" to jsonOf(sc.screenId), "title" to jsonOf(sc.title), "widget" to jsonOf(sc.widget.code),
                "entityId" to jsonOf(sc.entityId),
                "kanban" to (sc.kanban?.let { k ->
                    jsonObjectOf(
                        "groupField" to jsonOf(k.groupField), "columns" to jsonArrayOf(k.columns.map(::jsonOf)),
                        "titleField" to jsonOf(k.titleField), "detailFields" to jsonArrayOf(k.detailFields.map(::jsonOf)),
                        "card" to jsonArrayOf(k.card.map { el -> jsonObjectOf("field" to jsonOf(el.field), "style" to jsonOf(el.style.name)) }),
                        "columnMeta" to jsonObjectOf(*k.columnMeta.map { (col, m) -> col to encodeColumnMeta(m) }.toTypedArray()),
                        "detailForm" to (k.detailForm?.let { f -> jsonObjectOf("fields" to jsonArrayOf(f.fields.map(::jsonOf)), "submitLabel" to jsonOf(f.submitLabel)) } ?: JsonValue.Null)
                    )
                } ?: JsonValue.Null),
                "table" to (sc.table?.let { t ->
                    jsonObjectOf(
                        "columns" to jsonArrayOf(t.columns.map(::jsonOf)), "statusField" to jsonOf(t.statusField),
                        "inlineCreate" to jsonOf(t.inlineCreate), "editableFields" to jsonArrayOf(t.editableFields.map(::jsonOf))
                    )
                } ?: JsonValue.Null),
                "form" to (sc.form?.let { f -> jsonObjectOf("fields" to jsonArrayOf(f.fields.map(::jsonOf)), "submitLabel" to jsonOf(f.submitLabel)) } ?: JsonValue.Null),
                "checklist" to (sc.checklist?.let { c -> jsonObjectOf("labelField" to jsonOf(c.labelField), "doneField" to jsonOf(c.doneField)) } ?: JsonValue.Null),
                "dashboard" to (sc.dashboard?.let { d ->
                    jsonObjectOf("tiles" to jsonArrayOf(d.tiles.map { t ->
                        jsonObjectOf("label" to jsonOf(t.label), "value" to jsonOf(t.value), "count" to (t.count?.let(::encodeCount) ?: JsonValue.Null))
                    }))
                } ?: JsonValue.Null)
            )
        }),
        "seed" to jsonObjectOf(*s.seed.map { (entityId, rows) ->
            entityId to jsonArrayOf(rows.map { jsonObjectOf("id" to jsonOf(it.id), "values" to jsonStringMapOf(it.values)) })
        }.toTypedArray()),
        "binding" to encodeBinding(s.binding)
    )

    fun decode(o: JsonValue.Obj): InteractiveScreen {
        val entities = o.objectArray("entities").map { e ->
            val machine = e.obj("stateMachine")?.let { sm ->
                StateMachine(
                    requireNotNull(sm.string("field")) { "stateMachine.field kosong" },
                    (sm.obj("transitions")?.entries ?: emptyMap()).mapValues { (_, v) -> ((v as? JsonValue.Arr)?.items.orEmpty()).filterIsInstance<JsonValue.Str>().map { it.value }.toSet() }
                )
            }
            EntitySpec(
                requireNotNull(e.string("id")) { "entitas tanpa id" }, e.string("label").orEmpty(),
                e.objectArray("fields").map { f ->
                    val type = FieldType.entries.firstOrNull { it.name == f.string("type") }
                    FieldSpec(f.string("key").orEmpty(), f.string("label").orEmpty(), requireNotNull(type) { "tipe field '${f.string("type")}' tak dikenal" }, f.stringArray("options"), f.boolean("required") ?: false)
                },
                machine
            )
        }
        val screens = o.objectArray("screens").map { sc ->
            val widget = requireNotNull(WidgetKind.fromCode(sc.string("widget").orEmpty())) { "widget layar tak dikenal" }
            ScreenSpec(
                sc.string("screenId").orEmpty(), sc.string("title").orEmpty(), widget, sc.string("entityId"),
                sc.obj("kanban")?.let { k ->
                    KanbanConfig(
                        k.string("groupField").orEmpty(), k.stringArray("columns"), k.string("titleField").orEmpty(), k.stringArray("detailFields"),
                        card = k.objectArray("card").map { el ->
                            CardElement(
                                el.string("field").orEmpty(),
                                requireNotNull(CardStyle.entries.firstOrNull { it.name == el.string("style") }) {
                                    "Gaya kartu '${el.string("style").orEmpty()}' tidak dikenal"
                                }
                            )
                        },
                        columnMeta = k.obj("columnMeta")?.entries?.mapValues { (col, v) ->
                            decodeColumnMeta(v as? JsonValue.Obj ?: throw IllegalArgumentException("columnMeta.$col: harus objek"))
                        }.orEmpty(),
                        detailForm = k.obj("detailForm")?.let { f -> FormConfig(f.stringArray("fields"), f.string("submitLabel") ?: "Simpan") }
                    )
                },
                table = sc.obj("table")?.let { t ->
                    TableConfig(t.stringArray("columns"), t.string("statusField"), t.boolean("inlineCreate") ?: false, t.stringArray("editableFields"))
                },
                form = sc.obj("form")?.let { f -> FormConfig(f.stringArray("fields"), f.string("submitLabel") ?: "Simpan") },
                checklist = sc.obj("checklist")?.let { c -> ChecklistConfig(c.string("labelField").orEmpty(), c.string("doneField").orEmpty()) },
                dashboard = sc.obj("dashboard")?.let { d ->
                    DashboardConfig(d.objectArray("tiles").map { t -> TileSpec(t.string("label").orEmpty(), t.string("value"), t.obj("count")?.let(::decodeCount)) })
                }
            )
        }
        val seed = (o.obj("seed")?.entries ?: emptyMap()).mapValues { (_, rows) ->
            ((rows as? JsonValue.Arr)?.items.orEmpty()).filterIsInstance<JsonValue.Obj>().map { r ->
                PrototypeRow(r.string("id").orEmpty(), r.stringMap("values"))
            }
        }
        return InteractiveScreen(PrototypeSpec(entities, screens), seed, decodeBindingValue(o["binding"]))
    }

    internal fun encodeCount(c: CountSpec): JsonValue.Obj = jsonObjectOf(
        "moduleId" to jsonOf(c.moduleId), "field" to jsonOf(c.field), "equals" to jsonOf(c.equals),
        "notEquals" to jsonOf(c.notEquals), "suffix" to jsonOf(c.suffix)
    )

    internal fun decodeCount(o: JsonValue.Obj): CountSpec =
        CountSpec(o.string("moduleId").orEmpty(), o.string("field"), o.string("equals"), o.string("notEquals"), o.string("suffix").orEmpty())

    /** [DataBinding] ke JSON: memori ditulis `null` (plan induk §3.7 — tanpa kunci = memori). */
    internal fun encodeBinding(b: DataBinding): JsonValue = when (b) {
        is DataBinding.Memory -> JsonValue.Null
        is DataBinding.Api -> jsonObjectOf("type" to jsonOf("api"), "basePath" to jsonOf(b.basePath))
    }

    /** Tanpa kunci = memori (kompatibel mundur); tipe/bentuk tak dikenal **ditolak**, tidak ditebak. */
    internal fun decodeBindingValue(node: JsonValue?): DataBinding = when (node) {
        null, JsonValue.Null -> DataBinding.Memory
        is JsonValue.Obj -> when (val type = node.string("type")) {
            "memory", null -> DataBinding.Memory
            "api" -> DataBinding.Api(requireNotNull(node.string("basePath")) { "binding.basePath kosong" })
            else -> throw IllegalArgumentException("Tipe binding '$type' tidak dikenal.")
        }
        else -> throw IllegalArgumentException("Kunci 'binding' harus objek.")
    }

    private fun encodeColumnMeta(m: ColumnMeta): JsonValue.Obj = jsonObjectOf(
        "tintHex" to (m.tintHex?.let(::jsonOf) ?: JsonValue.Null),
        "wipLimit" to (m.wipLimit?.let(::jsonOf) ?: JsonValue.Null)
    )

    private fun decodeColumnMeta(o: JsonValue.Obj): ColumnMeta = ColumnMeta(o.long("tintHex"), o.int("wipLimit"))
}
