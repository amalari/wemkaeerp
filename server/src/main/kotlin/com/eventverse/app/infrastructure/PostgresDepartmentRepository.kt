package com.eventverse.app.infrastructure

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DepartmentsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

class PostgresDepartmentRepository : DepartmentRepository {

    override suspend fun findById(tenantId: TenantId, id: DepartmentId): Department? = DatabaseFactory.dbQuery(tenantId) {
        DepartmentsTable.selectAll()
            .where { (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.id eq id.value) }
            .map { toDepartment(it) }
            .singleOrNull()
    }

    override suspend fun findByCode(tenantId: TenantId, code: String): Department? = DatabaseFactory.dbQuery(tenantId) {
        DepartmentsTable.selectAll()
            .where { (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.code eq code) }
            .map { toDepartment(it) }
            .singleOrNull()
    }

    /** Hanya mengembalikan divisi AKTIF (archived_at IS NULL). */
    override suspend fun findAllByTenant(tenantId: TenantId): List<Department> = DatabaseFactory.dbQuery(tenantId) {
        DepartmentsTable.selectAll()
            .where {
                (DepartmentsTable.tenantId eq tenantId.value) and
                (DepartmentsTable.archivedAt.isNull())
            }
            .map { toDepartment(it) }
    }

    /** Mengembalikan semua divisi yang sudah DIARSIPKAN (archived_at IS NOT NULL). */
    override suspend fun findAllArchived(tenantId: TenantId): List<Department> = DatabaseFactory.dbQuery(tenantId) {
        DepartmentsTable.selectAll()
            .where {
                (DepartmentsTable.tenantId eq tenantId.value) and
                (DepartmentsTable.archivedAt.isNotNull())
            }
            .orderBy(DepartmentsTable.archivedAt, SortOrder.DESC)
            .map { toDepartment(it) }
    }

    override suspend fun save(tenantId: TenantId, department: Department): Result<Department> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            val exists = DepartmentsTable.selectAll()
                .where { (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.id eq department.id.value) }
                .count() > 0

            if (exists) {
                DepartmentsTable.update({ (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.id eq department.id.value) }) {
                    it[code] = department.code
                    it[displayName] = department.displayName
                    it[shortName] = department.shortName
                    it[colorHex] = department.colorHex
                    it[isCustom] = department.isCustom
                }
            } else {
                DepartmentsTable.insert {
                    it[DepartmentsTable.id] = department.id.value
                    it[DepartmentsTable.tenantId] = tenantId.value
                    it[DepartmentsTable.code] = department.code
                    it[displayName] = department.displayName
                    it[shortName] = department.shortName
                    it[colorHex] = department.colorHex
                    it[isCustom] = department.isCustom
                    // archivedAt defaults to NULL (active)
                }
            }
            if (department.tenantId == null) department.copy(tenantId = tenantId) else department
        }
    }

    /** Soft-delete: set archived_at = NOW(). */
    override suspend fun archive(tenantId: TenantId, id: DepartmentId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            DepartmentsTable.update({
                (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.id eq id.value)
            }) {
                it[archivedAt] = java.time.Instant.now().toString()
            }
        }
    }

    /** Unarchive: set archived_at = NULL (kembali aktif). */
    override suspend fun restore(tenantId: TenantId, id: DepartmentId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            DepartmentsTable.update({
                (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.id eq id.value)
            }) {
                it[archivedAt] = null
            }
        }
    }

    /** Hard delete — hanya untuk admin reset & restore-presets. */
    override suspend fun delete(tenantId: TenantId, id: DepartmentId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            DepartmentsTable.deleteWhere {
                (DepartmentsTable.tenantId eq tenantId.value) and (DepartmentsTable.id eq id.value)
            }
        }
    }

    override suspend fun restoreDefaultPresets(tenantId: TenantId): Result<List<Department>> = runCatching {
        val presets = Department.defaultPresets(tenantId)
        presets.forEach { save(tenantId, it).getOrThrow() }
        presets
    }

    private fun toDepartment(row: ResultRow): Department = Department(
        id = DepartmentId(row[DepartmentsTable.id]),
        code = row[DepartmentsTable.code],
        displayName = row[DepartmentsTable.displayName],
        shortName = row[DepartmentsTable.shortName],
        colorHex = row[DepartmentsTable.colorHex],
        isCustom = row[DepartmentsTable.isCustom],
        tenantId = TenantId(row[DepartmentsTable.tenantId]),
        archivedAt = row[DepartmentsTable.archivedAt]
    )
}
