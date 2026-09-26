package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingParameterCodec
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.ResolvedCostingParameters
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Mengambil parameter HPP yang sudah di-resolve dari 4 sumber dengan presedensi naik:
 * default → nodeParams → rateCard → sheetOverrides.
 */
class GetEffectiveCostingParametersUseCase(
    private val rateCardRepository: CostingRateCardRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        behavior: CostingBehavior,
        nodeParams: Map<String, String> = emptyMap(),
        sheetOverrides: Map<String, String> = emptyMap(),
        pricingAsOf: Instant,
        warnings: MutableList<String> = mutableListOf()
    ): Result<ResolvedCostingParameters> = runCatching {
        val base = ResolvedCostingParameters.defaultsFor(behavior)
        val rateCard = rateCardRepository.findEffectiveAt(tenantId, behavior, pricingAsOf)
        CostingParameterCodec.merge(
            base = base,
            nodeParams = nodeParams,
            rateCard = rateCard,
            sheetOverrides = sheetOverrides,
            warnings = warnings
        )
    }
}
