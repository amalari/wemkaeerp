package com.eventverse.app.shared.costing

import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.contracts.CostingCalculationResultCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Clock

object CostingSheetCodec {

    fun encode(sheet: CostingSheet): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(sheet.id.value),
        "tenantId" to jsonOf(sheet.tenantId.value),
        "number" to jsonOf(sheet.number.value),
        "techPackId" to jsonOf(sheet.techPackId),
        "orderQuantity" to jsonOf(sheet.orderQuantity),
        "behavior" to jsonOf(sheet.behavior.code),
        "status" to jsonOf(sheet.status.name),
        "pricingAsOf" to jsonOf(sheet.pricingAsOf.toString()),
        "parameterOverrides" to jsonStringMapOf(sheet.parameterOverrides),
        "latestResult" to (sheet.latestResult?.let { CostingCalculationResultCodec.encode(it) } ?: JsonValue.Null),
        "approvedSnapshot" to (sheet.approvedSnapshot?.let { encodeSnapshot(it) } ?: JsonValue.Null),
        "rejectionReason" to (sheet.rejectionReason?.let { jsonOf(it) } ?: JsonValue.Null),
        "notes" to jsonOf(sheet.notes),
        "linkedSpkNumber" to (sheet.linkedSpkNumber?.let { jsonOf(it) } ?: JsonValue.Null),
        "createdByUserId" to (sheet.createdByUserId?.let { jsonOf(it) } ?: JsonValue.Null),
        "createdAt" to jsonOf(sheet.createdAt.toString()),
        "updatedAt" to jsonOf(sheet.updatedAt.toString())
    )

    fun decode(obj: JsonValue.Obj): CostingSheet {
        val now = Clock.System.now()
        val id = CostingSheetId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val number = CostingNumber(obj.string("number") ?: "")
        val techPackId = obj.string("techPackId") ?: ""
        val orderQuantity = obj.long("orderQuantity") ?: 1L

        val behavior = obj.string("behavior")?.let { codeStr ->
            CostingBehavior.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: CostingBehavior.FULL_PACKAGE_COGS

        val status = obj.string("status")?.let { statusStr ->
            runCatching { CostingSheetStatus.valueOf(statusStr) }.getOrNull()
        } ?: CostingSheetStatus.DRAFT

        val pricingAsOf = DateTimeCodec.parseInstantOrFallback(obj.string("pricingAsOf"), now)

        val parameterOverrides = obj.obj("parameterOverrides")?.entries?.mapNotNull { (k, v) ->
            when (v) {
                is JsonValue.Str -> k to v.value
                is JsonValue.Num -> k to v.raw
                is JsonValue.Bool -> k to v.value.toString()
                else -> null
            }
        }?.toMap() ?: emptyMap()

        val latestResult = obj.obj("latestResult")?.let { CostingCalculationResultCodec.decode(it) }
        val approvedSnapshot = obj.obj("approvedSnapshot")?.let { decodeSnapshot(it) }
        val rejectionReason = obj.string("rejectionReason")
        val notes = obj.string("notes") ?: ""
        val linkedSpkNumber = obj.string("linkedSpkNumber")
        val createdByUserId = obj.string("createdByUserId")
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), now)
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), now)

        return CostingSheet(
            id = id,
            tenantId = tenantId,
            number = number,
            techPackId = techPackId,
            orderQuantity = orderQuantity,
            behavior = behavior,
            status = status,
            pricingAsOf = pricingAsOf,
            parameterOverrides = parameterOverrides,
            latestResult = latestResult,
            approvedSnapshot = approvedSnapshot,
            rejectionReason = rejectionReason,
            notes = notes,
            linkedSpkNumber = linkedSpkNumber,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun encodeSnapshot(snapshot: CostingSnapshot): JsonValue.Obj = jsonObjectOf(
        "snapshotId" to jsonOf(snapshot.snapshotId),
        "approvedAt" to jsonOf(snapshot.approvedAt.toString()),
        "approvedByUserId" to jsonOf(snapshot.approvedByUserId),
        "result" to CostingCalculationResultCodec.encode(snapshot.result),
        "inputFingerprint" to jsonOf(snapshot.inputFingerprint)
    )

    private fun decodeSnapshot(obj: JsonValue.Obj): CostingSnapshot {
        val now = Clock.System.now()
        val snapshotId = obj.string("snapshotId") ?: ""
        val approvedAt = DateTimeCodec.parseInstantOrFallback(obj.string("approvedAt"), now)
        val approvedByUserId = obj.string("approvedByUserId") ?: ""
        val result = obj.obj("result")?.let { CostingCalculationResultCodec.decode(it) }
            ?: error("Snapshot result must not be null")
        val inputFingerprint = obj.string("inputFingerprint") ?: ""

        return CostingSnapshot(
            snapshotId = snapshotId,
            approvedAt = approvedAt,
            approvedByUserId = approvedByUserId,
            result = result,
            inputFingerprint = inputFingerprint
        )
    }
}
