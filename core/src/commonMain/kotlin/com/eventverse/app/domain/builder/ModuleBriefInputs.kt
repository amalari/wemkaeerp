package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.toEvidenceRef
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.tenant.TenantId

/**
 * Perakitan masukan brief dari data draf + riwayat (FR-2, FR-3). Murni. Semua teks adalah data tenant.
 */
internal object ModuleBriefInputs {

    /** Modul dalam cakupan brief: OPERATIONAL, aktif di blueprint, dan (punya tautan peran atau layar berproposal). */
    fun inScope(draft: DiscoveryDraft, module: ModuleDefinition): Boolean {
        if (module.kind != ModuleKind.OPERATIONAL) return false
        if (module.id.value !in draft.blueprint.activeModuleCodes) return false
        val linked = draft.interview?.links.orEmpty().any { it.moduleId == module.id }
        val proposed = draft.screens.any { it.moduleId == module.id && it.proposal != null }
        return linked || proposed
    }

    /**
     * Peran + fitur tautan modul (FR-3.4): satu [RoleFeature] per fitur ("Peran: fitur") atau "Peran" bila tautan tanpa
     * fitur. Bukti [RoleFeature.evidence] berasal dari `link.basisRef` lewat adaptor interview → evidence; tautan
     * tanpa dasar tetap dibawa (tanpa bukti) — keputusan membuang butir tanpa dasar ada di penyusun, bukan di sini.
     */
    fun roleFeatures(draft: DiscoveryDraft, module: ModuleDefinition): List<RoleFeature> {
        val interview = draft.interview ?: return emptyList()
        return interview.links.filter { it.moduleId == module.id }.flatMap { link ->
            val role = interview.roles.firstOrNull { it.roleKey == link.roleKey }?.label ?: link.roleKey.value
            val evidence = link.basisRef.toEvidenceRef()
            if (link.features.isEmpty()) listOf(RoleFeature(role, evidence))
            else link.features.map { RoleFeature("$role: $it", evidence) }
        }.distinct()
    }

    fun request(
        tenantId: TenantId,
        draft: DiscoveryDraft,
        module: ModuleDefinition,
        narrative: String,
        alreadyAsked: List<Clarification>,
        feedback: String?
    ): ModuleBriefRequest {
        val slot = module.slot?.let(draft.pack::slot)
        return ModuleBriefRequest(
            tenantId = tenantId,
            moduleId = module.id.value,
            moduleName = module.displayName,
            narrative = narrative,
            roleFeatures = roleFeatures(draft, module),
            slotInput = slot?.defaultInput?.value,
            slotOutput = slot?.defaultOutput?.value,
            screenFieldLabels = draft.screens.filter { it.moduleId == module.id }
                .flatMap { it.proposal?.entity?.fields.orEmpty() }.map { it.label },
            alreadyAsked = alreadyAsked,
            feedback = feedback,
            upstreamModules = neighborNames(draft, module, incoming = true),
            downstreamModules = neighborNames(draft, module, incoming = false),
            otherModules = draft.pack.modules
                .filter { it.id != module.id && it.kind == ModuleKind.OPERATIONAL && it.id.value in draft.blueprint.activeModuleCodes }
                .map { it.displayName }
        )
    }

    /** Nama modul tetangga serah-terima (wawancara): yang menyerahkan ke [module] bila [incoming], selain itu yang menerima darinya. */
    private fun neighborNames(draft: DiscoveryDraft, module: ModuleDefinition, incoming: Boolean): List<String> =
        draft.interview?.handoffs.orEmpty()
            .filter { if (incoming) it.to == module.id else it.from == module.id }
            .map { if (incoming) it.from else it.to }
            .filter { it != module.id }
            .mapNotNull { id -> draft.pack.modules.firstOrNull { it.id == id }?.displayName }
            .distinct()
}
