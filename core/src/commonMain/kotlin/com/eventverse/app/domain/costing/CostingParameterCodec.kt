package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.pipeline.CostingBehavior

/**
 * Codec untuk mengurai parameter kalkulasi HPP dari `Map<String, String>`.
 *
 * Sumber peta ini bisa dari:
 * - `customFormulaParameters` node pipeline (tersimpan di DB sebagai JSONB)
 * - HTTP request body parameter override
 * - Seed V10 di `formulaParameters` kolom
 *
 * ## Kunci Wire Contract
 * Kunci-kunci ini adalah **kontrak wire** yang tidak boleh diubah tanpa migrasi DB:
 * mereka identik dengan kunci yang ter-seed di `V10__fix_tenant_pipeline_seed_and_business_preset.sql`.
 *
 * ## Aturan Parsing
 * - Menerima `%` di ekor: `"18.5%"` → 18.5
 * - Menerima koma desimal: `"18,5"` → 18.5
 * - **TIDAK PERNAH** menafsirkan `"0.185"` sebagai 18.5% — kunci `*Percent` selalu dibaca
 *   sebagai persen langsung (18.5 → 18.5%), bukan desimal (0.185 → 18.5%)
 * - Kunci yang tidak terbaca jatuh ke nilai default yang di-log sebagai peringatan
 */
object CostingParameterCodec {

    // Kunci wire — identik dengan seed V10
    private const val KEY_MARGIN_PERCENT = "marginPercent"
    private const val KEY_OVERHEAD_PER_PCS_IDR = "overheadPerPcsIdr"
    private const val KEY_FABRIC_WASTAGE_TOLERANCE_PERCENT = "fabricWastageTolerancePercent"
    private const val KEY_SERVICE_FEE_PER_PCS_IDR = "serviceFeePerPcsIdr"
    private const val KEY_INCLUDE_FABRIC_COST = "includeFabricCost"
    private const val KEY_RETAIL_MARKUP_PERCENT = "retailMarkupPercent"
    private const val KEY_MARKETPLACE_FEE_PERCENT = "marketplaceFeePercent"
    private const val KEY_PACKING_COST_PER_ORDER_IDR = "packingCostPerOrderIdr"
    private const val KEY_PACKING_COST_PER_PCS_IDR = "packingCostPerPcsIdr"
    // Dua kunci baru yang tidak ada di seed V10 (fallback ke default sistem)
    private const val KEY_LABOR_RATE_PER_SAM_MINUTE_IDR = "laborRatePerSamMinuteIdr"
    private const val KEY_SUBCONTRACT_RATE_PER_SAM_MINUTE_IDR = "subcontractRatePerSamMinuteIdr"

