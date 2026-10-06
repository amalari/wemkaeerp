package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kawat JSON [SlotDefinition], dipisah dari `DomainPackCodec` (batas ukuran file). Kunci `defaultWidget` dan
 * `defaultStatuses` **opsional** — pack lama tanpa keduanya terbaca (null/kosong). Kunci yang ada tetapi rusak
 * (widget tak dikenal, status bukan string) **ditolak** dengan path, tidak diabaikan (Kontrak 4).
 */
internal object SlotDefinitionCodec {

    fun encode(s: SlotDefinition): JsonValue.Obj = jsonObjectOf(
        "code" to jsonOf(s.code.value), "displayName" to jsonOf(s.displayName), "phase" to jsonOf(s.phase.value),
        "defaultInput" to jsonOf(s.defaultInput.value), "defaultOutput" to jsonOf(s.defaultOutput.value),
        "defaultWidget" to jsonOf(s.defaultWidget?.code),
        "defaultStatuses" to jsonArrayOf(s.defaultStatuses.map(::jsonOf))
    )

    /** @param path path objek slot di dokumen pack, mis. `$.slots[3]`. Galat konstruktor domain dibungkus pemanggil. */
    fun decode(o: JsonValue.Obj, path: String): SlotDefinition {
        fun fail(key: String, message: String): Nothing = throw DomainPackDecodeException("$path.$key", message)
        fun str(key: String): String = (o[key] as? JsonValue.Str)?.value ?: fail(key, "wajib string")
        val widget = when (val v = o["defaultWidget"]) {
            null, JsonValue.Null -> null
            is JsonValue.Str -> WidgetKind.fromCode(v.value)
                ?: fail("defaultWidget", "'${v.value}' bukan widget yang dikenal: ${WidgetKind.entries.joinToString { it.code }}")
            else -> fail("defaultWidget", "harus string atau null")
        }
        val statuses = when (val v = o["defaultStatuses"]) {
            null, JsonValue.Null -> emptyList()
            is JsonValue.Arr -> v.items.mapIndexed { i, item -> (item as? JsonValue.Str)?.value ?: fail("defaultStatuses[$i]", "harus string") }
            else -> fail("defaultStatuses", "harus array string")
        }
        return try {
            SlotDefinition(
                SlotCode(str("code")), str("displayName"), PhaseCode(str("phase")),
                PortType(str("defaultInput")), PortType(str("defaultOutput")), widget, statuses
            )
        } catch (e: IllegalArgumentException) {
            throw DomainPackDecodeException(path, e.message ?: "slot tidak sah")
        }
    }
}
