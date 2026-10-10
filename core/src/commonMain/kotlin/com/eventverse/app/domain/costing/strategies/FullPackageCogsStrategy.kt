package com.eventverse.app.domain.costing.strategies

import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.CostingFormulaOutput
import com.eventverse.app.domain.costing.CostingFormulaStrategy
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.Rounding
import com.eventverse.app.domain.common.divideWithRounding
import com.eventverse.app.domain.contracts.CostBucket
import com.eventverse.app.domain.contracts.CostBucketKind
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics

/**
 * Strategi HPP untuk model bisnis **FOB Full Package**.
 *
 * Semua komponen biaya masuk HPP:
 * - MATERIAL: kain + aksesoris (OWNED tertagih, CONSIGNED nol + tidak ditagihkan)
 * - LABOR: `samLangsung × laborRatePerSamMinute`
 * - SUBCONTRACT: `samSubkon × subcontractRatePerSamMinute`
 * - OVERHEAD: `overheadPerUnit`
 * - PACKAGING: `packingCostPerUnit` + alokasi `packingCostPerOrder`
 * - MARGIN: informatif saja (`isBillableToClient = false`), margin masuk harga lewat `marginRatio`
 *
 * ## Jebakan margin ganda
 * Bucket MARGIN **harus** `isBillableToClient = false`. Jika ditagihkan DAN `marginRatio > 0`,
 * margin dihitung dua kali: sekali di bucket (masuk `billablePerUnit`) dan sekali lagi
 * di `sellingPricePerUnit = billable + billable × marginRatio`.
 */
object FullPackageCogsStrategy : CostingFormulaStrategy {
    override val behavior = CostingBehavior.FULL_PACKAGE_COGS

    override fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput> = runCatching {
        val params = input.parameters
        val currency = input.currency
        val bomPreview = input.bomCostPreview
        val warnings = mutableListOf<String>()

        // --- MATERIAL buckets (satu per baris BOM)
        val materialBuckets = mutableListOf<CostBucket>()
        var consignedNotional = Money.zero(currency)

        for (line in bomPreview.lines) {
            val bucket = CostBucket.material(
                label = line.material.displayLabel,
                amountPerUnit = line.costPerGarment,
                ownership = line.ownership,
                behavior = behavior
            )
            // sourceRefs memberi tautan balik untuk drill-down UI
            val bucketWithRef = bucket.copy(sourceRefs = listOf(line.lineId))
            materialBuckets.add(bucketWithRef)

            if (line.ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
                consignedNotional = consignedNotional + line.costPerGarment
            }
        }

        if (bomPreview.unpricedLines.isNotEmpty()) {
            warnings.add("${bomPreview.unpricedLines.size} baris BOM belum punya harga - biaya material tidak akurat")
        }

        // --- LABOR bucket
        val samDirect = input.samBreakdown.directMinutes()
        val laborCost = params.laborRatePerSamMinute * samDirect
        val laborBucket = CostBucket(
            kind = CostBucketKind.LABOR,
            label = "Tenaga Kerja Langsung (${samDirect.formatted(1)} menit SAM)",
            amountPerUnit = laborCost,
            isBillableToClient = true,
            sourceRefs = emptyList()
        )

        // --- SUBCONTRACT bucket
        val samSubcon = input.samBreakdown.subcontractMinutes()
        val buckets = mutableListOf<CostBucket>()
        buckets.addAll(materialBuckets)
        buckets.add(laborBucket)

        if (!samSubcon.isZero) {
            val subconCost = params.subcontractRatePerSamMinute * samSubcon
            buckets.add(CostBucket(
                kind = CostBucketKind.SUBCONTRACT,
                label = "Jasa Subkon Eksternal (${samSubcon.formatted(1)} menit SAM)",
                amountPerUnit = subconCost,
                isBillableToClient = true
            ))
        }

        // --- OVERHEAD bucket
        if (!params.overheadPerUnit.isZero) {
            buckets.add(CostBucket(
                kind = CostBucketKind.OVERHEAD,
                label = "Overhead Pabrik",
                amountPerUnit = params.overheadPerUnit,
                isBillableToClient = true
            ))
        }

        // --- PACKAGING
        var packingResidual = Money.zero(currency)
        if (!params.packingCostPerUnit.isZero) {
            buckets.add(CostBucket(
                kind = CostBucketKind.PACKAGING,
                label = "Kemasan per Pcs",
                amountPerUnit = params.packingCostPerUnit,
                isBillableToClient = true
            ))
        }
        if (!params.packingCostPerOrder.isZero && input.orderQuantity > 0L) {
            // Alokasikan biaya per-order ke per-pcs, simpan sisa di residual
            val totalMinor = params.packingCostPerOrder.minorUnits
            val qty = input.orderQuantity
            val perUnitMinor = divideWithRounding(totalMinor, qty, Rounding.DOWN)
            val residualMinor = totalMinor - (perUnitMinor * qty)
            val packingPerUnit = Money(perUnitMinor, currency)
            packingResidual = Money(residualMinor, currency)
            if (!packingPerUnit.isZero) {
                buckets.add(CostBucket(
                    kind = CostBucketKind.PACKAGING,
                    label = "Kemasan per Order (dialokasi ${input.orderQuantity} pcs)",
                    amountPerUnit = packingPerUnit,
                    isBillableToClient = true
                ))
            }
        }

        // --- MARGIN (informatif, tidak ditagihkan)
        if (params.marginRatio.isPositive) {
            val billableSoFar = Money.sum(buckets.filter { it.isBillableToClient }.map { it.amountPerUnit }, currency)
            val marginAmount = billableSoFar * params.marginRatio
            buckets.add(CostBucket(
                kind = CostBucketKind.MARGIN,
                label = "Margin Laba (${params.marginRatio.asPercentageString()})",
                amountPerUnit = marginAmount,
                isBillableToClient = false  // WAJIB false — lihat KDoc jebakan margin ganda
            ))
        }

        CostingFormulaOutput(
            buckets = buckets,
            marginRatio = params.marginRatio,
            consignedMaterialValueHandled = consignedNotional * input.orderQuantity,
            roundingResidual = packingResidual,
            warnings = warnings
        )
    }
}
