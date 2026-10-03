package com.eventverse.app.presentation.builder.chat

import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.shared.json.JsonValue

/** Satu gelembung chat Builder (hasil parse `GET /api/builder/chat`). */
internal data class BuilderChatEntry(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val summary: List<String>,
    val hasPendingPatch: Boolean,
    val applied: Boolean
)

internal fun parseMessages(raw: JsonValue): List<BuilderChatEntry> =
    ((raw as? JsonValue.Obj)?.get("messages") as? JsonValue.Arr)?.items
        ?.filterIsInstance<JsonValue.Obj>()
        ?.map { m ->
            BuilderChatEntry(
                id = m.string("id").orEmpty(),
                isUser = m.string("role") == "USER",
                text = m.string("text").orEmpty(),
                summary = (m.get("summary") as? JsonValue.Arr)?.items
                    ?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty(),
                hasPendingPatch = (m.get("hasPendingPatch") as? JsonValue.Bool)?.value == true,
                applied = (m.get("appliedDraftId") as? JsonValue.Str)?.value != null
            )
        }.orEmpty()

/** Fitur satu modul aktif: layar prototype-nya + kontrak data masuk/keluar slotnya. */
internal data class ModuleFeatures(val moduleName: String, val items: List<String>)

/**
 * Turunan "fitur" dari draf: tidak ada entitas fitur terpisah di domain, jadi daftarnya dirakit dari
 * modul aktif — layar prototype modul itu, lalu port masuk/keluar slotnya. Murni data; tanpa tahu industri.
 */
internal fun featuresOf(draft: DiscoveryDraftUi): List<ModuleFeatures> =
    draft.activeModules.map { module ->
        val screens = draft.screens.filter { it.moduleId == module.id }.map { "Layar: ${it.title}" }
        val ports = listOfNotNull(
            module.slotInput?.let { "Menerima: $it" },
            module.slotOutput?.let { "Menghasilkan: $it" }
        )
        ModuleFeatures(module.displayName, screens + ports)
    }

/** Pesan pembuka lokal (tidak disimpan) bila riwayat kosong tetapi tenant sudah punya draf kerja. */
internal fun openingEntryFor(draft: DiscoveryDraftUi): BuilderChatEntry = BuilderChatEntry(
    id = "local-opening",
    isUser = false,
    text = "Draf kerja ${draft.packDisplayName} sudah siap (blueprint ${draft.blueprintCode.uppercase()}). " +
        "Ceritakan perubahan yang diinginkan, atau buka Hasil untuk melihat modul, fitur, alur data, dan prototype.",
    summary = draft.activeModules.map { it.displayName },
    hasPendingPatch = false,
    applied = true
)
