package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf
import com.eventverse.app.shared.pack.InteractiveScreenCodec

/** Ringkasan draf discovery untuk klien (`GET /api/discovery/drafts/{id}`), dipisah dari rute demi batas ukuran file. */
internal fun summaryObj(stored: StoredDiscoveryDraft, narrative: String? = null): JsonValue.Obj {
    // Sample data berupa data (plan §4), v2: dihitung dari pack **registri hidup**, bukan snapshot
    // beku di draf. Draf lama menyimpan pack sebelum kosakata `sampleRows`, dan dokumen beku tidak
    // boleh ditimpa (Kontrak 5 tenant-variability-rules) — pelajaran yang sama dengan backfill di
    // EnsureTenantWorkingDraftUseCase. Pack yang belum terdaftar tetap memakai snapshot-nya sendiri.
    val samplePack = DomainPackRegistry.find(stored.draft.pack.code) ?: stored.draft.pack
    return jsonObjectOf(
    "id" to jsonOf(stored.id.value),
    // Narasi asli (E1/E2): dipulihkan dari buku demand supaya prospek yang kembali melihat
    // ceritanya sendiri, bukan mulai dari kosong. Demand lahir sebelum V80 → null.
    "narrative" to jsonOf(narrative),
    "ownerUserId" to jsonOf(stored.ownerUserId.value),
    "prospectLeadId" to jsonOf(stored.prospectLeadId),
    "status" to jsonOf(stored.status.name),
    "schemaVersion" to jsonOf(stored.schemaVersion),
    "packCode" to jsonOf(stored.draft.pack.code.value),
    "packDisplayName" to jsonOf(stored.draft.pack.displayName),
    // Kosakata label port & slot ikut ringkasan: sumber utama label klien, bekerja juga untuk
    // draf pra-handoff yang pack-nya belum terdaftar di registry klien. Label = data tampilan
    // dari pack (bukan fakta kontrak/harga), jadi tetap dalam batas "ringkasan" di atas.
    "portLabels" to jsonStringMapOf(stored.draft.pack.portLabels),
    "slotLabels" to jsonStringMapOf(stored.draft.pack.slots.associate { it.code.value to it.displayName }),
    "blueprintCode" to jsonOf(stored.draft.blueprint.code.value),
    "blueprintDescription" to jsonOf(stored.draft.blueprint.description),
    "moduleCount" to jsonOf(stored.draft.pack.modules.size),
    "activeModuleCount" to jsonOf(stored.draft.blueprint.activeModuleCodes.size),
    "screenCount" to jsonOf(stored.draft.screens.size),
    "createdAt" to jsonOf(stored.createdAt?.toString()),
    "lockedAt" to jsonOf(stored.lockedAt?.toString()),
    // Data penuh untuk renderer Fase D (ModuleMapPane/DataFlowPane/PrototypeRenderer) — tetap
    // ringkasan: tidak ada parameter, fakta kontrak, atau harga di sini.
    "modules" to jsonArrayOf(stored.draft.pack.modules.map { m ->
        val slot = m.slot
        val slotDef = slot?.let { runCatching { DomainPackRegistry.slotDefinition(it) }.getOrNull() }
        jsonObjectOf(
            "id" to jsonOf(m.id.value),
            "displayName" to jsonOf(m.displayName),
            "section" to jsonOf(m.section.value),
            "kind" to jsonOf(m.kind.name),
            "iconKey" to jsonOf(m.iconKey),
            "slot" to jsonOf(slot?.value),
            "slotInput" to jsonOf(slotDef?.defaultInput?.value),
            "slotOutput" to jsonOf(slotDef?.defaultOutput?.value)
        )
    }),
    "sections" to jsonArrayOf(stored.draft.pack.sections.map { s ->
        jsonObjectOf(
            "code" to jsonOf(s.code.value),
            "displayName" to jsonOf(s.displayName),
            "order" to jsonOf(s.order),
            "colorHex" to jsonOf(s.colorHex),
            "tintHex" to jsonOf(s.tintHex)
        )
    }),
    "activeModuleCodes" to jsonArrayOf(stored.draft.blueprint.activeModuleCodes.map(::jsonOf)),
    "screens" to jsonArrayOf(stored.draft.screens.map { s ->
        // Layar default-* diproyeksikan ke usulan pack hidup (WidgetRegistry.screenFor) supaya
        // revisi watak layar di pack mengalir ke draf beku; sampleRows mengikuti yang terproyeksi.
        val projected = WidgetRegistry.screenFor(s, samplePack)
        jsonObjectOf(
            "screenId" to jsonOf(s.screenId),
            "moduleId" to jsonOf(s.moduleId.value),
            "title" to jsonOf(projected.title),
            "widget" to jsonOf(projected.widget),
            // Sample data berupa data (plan §4): dihitung WidgetRegistry di server dari pack
            // registri hidup (samplePack) agar klien tidak perlu merekonstruksi DomainPack.
            "sampleRows" to jsonArrayOf(WidgetRegistry.sampleRowsFor(projected, samplePack).map { row ->
                jsonObjectOf(*row.map { (k, v) -> k to jsonOf(v) }.toTypedArray())
            }),
            // TRD-PLAT-003: versi bisa dimainkan (null untuk widget non-kanban → klien menggambar statis).
            "interactive" to (WidgetRegistry.interactiveFor(projected, samplePack)?.let(InteractiveScreenCodec::encode) ?: com.eventverse.app.shared.json.JsonValue.Null)
        )
    })
    )
}
