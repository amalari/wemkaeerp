package com.eventverse.app.shared.vendor

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.SubcontractNeed
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorAssignmentId
import com.eventverse.app.domain.vendor.VendorAssignmentStatus
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorName
import com.eventverse.app.domain.vendor.VendorPriceSource
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.domain.vendor.VendorQueueItem
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Clock

/** Codec penugasan vendor dan antrean "Menunggu Vendor". */
object VendorAssignmentCodec {

    fun encodeAssignment(a: VendorAssignment): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(a.id.value),
        "tenantId" to jsonOf(a.tenantId.value),
        "subjectId" to jsonOf(a.subjectId),
        "subjectLabel" to jsonOf(a.subjectLabel),
        "processCode" to jsonOf(a.processCode),
        "processName" to jsonOf(a.processName),
        "vendorId" to jsonOf(a.vendorId.value),
        "vendorName" to jsonOf(a.vendorName.value),
        "vendorPhone" to jsonOf(a.vendorPhone),
        "pricePerUnitIdr" to jsonOf(a.pricePerUnitIdr),
        "unit" to jsonOf(a.unit.name),
        "quantityPcs" to jsonOf(a.quantityPcs),
        "unitsPerPiece" to jsonOf(a.unitsPerPiece),
        "totalIdr" to jsonOf(a.totalIdr),
        "priceSource" to jsonOf(a.priceSource.name),
        "expectedReturnAt" to jsonOf(a.expectedReturnAt?.toString()),
        "notes" to jsonOf(a.notes),
        "status" to jsonOf(a.status.name),
        "assignedByUserId" to jsonOf(a.assignedByUserId),
        "assignedAt" to jsonOf(a.assignedAt.toString()),
        "cancelledAt" to jsonOf(a.cancelledAt?.toString())
    )

    fun decodeAssignment(obj: JsonValue.Obj): VendorAssignment = VendorAssignment(
        id = VendorAssignmentId(obj.string("id") ?: ""),
        tenantId = TenantId(obj.string("tenantId") ?: ""),
        subjectId = obj.string("subjectId") ?: "",
        subjectLabel = obj.string("subjectLabel") ?: "",
        processCode = obj.string("processCode") ?: "",
        processName = obj.string("processName") ?: "",
        vendorId = VendorId(obj.string("vendorId") ?: ""),
        vendorName = VendorName(obj.string("vendorName") ?: ""),
        vendorPhone = obj.string("vendorPhone") ?: "",
        pricePerUnitIdr = obj.long("pricePerUnitIdr") ?: 0L,
        unit = VendorCodec.decodeUnit(obj.string("unit")) ?: VendorPriceUnit.PER_PIECE,
        quantityPcs = obj.int("quantityPcs") ?: 1,
        unitsPerPiece = obj.int("unitsPerPiece") ?: 1,
        priceSource = enumOrNull<VendorPriceSource>(obj.string("priceSource")) ?: VendorPriceSource.PRICE_LIST,
        expectedReturnAt = DateTimeCodec.parseLocalDateOrNull(obj.string("expectedReturnAt")),
        notes = obj.string("notes") ?: "",
        status = enumOrNull<VendorAssignmentStatus>(obj.string("status")) ?: VendorAssignmentStatus.ASSIGNED,
        assignedByUserId = obj.string("assignedByUserId") ?: "",
        assignedAt = DateTimeCodec.parseInstantOrFallback(obj.string("assignedAt"), Clock.System.now()),
        cancelledAt = DateTimeCodec.parseInstantOrNull(obj.string("cancelledAt"))
    )

    fun encodeNeed(need: SubcontractNeed): JsonValue.Obj = jsonObjectOf(
        "subjectId" to jsonOf(need.subjectId),
        "subjectLabel" to jsonOf(need.subjectLabel),
        "clientName" to jsonOf(need.clientName),
        "styleName" to jsonOf(need.styleName),
        "processCode" to jsonOf(need.processCode),
        "processName" to jsonOf(need.processName),
        "quantityPcs" to jsonOf(need.quantityPcs),
        "dueDate" to jsonOf(need.dueDate?.toString())
    )

    fun decodeNeed(obj: JsonValue.Obj): SubcontractNeed = SubcontractNeed(
        subjectId = obj.string("subjectId") ?: "",
        subjectLabel = obj.string("subjectLabel") ?: "",
        clientName = obj.string("clientName") ?: "",
        styleName = obj.string("styleName") ?: "",
        processCode = obj.string("processCode") ?: "",
        processName = obj.string("processName") ?: "",
        quantityPcs = obj.int("quantityPcs") ?: 1,
        dueDate = DateTimeCodec.parseLocalDateOrNull(obj.string("dueDate"))
    )

    fun encodeQueue(items: List<VendorQueueItem>): String = jsonArrayOf(
        items.map { item ->
            jsonObjectOf(
                "need" to encodeNeed(item.need),
                "assignment" to (item.assignment?.let(::encodeAssignment) ?: JsonValue.Null)
            )
        }
    ).encode()

    fun decodeQueue(json: String): List<VendorQueueItem> =
        JsonParser.parseArray(json).filterIsInstance<JsonValue.Obj>().map { obj ->
            VendorQueueItem(
                need = decodeNeed(obj.obj("need") ?: JsonValue.Obj(emptyMap())),
                assignment = obj.obj("assignment")?.let(::decodeAssignment)
            )
        }

    fun encodeAssignments(items: List<VendorAssignment>): String = jsonArrayOf(items.map(::encodeAssignment)).encode()

    fun decodeAssignments(json: String): List<VendorAssignment> =
        JsonParser.parseArray(json).filterIsInstance<JsonValue.Obj>().map(::decodeAssignment)

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
}
