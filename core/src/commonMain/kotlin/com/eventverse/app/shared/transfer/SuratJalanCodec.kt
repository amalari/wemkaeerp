package com.eventverse.app.shared.transfer

import com.eventverse.app.domain.transfer.CartonId
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

object SuratJalanCodec {

    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encodeItem(item: SuratJalanItem): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(item.id),
        "workCardId" to (item.workCardId?.let { jsonOf(it.value) } ?: JsonValue.Null),
        "bundleNo" to (item.bundleNo?.let(::jsonOf) ?: JsonValue.Null),
        "cartonId" to (item.cartonId?.let { jsonOf(it.value) } ?: JsonValue.Null),
        "sizeLabel" to jsonOf(item.sizeLabel),
        "colorway" to jsonOf(item.colorway),
        "qtyPcs" to jsonOf(item.qtyPcs),
        "notes" to jsonOf(item.notes)
    )

    fun decodeItem(obj: JsonValue.Obj): SuratJalanItem = SuratJalanItem(
        id = obj.string("id") ?: "",
        workCardId = obj.string("workCardId")?.let(::WorkCardId),
        bundleNo = obj.int("bundleNo"),
        cartonId = obj.string("cartonId")?.let(::CartonId),
        sizeLabel = obj.string("sizeLabel") ?: "",
        colorway = obj.string("colorway") ?: "",
        qtyPcs = obj.int("qtyPcs") ?: 0,
        notes = obj.string("notes") ?: ""
    )

    fun encode(manifest: SuratJalanManifest): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(manifest.id.value),
        "tenantId" to jsonOf(manifest.tenantId),
        "sjNumber" to jsonOf(manifest.sjNumber.value),
        "transferType" to jsonOf(manifest.transferType.name),
        "subjectKind" to jsonOf(manifest.subject.kind.name),
        "subjectId" to jsonOf(manifest.subject.subjectId),
        "orderNumber" to jsonOf(manifest.subject.orderNumber),
        "articleName" to jsonOf(manifest.subject.articleName),
        "originLocationId" to (manifest.originLocationId?.let { jsonOf(it.value) } ?: JsonValue.Null),
        "destinationLocationId" to (manifest.destinationLocationId?.let { jsonOf(it.value) } ?: JsonValue.Null),
        "vendorRef" to (manifest.vendorRef?.let(::jsonOf) ?: JsonValue.Null),
        "customerName" to (manifest.customerName?.let(::jsonOf) ?: JsonValue.Null),
        "customerAddress" to (manifest.customerAddress?.let(::jsonOf) ?: JsonValue.Null),
        "carrierName" to (manifest.carrierName?.let(::jsonOf) ?: JsonValue.Null),
        "driverName" to (manifest.driverName?.let(::jsonOf) ?: JsonValue.Null),
        "vehiclePlate" to (manifest.vehiclePlate?.let(::jsonOf) ?: JsonValue.Null),
        "status" to jsonOf(manifest.status.name),
        "legKey" to (manifest.legKey?.let(::jsonOf) ?: JsonValue.Null),
        "items" to jsonArrayOf(manifest.items.map(::encodeItem)),
        "unitServiceFeeIdr" to jsonOf(manifest.unitServiceFeeIdr),
        "expectedReturnDate" to (manifest.expectedReturnDate?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "dispatchedAt" to (manifest.dispatchedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "receivedAt" to (manifest.receivedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "notes" to jsonOf(manifest.notes),
        "totalPcs" to jsonOf(manifest.totalPcs),
        "totalCartons" to jsonOf(manifest.totalCartons)
    )

    fun decode(obj: JsonValue.Obj): SuratJalanManifest {
        val itemsList = obj.objectArray("items").map(::decodeItem)

        return SuratJalanManifest(
            id = SuratJalanId(obj.string("id") ?: ""),
            tenantId = obj.string("tenantId") ?: "",
            sjNumber = SuratJalanNumber(obj.string("sjNumber") ?: ""),
            transferType = runCatching {
                TransferType.valueOf(obj.string("transferType") ?: TransferType.INTERNAL_SITE_TRANSFER.name)
            }.getOrDefault(TransferType.INTERNAL_SITE_TRANSFER),
            subject = WorkSubjectRef(
                kind = runCatching {
                    WorkSubjectKind.valueOf(obj.string("subjectKind") ?: WorkSubjectKind.BULK_WORK_ORDER.name)
                }.getOrDefault(WorkSubjectKind.BULK_WORK_ORDER),
                subjectId = obj.string("subjectId") ?: "",
                orderNumber = obj.string("orderNumber") ?: "",
                articleName = obj.string("articleName") ?: ""
            ),
            originLocationId = obj.string("originLocationId")?.let(::LocationId),
            destinationLocationId = obj.string("destinationLocationId")?.let(::LocationId),
            vendorRef = obj.string("vendorRef"),
            customerName = obj.string("customerName"),
            customerAddress = obj.string("customerAddress"),
            carrierName = obj.string("carrierName"),
            driverName = obj.string("driverName"),
            vehiclePlate = obj.string("vehiclePlate"),
            status = runCatching {
                TransferStatus.valueOf(obj.string("status") ?: TransferStatus.DRAFT.name)
            }.getOrDefault(TransferStatus.DRAFT),
            legKey = obj.string("legKey"),
            items = itemsList,
            unitServiceFeeIdr = obj.long("unitServiceFeeIdr") ?: 0L,
            expectedReturnDate = obj.string("expectedReturnDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            dispatchedAt = obj.string("dispatchedAt")?.let { DateTimeCodec.parseInstantOrFallback(it, EPOCH) },
            receivedAt = obj.string("receivedAt")?.let { DateTimeCodec.parseInstantOrFallback(it, EPOCH) },
            notes = obj.string("notes") ?: ""
        )
    }
}
