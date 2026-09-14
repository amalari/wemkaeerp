package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingRateCard
import com.eventverse.app.domain.costing.CostingRateCardId
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.CostingParameterCodec
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Meng-seed rate card dari parameter node pipeline yang sudah dikonfigurasi.
 * Dipanggil saat tenant pertama kali mengaktifkan modul COSTING_HPP dari canvas.
 *
 * @param formulaParameters `customFormulaParameters` dari node pipeline (format V10).
 */
class SeedCostingRateCardFromPipelineUseCase(
    private val rateCardRepository: CostingRateCardRepository,
    private val upsertRateCardUseCase: UpsertCostingRateCardUseCase,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        behavior: CostingBehavior,
        formulaParameters: Map<String, String>,
        seededFromNodeId: String,
        seededByUserId: String? = null
    ): Result<CostingRateCard> = runCatching {
        // Cek apakah sudah ada rate card — jika sudah, tidak seed ulang
        val existing = rateCardRepository.findActive(tenantId, behavior)
        if (existing != null) return@runCatching existing

        val base = com.eventverse.app.domain.costing.ResolvedCostingParameters.defaultsFor(behavior)
        val warnings = mutableListOf<String>()
        val params = CostingParameterCodec.merge(
            base = base,
            nodeParams = formulaParameters,
            warnings = warnings
        )

        upsertRateCardUseCase(
            tenantId = tenantId,
            behavior = behavior,
            updatedByUserId = seededByUserId,
            updater = {
                copy(
                    laborRatePerSamMinute = params.laborRatePerSamMinute,
                    subcontractRatePerSamMinute = params.subcontractRatePerSamMinute,
                    serviceFeePerUnit = if (!params.serviceFeePerUnit.isZero) params.serviceFeePerUnit else null,
                    overheadPerUnit = if (!params.overheadPerUnit.isZero) params.overheadPerUnit else null,
                    packingCostPerUnit = if (!params.packingCostPerUnit.isZero) params.packingCostPerUnit else null,
                    packingCostPerOrder = if (!params.packingCostPerOrder.isZero) params.packingCostPerOrder else null,
                    marginRatio = if (params.marginRatio.isPositive) params.marginRatio else null,
                    retailMarkupRatio = if (params.retailMarkupRatio.isPositive) params.retailMarkupRatio else null,
                    marketplaceFeeRatio = if (params.marketplaceFeeRatio.isPositive) params.marketplaceFeeRatio else null,
                    fabricWastageToleranceRatio = if (params.fabricWastageToleranceRatio.isPositive) params.fabricWastageToleranceRatio else null,
                    includeFabricCost = params.includeFabricCost,
                    seededFromNodeId = seededFromNodeId,
                    description = "Seeded otomatis dari parameter node pipeline $seededFromNodeId"
                )
            }
        ).getOrThrow()
    }
}
