package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class CostingScenarioSpec(
    val name: String,
    val orderQuantity: Long,
    val behavior: CostingBehavior,
    val parameterOverrides: Map<String, String> = emptyMap()
)

data class CostingScenarioComparison(
    val scenarioName: String,
    val orderQuantity: Long,
    val behavior: CostingBehavior,
    val result: CostingCalculationResult
)

/**
 * Membandingkan beberapa skenario simulasi HPP (misal variasi kuantitas order, margin, atau behavior)
 * secara berdampingan tanpa menyimpan ke database.
 */
class CompareCostingScenariosUseCase(
    private val simulateCostingUseCase: SimulateCostingUseCase
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        techPackId: String,
        pricingAsOf: Instant,
        scenarios: List<CostingScenarioSpec>
    ): Result<List<CostingScenarioComparison>> = runCatching {
        scenarios.map { spec ->
            val result = simulateCostingUseCase(
                SimulateCostingCommand(
                    tenantId = tenantId,
                    techPackId = techPackId,
                    orderQuantity = spec.orderQuantity,
                    behavior = spec.behavior,
                    parameterOverrides = spec.parameterOverrides,
                    pricingAsOf = pricingAsOf
                )
            ).getOrThrow()

            CostingScenarioComparison(
                scenarioName = spec.name,
                orderQuantity = spec.orderQuantity,
                behavior = spec.behavior,
                result = result
            )
        }
    }
}
