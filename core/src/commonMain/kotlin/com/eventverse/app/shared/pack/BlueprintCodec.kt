package com.eventverse.app.shared.pack

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf

/**
 * Parser tunggal JSON ⇄ [Blueprint], dipakai `DiscoveryDraftCodec` (kunci `blueprint`) dan [DomainPackCodec]
 * (kunci `blueprints`, TRD-PLAT-008). **Ketat** (Kontrak 4): field wajib hilang atau bertipe salah ditolak dengan
 * path; tidak ada default. Galat memakai [DomainPackDecodeException]; pemanggil yang punya jenis galat sendiri
 * membungkusnya.
 */
object BlueprintCodec {

    fun encode(bp: Blueprint): JsonValue.Obj = jsonObjectOf(
        "code" to jsonOf(bp.code.value), "pack" to jsonOf(bp.pack.value),
        "displayName" to jsonOf(bp.displayName), "shortBadge" to jsonOf(bp.shortBadge),
        "description" to jsonOf(bp.description), "targetClientProfile" to jsonOf(bp.targetClientProfile),
        "modules" to jsonArrayOf(bp.modules.map { m ->
            jsonObjectOf(
                "moduleCode" to jsonOf(m.moduleCode), "active" to jsonOf(m.active),
                "parameters" to jsonStringMapOf(m.parameters)
            )
        })
    )

    fun decode(obj: JsonValue.Obj, path: String): Blueprint {
        fun fail(sub: String?, message: String): Nothing =
            throw DomainPackDecodeException(if (sub == null) path else "$path.$sub", message)

        fun str(o: JsonValue.Obj, key: String, at: String): String =
            (o[key] as? JsonValue.Str)?.value ?: throw DomainPackDecodeException("$at.$key", "wajib string")

        val rawModules = (obj["modules"] as? JsonValue.Arr)?.items ?: fail("modules", "wajib array")
        val modules = rawModules.mapIndexed { i, item ->
            val at = "$path.modules[$i]"
            val m = item as? JsonValue.Obj ?: throw DomainPackDecodeException(at, "harus objek")
            val params = when (val v = m["parameters"]) {
                null -> emptyMap()
                is JsonValue.Obj -> v.entries.mapValues { (k, p) ->
                    (p as? JsonValue.Str)?.value ?: throw DomainPackDecodeException("$at.parameters.$k", "harus string")
                }
                else -> throw DomainPackDecodeException("$at.parameters", "wajib objek")
            }
            try {
                BlueprintModule(
                    moduleCode = str(m, "moduleCode", at),
                    active = (m["active"] as? JsonValue.Bool)?.value ?: throw DomainPackDecodeException("$at.active", "wajib boolean"),
                    parameters = params
                )
            } catch (e: DomainPackDecodeException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw DomainPackDecodeException(at, e.message ?: "tidak sah")
            }
        }
        fun <T> field(key: String, ctor: (String) -> T): T {
            val raw = str(obj, key, path)
            return try { ctor(raw) } catch (e: IllegalArgumentException) { fail(key, e.message ?: "tidak sah") }
        }
        return try {
            Blueprint(
                code = field("code", ::BlueprintCode),
                pack = field("pack", ::DomainPackCode),
                displayName = str(obj, "displayName", path),
                shortBadge = str(obj, "shortBadge", path),
                description = str(obj, "description", path),
                targetClientProfile = str(obj, "targetClientProfile", path),
                modules = modules
            )
        } catch (e: DomainPackDecodeException) {
            throw e
        } catch (e: IllegalArgumentException) {
            fail(null, e.message ?: "tidak sah")
        } catch (e: IllegalStateException) {
            fail(null, e.message ?: "tidak sah")
        }
    }
}
