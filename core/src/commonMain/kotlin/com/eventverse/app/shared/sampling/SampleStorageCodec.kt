package com.eventverse.app.shared.sampling

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.storage.SampleStorageRecord
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordId
import com.eventverse.app.domain.sampling.storage.SampleStorageStatus
import com.eventverse.app.domain.sampling.storage.StorageCustodian
import com.eventverse.app.domain.sampling.storage.StorageLocationLabel
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/** Serialisasi kustodi penyimpanan sampel — satu baris per field, tanpa logika. */
object SampleStorageCodec {

    fun encodeCustodian(custodian: StorageCustodian?): JsonValue = custodian?.let {
        jsonObjectOf("email" to jsonOf(it.email), "name" to jsonOf(it.name))
    } ?: JsonValue.Null

    fun decodeCustodian(obj: JsonValue.Obj?): StorageCustodian? {
        obj ?: return null
        val email = obj.string("email").orEmpty()
        val name = obj.string("name").orEmpty()
        if (email.isBlank() && name.isBlank()) return null
        return StorageCustodian(email, name)
    }

    fun encodeRecord(record: SampleStorageRecord): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(record.id.value),
        "tenantId" to jsonOf(record.tenantId.value),
        "orderId" to jsonOf(record.orderId.value),
        "dealId" to jsonOf(record.dealId),
        "location" to jsonOf(record.location.value),
        "qtyPcs" to jsonOf(record.qtyPcs),
        "storedBy" to encodeCustodian(record.storedBy),
        "storedAt" to jsonOf(record.storedAt.toString()),
        "releasedBy" to encodeCustodian(record.releasedBy),
        "releasedAt" to jsonOf(record.releasedAt?.toString()),
        "partialReason" to jsonOf(record.partialReason)
    )

    fun decodeRecord(obj: JsonValue.Obj): SampleStorageRecord? = runCatching {
        SampleStorageRecord(
            id = SampleStorageRecordId(obj.string("id").orEmpty()),
            tenantId = TenantId(obj.string("tenantId").orEmpty()),
            orderId = SamplingOrderId(obj.string("orderId").orEmpty()),
            dealId = obj.string("dealId"),
            location = StorageLocationLabel(obj.string("location").orEmpty()),
            qtyPcs = obj.int("qtyPcs") ?: 1,
            storedBy = requireNotNull(decodeCustodian(obj.obj("storedBy"))) { "storedBy kosong" },
            storedAt = Instant.parse(obj.string("storedAt").orEmpty()),
            releasedBy = decodeCustodian(obj.obj("releasedBy")),
            releasedAt = obj.string("releasedAt")?.let(Instant::parse),
            partialReason = obj.string("partialReason")
        )
    }.getOrNull()

    fun encodeStatus(status: SampleStorageStatus): JsonValue.Obj = jsonObjectOf(
        "record" to (status.record?.let(::encodeRecord) ?: JsonValue.Null),
        "readyCount" to jsonOf(status.readyCount),
        "total" to jsonOf(status.total),
        "pendingSpkNumbers" to jsonArrayOf(status.pendingSpkNumbers.map { jsonOf(it) })
    )

    fun decodeStatus(obj: JsonValue.Obj): SampleStorageStatus = SampleStorageStatus(
        record = obj.obj("record")?.let(::decodeRecord),
        readyCount = obj.int("readyCount") ?: 0,
        total = obj.int("total") ?: 0,
        pendingSpkNumbers = obj.stringArray("pendingSpkNumbers")
    )
}
