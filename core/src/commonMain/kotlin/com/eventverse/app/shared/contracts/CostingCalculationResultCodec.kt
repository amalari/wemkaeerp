package com.eventverse.app.shared.contracts

import com.eventverse.app.domain.contracts.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object CostingCalculationResultCodec {

    fun encode(result: CostingCalculationResult): JsonValue.Obj = jsonObjectOf(
        "costingId" to jsonOf(result.costingId),
        "tenantId" to jsonOf(result.tenantId.value),
        "techPackId" to jsonOf(result.techPackId),
        "orderQuantity" to jsonOf(result.orderQuantity),
        "behavior" to jsonOf(result.behavior.code),
        "buckets" to jsonArrayOf(result.buckets.map(::encodeCostBucket)),
        "marginRatio" to MeasureCodec.encodeRatio(result.marginRatio),
        "formulaParameters" to JsonValue.Obj(result.formulaParameters.mapValues { jsonOf(it.value) }),
        "consignedMaterialValueHandled" to MeasureCodec.encodeMoney(result.consignedMaterialValueHandled),
        "calculatedAt" to jsonOf(result.calculatedAt.toString()),
        "cogsPerUnit" to MeasureCodec.encodeMoney(result.cogsPerUnit),
        "billablePerUnit" to MeasureCodec.encodeMoney(result.billablePerUnit),
        "billableTotal" to MeasureCodec.encodeMoney(result.billableTotal),
        "sellingPricePerUnit" to MeasureCodec.encodeMoney(result.sellingPricePerUnit)
    )

    fun decode(obj: JsonValue.Obj): CostingCalculationResult {
        val costingId = obj.string("costingId") ?: ""
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val techPackId = obj.string("techPackId") ?: ""
        val orderQuantity = obj.long("orderQuantity") ?: 1L
        val behavior = obj.string("behavior")?.let { codeStr ->
            CostingBehavior.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: CostingBehavior.FULL_PACKAGE_COGS

        val buckets = obj.objectArray("buckets").map(::decodeCostBucket)
        val marginRatio = MeasureCodec.decodeRatio(obj.obj("marginRatio"))
        val formulaParams = obj.obj("formulaParameters")?.entries?.mapNotNull { (k, v) ->
            when (v) {
                is JsonValue.Str -> k to v.value
                else -> null
            }
        }?.toMap() ?: emptyMap()

        val consignedHandled = MeasureCodec.decodeMoney(obj.obj("consignedMaterialValueHandled"))
        val calculatedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("calculatedAt"),
            kotlinx.datetime.Clock.System.now()
        )

        return CostingCalculationResult(
            costingId = costingId,
            tenantId = tenantId,
            techPackId = techPackId,
            orderQuantity = orderQuantity,
            behavior = behavior,
            buckets = buckets,
            marginRatio = marginRatio,
            formulaParameters = formulaParams,
            consignedMaterialValueHandled = consignedHandled,
            calculatedAt = calculatedAt
        )
    }

    private fun encodeCostBucket(b: CostBucket): JsonValue.Obj = jsonObjectOf(
        "kind" to jsonOf(b.kind.name),
        "label" to jsonOf(b.label),
        "amountPerUnit" to MeasureCodec.encodeMoney(b.amountPerUnit),
        "ownership" to jsonOf(b.ownership.code),
        "isBillableToClient" to jsonOf(b.isBillableToClient)
    )

    private fun decodeCostBucket(obj: JsonValue.Obj): CostBucket {
        val kind = obj.string("kind")?.let { runCatching { CostBucketKind.valueOf(it) }.getOrNull() }
            ?: CostBucketKind.MATERIAL
        val label = obj.string("label") ?: ""
        val amount = MeasureCodec.decodeMoney(obj.obj("amountPerUnit"))
        val ownership = obj.string("ownership")?.let { codeStr ->
            StockOwnershipSemantics.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL
        val isBillable = obj.boolean("isBillableToClient") ?: (ownership != StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL)

        return CostBucket(
            kind = kind,
            label = label,
            amountPerUnit = amount,
            ownership = ownership,
            isBillableToClient = isBillable
        )
    }
}
