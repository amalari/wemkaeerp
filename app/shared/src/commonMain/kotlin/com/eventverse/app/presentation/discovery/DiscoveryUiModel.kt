package com.eventverse.app.presentation.discovery

import com.eventverse.app.shared.json.JsonValue

/**
 * Model tampilan draf discovery (Fase D): hasil parse ringkasan `GET /api/discovery/drafts/{id}`.
 * Murni data tampilan — validasi domain tetap milik server; klien hanya menggambar.
 */
data class DiscoveryModuleUi(
    val id: String,
    val displayName: String,
    val section: String,
    val kind: String,
    val slot: String?,
    val slotInput: String?,
    val slotOutput: String?,
    val active: Boolean
)

data class DiscoveryScreenUi(
    val screenId: String,
    val moduleId: String,
    val title: String,
    val widget: String,
    val sampleRows: List<Map<String, String>>
)

data class DiscoveryDraftUi(
    val id: String,
    val status: String,
    val packCode: String,
    val packDisplayName: String,
    val blueprintCode: String,
    val blueprintDescription: String,
    val modules: List<DiscoveryModuleUi>,
    val activeModuleCodes: List<String>,
    val screens: List<DiscoveryScreenUi>
) {
    val activeModules: List<DiscoveryModuleUi> get() = modules.filter { it.active }
    val sections: List<String> get() = modules.map { it.section }.distinct()

    companion object {
        fun fromJson(o: JsonValue.Obj): DiscoveryDraftUi {
            fun arr(key: String): List<JsonValue> = (o[key] as? JsonValue.Arr)?.items ?: emptyList()
            fun arr2(src: JsonValue.Obj, key: String): List<Map<String, String>> =
                ((src[key] as? JsonValue.Arr)?.items ?: emptyList())
                    .mapNotNull { it as? JsonValue.Obj }
                    .map { row -> row.entries.mapValues { (_, v) -> (v as? JsonValue.Str)?.value.orEmpty() } }
            return DiscoveryDraftUi(
                id = o.string("id").orEmpty(),
                status = o.string("status").orEmpty(),
                packCode = o.string("packCode").orEmpty(),
                packDisplayName = o.string("packDisplayName").orEmpty(),
                blueprintCode = o.string("blueprintCode").orEmpty(),
                blueprintDescription = o.string("blueprintDescription").orEmpty(),
                modules = arr("modules").mapNotNull { it as? JsonValue.Obj }.map { m ->
                    DiscoveryModuleUi(
                        id = m.string("id").orEmpty(),
                        displayName = m.string("displayName").orEmpty(),
                        section = m.string("section").orEmpty(),
                        kind = m.string("kind").orEmpty(),
                        slot = m.string("slot"),
                        slotInput = m.string("slotInput"),
                        slotOutput = m.string("slotOutput"),
                        active = m.string("id") in (arr("activeModuleCodes").mapNotNull { (it as? JsonValue.Str)?.value })
                    )
                },
                activeModuleCodes = arr("activeModuleCodes").mapNotNull { (it as? JsonValue.Str)?.value },
                screens = arr("screens").mapNotNull { it as? JsonValue.Obj }.map { s ->
                    DiscoveryScreenUi(
                        screenId = s.string("screenId").orEmpty(),
                        moduleId = s.string("moduleId").orEmpty(),
                        title = s.string("title").orEmpty(),
                        widget = s.string("widget").orEmpty(),
                        sampleRows = arr2(s, "sampleRows")
                    )
                }
            )
        }
    }
}
