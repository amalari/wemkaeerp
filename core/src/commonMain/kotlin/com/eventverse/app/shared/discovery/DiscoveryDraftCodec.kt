package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException

/** Dokumen draf tidak sah. [path] menunjuk bagian yang salah (`$.blueprint.modules[0].moduleCode`). */
class DiscoveryDraftDecodeException(val path: String, message: String) : IllegalArgumentException("$path: $message")

/**
 * Parser tunggal JSON ⇄ [DiscoveryDraft] (plan §2 A1). Dipakai kolom `ops.discovery_drafts.document`,
 * route `PUT /api/discovery/drafts/{id}`, dan sebagai **format keluaran agent AI** (T1: bukan DSL baru —
 * dokumen ini membungkus `DomainPackCodec` dan `Blueprint` yang sudah ada).
 *
 * **Ketat** (tenant-variability-rules Kontrak 4): field wajib yang hilang atau nilai rusak **ditolak** dengan
 * path, tidak diganti default. Invarian lintas-bagian (blueprint ↔ modul pack) tetap milik `DiscoveryDraft.init`,
 * dibungkus menjadi galat berpath `$.blueprint` / `$.screens`.
 */
object DiscoveryDraftCodec {

    fun encode(draft: DiscoveryDraft): JsonValue.Obj = jsonObjectOf(
        "pack" to DomainPackCodec.encode(draft.pack),
        "blueprint" to encodeBlueprint(draft.blueprint),
        "screens" to jsonArrayOf(draft.screens.map { s ->
            jsonObjectOf(
                "screenId" to jsonOf(s.screenId), "moduleId" to jsonOf(s.moduleId.value),
                "title" to jsonOf(s.title), "widget" to jsonOf(s.widget)
            )
        })
    )

    fun encodeToString(draft: DiscoveryDraft): String = encode(draft).encode()

    fun decode(raw: String): DiscoveryDraft {
        val root = try { JsonParser.parseObject(raw) } catch (e: Exception) {
            throw DiscoveryDraftDecodeException("$", "bukan objek JSON (${e.message})")
        }
        return decode(root)
    }

    fun decode(root: JsonValue.Obj): DiscoveryDraft {
        val pack = try {
            DomainPackCodec.decode(root.obj("pack") ?: fail("$.pack", "wajib objek pack"))
        } catch (e: DomainPackDecodeException) {
            throw DiscoveryDraftDecodeException("$.pack${e.path.removePrefix("$")}", e.message ?: "pack tidak sah")
        }
        val blueprint = decodeBlueprint(root.obj("blueprint") ?: fail("$.blueprint", "wajib objek blueprint"))
        val screens = root.objectArray("screens").mapIndexed { i, s ->
            val r = Reader(s, "$.screens[$i]")
            r.build {
                PrototypeScreen(
                    screenId = r.string("screenId"),
                    moduleId = r.value("moduleId", ::ModuleId),
                    title = r.string("title"),
                    widget = r.string("widget")
                )
            }
        }
        return try {
            DiscoveryDraft(pack = pack, blueprint = blueprint, screens = screens)
        } catch (e: IllegalArgumentException) {
            throw DiscoveryDraftDecodeException("$.blueprint", e.message ?: "draf tidak sah")
        }
    }

    private fun encodeBlueprint(bp: Blueprint): JsonValue.Obj = jsonObjectOf(
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

    private fun decodeBlueprint(obj: JsonValue.Obj): Blueprint {
        val r = Reader(obj, "$.blueprint")
        val modules = r.objects("modules").map { m ->
            m.build {
                BlueprintModule(
                    moduleCode = m.string("moduleCode"),
                    active = m.boolean("active") ?: m.fail("active", "wajib boolean"),
                    parameters = m.stringMap("parameters")
                )
            }
        }
        return r.build {
            Blueprint(
                code = r.value("code", ::BlueprintCode),
                pack = r.value("pack", ::DomainPackCode),
                displayName = r.string("displayName"),
                shortBadge = r.string("shortBadge"),
                description = r.string("description"),
                targetClientProfile = r.string("targetClientProfile"),
                modules = modules
            )
        }
    }

    private fun fail(path: String, message: String): Nothing = throw DiscoveryDraftDecodeException(path, message)

    /** Pola sama dengan `DomainPackCodec.Reader`: pembaca satu objek dengan path untuk pesan galat. */
    private class Reader(private val obj: JsonValue.Obj, private val path: String) {

        fun fail(key: String?, message: String): Nothing =
            throw DiscoveryDraftDecodeException(if (key == null) path else "$path.$key", message)

        fun <T> build(block: () -> T): T = try { block() } catch (e: DiscoveryDraftDecodeException) { throw e } catch (e: IllegalArgumentException) {
            fail(null, e.message ?: "tidak sah")
        } catch (e: IllegalStateException) {
            fail(null, e.message ?: "tidak sah")
        }

        fun string(key: String): String = when (val v = obj[key]) {
            is JsonValue.Str -> v.value
            else -> fail(key, "wajib string")
        }

        fun boolean(key: String): Boolean? = (obj[key] as? JsonValue.Bool)?.value

        fun <T> value(key: String, ctor: (String) -> T): T {
            val raw = string(key)
            return try { ctor(raw) } catch (e: IllegalArgumentException) { fail(key, e.message ?: "tidak sah") }
        }

        fun objects(key: String): List<Reader> = when (val v = obj[key]) {
            is JsonValue.Arr -> v.items.mapIndexed { i, item ->
                Reader(item as? JsonValue.Obj ?: fail("$key[$i]", "harus objek"), "$path.$key[$i]")
            }
            else -> fail(key, "wajib array")
        }

        fun stringMap(key: String): Map<String, String> = when (val v = obj[key]) {
            null -> emptyMap()
            is JsonValue.Obj -> v.entries.mapNotNull { (k, v) -> (v as? JsonValue.Str)?.let { k to it.value } }.toMap()
            else -> fail(key, "wajib objek")
        }
    }
}
