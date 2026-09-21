package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.CartonId
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import com.eventverse.app.infrastructure.tables.SuratJalanItemsTable
import com.eventverse.app.infrastructure.tables.SuratJalanManifestsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PostgresSuratJalanRepository : SuratJalanRepository {

    override suspend fun findById(id: SuratJalanId): SuratJalanManifest? =
        DatabaseFactory.dbQuery(null) {
            val manifestRow = SuratJalanManifestsTable.selectAll()
                .where { SuratJalanManifestsTable.id eq id.value }
                .singleOrNull() ?: return@dbQuery null

            val itemRows = SuratJalanItemsTable.selectAll()
                .where { SuratJalanItemsTable.manifestId eq id.value }
                .toList()

            hydrate(manifestRow, itemRows)
        }

    override suspend fun findByNumber(tenantId: String, sjNumber: SuratJalanNumber): SuratJalanManifest? =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            val manifestRow = SuratJalanManifestsTable.selectAll()
                .where {
                    (SuratJalanManifestsTable.tenantId eq tenantId) and
                    (SuratJalanManifestsTable.sjNumber eq sjNumber.value)
                }
                .singleOrNull() ?: return@dbQuery null

            val itemRows = SuratJalanItemsTable.selectAll()
                .where { SuratJalanItemsTable.manifestId eq manifestRow[SuratJalanManifestsTable.id] }
                .toList()

            hydrate(manifestRow, itemRows)
        }

    override suspend fun findByTenant(tenantId: String, transferType: TransferType?): List<SuratJalanManifest> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            val query = SuratJalanManifestsTable.selectAll()
                .where {
                    if (transferType != null) {
                        (SuratJalanManifestsTable.tenantId eq tenantId) and
                        (SuratJalanManifestsTable.transferType eq transferType.name)
                    } else {
                        SuratJalanManifestsTable.tenantId eq tenantId
                    }
                }
                .orderBy(SuratJalanManifestsTable.dispatchedAt, SortOrder.DESC)

            val manifests = query.toList()
            if (manifests.isEmpty()) return@dbQuery emptyList()

            val manifestIds = manifests.map { it[SuratJalanManifestsTable.id] }
            val itemsByManifest = SuratJalanItemsTable.selectAll()
                .where { SuratJalanItemsTable.manifestId inList manifestIds }
                .groupBy { it[SuratJalanItemsTable.manifestId] }

            manifests.map { manifestRow ->
                hydrate(manifestRow, itemsByManifest[manifestRow[SuratJalanManifestsTable.id]] ?: emptyList())
            }
        }

    override suspend fun findBySubject(tenantId: String, subjectId: String): List<SuratJalanManifest> =
        DatabaseFactory.dbQuery(TenantId(tenantId)) {
            val manifests = SuratJalanManifestsTable.selectAll()
                .where {
                    (SuratJalanManifestsTable.tenantId eq tenantId) and
                    (SuratJalanManifestsTable.subjectId eq subjectId)
                }
                .orderBy(SuratJalanManifestsTable.dispatchedAt, SortOrder.DESC)
                .toList()

            if (manifests.isEmpty()) return@dbQuery emptyList()

            val manifestIds = manifests.map { it[SuratJalanManifestsTable.id] }
            val itemsByManifest = SuratJalanItemsTable.selectAll()
                .where { SuratJalanItemsTable.manifestId inList manifestIds }
                .groupBy { it[SuratJalanItemsTable.manifestId] }

            manifests.map { manifestRow ->
                hydrate(manifestRow, itemsByManifest[manifestRow[SuratJalanManifestsTable.id]] ?: emptyList())
            }
        }

    override suspend fun save(manifest: SuratJalanManifest) {
        DatabaseFactory.dbQuery(TenantId(manifest.tenantId)) {
            val existing = SuratJalanManifestsTable.selectAll()
                .where { SuratJalanManifestsTable.id eq manifest.id.value }
                .singleOrNull()

            if (existing == null) {
                SuratJalanManifestsTable.insert {
                    it[id] = manifest.id.value
                    it[tenantId] = manifest.tenantId
                    it[sjNumber] = manifest.sjNumber.value
                    it[transferType] = manifest.transferType.name
                    it[subjectKind] = manifest.subject.kind.name
                    it[subjectId] = manifest.subject.subjectId
                    it[orderNumber] = manifest.subject.orderNumber
                    it[articleName] = manifest.subject.articleName
                    it[originLocationId] = manifest.originLocationId?.value
                    it[destinationLocationId] = manifest.destinationLocationId?.value
                    it[vendorRef] = manifest.vendorRef
                    it[customerName] = manifest.customerName
                    it[customerAddress] = manifest.customerAddress
                    it[carrierName] = manifest.carrierName
                    it[driverName] = manifest.driverName
                    it[vehiclePlate] = manifest.vehiclePlate
                    it[status] = manifest.status.name
                    it[unitServiceFeeIdr] = manifest.unitServiceFeeIdr
                    it[expectedReturnDate] = manifest.expectedReturnDate
                    it[dispatchedAt] = manifest.dispatchedAt
                    it[receivedAt] = manifest.receivedAt
                    it[notes] = manifest.notes
                }
            } else {
                SuratJalanManifestsTable.update({ SuratJalanManifestsTable.id eq manifest.id.value }) {
                    it[status] = manifest.status.name
                    it[carrierName] = manifest.carrierName
                    it[driverName] = manifest.driverName
                    it[vehiclePlate] = manifest.vehiclePlate
                    it[dispatchedAt] = manifest.dispatchedAt
                    it[receivedAt] = manifest.receivedAt
                    it[notes] = manifest.notes
                }
            }

            // Sync items (delete and re-insert)
            SuratJalanItemsTable.deleteWhere { manifestId eq manifest.id.value }
            for (item in manifest.items) {
                SuratJalanItemsTable.insert {
                    it[id] = item.id
                    it[manifestId] = manifest.id.value
                    it[workCardId] = item.workCardId?.value
                    it[bundleNo] = item.bundleNo
                    it[cartonId] = item.cartonId?.value
                    it[sizeLabel] = item.sizeLabel
                    it[colorway] = item.colorway
                    it[qtyPcs] = item.qtyPcs
                    it[notes] = item.notes
                }
            }
        }
    }

    private fun hydrate(manifestRow: ResultRow, itemRows: List<ResultRow>): SuratJalanManifest {
        val items = itemRows.map { row ->
            SuratJalanItem(
                id = row[SuratJalanItemsTable.id],
                workCardId = row[SuratJalanItemsTable.workCardId]?.let(::WorkCardId),
                bundleNo = row[SuratJalanItemsTable.bundleNo],
                cartonId = row[SuratJalanItemsTable.cartonId]?.let(::CartonId),
                sizeLabel = row[SuratJalanItemsTable.sizeLabel],
                colorway = row[SuratJalanItemsTable.colorway],
                qtyPcs = row[SuratJalanItemsTable.qtyPcs],
                notes = row[SuratJalanItemsTable.notes]
            )
        }

        return SuratJalanManifest(
            id = SuratJalanId(manifestRow[SuratJalanManifestsTable.id]),
            tenantId = manifestRow[SuratJalanManifestsTable.tenantId],
            sjNumber = SuratJalanNumber(manifestRow[SuratJalanManifestsTable.sjNumber]),
            transferType = TransferType.valueOf(manifestRow[SuratJalanManifestsTable.transferType]),
            subject = WorkSubjectRef(
                kind = WorkSubjectKind.valueOf(manifestRow[SuratJalanManifestsTable.subjectKind]),
                subjectId = manifestRow[SuratJalanManifestsTable.subjectId],
                orderNumber = manifestRow[SuratJalanManifestsTable.orderNumber],
                articleName = manifestRow[SuratJalanManifestsTable.articleName]
            ),
            originLocationId = manifestRow[SuratJalanManifestsTable.originLocationId]?.let(::LocationId),
            destinationLocationId = manifestRow[SuratJalanManifestsTable.destinationLocationId]?.let(::LocationId),
            vendorRef = manifestRow[SuratJalanManifestsTable.vendorRef],
            customerName = manifestRow[SuratJalanManifestsTable.customerName],
            customerAddress = manifestRow[SuratJalanManifestsTable.customerAddress],
            carrierName = manifestRow[SuratJalanManifestsTable.carrierName],
            driverName = manifestRow[SuratJalanManifestsTable.driverName],
            vehiclePlate = manifestRow[SuratJalanManifestsTable.vehiclePlate],
            status = TransferStatus.valueOf(manifestRow[SuratJalanManifestsTable.status]),
            items = items,
            unitServiceFeeIdr = manifestRow[SuratJalanManifestsTable.unitServiceFeeIdr],
            expectedReturnDate = manifestRow[SuratJalanManifestsTable.expectedReturnDate],
            dispatchedAt = manifestRow[SuratJalanManifestsTable.dispatchedAt],
            receivedAt = manifestRow[SuratJalanManifestsTable.receivedAt],
            notes = manifestRow[SuratJalanManifestsTable.notes]
        )
    }
}
