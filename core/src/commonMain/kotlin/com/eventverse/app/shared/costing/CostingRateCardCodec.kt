package com.eventverse.app.shared.costing

import com.eventverse.app.domain.costing.CostingRateCard
import com.eventverse.app.domain.costing.CostingRateCardId
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Clock

object CostingRateCardCodec {

    fun encode(card: CostingRateCard): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(card.id.value),
        "tenantId" to jsonOf(card.tenantId.value),
        "behavior" to jsonOf(card.behavior.code),
        "version" to jsonOf(card.version.toLong()),
        "effectiveFrom" to jsonOf(card.effectiveFrom.toString()),
        "effectiveTo" to (card.effectiveTo?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "description" to jsonOf(card.description),
        "laborRatePerSamMinute" to (card.laborRatePerSamMinute?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null),
        "subcontractRatePerSamMinute" to (card.subcontractRatePerSamMinute?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null),
        "serviceFeePerUnit" to (card.serviceFeePerUnit?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null),
        "overheadPerUnit" to (card.overheadPerUnit?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null),
        "packingCostPerUnit" to (card.packingCostPerUnit?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null),
        "packingCostPerOrder" to (card.packingCostPerOrder?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null),
        "marginRatio" to (card.marginRatio?.let { MeasureCodec.encodeRatio(it) } ?: JsonValue.Null),
        "retailMarkupRatio" to (card.retailMarkupRatio?.let { MeasureCodec.encodeRatio(it) } ?: JsonValue.Null),
        "marketplaceFeeRatio" to (card.marketplaceFeeRatio?.let { MeasureCodec.encodeRatio(it) } ?: JsonValue.Null),
        "fabricWastageToleranceRatio" to (card.fabricWastageToleranceRatio?.let { MeasureCodec.encodeRatio(it) } ?: JsonValue.Null),
        "includeFabricCost" to (card.includeFabricCost?.let { jsonOf(it) } ?: JsonValue.Null),
        "seededFromNodeId" to (card.seededFromNodeId?.let { jsonOf(it) } ?: JsonValue.Null),
        "createdByUserId" to (card.createdByUserId?.let { jsonOf(it) } ?: JsonValue.Null),
        "createdAt" to jsonOf(card.createdAt.toString()),
        "updatedAt" to jsonOf(card.updatedAt.toString())
    )

    fun decode(obj: JsonValue.Obj): CostingRateCard {
        val now = Clock.System.now()
        val id = CostingRateCardId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val behavior = obj.string("behavior")?.let { codeStr ->
            CostingBehavior.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: CostingBehavior.FULL_PACKAGE_COGS

        val version = (obj.long("version") ?: 1L).toInt()
        val effectiveFrom = DateTimeCodec.parseInstantOrFallback(obj.string("effectiveFrom"), now)
        val effectiveTo = obj.string("effectiveTo")?.let { DateTimeCodec.parseInstantOrNull(it) }
        val description = obj.string("description") ?: ""

        val laborRate = obj.obj("laborRatePerSamMinute")?.let { MeasureCodec.decodeMoney(it) }
        val subconRate = obj.obj("subcontractRatePerSamMinute")?.let { MeasureCodec.decodeMoney(it) }
        val serviceFee = obj.obj("serviceFeePerUnit")?.let { MeasureCodec.decodeMoney(it) }
        val overhead = obj.obj("overheadPerUnit")?.let { MeasureCodec.decodeMoney(it) }
        val packingUnit = obj.obj("packingCostPerUnit")?.let { MeasureCodec.decodeMoney(it) }
        val packingOrder = obj.obj("packingCostPerOrder")?.let { MeasureCodec.decodeMoney(it) }
        val margin = obj.obj("marginRatio")?.let { MeasureCodec.decodeRatio(it) }
        val retailMarkup = obj.obj("retailMarkupRatio")?.let { MeasureCodec.decodeRatio(it) }
        val marketplaceFee = obj.obj("marketplaceFeeRatio")?.let { MeasureCodec.decodeRatio(it) }
        val fabricWaste = obj.obj("fabricWastageToleranceRatio")?.let { MeasureCodec.decodeRatio(it) }
        val includeFabric = obj.boolean("includeFabricCost")
        val seededFromNode = obj.string("seededFromNodeId")
        val createdBy = obj.string("createdByUserId")
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), now)
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), now)

        return CostingRateCard(
            id = id,
            tenantId = tenantId,
            behavior = behavior,
            version = version,
            effectiveFrom = effectiveFrom,
            effectiveTo = effectiveTo,
            description = description,
            laborRatePerSamMinute = laborRate,
            subcontractRatePerSamMinute = subconRate,
            serviceFeePerUnit = serviceFee,
            overheadPerUnit = overhead,
            packingCostPerUnit = packingUnit,
            packingCostPerOrder = packingOrder,
            marginRatio = margin,
            retailMarkupRatio = retailMarkup,
            marketplaceFeeRatio = marketplaceFee,
            fabricWastageToleranceRatio = fabricWaste,
            includeFabricCost = includeFabric,
            seededFromNodeId = seededFromNode,
            createdByUserId = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
