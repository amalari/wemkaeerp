package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.pipeline.CostingBehavior

/**
 * Parameter kalkulasi yang sudah di-resolve dari 4 sumber dengan presedensi naik:
 *
 * ```
 * 1. defaultsFor(behavior)         — hardcoded default sistem
 * 2. customFormulaParameters node  — konfigurasi V10 / pipeline node tenant
 * 3. CostingRateCard               — rate card ber-tanggal-berlaku per tenant
 * 4. override per lembar           — override manual saat membuat draft
 * ```
 *
 * Semua field **non-null** karena merge sudah memilih nilai terbaik yang tersedia.
 * [provenance] mencatat sumber mana yang menang untuk tiap kunci — dasar audit
 * saat berselisih dengan klien atau auditor.
 *
 * ## Mengapa provenance penting?
 * `submitForApproval` menolak jika `provenance` menunjukkan `HARDCODED_DEFAULT`
 * untuk kunci yang menentukan (margin di FOB, ongkos jasa di CMT) — ini mencegah
 * HPP yang margin-nya 0 karena rate card belum diisi lolos menjadi komitmen komersial.
 */
data class ResolvedCostingParameters(
    val behavior: CostingBehavior,

    /** Rp per SAM-menit untuk operator in-house. */
    val laborRatePerSamMinute: Money,

    /** Rp per SAM-menit untuk operasi subkon. */
    val subcontractRatePerSamMinute: Money,

    /** Biaya jasa per pcs (khusus SERVICE_FEE_ONLY). */
    val serviceFeePerUnit: Money,

    /** Overhead pabrik per pcs. */
    val overheadPerUnit: Money,

    /** Biaya packing per pcs. */
    val packingCostPerUnit: Money,

    /** Biaya packing per order (dialokasi + residual). */
    val packingCostPerOrder: Money,

    /** Rasio margin atas billablePerUnit. */
    val marginRatio: Ratio,

    /** Markup retail: hargaRetail = billable × (1 + retailMarkupRatio). */
    val retailMarkupRatio: Ratio,

    /**
     * Fee marketplace dihitung di atas **harga retail**, bukan HPP.
     * 6.5% × (HPP × 1.65) — bukan 6.5% × HPP. Ini sirkularitas yang wajib dikunci test.
     */
    val marketplaceFeeRatio: Ratio,

    /** Toleransi susut kain (menambah gross requirement BOM). */
    val fabricWastageToleranceRatio: Ratio,

    /**
     * True jika biaya kain masuk HPP.
     * Pada CMT (SERVICE_FEE_ONLY), false — tapi `includeFabricCost = false` tidak bisa
     * membuat kain CONSIGNED jadi tertagih (invarian di `CostBucket.init` lebih kuat).
     */
    val includeFabricCost: Boolean,

    /**
     * Provenance tiap kunci — untuk audit dan validasi pra-approve.
     * Key = nama field sebagai String, Value = sumber yang menang.
     */
    val provenance: Map<String, CostingParameterSource> = emptyMap()
) {
    companion object {
        /**
         * Default sistem untuk tiap [CostingBehavior].
         * Nilai ini dipakai kalau tidak ada sumber lain yang mengisi parameter.
         *
         * FOB: margin 18.5% (seed V10), overhead 2000 IDR/pcs
         * CMT: serviceFee 0 (harus diisi di rate card), overhead 0
         * D2C: markup 62%, marketplaceFee 6.5%
         */
        fun defaultsFor(behavior: CostingBehavior): ResolvedCostingParameters = when (behavior) {
            CostingBehavior.FULL_PACKAGE_COGS -> ResolvedCostingParameters(
                behavior = behavior,
                laborRatePerSamMinute = Money.idrMinor(33700L),   // Rp 337/menit
                subcontractRatePerSamMinute = Money.idrMinor(40000L), // Rp 400/menit
                serviceFeePerUnit = Money.zero(),
                overheadPerUnit = Money.idr(2_000L),
                packingCostPerUnit = Money.zero(),
                packingCostPerOrder = Money.zero(),
                marginRatio = Ratio.percent(18.5),
                retailMarkupRatio = Ratio.ZERO,
                marketplaceFeeRatio = Ratio.ZERO,
                fabricWastageToleranceRatio = Ratio.ZERO,
                includeFabricCost = true,
                provenance = CostingParameterSource.entries.associate { it.name to CostingParameterSource.HARDCODED_DEFAULT }
            )
            CostingBehavior.SERVICE_FEE_ONLY -> ResolvedCostingParameters(
                behavior = behavior,
                laborRatePerSamMinute = Money.idrMinor(40000L),
                subcontractRatePerSamMinute = Money.idrMinor(40000L),
                serviceFeePerUnit = Money.zero(),   // WAJIB diisi di rate card
                overheadPerUnit = Money.zero(),
                packingCostPerUnit = Money.zero(),
                packingCostPerOrder = Money.zero(),
                marginRatio = Ratio.ZERO,
                retailMarkupRatio = Ratio.ZERO,
                marketplaceFeeRatio = Ratio.ZERO,
                fabricWastageToleranceRatio = Ratio.ZERO,
                includeFabricCost = false,
                provenance = CostingParameterSource.entries.associate { it.name to CostingParameterSource.HARDCODED_DEFAULT }
            )
            CostingBehavior.RETAIL_VALUATION_WITH_FEES -> ResolvedCostingParameters(
                behavior = behavior,
                laborRatePerSamMinute = Money.idrMinor(33700L),
                subcontractRatePerSamMinute = Money.idrMinor(40000L),
                serviceFeePerUnit = Money.zero(),
                overheadPerUnit = Money.idr(2_000L),
                packingCostPerUnit = Money.zero(),
                packingCostPerOrder = Money.zero(),
                marginRatio = Ratio.percent(18.5),
                retailMarkupRatio = Ratio.percent(62.0),           // HPP × 1.62 = hargaRetail
                marketplaceFeeRatio = Ratio.percent(6.5),
                fabricWastageToleranceRatio = Ratio.ZERO,
                includeFabricCost = true,
                provenance = CostingParameterSource.entries.associate { it.name to CostingParameterSource.HARDCODED_DEFAULT }
            )
            CostingBehavior.INDIRECT_OVERHEAD -> ResolvedCostingParameters(
                behavior = behavior,
                laborRatePerSamMinute = Money.zero(),
                subcontractRatePerSamMinute = Money.zero(),
                serviceFeePerUnit = Money.zero(),
                overheadPerUnit = Money.idr(2_000L),
                packingCostPerUnit = Money.zero(),
                packingCostPerOrder = Money.zero(),
                marginRatio = Ratio.ZERO,
                retailMarkupRatio = Ratio.ZERO,
                marketplaceFeeRatio = Ratio.ZERO,
                fabricWastageToleranceRatio = Ratio.ZERO,
                includeFabricCost = false,
                provenance = CostingParameterSource.entries.associate { it.name to CostingParameterSource.HARDCODED_DEFAULT }
            )
        }

        /**
         * Kunci provenance yang dianggap "kritis" per behavior.
         * `submitForApproval` menolak jika kunci ini masih `HARDCODED_DEFAULT`.
         */
        fun criticalProvenanceKeys(behavior: CostingBehavior): Set<String> = when (behavior) {
            CostingBehavior.FULL_PACKAGE_COGS -> setOf("marginRatio", "laborRatePerSamMinute")
            CostingBehavior.SERVICE_FEE_ONLY -> setOf("serviceFeePerUnit", "laborRatePerSamMinute")
            CostingBehavior.RETAIL_VALUATION_WITH_FEES -> setOf("marginRatio", "retailMarkupRatio")
            CostingBehavior.INDIRECT_OVERHEAD -> setOf("overheadPerUnit")
        }
    }
}
