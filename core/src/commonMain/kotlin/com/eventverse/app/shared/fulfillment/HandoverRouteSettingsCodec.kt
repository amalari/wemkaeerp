package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.pack.HandoverRouteCodec

/**
 * Kontrak JSON tunggal rute serah terima (TRD-FLOW-003 §4.4), dipakai server dan klien.
 *
 * Kunci `route`, `routeLabel`, `mode`, `modeLabel`, `isExplicit` **kompatibel mundur** dengan
 * `FulfillmentRouteConfigCodec`; ditambah `active` dan `sortOrder`. `tenantId` tidak pernah dibaca dari
 * payload. Seluruh decoder **menolak** isi tak sah (kode tak sah, mode tak dikenal, baris tanpa field
 * wajib) dengan [IllegalArgumentException]; tidak ada baris yang dilewati diam-diam.
 */
object HandoverRouteSettingsCodec {

    fun encode(view: HandoverRouteSettingsView): JsonValue.Obj = jsonObjectOf(
        "tenantId" to jsonOf(view.tenantId.value),
        "hasAdminHubRoute" to jsonOf(view.hasAdminHubRoute),
        "routes" to jsonArrayOf(view.settings.map { s ->
            jsonObjectOf(
                "route" to jsonOf(s.route.code.value),
                "routeLabel" to jsonOf(s.route.label),
                "mode" to jsonOf(s.mode.name),
                "modeLabel" to jsonOf(s.mode.displayName),
                "isExplicit" to jsonOf(s.isExplicit),
                "active" to jsonOf(s.route.active),
                "sortOrder" to jsonOf(s.route.sortOrder)
            )
        })
    )

    /** Body `PUT route-settings`: `{ "routes": [ { "route": "CODE", "mode": "DIRECT" } ] }`. */
    fun decodeModes(body: JsonValue.Obj): Map<HandoverRouteCode, HandoverMode> {
        val rows = body["routes"] as? JsonValue.Arr ?: throw IllegalArgumentException("\$.routes: harus array")
        val modes = LinkedHashMap<HandoverRouteCode, HandoverMode>()
        rows.items.forEachIndexed { i, item ->
            val at = "\$.routes[$i]"
            val row = item as? JsonValue.Obj ?: throw IllegalArgumentException("$at: harus objek")
            val code = HandoverRouteCode.parse(row.string("route")).getOrElse { throw IllegalArgumentException("$at.route: ${it.message}") }
            val mode = HandoverMode.entries.firstOrNull { it.name == row.string("mode") }
                ?: throw IllegalArgumentException("$at.mode: tidak dikenal '${row.string("mode")}'")
            require(modes.put(code, mode) == null) { "$at.route: kode ganda '${code.value}'" }
        }
        return modes
    }

    /** Body `GET/PUT routes`: `{ "routes": [ { "code", "label", "from"?, "to"?, "sortOrder", "active" } ] }`. */
    fun encodeRoutes(routes: List<HandoverRoute>): JsonValue.Obj = jsonObjectOf("routes" to HandoverRouteCodec.encode(routes))

    fun decodeRoutes(body: JsonValue.Obj): List<HandoverRoute> = HandoverRouteCodec.decode(body["routes"] ?: throw IllegalArgumentException("\$.routes: wajib ada"), "\$.routes")
}
