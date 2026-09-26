package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.CostingStrategyResolver
import com.eventverse.app.domain.costing.SamMinutesCalculator
import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.techpack.usecases.PreviewBomMaterialCostUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class SimulateCostingCommand(
    val tenantId: TenantId,
    val techPackId: String,
    val orderQuantity: Long,
    val behavior: CostingBehavior,
    val parameterOverrides: Map<String, String> = emptyMap(),
    val pricingAsOf: Instant
)

/**
 * Simulasi HPP tanpa membuat atau menyimpan lembar.
 * Digunakan untuk preview cepat sebelum commit ke draft.
 */
class SimulateCostingUseCase(
    private val techPackRepository: TechPackRepository,
    private val materialRepository: MaterialItemRepository,
    private val materialPriceRepository: MaterialPriceRepository,
    private val rateCardRepository: CostingRateCardRepository,
    private val strategyResolver: CostingStrategyResolver = CostingStrategyResolver(),
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: SimulateCostingCommand): Result<CostingCalculationResult> = runCatching {
        val now = clock.now()
        val techPackId = TechPackId(command.techPackId)

        val resolveParamsUseCase = GetEffectiveCostingParametersUseCase(rateCardRepository)
        val params = resolveParamsUseCase(
            tenantId = command.tenantId,
            behavior = command.behavior,
            sheetOverrides = command.parameterOverrides,
            pricingAsOf = command.pricingAsOf
        ).getOrThrow()

        val techPack = techPackRepository.findById(command.tenantId, techPackId)
            ?: error("Tech Pack tidak ditemukan: ${command.techPackId}")

        val bomPreview = PreviewBomMaterialCostUseCase(
            techPackRepository = techPackRepository,
            materialRepository = materialRepository,
            priceRepository = materialPriceRepository
        ).invoke(
            tenantId = command.tenantId,
            techPackId = techPackId,
            orderQuantity = command.orderQuantity,
            at = command.pricingAsOf
        ).getOrThrow()

        val samBreakdown = SamMinutesCalculator.calculate(
            techPack.laborOperations.map {
                LaborOperation(
                    operationId = it.operationId,
                    name = it.name,
                    samMinutes = it.samMinutes,
                    workstation = it.workstation,
                    isSubcontracted = it.isSubcontracted
                )
            }
        )

        strategyResolver.resolve(
            costingId = "sim-${now.toEpochMilliseconds()}",
            tenantId = command.tenantId,
            input = CostingFormulaInput(
                techPack = techPack,
                bomCostPreview = bomPreview,
                orderQuantity = command.orderQuantity,
                samBreakdown = samBreakdown,
                parameters = params,
                pricingAsOf = command.pricingAsOf
            ),
            calculatedAt = now
        ).getOrThrow()
    }
}
