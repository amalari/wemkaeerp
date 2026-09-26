package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

enum class CostBucketKind(val displayName: String, val countsInCogs: Boolean) {
    MATERIAL("Bahan Baku & Trim", countsInCogs = true),
    LABOR("Tenaga Kerja Langsung", countsInCogs = true),
    SUBCONTRACT("Jasa Subkon Eksternal", countsInCogs = true),
    OVERHEAD("Overhead Pabrik / Operasional", countsInCogs = true),
    PACKAGING("Kemasan & Finishing", countsInCogs = true),
    MARKETPLACE_FEE("Biaya Marketplace / Distribusi", countsInCogs = false),
    MARGIN("Margin Laba / Keuntungan", countsInCogs = false);
}

data class CostBucket(
    val kind: CostBucketKind,
    val label: String,
    val amountPerUnit: Money,
    val ownership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val isBillableToClient: Boolean = true,
    /** BOM lineId(s) atau ref lain yang menghasilkan bucket ini — wajib untuk drill-down UI dan audit. */
    val sourceRefs: List<String> = emptyList()
) {
    init {
        require(!(ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL && isBillableToClient)) {
            "Bahan konsinyasi '$label' tidak boleh ditagihkan ke klien (Kontrak 4)."
        }
        require(ownership != StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL || amountPerUnit.isZero) {
            "Bahan konsinyasi '$label' harus bernilai 0 di neraca pabrik (Kontrak 3)."
        }
    }

    companion object {
        fun material(
            label: String,
            amountPerUnit: Money,
            ownership: StockOwnershipSemantics,
            behavior: CostingBehavior
        ): CostBucket {
            val isBillable = when (ownership) {
                StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL -> false
                StockOwnershipSemantics.OWNED_RAW_MATERIAL -> behavior != CostingBehavior.SERVICE_FEE_ONLY
                StockOwnershipSemantics.NON_STOCK_SERVICE -> true
                StockOwnershipSemantics.INTERNAL_FINISHED_GOODS -> false
            }
            val safeAmount = if (ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
                Money.zero(amountPerUnit.currency)
            } else {
                amountPerUnit
            }
            return CostBucket(
                kind = CostBucketKind.MATERIAL,
                label = label,
                amountPerUnit = safeAmount,
                ownership = ownership,
                isBillableToClient = isBillable
            )
        }
    }
}

data class CostingCalculationResult(
    val costingId: String,
    val tenantId: TenantId,
    val techPackId: String,
    val orderQuantity: Long,
    val behavior: CostingBehavior,
    val buckets: List<CostBucket> = emptyList(),
    val marginRatio: Ratio = Ratio.ZERO,
    val formulaParameters: Map<String, String> = emptyMap(),
    /**
     * Nilai total per order dari kain konsinyasi klien — bukan untuk penagihan,
     * tapi untuk rekonsiliasi perca dan klaim kerusakan. Ini adalah total per order
     * (bukan per pcs), konsisten dengan [BomCostPreview.consignedNotionalValue].
     */
    val consignedMaterialValueHandled: Money = Money.zero(),
    val calculatedAt: Instant,
    /**
     * Sisa sen dari pembagian biaya per-order (mis. packingCostPerOrder) ke biaya per-pcs.
     * packingCostPerOrder = 4200 dengan qty 9 → perUnit = 467, residual = 4200 - (467*9) = 97 sen.
     * `billableTotal` menambahkan residual ini agar total order tepat ke sen.
     */
    val roundingResidual: Money = Money.zero(),
    /** Mata uang yang dipakai di seluruh bucket dan perhitungan. Default IDR. */
    val currency: CurrencyCode = CurrencyCode.IDR,
) : ModulePortPayload {
    override val portDataType: String = PortDataTypeRegistry.COSTING_CALCULATION_RESULT

    val cogsPerUnit: Money
        get() {
            val cogsBuckets = buckets.filter { it.kind.countsInCogs }
            return Money.sum(cogsBuckets.map { it.amountPerUnit }, currency)
        }

    val billablePerUnit: Money
        get() {
            val billableBuckets = buckets.filter { it.isBillableToClient }
            return Money.sum(billableBuckets.map { it.amountPerUnit }, currency)
        }

    /**
     * Total tagihan per order = (billablePerUnit × orderQuantity) + roundingResidual.
     * Residual memastikan biaya per-order (mis. packing 4200/order) tidak menciptakan
     * uang dari udara saat qty tidak habis dibagi.
     */
    val billableTotal: Money
        get() = (billablePerUnit * orderQuantity) + roundingResidual

    val sellingPricePerUnit: Money
        get() = billablePerUnit + (billablePerUnit * marginRatio)
}
