package com.eventverse.app.domain.costing

import com.eventverse.app.domain.costing.strategies.FullPackageCogsStrategy
import com.eventverse.app.domain.costing.strategies.IndirectOverheadStrategy
import com.eventverse.app.domain.costing.strategies.RetailValuationWithFeesStrategy
import com.eventverse.app.domain.costing.strategies.ServiceFeeOnlyStrategy
import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Resolver yang memilih [CostingFormulaStrategy] yang tepat dan mengeksekusinya.
 *
 * Ini adalah satu-satunya titik yang boleh memanggil `strategy.calculate(...)`.
 * Tidak ada use case yang boleh memanggil strategi secara langsung — selalu lewat resolver.
 *
 * ## Alasan pemisahan resolver dari agregat
 * Resolver mengkoordinasikan strategi + membangun [CostingCalculationResult] dari output.
 * Agregat [CostingSheet] hanya peduli pada apakah ada `CostingCalculationResult` yang valid,
 * bukan bagaimana cara menghasilkannya. Pemisahan ini membuat keduanya bisa ditest secara
 * independen.
 */
class CostingStrategyResolver(
    private val strategies: Map<CostingBehavior, CostingFormulaStrategy> = defaultStrategies()
) {
    companion object {
        fun defaultStrategies(): Map<CostingBehavior, CostingFormulaStrategy> = mapOf(
            CostingBehavior.FULL_PACKAGE_COGS to FullPackageCogsStrategy,
            CostingBehavior.SERVICE_FEE_ONLY to ServiceFeeOnlyStrategy,
            CostingBehavior.RETAIL_VALUATION_WITH_FEES to RetailValuationWithFeesStrategy,
            CostingBehavior.INDIRECT_OVERHEAD to IndirectOverheadStrategy
        )
    }

    /**
     * Memilih strategi, menjalankan kalkulasi, dan mengemas hasilnya sebagai [CostingCalculationResult].
     *
     * @param costingId ID unik untuk hasil kalkulasi ini.
     * @param tenantId Tenant yang memiliki lembar HPP ini.
     * @param input Semua data yang dibutuhkan strategi.
     * @param calculatedAt Titik waktu kalkulasi (dari clock di use case, bukan di sini).
     * @return `Result.success(CostingCalculationResult)` atau `Result.failure` dengan pesan domain.
     */
    fun resolve(
        costingId: String,
        tenantId: TenantId,
        input: CostingFormulaInput,
        calculatedAt: Instant
    ): Result<CostingCalculationResult> {
        val behavior = input.parameters.behavior
        val strategy = strategies[behavior]
            ?: return Result.failure(
                IllegalStateException("Tidak ada strategi HPP untuk behavior '${behavior.displayName}'")
            )

        return strategy.calculate(input).map { output ->
            CostingCalculationResult(
                costingId = costingId,
                tenantId = tenantId,
                techPackId = input.techPack.id.value,
                orderQuantity = input.orderQuantity,
                behavior = behavior,
                buckets = output.buckets,
                marginRatio = output.marginRatio,
                formulaParameters = CostingParameterCodec.encodeOverrides(input.parameters),
                consignedMaterialValueHandled = output.consignedMaterialValueHandled,
                calculatedAt = calculatedAt,
                roundingResidual = output.roundingResidual,
                currency = input.currency
            )
        }.mapFailure { cause ->
            IllegalStateException(
                "Kalkulasi HPP behavior '${behavior.displayName}' gagal: ${cause.message}",
                cause
            )
        }
    }
}

/** Extension helper untuk `Result.mapFailure` yang belum ada di stdlib KMP. */
private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    this.recoverCatching { throw transform(it) }
