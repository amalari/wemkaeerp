package com.eventverse.app.shared.pack

import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Codec satu daftar [HandoverRoute]: dipakai `DomainPack.handoverRouteTemplate` **dan** kontrak API
 * `/api/tenant/fulfillment/routes` (TRD-FLOW-003 §4.4), supaya bentuk rute punya satu parser.
 *
 * Kunci absen/null = daftar kosong (pack/tenant tanpa rute). Isi rusak — kode tak sah, `from`/`to` yang tak
 * terbaca, field wajib hilang — **ditolak** berpath; tidak ada baris yang dilewati diam-diam.
 */
internal object HandoverRouteCodec {

    fun encode(routes: List<HandoverRoute>): JsonValue.Arr = jsonArrayOf(routes.map { r ->
        jsonObjectOf(
            "code" to jsonOf(r.code.value),
            "label" to jsonOf(r.label),
            "from" to jsonOf(r.from?.key),
            "to" to jsonOf(r.to?.key),
            "sortOrder" to jsonOf(r.sortOrder),
            "active" to jsonOf(r.active)
        )
    })

    fun decode(raw: JsonValue?, at: String): List<HandoverRoute> = when (raw) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> raw.items.mapIndexed { i, item ->
            val p = "$at[$i]"
            val o = item as? JsonValue.Obj ?: throw DomainPackDecodeException(p, "harus objek")
            try {
                HandoverRoute(
                    code = HandoverRouteCode.parse(o.string("code")).getOrElse { throw DomainPackDecodeException("$p.code", it.message ?: "tidak sah") },
                    label = o.string("label") ?: throw DomainPackDecodeException("$p.label", "wajib string"),
                    from = node(o, "from", p),
                    to = node(o, "to", p),
                    sortOrder = if (o.has("sortOrder")) o.int("sortOrder") ?: throw DomainPackDecodeException("$p.sortOrder", "harus angka") else 0,
                    active = if (o.has("active")) o.boolean("active") ?: throw DomainPackDecodeException("$p.active", "harus boolean") else true
                )
            } catch (e: IllegalArgumentException) {
                if (e is DomainPackDecodeException) throw e else throw DomainPackDecodeException(p, e.message ?: "tidak sah")
            }
        }
        else -> throw DomainPackDecodeException(at, "harus array")
    }

    private fun node(o: JsonValue.Obj, key: String, path: String): FlowNodeRef? {
        val raw = o[key]
        if (raw == null || raw == JsonValue.Null) return null
        val text = (raw as? JsonValue.Str)?.value ?: throw DomainPackDecodeException("$path.$key", "harus string")
        return FlowNodeRef.parse(text) ?: throw DomainPackDecodeException("$path.$key", "simpul alur tak dikenal '$text'")
    }
}
