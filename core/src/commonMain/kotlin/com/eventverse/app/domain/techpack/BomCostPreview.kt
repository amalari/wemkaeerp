package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.ResolvedPrice
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlinx.datetime.Instant

data class BomLineCost(
    val lineId: String,
    val material: MaterialRef,
    val category: MaterialCategory,
    val grossQuantityPerGarment: Quantity,
    val grossQuantityTotal: Quantity,
    val resolvedPrice: ResolvedPrice?,
    val costPerGarment: Money,
    val costTotal: Money,
    val ownership: StockOwnershipSemantics
) {
    val isPriced: Boolean get() = resolvedPrice != null
}

data class BomCostPreview(
    val techPackId: TechPackId,
    val orderQuantity: Long,
    val at: Instant,
    val currency: CurrencyCode = CurrencyCode.IDR,
    val lines: List<BomLineCost> = emptyList()
) {
    val materialCostPerGarment: Money
        get() {
            val billedLines = lines.filter { it.ownership != StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL }
            return Money.sum(billedLines.map { it.costPerGarment }, currency)
        }

    val materialCostTotal: Money
        get() {
            val billedLines = lines.filter { it.ownership != StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL }
            return Money.sum(billedLines.map { it.costTotal }, currency)
        }

    val unpricedLines: List<BomLineCost>
        get() = lines.filter { it.resolvedPrice == null && it.ownership != StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL }

    val consignedNotionalValue: Money
        get() {
            val consignedLines = lines.filter { it.ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL }
            return Money.sum(consignedLines.map { it.costTotal }, currency)
        }

    val isComplete: Boolean
        get() = unpricedLines.isEmpty()
}
