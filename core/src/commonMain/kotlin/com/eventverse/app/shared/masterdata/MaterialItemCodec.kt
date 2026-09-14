package com.eventverse.app.shared.masterdata

import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object MaterialItemCodec {

    fun encodeConversion(conv: UomConversion): JsonValue.Obj = jsonObjectOf(
        "from" to jsonOf(conv.from.code),
        "equivalent" to MeasureCodec.encodeQuantity(conv.equivalent)
    )

    fun decodeConversion(obj: JsonValue.Obj): UomConversion {
        val from = UnitOfMeasure.fromCode(obj.string("from")) ?: UnitOfMeasure.PIECE
        val equivalent = MeasureCodec.decodeQuantity(obj.obj("equivalent"))
        return UomConversion(from, equivalent)
    }

    fun encodeMaterial(item: MaterialItem): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(item.id.value),
        "tenantId" to jsonOf(item.tenantId.value),
        "code" to jsonOf(item.code.value),
        "name" to jsonOf(item.name),
        "category" to jsonOf(item.category.code),
        "baseUom" to jsonOf(item.baseUom.code),
        "alternateUoms" to jsonArrayOf(item.alternateUoms.map(::encodeConversion)),
        "defaultOwnership" to jsonOf(item.defaultOwnership.code),
        "description" to jsonOf(item.description),
        "customAttributes" to item.customAttributes.toJsonValue(),
        "createdAt" to jsonOf(item.createdAt.toString()),
        "updatedAt" to jsonOf(item.updatedAt.toString()),
        "archivedAt" to jsonOf(item.archivedAt?.toString())
    )

    fun decodeMaterial(obj: JsonValue.Obj): MaterialItem {
        val id = MaterialId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val code = MaterialCode(obj.string("code") ?: "MAT-000")
        val name = obj.string("name") ?: ""
        val category = MaterialCategory.fromCode(obj.string("category")) ?: MaterialCategory.YARN
        val baseUom = UnitOfMeasure.fromCode(obj.string("baseUom")) ?: category.defaultUom
        val alternateUoms = obj.objectArray("alternateUoms").map(::decodeConversion)
        val defaultOwnership = obj.string("defaultOwnership")?.let { codeStr ->
            StockOwnershipSemantics.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

        val description = obj.string("description") ?: ""
        val customAttributes = obj.obj("customAttributes")?.let { CustomAttributes.fromJsonValue(it) } ?: CustomAttributes.EMPTY
        val createdAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("createdAt"),
            kotlinx.datetime.Clock.System.now()
        )
        val updatedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("updatedAt"),
            createdAt
        )
        val archivedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))

        return MaterialItem(
            id = id,
            tenantId = tenantId,
            code = code,
            name = name,
            category = category,
            baseUom = baseUom,
            alternateUoms = alternateUoms,
            defaultOwnership = defaultOwnership,
            description = description,
            customAttributes = customAttributes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archivedAt = archivedAt
        )
    }

    fun encodeSummary(summary: MaterialSummary): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(summary.id.value),
        "code" to jsonOf(summary.code.value),
        "name" to jsonOf(summary.name),
        "category" to jsonOf(summary.category.code),
        "baseUom" to jsonOf(summary.baseUom.code),
        "defaultOwnership" to jsonOf(summary.defaultOwnership.code)
    )

    fun decodeSummary(obj: JsonValue.Obj): MaterialSummary {
        val id = MaterialId(obj.string("id") ?: "")
        val code = MaterialCode(obj.string("code") ?: "MAT-000")
        val name = obj.string("name") ?: ""
        val category = MaterialCategory.fromCode(obj.string("category")) ?: MaterialCategory.YARN
        val baseUom = UnitOfMeasure.fromCode(obj.string("baseUom")) ?: category.defaultUom
        val ownership = obj.string("defaultOwnership")?.let { codeStr ->
            StockOwnershipSemantics.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL

        return MaterialSummary(id, code, name, category, baseUom, ownership)
    }
}
