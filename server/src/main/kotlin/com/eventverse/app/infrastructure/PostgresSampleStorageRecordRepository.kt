package com.eventverse.app.infrastructure

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.storage.SampleStorageRecord
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordId
import com.eventverse.app.domain.sampling.storage.SampleStorageRecordRepository
import com.eventverse.app.domain.sampling.storage.StorageCustodian
import com.eventverse.app.domain.sampling.storage.StorageLocationLabel
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.SampleStorageRecordsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Setiap query memfilter `tenant_id` secara eksplisit — RLS belum efektif (lihat
 * `docs/tenant-isolation-rls-status.md`), jadi klausa itulah pemisah tenant yang sebenarnya.
 */
class PostgresSampleStorageRecordRepository : SampleStorageRecordRepository {

    override suspend fun findLatestByOrderId(tenantId: TenantId, orderId: SamplingOrderId): SampleStorageRecord? =
        DatabaseFactory.dbQuery(tenantId) {
            SampleStorageRecordsTable.selectAll()
                .where {
                    (SampleStorageRecordsTable.tenantId eq tenantId.value) and
                        (SampleStorageRecordsTable.samplingOrderId eq orderId.value)
                }
                .orderBy(SampleStorageRecordsTable.storedAt, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.toRecord()
        }

    override suspend fun findByDealId(tenantId: TenantId, dealId: String): List<SampleStorageRecord> =
        DatabaseFactory.dbQuery(tenantId) {
            SampleStorageRecordsTable.selectAll()
                .where {
                    (SampleStorageRecordsTable.tenantId eq tenantId.value) and
                        (SampleStorageRecordsTable.dealId eq dealId)
                }
                .map { it.toRecord() }
        }

    override suspend fun save(record: SampleStorageRecord): SampleStorageRecord =
        DatabaseFactory.dbQuery(record.tenantId) {
            val exists = SampleStorageRecordsTable.selectAll()
                .where {
                    (SampleStorageRecordsTable.tenantId eq record.tenantId.value) and
                        (SampleStorageRecordsTable.id eq record.id.value)
                }
                .any()
            if (exists) {
                SampleStorageRecordsTable.update({
                    (SampleStorageRecordsTable.tenantId eq record.tenantId.value) and
                        (SampleStorageRecordsTable.id eq record.id.value)
                }) {
                    it[releasedByEmail] = record.releasedBy?.email
                    it[releasedByName] = record.releasedBy?.name
                    it[releasedAt] = record.releasedAt
                    it[partialReason] = record.partialReason
                }
            } else {
                SampleStorageRecordsTable.insert {
                    it[id] = record.id.value
                    it[tenantId] = record.tenantId.value
                    it[samplingOrderId] = record.orderId.value
                    it[dealId] = record.dealId
                    it[locationLabel] = record.location.value
                    it[qtyPcs] = record.qtyPcs
                    it[storedByEmail] = record.storedBy.email
                    it[storedByName] = record.storedBy.name
                    it[storedAt] = record.storedAt
                    it[releasedByEmail] = record.releasedBy?.email
                    it[releasedByName] = record.releasedBy?.name
                    it[releasedAt] = record.releasedAt
                    it[partialReason] = record.partialReason
                }
            }
            record
        }

    private fun ResultRow.toRecord(): SampleStorageRecord {
        val releasedEmail = this[SampleStorageRecordsTable.releasedByEmail].orEmpty()
        val releasedName = this[SampleStorageRecordsTable.releasedByName].orEmpty()
        return SampleStorageRecord(
            id = SampleStorageRecordId(this[SampleStorageRecordsTable.id]),
            tenantId = TenantId(this[SampleStorageRecordsTable.tenantId]),
            orderId = SamplingOrderId(this[SampleStorageRecordsTable.samplingOrderId]),
            dealId = this[SampleStorageRecordsTable.dealId],
            location = StorageLocationLabel(this[SampleStorageRecordsTable.locationLabel]),
            qtyPcs = this[SampleStorageRecordsTable.qtyPcs],
            storedBy = StorageCustodian(
                this[SampleStorageRecordsTable.storedByEmail],
                this[SampleStorageRecordsTable.storedByName]
            ),
            storedAt = this[SampleStorageRecordsTable.storedAt],
            releasedBy = if (releasedEmail.isBlank() && releasedName.isBlank()) null
            else StorageCustodian(releasedEmail, releasedName),
            releasedAt = this[SampleStorageRecordsTable.releasedAt],
            partialReason = this[SampleStorageRecordsTable.partialReason]
        )
    }
}
