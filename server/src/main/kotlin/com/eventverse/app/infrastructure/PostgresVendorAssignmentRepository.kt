package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorAssignmentId
import com.eventverse.app.domain.vendor.VendorAssignmentRepository
import com.eventverse.app.domain.vendor.VendorAssignmentStatus
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorName
import com.eventverse.app.domain.vendor.VendorPriceSource
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.infrastructure.tables.VendorAssignmentsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Penugasan vendor (tabel V64). Baris tidak pernah dihapus: penugasan yang diganti berstatus
 * `CANCELLED`, sehingga riwayat "siapa pernah ditunjuk dengan harga berapa" tetap utuh.
 */
class PostgresVendorAssignmentRepository : VendorAssignmentRepository {

    override suspend fun findById(tenantId: TenantId, id: VendorAssignmentId): VendorAssignment? =
        DatabaseFactory.dbQuery(tenantId) {
            VendorAssignmentsTable.selectAll()
                .where { (VendorAssignmentsTable.tenantId eq tenantId.value) and (VendorAssignmentsTable.id eq id.value) }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun findActive(tenantId: TenantId): List<VendorAssignment> =
        DatabaseFactory.dbQuery(tenantId) {
            VendorAssignmentsTable.selectAll()
                .where {
                    (VendorAssignmentsTable.tenantId eq tenantId.value) and
                        (VendorAssignmentsTable.status eq VendorAssignmentStatus.ASSIGNED.name)
                }
                .map(::hydrate)
        }

    override suspend fun findByVendor(tenantId: TenantId, vendorId: VendorId): List<VendorAssignment> =
        DatabaseFactory.dbQuery(tenantId) {
            VendorAssignmentsTable.selectAll()
                .where {
                    (VendorAssignmentsTable.tenantId eq tenantId.value) and
                        (VendorAssignmentsTable.vendorId eq vendorId.value)
                }
                .orderBy(VendorAssignmentsTable.assignedAt to SortOrder.DESC)
                .map(::hydrate)
        }

    override suspend fun save(assignment: VendorAssignment): VendorAssignment = DatabaseFactory.dbQuery(assignment.tenantId) {
        val exists = VendorAssignmentsTable.selectAll()
            .where { VendorAssignmentsTable.id eq assignment.id.value }
            .any()

        if (exists) {
            // Hanya status yang boleh berubah setelah penugasan dibuat; harganya snapshot.
            VendorAssignmentsTable.update({
                (VendorAssignmentsTable.tenantId eq assignment.tenantId.value) and
                    (VendorAssignmentsTable.id eq assignment.id.value)
            }) {
                it[status] = assignment.status.name
                it[cancelledAt] = assignment.cancelledAt
            }
        } else {
            VendorAssignmentsTable.insert {
                it[id] = assignment.id.value
                it[tenantId] = assignment.tenantId.value
                it[subjectId] = assignment.subjectId
                it[subjectLabel] = assignment.subjectLabel
                it[processCode] = assignment.processCode
                it[processName] = assignment.processName
                it[vendorId] = assignment.vendorId.value
                it[vendorName] = assignment.vendorName.value
                it[vendorPhone] = assignment.vendorPhone
                it[pricePerUnitIdr] = assignment.pricePerUnitIdr
                it[priceUnit] = assignment.unit.name
                it[quantityPcs] = assignment.quantityPcs
                it[unitsPerPiece] = assignment.unitsPerPiece
                it[priceSource] = assignment.priceSource.name
                it[expectedReturnAt] = assignment.expectedReturnAt
                it[notes] = assignment.notes
                it[status] = assignment.status.name
                it[assignedByUserId] = assignment.assignedByUserId
                it[assignedAt] = assignment.assignedAt
                it[cancelledAt] = assignment.cancelledAt
            }
        }
        assignment
    }

    private fun hydrate(row: ResultRow): VendorAssignment = VendorAssignment(
        id = VendorAssignmentId(row[VendorAssignmentsTable.id]),
        tenantId = TenantId(row[VendorAssignmentsTable.tenantId]),
        subjectId = row[VendorAssignmentsTable.subjectId],
        subjectLabel = row[VendorAssignmentsTable.subjectLabel],
        processCode = row[VendorAssignmentsTable.processCode],
        processName = row[VendorAssignmentsTable.processName],
        vendorId = VendorId(row[VendorAssignmentsTable.vendorId]),
        vendorName = VendorName(row[VendorAssignmentsTable.vendorName]),
        vendorPhone = row[VendorAssignmentsTable.vendorPhone],
        pricePerUnitIdr = row[VendorAssignmentsTable.pricePerUnitIdr],
        unit = runCatching { VendorPriceUnit.valueOf(row[VendorAssignmentsTable.priceUnit]) }.getOrDefault(VendorPriceUnit.PER_PIECE),
        quantityPcs = row[VendorAssignmentsTable.quantityPcs],
        unitsPerPiece = row[VendorAssignmentsTable.unitsPerPiece],
        priceSource = runCatching { VendorPriceSource.valueOf(row[VendorAssignmentsTable.priceSource]) }
            .getOrDefault(VendorPriceSource.PRICE_LIST),
        expectedReturnAt = row[VendorAssignmentsTable.expectedReturnAt],
        notes = row[VendorAssignmentsTable.notes],
        status = runCatching { VendorAssignmentStatus.valueOf(row[VendorAssignmentsTable.status]) }
            .getOrDefault(VendorAssignmentStatus.ASSIGNED),
        assignedByUserId = row[VendorAssignmentsTable.assignedByUserId],
        assignedAt = row[VendorAssignmentsTable.assignedAt],
        cancelledAt = row[VendorAssignmentsTable.cancelledAt]
    )
}
