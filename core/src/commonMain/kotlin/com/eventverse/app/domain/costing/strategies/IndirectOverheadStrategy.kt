package com.eventverse.app.domain.costing.strategies

import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.CostingFormulaOutput
import com.eventverse.app.domain.costing.CostingFormulaStrategy
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.contracts.CostBucket
import com.eventverse.app.domain.contracts.CostBucketKind
import com.eventverse.app.domain.pipeline.CostingBehavior

/**
 * Strategi untuk **INDIRECT_OVERHEAD** — digunakan pada node non-produksi yang tetap
 * mengonsumsi sumber daya (mis. Quality Control, Production MRP).
 *
 * Hanya overhead yang dihitung; tidak ada material, labor langsung, atau penagihan ke klien.
 * Hasilnya masuk ke analisis biaya internal, bukan ke invoice.
 */
object IndirectOverheadStrategy : CostingFormulaStrategy {
    override val behavior = CostingBehavior.INDIRECT_OVERHEAD

    override fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput> = runCatching {
        val params = input.parameters
        val currency = input.currency

        val buckets = mutableListOf<CostBucket>()

        if (!params.overheadPerUnit.isZero) {
            buckets.add(CostBucket(
                kind = CostBucketKind.OVERHEAD,
                label = "Overhead Operasional",
                amountPerUnit = params.overheadPerUnit,
                isBillableToClient = false  // overhead tidak ditagihkan ke klien secara langsung
            ))
        }

        CostingFormulaOutput(
            buckets = buckets,
            marginRatio = params.marginRatio,
            consignedMaterialValueHandled = Money.zero(currency),
            roundingResidual = Money.zero(currency)
        )
    }
}
