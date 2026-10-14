package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Clarification
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

    /** Peran + fitur tautan modul: "Peran: fitur" per fitur, atau "Peran" bila tautan tanpa fitur. */
    fun roleFeatures(draft: DiscoveryDraft, module: ModuleDefinition): List<String> {
        val interview = draft.interview ?: return emptyList()
        return interview.links.filter { it.moduleId == module.id }.flatMap { link ->
            val role = interview.roles.firstOrNull { it.roleKey == link.roleKey }?.label ?: link.roleKey.value
            if (link.features.isEmpty()) listOf(role) else link.features.map { "$role: $it" }
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
            feedback = feedback
        )
    }
}
