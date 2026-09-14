package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class BomLine(
    val lineId: String,
    val material: MaterialRef,
    val category: MaterialCategory,
    val netQuantityPerGarment: Quantity,
    val wasteAllowance: Ratio = Ratio.ZERO,
    val ownership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val notes: String = ""
) {
    val grossQuantityPerGarment: Quantity
        get() = netQuantityPerGarment + (netQuantityPerGarment * wasteAllowance)

    fun grossFor(orderQuantity: Long): Quantity =
        grossQuantityPerGarment * orderQuantity
}

data class LaborOperation(
    val operationId: String,
    val name: String,
    val samMinutes: Ratio,
    val workstation: String,
    val isSubcontracted: Boolean = false
)

data class SizeYieldFactor(
    val sizeLabel: String,
    val scale: Ratio = Ratio.ONE,
    val orderedQuantity: Long = 0L
)

data class TechPackAndYieldData(
    val techPackId: String,
    val tenantId: TenantId,
    val sourceSampleSpecId: String? = null,
    val styleCode: String,
    val styleName: String,
    val bomLines: List<BomLine> = emptyList(),
    val laborOperations: List<LaborOperation> = emptyList(),
    val sizeYieldFactors: List<SizeYieldFactor> = emptyList(),
    val preparedAt: Instant
) : ModulePortPayload {
    override val portDataType: String = PortDataTypeRegistry.TECH_PACK_AND_YIELD_DATA
}
