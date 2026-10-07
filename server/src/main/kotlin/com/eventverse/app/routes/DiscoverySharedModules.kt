package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.ModuleReferenceRules
import com.eventverse.app.domain.pack.slotPorts
import com.eventverse.app.shared.discovery.InterviewBasisCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf

/**
 * Modul bersama yang dirujuk pack draf (B6), dikirim ke klien sebagai `sharedModules` — terpisah dari `modules`
 * karena modul rujukan **belum** masuk RBAC/kanvas (menunggu persetujuan). Memuat `label` pack, `portMapping`, port
 * slot dalam kosakata pack, serta `origin` dan `basis` bila wawancara menautkannya. Pack tanpa rujukan: ringkasan
 * dikembalikan apa adanya (draf lama tidak berubah).
 */
internal fun withSharedModules(base: JsonValue.Obj, stored: StoredDiscoveryDraft): JsonValue.Obj {
    val pack = stored.draft.pack
    if (pack.moduleReferences.isEmpty()) return base
    val links = stored.draft.interview?.links.orEmpty()
    val shared = pack.moduleReferences.mapNotNull { ref ->
        val module = ModuleReferenceRules.offered(ref.platformModuleId)?.first ?: return@mapNotNull null
        val ports = pack.slotPorts(ref.platformModuleId)
        val link = links.firstOrNull { it.moduleId == ref.platformModuleId }
        val extra = buildMap<String, JsonValue> {
            link?.let { put("origin", jsonOf(it.origin.code)) }
            link?.basisRef?.let { put("basis", InterviewBasisCodec.encodeRef(it)) }
        }
        JsonValue.Obj(jsonObjectOf(
            "id" to jsonOf(ref.platformModuleId.value), "displayName" to jsonOf(ref.label),
            "kind" to jsonOf(module.kind.name), "slotInput" to jsonOf(ports?.first?.value), "slotOutput" to jsonOf(ports?.second?.value),
            "portMapping" to jsonStringMapOf(ref.portMapping.entries.associate { it.key.value to it.value.value })
        ).entries + extra)
    }
    return JsonValue.Obj(base.entries + ("sharedModules" to jsonArrayOf(shared)))
}