    /**
     * Membaca nilai [Double] dari peta parameter, menoleransi %, koma, dan spasi.
     * Mengembalikan null jika kunci tidak ada atau nilai tidak terbaca.
     */
    private fun Map<String, String>.readDouble(key: String): Double? {
        val raw = this[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            raw.trimEnd('%').replace(',', '.').trim().toDouble()
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun Map<String, String>.readLong(key: String): Long? {
        val raw = this[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return raw.replace(",", "").toLongOrNull()
    }

    private fun Map<String, String>.readBoolean(key: String): Boolean? {
        val raw = this[key]?.trim()?.lowercase() ?: return null
        return when (raw) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    }

    /**
     * Melakukan merge dari [nodeParams] dan [rateCard] di atas [base] defaults.
     *
     * Presedensi: `base` < `nodeParams` < `rateCard` < `sheetOverrides`.
     * Hasil merge dikemas dalam [ResolvedCostingParameters] dengan [provenance] lengkap.
     *
     * @param warnings Mutable list yang diisi peringatan jika ada nilai yang tidak terbaca.
     */
    fun merge(
        base: ResolvedCostingParameters,
        nodeParams: Map<String, String> = emptyMap(),
        rateCard: CostingRateCard? = null,
        sheetOverrides: Map<String, String> = emptyMap(),
        warnings: MutableList<String> = mutableListOf()
    ): ResolvedCostingParameters {
        val provenance = base.provenance.toMutableMap()

        fun <T> resolve(
            fieldName: String,
            baseValue: T,
            nodeValue: T?,
            rateCardValue: T?,
            overrideValue: T?
        ): T {
            return when {
                overrideValue != null -> {
                    provenance[fieldName] = CostingParameterSource.SHEET_OVERRIDE
                    overrideValue
                }
                rateCardValue != null -> {
                    provenance[fieldName] = CostingParameterSource.RATE_CARD
                    rateCardValue
                }
                nodeValue != null -> {
                    provenance[fieldName] = CostingParameterSource.PIPELINE_NODE
                    nodeValue
                }
                else -> baseValue
            }
        }

        // Node params parsing
        val nodeMarginPct = nodeParams.readDouble(KEY_MARGIN_PERCENT)
        val nodeOverheadIdr = nodeParams.readLong(KEY_OVERHEAD_PER_PCS_IDR)
        val nodeWastagePct = nodeParams.readDouble(KEY_FABRIC_WASTAGE_TOLERANCE_PERCENT)
        val nodeServiceFeeIdr = nodeParams.readLong(KEY_SERVICE_FEE_PER_PCS_IDR)
        val nodeIncludeFabric = nodeParams.readBoolean(KEY_INCLUDE_FABRIC_COST)
        val nodeRetailMarkupPct = nodeParams.readDouble(KEY_RETAIL_MARKUP_PERCENT)
        val nodeMarketplaceFeePct = nodeParams.readDouble(KEY_MARKETPLACE_FEE_PERCENT)
        val nodePackingOrderIdr = nodeParams.readLong(KEY_PACKING_COST_PER_ORDER_IDR)
        val nodePackingPcsIdr = nodeParams.readLong(KEY_PACKING_COST_PER_PCS_IDR)
        val nodeLaborRateIdr = nodeParams.readLong(KEY_LABOR_RATE_PER_SAM_MINUTE_IDR)
        val nodeSubcontractRateIdr = nodeParams.readLong(KEY_SUBCONTRACT_RATE_PER_SAM_MINUTE_IDR)

        // Warn tentang kunci yang tidak terbaca (ada tapi nilainya invalid)
        nodeParams.keys.forEach { key ->
            if (key in listOf(KEY_MARGIN_PERCENT, KEY_OVERHEAD_PER_PCS_IDR, KEY_FABRIC_WASTAGE_TOLERANCE_PERCENT,
                    KEY_SERVICE_FEE_PER_PCS_IDR, KEY_INCLUDE_FABRIC_COST, KEY_RETAIL_MARKUP_PERCENT,
                    KEY_MARKETPLACE_FEE_PERCENT, KEY_PACKING_COST_PER_ORDER_IDR, KEY_PACKING_COST_PER_PCS_IDR,
                    KEY_LABOR_RATE_PER_SAM_MINUTE_IDR, KEY_SUBCONTRACT_RATE_PER_SAM_MINUTE_IDR)) return@forEach
            warnings.add("Kunci parameter tidak dikenal: '$key' - diabaikan")
        }

        // Override params parsing
        val ovMarginPct = sheetOverrides.readDouble(KEY_MARGIN_PERCENT)
        val ovOverheadIdr = sheetOverrides.readLong(KEY_OVERHEAD_PER_PCS_IDR)
        val ovServiceFeeIdr = sheetOverrides.readLong(KEY_SERVICE_FEE_PER_PCS_IDR)
        val ovLaborRateIdr = sheetOverrides.readLong(KEY_LABOR_RATE_PER_SAM_MINUTE_IDR)
        val ovPackingPcsIdr = sheetOverrides.readLong(KEY_PACKING_COST_PER_PCS_IDR)
        val ovPackingOrderIdr = sheetOverrides.readLong(KEY_PACKING_COST_PER_ORDER_IDR)

        return base.copy(
            laborRatePerSamMinute = resolve(
                "laborRatePerSamMinute", base.laborRatePerSamMinute,
                nodeLaborRateIdr?.let { Money.idr(it) },
                rateCard?.laborRatePerSamMinute,
                ovLaborRateIdr?.let { Money.idr(it) }
            ),
            subcontractRatePerSamMinute = resolve(
                "subcontractRatePerSamMinute", base.subcontractRatePerSamMinute,
                nodeSubcontractRateIdr?.let { Money.idr(it) },
                rateCard?.subcontractRatePerSamMinute,
                null
            ),
            serviceFeePerUnit = resolve(
                "serviceFeePerUnit", base.serviceFeePerUnit,
                nodeServiceFeeIdr?.let { Money.idr(it) },
                rateCard?.serviceFeePerUnit,
                ovServiceFeeIdr?.let { Money.idr(it) }
            ),
            overheadPerUnit = resolve(
                "overheadPerUnit", base.overheadPerUnit,
                nodeOverheadIdr?.let { Money.idr(it) },
                rateCard?.overheadPerUnit,
                ovOverheadIdr?.let { Money.idr(it) }
            ),
            packingCostPerUnit = resolve(
                "packingCostPerUnit", base.packingCostPerUnit,
                nodePackingPcsIdr?.let { Money.idr(it) },
                rateCard?.packingCostPerUnit,
                ovPackingPcsIdr?.let { Money.idr(it) }
            ),
            packingCostPerOrder = resolve(
                "packingCostPerOrder", base.packingCostPerOrder,
                nodePackingOrderIdr?.let { Money.idr(it) },
                rateCard?.packingCostPerOrder,
                ovPackingOrderIdr?.let { Money.idr(it) }
            ),
            marginRatio = resolve(
                "marginRatio", base.marginRatio,
                nodeMarginPct?.let { Ratio.percent(it) },
                rateCard?.marginRatio,
                ovMarginPct?.let { Ratio.percent(it) }
            ),
            retailMarkupRatio = resolve(
                "retailMarkupRatio", base.retailMarkupRatio,
                nodeRetailMarkupPct?.let { Ratio.percent(it) },
                rateCard?.retailMarkupRatio,
                null
            ),
            marketplaceFeeRatio = resolve(
                "marketplaceFeeRatio", base.marketplaceFeeRatio,
                nodeMarketplaceFeePct?.let { Ratio.percent(it) },
                rateCard?.marketplaceFeeRatio,
                null
            ),
            fabricWastageToleranceRatio = resolve(
                "fabricWastageToleranceRatio", base.fabricWastageToleranceRatio,
                nodeWastagePct?.let { Ratio.percent(it) },
                rateCard?.fabricWastageToleranceRatio,
                null
            ),
            includeFabricCost = resolve(
                "includeFabricCost", base.includeFabricCost,
                nodeIncludeFabric,
                rateCard?.includeFabricCost,
                null
            ),
            provenance = provenance
        )
    }

    /**
     * Mengkodekan parameter yang dapat dikustomisasi ke `Map<String, String>`.
     * Dipakai untuk menyimpan override per lembar atau parameter node.
     */
    fun encodeOverrides(params: ResolvedCostingParameters): Map<String, String> = buildMap {
        put(KEY_MARGIN_PERCENT, params.marginRatio.toDouble() * 100.0)
        put(KEY_OVERHEAD_PER_PCS_IDR, params.overheadPerUnit.toWholeUnits())
        put(KEY_SERVICE_FEE_PER_PCS_IDR, params.serviceFeePerUnit.toWholeUnits())
        put(KEY_INCLUDE_FABRIC_COST, params.includeFabricCost)
        put(KEY_RETAIL_MARKUP_PERCENT, params.retailMarkupRatio.toDouble() * 100.0)
        put(KEY_MARKETPLACE_FEE_PERCENT, params.marketplaceFeeRatio.toDouble() * 100.0)
        put(KEY_PACKING_COST_PER_ORDER_IDR, params.packingCostPerOrder.toWholeUnits())
        put(KEY_PACKING_COST_PER_PCS_IDR, params.packingCostPerUnit.toWholeUnits())
        put(KEY_LABOR_RATE_PER_SAM_MINUTE_IDR, params.laborRatePerSamMinute.toWholeUnits())
        put(KEY_SUBCONTRACT_RATE_PER_SAM_MINUTE_IDR, params.subcontractRatePerSamMinute.toWholeUnits())
    }.mapValues { (_, v) -> v.toString() }
}
