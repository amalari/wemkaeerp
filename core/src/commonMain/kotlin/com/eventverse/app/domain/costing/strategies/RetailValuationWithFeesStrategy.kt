package com.eventverse.app.domain.costing.strategies

import com.eventverse.app.domain.costing.CostingFormulaInput
import com.eventverse.app.domain.costing.CostingFormulaOutput
import com.eventverse.app.domain.costing.CostingFormulaStrategy
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.contracts.CostBucket
import com.eventverse.app.domain.contracts.CostBucketKind
import com.eventverse.app.domain.pipeline.CostingBehavior

/**
 * Strategi HPP untuk model bisnis **Brand D2C** (Direct-to-Consumer).
 *
 * Merek memiliki seluruh proses dari bahan baku hingga penjualan retail.
 * HPP mencakup semua komponen + markup retail + fee marketplace.
 *
 * ## Urutan kalkulasi D2C (penting!)
 * 1. Hitung HPP dasar (MATERIAL + LABOR + OVERHEAD + PACKAGING) → `hppBase`
 * 2. Markup retail: `hargaRetail = hppBase × (1 + retailMarkupRatio)` → bucket MARGIN
 * 3. Fee marketplace: `feeAmount = hargaRetail × marketplaceFeeRatio` → bucket MARKETPLACE_FEE
 *
 * ## Jebakan sirkularitas fee marketplace
 * Fee marketplace dihitung di atas **harga retail**, bukan HPP.
 * `fee = (HPP × (1 + markup)) × feeMarketplace`
 * BUKAN: `fee = HPP × feeMarketplace`
 *
 * Misalnya: HPP = Rp 50.000, markup 62%, fee marketplace 6.5%:
 * - hargaRetail = 50.000 × 1.62 = Rp 81.000
 * - feeMarketplace = 81.000 × 0.065 = Rp 5.265 ← bukan 50.000 × 0.065 = 3.250!
 *
 * Ini adalah jebakan yang paling sering ditemukan saat implementasi D2C tanpa spec.
 */
object RetailValuationWithFeesStrategy : CostingFormulaStrategy {
    override val behavior = CostingBehavior.RETAIL_VALUATION_WITH_FEES

    override fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput> = runCatching {
        val params = input.parameters
        val currency = input.currency
        val bomPreview = input.bomCostPreview
        val warnings = mutableListOf<String>()

        val buckets = mutableListOf<CostBucket>()

        // --- MATERIAL buckets
        for (line in bomPreview.lines) {
            buckets.add(
                CostBucket.material(
                    label = line.material.displayLabel,
                    amountPerUnit = line.costPerGarment,
                    ownership = line.ownership,
                    behavior = behavior
                ).copy(sourceRefs = listOf(line.lineId))
            )
        }

        if (bomPreview.unpricedLines.isNotEmpty()) {
            warnings.add("${bomPreview.unpricedLines.size} baris BOM belum punya harga")
        }

        // --- LABOR
        val samDirect = input.samBreakdown.directMinutes()
        buckets.add(CostBucket(
            kind = CostBucketKind.LABOR,
            label = "Tenaga Kerja (${samDirect.toDouble().let { "%.1f".format(it) }} menit SAM)",
            amountPerUnit = params.laborRatePerSamMinute * samDirect,
            isBillableToClient = true
        ))

        // --- SUBCONTRACT
        val samSubcon = input.samBreakdown.subcontractMinutes()
        if (!samSubcon.isZero) {
            buckets.add(CostBucket(
                kind = CostBucketKind.SUBCONTRACT,
                label = "Jasa Subkon Eksternal",
                amountPerUnit = params.subcontractRatePerSamMinute * samSubcon,
                isBillableToClient = true
            ))
        }

        // --- OVERHEAD
        if (!params.overheadPerUnit.isZero) {
            buckets.add(CostBucket(
                kind = CostBucketKind.OVERHEAD,
                label = "Overhead Pabrik",
                amountPerUnit = params.overheadPerUnit,
                isBillableToClient = true
            ))
        }

        // --- PACKAGING
        if (!params.packingCostPerUnit.isZero) {
            buckets.add(CostBucket(
                kind = CostBucketKind.PACKAGING,
                label = "Kemasan & Label",
                amountPerUnit = params.packingCostPerUnit,
                isBillableToClient = true
            ))
        }

        // --- MARKUP RETAIL: hargaRetail = hppBase × (1 + retailMarkupRatio)
        val hppBase = Money.sum(buckets.filter { it.isBillableToClient }.map { it.amountPerUnit }, currency)

        val retailMarkupAmount = if (params.retailMarkupRatio.isPositive) {
            val amount = hppBase * params.retailMarkupRatio
            buckets.add(CostBucket(
                kind = CostBucketKind.MARGIN,
                label = "Markup Retail (${params.retailMarkupRatio.asPercentageString()})",
                amountPerUnit = amount,
                isBillableToClient = false  // WAJIB false — margin tidak ditagihkan per Kontrak
            ))
            amount
        } else {
            Money.zero(currency)
        }

        // --- MARKETPLACE FEE: fee = hargaRetail × feeRatio
        // hargaRetail = hppBase + retailMarkupAmount (belum termasuk fee-nya sendiri)
        if (params.marketplaceFeeRatio.isPositive) {
            val hargaRetail = hppBase + retailMarkupAmount
            val feeAmount = hargaRetail * params.marketplaceFeeRatio
            buckets.add(CostBucket(
                kind = CostBucketKind.MARKETPLACE_FEE,
                label = "Biaya Marketplace (${params.marketplaceFeeRatio.asPercentageString()} × harga retail)",
                amountPerUnit = feeAmount,
                isBillableToClient = false  // Fee dipotong dari pendapatan, bukan ditagih ke pembeli
            ))
        }

        CostingFormulaOutput(
            buckets = buckets,
            marginRatio = params.retailMarkupRatio,   // retailMarkup sebagai pengganti marginRatio
            consignedMaterialValueHandled = bomPreview.consignedNotionalValue,
            roundingResidual = Money.zero(currency),
            warnings = warnings
        )
    }
}
