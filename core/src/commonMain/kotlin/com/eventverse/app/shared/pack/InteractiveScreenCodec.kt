package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.KanbanConfig
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.StateMachine
import com.eventverse.app.domain.prototype.TableConfig
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
                        "options" to jsonArrayOf(f.options.map(::jsonOf))
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
                        "titleField" to jsonOf(k.titleField), "detailFields" to jsonArrayOf(k.detailFields.map(::jsonOf))
                    )
                } ?: JsonValue.Null),
                "table" to (sc.table?.let { t ->
                    jsonObjectOf("columns" to jsonArrayOf(t.columns.map(::jsonOf)), "statusField" to jsonOf(t.statusField))
                } ?: JsonValue.Null)
            )
        }),
        "seed" to jsonObjectOf(*s.seed.map { (entityId, rows) ->
            entityId to jsonArrayOf(rows.map { jsonObjectOf("id" to jsonOf(it.id), "values" to jsonStringMapOf(it.values)) })
        }.toTypedArray())
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
                    FieldSpec(f.string("key").orEmpty(), f.string("label").orEmpty(), requireNotNull(type) { "tipe field '${f.string("type")}' tak dikenal" }, f.stringArray("options"))
                },
                machine
            )
        }
        val screens = o.objectArray("screens").map { sc ->
            val widget = requireNotNull(WidgetKind.fromCode(sc.string("widget").orEmpty())) { "widget layar tak dikenal" }
            ScreenSpec(
                sc.string("screenId").orEmpty(), sc.string("title").orEmpty(), widget, sc.string("entityId").orEmpty(),
                sc.obj("kanban")?.let { k -> KanbanConfig(k.string("groupField").orEmpty(), k.stringArray("columns"), k.string("titleField").orEmpty(), k.stringArray("detailFields")) },
                table = sc.obj("table")?.let { t -> TableConfig(t.stringArray("columns"), t.string("statusField")) }
            )
        }
        val seed = (o.obj("seed")?.entries ?: emptyMap()).mapValues { (_, rows) ->
            ((rows as? JsonValue.Arr)?.items.orEmpty()).filterIsInstance<JsonValue.Obj>().map { r ->
                PrototypeRow(r.string("id").orEmpty(), r.stringMap("values"))
            }
        }
        return InteractiveScreen(PrototypeSpec(entities, screens), seed)
    }
}
