package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentSlots
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.prospect.CapabilityRequirement
import com.eventverse.app.domain.prospect.CoverageAnalysis
import com.eventverse.app.domain.prospect.CoverageDecision
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import com.eventverse.app.domain.prospect.usecases.ProspectPricingResult

/**
 * Estimasi dari [DiscoveryDraft] (plan §3 B1): langganan dari **modul platform yang sudah ada**, dan
 * rentang pembangunan dari **modul baru berprefiks + layar kustom**.
 *
 * Draf **tidak dihargai dengan rumus kedua**: ia disintesis menjadi [CoverageAnalysis] — keputusan
 * cakupan yang sama dengan jalur prospek lama — lalu didelegasikan ke [PriceProspectFlowUseCase].
 * Satu sumber rumus harga; "estimasi berubah bila modul/layar berubah" diwarisi dari mesin sizing
 * yang sudah teruji ([BuildFeatureVector.screenCount] dari layar kustom draf).
 *
 * Archetype tiap modul draf = **slot pack-nya sendiri** — produktivitas historis garment tidak dipinjam
 * untuk modul klinik (`ModuleBuildRepository` sengaja memfilter kandidat per archetype).
 */
class PriceDiscoveryDraftUseCase(
    private val billableCatalog: suspend () -> List<ModuleCatalogEntry>,
    private val priceProspectFlow: PriceProspectFlowUseCase
) {

    data class DraftPricing(
        val packCode: DomainPackCode,
        val coveredModuleIds: List<String>,
        val newModuleIds: List<String>,
        val customScreenCount: Int,
        val pricing: ProspectPricingResult
    ) {
        val isFullyCovered: Boolean get() = newModuleIds.isEmpty()
    }

    suspend operator fun invoke(
        draft: DiscoveryDraft,
        marginPercent: Percentage,
        expectedTenantCount: Int = PriceProspectFlowUseCase.DEFAULT_EXPECTED_TENANT_COUNT,
        amortizationMonths: Int = PriceProspectFlowUseCase.DEFAULT_AMORTIZATION_MONTHS
    ): Result<DraftPricing> = runCatching {
        val billable = billableCatalog()

        val decisions = draft.pack.modules.map { module ->
            val entry = billable.firstOrNull { it.moduleId == module.id.value }
            if (entry != null) {
                CoverageDecision.CoveredByCatalog(requirementOf(draft, module), entry)
            } else {
                CoverageDecision.Gap(
                    requirement = requirementOf(draft, module),
                    proposedModuleId = module.id.value,
                    features = featuresFor(draft.screens.count { it.moduleId == module.id })
                )
            }
        }

        val pricing = priceProspectFlow(
            CoverageAnalysis(decisions),
            marginPercent = marginPercent,
            expectedTenantCount = expectedTenantCount,
            amortizationMonths = amortizationMonths
        ).getOrThrow()

        val coveredIds = decisions.filterIsInstance<CoverageDecision.CoveredByCatalog>().map { it.entry.moduleId }
        DraftPricing(
            packCode = draft.pack.code,
            coveredModuleIds = coveredIds,
            newModuleIds = draft.pack.modules.map { it.id.value }.filterNot { it in coveredIds.toSet() },
            customScreenCount = draft.screens.size,
            pricing = pricing
        )
    }

    /** Draf adalah buktinya sendiri: kutipan menunjuk modul draf, bukan narasi mentah yang sudah lalu. */
    private fun requirementOf(draft: DiscoveryDraft, module: ModuleDefinition) = CapabilityRequirement(
        archetype = module.slot ?: GarmentSlots.CUSTOM_EXTENSION,
        title = module.displayName,
        description = module.description,
        sourceQuote = "Discovery draft ${draft.blueprint.code.value}: modul ${module.id.value}"
    )

    companion object {
        /**
         * Vektor **minimum konservatif** untuk modul generik hasil discovery: setiap modul operasional
         * pasti punya ≥1 entitas, 1 use case, 1 endpoint, 1 tabel, dan layarnya (min. 1 — setiap modul
         * butuh minimal satu layar agar bisa dipakai). Bottom-up tanpa data historis yang lebih kaya
         * akan menaikkan angkanya lewat kandidat sejenis saat data itu ada.
         */
        fun featuresFor(screenCount: Int): BuildFeatureVector = BuildFeatureVector(
            entityCount = 1,
            useCaseCount = 1,
            screenCount = screenCount.coerceAtLeast(1),
            apiEndpointCount = 1,
            dbTableCount = 1
        )

        /** Layar satu modul; dipakai test untuk membuktikan estimasi merespons perubahan layar. */
        fun screensOf(draft: DiscoveryDraft, moduleId: String): List<PrototypeScreen> =
            draft.screens.filter { it.moduleId.value == moduleId }
    }
}
