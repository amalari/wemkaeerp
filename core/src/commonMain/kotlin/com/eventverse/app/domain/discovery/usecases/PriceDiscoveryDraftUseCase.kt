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

    /**
     * Rincian satu modul: [monthlyIdr] = harga langganan (modul yang sudah ada di katalog), atau
     * [gapLowIdr]–[gapHighIdr] = rentang bulanan modul yang harus dibangun (null bila tak bisa diestimasi).
     */
    data class ModuleLine(
        val moduleId: String,
        val displayName: String,
        val covered: Boolean,
        val monthlyIdr: Long? = null,
        val gapLowIdr: Long? = null,
        val gapHighIdr: Long? = null
    )

    data class DraftPricing(
        val packCode: DomainPackCode,
        val coveredModuleIds: List<String>,
        val newModuleIds: List<String>,
        val customScreenCount: Int,
        val pricing: ProspectPricingResult,
        val lines: List<ModuleLine> = emptyList()
    ) {
        val isFullyCovered: Boolean get() = newModuleIds.isEmpty()
    }

    suspend operator fun invoke(
        draft: DiscoveryDraft,
        marginPercent: Percentage,
        expectedTenantCount: Int = PriceProspectFlowUseCase.DEFAULT_EXPECTED_TENANT_COUNT,
        amortizationMonths: Int = PriceProspectFlowUseCase.DEFAULT_AMORTIZATION_MONTHS,
        /**
         * Harga hanya modul-modul ini ("lihat harga bila modul X dilepas"). Null = seluruh modul pack.
         * Id di luar pack **ditolak**, bukan diabaikan — harga yang diam-diam mengabaikan pilihan menyesatkan.
         */
        onlyModuleIds: Set<String>? = null
    ): Result<DraftPricing> = runCatching {
        val packModules = draft.pack.modules
        onlyModuleIds?.let { wanted ->
            val unknown = wanted - packModules.map { it.id.value }.toSet()
            require(unknown.isEmpty()) { "Modul tidak ada di draf: ${unknown.sorted().joinToString()}" }
        }
        val modules = if (onlyModuleIds == null) packModules else packModules.filter { it.id.value in onlyModuleIds }
        require(modules.isNotEmpty()) { "Tidak ada modul yang dipilih untuk dihitung" }
        val billable = billableCatalog()

        val decisions = modules.map { module ->
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
        val coveredSet = coveredIds.toSet()
        DraftPricing(
            packCode = draft.pack.code,
            coveredModuleIds = coveredIds,
            newModuleIds = modules.map { it.id.value }.filterNot { it in coveredSet },
            customScreenCount = draft.screens.count { s -> modules.any { it.id == s.moduleId } },
            pricing = pricing,
            lines = modules.map { m -> lineOf(m, billable, pricing) }
        )
    }

    private fun lineOf(m: ModuleDefinition, billable: List<ModuleCatalogEntry>, pricing: ProspectPricingResult): ModuleLine {
        val entry = billable.firstOrNull { it.moduleId == m.id.value }
        if (entry != null) return ModuleLine(m.id.value, m.displayName, covered = true, monthlyIdr = entry.baseMonthlyPriceIdr?.amount)
        val gap = pricing.gapPricings.firstOrNull { it.gap.proposedModuleId == m.id.value }
        return ModuleLine(m.id.value, m.displayName, covered = false, gapLowIdr = gap?.lowMonthly?.amount, gapHighIdr = gap?.highMonthly?.amount)
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
