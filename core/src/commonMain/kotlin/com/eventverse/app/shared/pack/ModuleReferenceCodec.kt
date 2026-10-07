package com.eventverse.app.shared.pack

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleReference
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf

/** Codec `DomainPack.moduleReferences`. Absen/null = tanpa rujukan; isi rusak **ditolak** berpath, tidak diabaikan. */
internal object ModuleReferenceCodec {

    fun encode(refs: List<ModuleReference>): JsonValue.Arr = jsonArrayOf(refs.map { r ->
        jsonObjectOf(
            "platformModuleId" to jsonOf(r.platformModuleId.value), "label" to jsonOf(r.label),
            "portMapping" to jsonStringMapOf(r.portMapping.entries.associate { it.key.value to it.value.value })
        )
    })

    fun decode(raw: JsonValue?, at: String): List<ModuleReference> = when (raw) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> raw.items.mapIndexed { i, item ->
            val p = "$at[$i]"
            val o = item as? JsonValue.Obj ?: fail(p, "harus objek")
            fun str(k: String) = o.string(k) ?: fail("$p.$k", "wajib string")
            val mapping = when (val m = o["portMapping"]) {
                is JsonValue.Obj -> m.entries.map { (k, v) ->
                    val target = (v as? JsonValue.Str)?.value ?: fail("$p.portMapping.$k", "harus string")
                    port("$p.portMapping.$k", k) to port("$p.portMapping.$k", target)
                }.toMap()
                else -> fail("$p.portMapping", "wajib objek")
            }
            try { ModuleReference(ModuleId(str("platformModuleId")), str("label"), mapping) }
            catch (e: IllegalArgumentException) { if (e is DomainPackDecodeException) throw e else fail("$p.platformModuleId", e.message ?: "tidak sah") }
        }
        else -> fail(at, "harus array")
    }

    private fun port(path: String, raw: String): PortType =
        try { PortType(raw) } catch (e: IllegalArgumentException) { fail(path, e.message ?: "tidak sah") }

    private fun fail(path: String, message: String): Nothing = throw DomainPackDecodeException(path, message)
}
