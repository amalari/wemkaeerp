package com.eventverse.app.shared.pack

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.RoleHint
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Codec `DomainPack.roleHints`. Kunci absen/null = pack tanpa kamus (bukan galat); isi rusak **ditolak** berpath. */
internal object RoleHintCodec {

    fun encode(hints: List<RoleHint>): JsonValue.Arr =
        jsonArrayOf(hints.map { jsonObjectOf("word" to jsonOf(it.word), "label" to jsonOf(it.label), "moduleId" to jsonOf(it.moduleId.value)) })

    fun decode(raw: JsonValue?, at: String): List<RoleHint> = when (raw) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> raw.items.mapIndexed { i, item ->
            val p = "$at[$i]"
            val o = item as? JsonValue.Obj ?: throw DomainPackDecodeException(p, "harus objek")
            fun str(k: String) = o.string(k) ?: throw DomainPackDecodeException("$p.$k", "wajib string")
            try { RoleHint(str("word"), str("label"), ModuleId(str("moduleId"))) }
            catch (e: IllegalArgumentException) { if (e is DomainPackDecodeException) throw e else throw DomainPackDecodeException(p, e.message ?: "tidak sah") }
        }
        else -> throw DomainPackDecodeException(at, "harus array")
    }
}
