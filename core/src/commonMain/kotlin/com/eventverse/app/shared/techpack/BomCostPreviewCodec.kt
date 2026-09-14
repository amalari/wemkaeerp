package com.eventverse.app.shared.techpack

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.BomCostPreview
import com.eventverse.app.domain.techpack.BomLineCost
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.contracts.MaterialRefCodec
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.masterdata.MaterialPriceCodec
import kotlinx.datetime.Clock

object BomCostPreviewCodec {

    fun encode(preview: BomCostPreview): JsonValue.Obj = jsonObjectOf(
        "techPackId" to jsonOf(preview.techPackId.value),
        "orderQuantity" to jsonOf(preview.orderQuantity),
        "at" to jsonOf(preview.at.toString()),
        "currency" to jsonOf(preview.currency.code),
        "materialCostPerGarment" to MeasureCodec.encodeMoney(preview.materialCostPerGarment),
        "materialCostTotal" to MeasureCodec.encodeMoney(preview.materialCostTotal),
        "consignedNotionalValue" to MeasureCodec.encodeMoney(preview.consignedNotionalValue),
        "isComplete" to jsonOf(preview.isComplete),
        "lines" to jsonArrayOf(preview.lines.map(::encodeLineCost))
    )

    fun decode(obj: JsonValue.Obj): BomCostPreview {
        val techPackId = TechPackId(obj.string("techPackId") ?: "")
        val orderQuantity = obj.long("orderQuantity") ?: 1L
        val at = DateTimeCodec.parseInstantOrFallback(obj.string("at"), Clock.System.now())
        val currency = obj.string("currency")?.let { codeStr ->
            runCatching { CurrencyCode.valueOf(codeStr) }.getOrNull()
        } ?: CurrencyCode.IDR
        val lines = obj.objectArray("lines").map(::decodeLineCost)

        return BomCostPreview(
            techPackId = techPackId,
            orderQuantity = orderQuantity,
            at = at,
            currency = currency,
            lines = lines
        )
    }

    fun encodeLineCost(line: BomLineCost): JsonValue.Obj = jsonObjectOf(
        "lineId" to jsonOf(line.lineId),
        "material" to MaterialRefCodec.encode(line.material),
        "category" to jsonOf(line.category.code),
        "grossQuantityPerGarment" to MeasureCodec.encodeQuantity(line.grossQuantityPerGarment),
        "grossQuantityTotal" to MeasureCodec.encodeQuantity(line.grossQuantityTotal),
        "resolvedPrice" to (line.resolvedPrice?.let(MaterialPriceCodec::encodeResolvedPrice) ?: JsonValue.Null),
        "costPerGarment" to MeasureCodec.encodeMoney(line.costPerGarment),
        "costTotal" to MeasureCodec.encodeMoney(line.costTotal),
        "ownership" to jsonOf(line.ownership.code),
        "isPriced" to jsonOf(line.isPriced)
    )

    fun decodeLineCost(obj: JsonValue.Obj): BomLineCost {
        val lineId = obj.string("lineId") ?: ""
        val material = obj.obj("material")?.let(MaterialRefCodec::decode)
            ?: com.eventverse.app.domain.contracts.MaterialRef.unresolved("")
        val category = MaterialCategory.fromCode(obj.string("category")) ?: MaterialCategory.YARN
        val grossPerGarment = MeasureCodec.decodeQuantity(obj.obj("grossQuantityPerGarment"))
        val grossTotal = MeasureCodec.decodeQuantity(obj.obj("grossQuantityTotal"))
        val resolvedPrice = obj.obj("resolvedPrice")?.let(MaterialPriceCodec::decodeResolvedPrice)
        val costPerGarment = MeasureCodec.decodeMoney(obj.obj("costPerGarment"))
        val costTotal = MeasureCodec.decodeMoney(obj.obj("costTotal"))
        val ownership = obj.string("ownership")?.let { codeStr ->
            StockOwnershipSemantics.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

        return BomLineCost(
            lineId = lineId,
            material = material,
            category = category,
            grossQuantityPerGarment = grossPerGarment,
            grossQuantityTotal = grossTotal,
            resolvedPrice = resolvedPrice,
            costPerGarment = costPerGarment,
            costTotal = costTotal,
            ownership = ownership
        )
    }
}
