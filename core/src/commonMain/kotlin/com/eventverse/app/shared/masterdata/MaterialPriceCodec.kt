package com.eventverse.app.shared.masterdata

import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object MaterialPriceCodec {

    fun encodePrice(price: MaterialPrice): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(price.id.value),
        "tenantId" to jsonOf(price.tenantId.value),
        "materialId" to jsonOf(price.materialId.value),
        "unitPrice" to MeasureCodec.encodeUnitPrice(price.unitPrice),
        "source" to jsonOf(price.source.name),
        "effectiveFrom" to jsonOf(price.effectiveFrom.toString()),
        "note" to jsonOf(price.note),
        "recordedByUserId" to jsonOf(price.recordedByUserId),
        "recordedAt" to jsonOf(price.recordedAt.toString())
    )

    fun decodePrice(obj: JsonValue.Obj): MaterialPrice {
        val id = MaterialPriceId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val materialId = MaterialId(obj.string("materialId") ?: "")
        val unitPrice = MeasureCodec.decodeUnitPrice(obj.obj("unitPrice"))
        val source = obj.string("source")?.let { runCatching { PriceSource.valueOf(it) }.getOrNull() }
            ?: PriceSource.STANDARD
        val effectiveFrom = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("effectiveFrom"),
            kotlinx.datetime.Clock.System.now()
        )
        val note = obj.string("note") ?: ""
        val recordedByUserId = obj.string("recordedByUserId") ?: ""
        val recordedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("recordedAt"),
            effectiveFrom
        )

        return MaterialPrice(
            id = id,
            tenantId = tenantId,
            materialId = materialId,
            unitPrice = unitPrice,
            source = source,
            effectiveFrom = effectiveFrom,
            note = note,
            recordedByUserId = recordedByUserId,
            recordedAt = recordedAt
        )
    }

    fun encodeHistory(history: MaterialPriceHistory): JsonValue.Obj = jsonObjectOf(
        "materialId" to jsonOf(history.materialId.value),
        "entries" to jsonArrayOf(history.entries.map(::encodePrice))
    )

    fun decodeHistory(obj: JsonValue.Obj): MaterialPriceHistory {
        val materialId = MaterialId(obj.string("materialId") ?: "")
        val entries = obj.objectArray("entries").map(::decodePrice)
        return MaterialPriceHistory(materialId, entries)
    }

    fun encodePolicy(policy: TenantPricePolicy): JsonValue.Obj = jsonObjectOf(
        "tenantId" to jsonOf(policy.tenantId.value),
        "preferenceOrder" to jsonArrayOf(policy.preferenceOrder.map { jsonOf(it.name) }),
        "fallbackToStandard" to jsonOf(policy.fallbackToStandard)
    )

    fun decodePolicy(obj: JsonValue.Obj): TenantPricePolicy {
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val preferenceOrder = obj.stringArray("preferenceOrder").mapNotNull { name ->
            runCatching { PriceSource.valueOf(name) }.getOrNull()
        }.ifEmpty { listOf(PriceSource.STANDARD) }
        val fallback = obj.boolean("fallbackToStandard") ?: true
        return TenantPricePolicy(tenantId, preferenceOrder, fallback)
    }

    fun encodeResolvedPrice(resolved: ResolvedPrice): JsonValue.Obj = jsonObjectOf(
        "materialId" to jsonOf(resolved.materialId.value),
        "unitPrice" to MeasureCodec.encodeUnitPrice(resolved.unitPrice),
        "source" to jsonOf(resolved.source.name),
        "effectiveFrom" to jsonOf(resolved.effectiveFrom.toString()),
        "explanation" to jsonOf(resolved.explanation)
    )

    fun decodeResolvedPrice(obj: JsonValue.Obj): ResolvedPrice {
        val materialId = MaterialId(obj.string("materialId") ?: "")
        val unitPrice = MeasureCodec.decodeUnitPrice(obj.obj("unitPrice"))
        val source = obj.string("source")?.let { runCatching { PriceSource.valueOf(it) }.getOrNull() }
            ?: PriceSource.STANDARD
        val effectiveFrom = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("effectiveFrom"),
            kotlinx.datetime.Clock.System.now()
        )
        val explanation = obj.string("explanation") ?: ""
        return ResolvedPrice(materialId, unitPrice, source, effectiveFrom, explanation)
    }
}
