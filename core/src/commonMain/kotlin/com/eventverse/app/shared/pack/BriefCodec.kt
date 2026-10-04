package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.brief.RequirementsBrief
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Kawat JSON [RequirementsBrief] (kontrak v1; respons `POST /api/builder/draft/brief`). Hanya encode — klien tak mengirim brief. */
object BriefCodec {
    fun encode(b: RequirementsBrief): JsonValue.Obj = jsonObjectOf(
        "packCode" to jsonOf(b.packCode),
        "modules" to jsonArrayOf(b.modules.map { m ->
            jsonObjectOf(
                "moduleId" to jsonOf(m.moduleId), "displayName" to jsonOf(m.displayName),
                "screens" to jsonArrayOf(m.screens.map { jsonObjectOf("title" to jsonOf(it.title), "widget" to jsonOf(it.widget), "entityId" to jsonOf(it.entityId)) }),
                "entities" to jsonArrayOf(m.entities.map { e ->
                    jsonObjectOf(
                        "id" to jsonOf(e.id), "label" to jsonOf(e.label), "statusField" to jsonOf(e.statusField),
                        "fields" to jsonArrayOf(e.fields.map { f ->
                            jsonObjectOf("label" to jsonOf(f.label), "type" to jsonOf(f.type), "required" to jsonOf(f.required), "options" to jsonArrayOf(f.options.map(::jsonOf)))
                        }),
                        "transitions" to jsonObjectOf(*e.transitions.map { (k, v) -> k to jsonArrayOf(v.map(::jsonOf)) }.toTypedArray())
                    )
                })
            )
        }),
        "changes" to jsonArrayOf(b.changes.map(SpecOpCodec::encode)),
        "coverage" to jsonArrayOf(b.coverage.map { c ->
            jsonObjectOf(
                "moduleId" to jsonOf(c.moduleId), "displayName" to jsonOf(c.displayName), "covered" to jsonOf(c.covered),
                "monthlyIdr" to (c.monthlyIdr?.let { jsonOf(it) } ?: JsonValue.Null),
                "gapLowIdr" to (c.gapLowIdr?.let { jsonOf(it) } ?: JsonValue.Null),
                "gapHighIdr" to (c.gapHighIdr?.let { jsonOf(it) } ?: JsonValue.Null)
            )
        }),
        "customNeeds" to jsonArrayOf(b.customNeeds.map(::jsonOf))
    )
}
