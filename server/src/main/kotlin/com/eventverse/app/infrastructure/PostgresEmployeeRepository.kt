package com.eventverse.app.infrastructure

import com.eventverse.app.domain.orgchart.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DepartmentsTable
import com.eventverse.app.infrastructure.tables.EmployeesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

class PostgresEmployeeRepository : EmployeeRepository {

    override suspend fun findById(tenantId: TenantId, id: OrgNodeId): OrgNode? = DatabaseFactory.dbQuery(tenantId) {
        (EmployeesTable leftJoin DepartmentsTable)
            .selectAll()
            .where { (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.id eq id.value) }
            .map { toOrgNode(it) }
            .singleOrNull()
    }

    override suspend fun findByEmail(tenantId: TenantId, email: String): OrgNode? = DatabaseFactory.dbQuery(tenantId) {
        (EmployeesTable leftJoin DepartmentsTable)
            .selectAll()
            .where { (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.email eq email) }
            .map { toOrgNode(it) }
            .singleOrNull()
    }

    /** Hanya mengembalikan karyawan AKTIF (archived_at IS NULL). */
    override suspend fun findAllByTenant(tenantId: TenantId): List<OrgNode> = DatabaseFactory.dbQuery(tenantId) {
        (EmployeesTable leftJoin DepartmentsTable)
            .selectAll()
            .where {
                (EmployeesTable.tenantId eq tenantId.value) and
                (EmployeesTable.archivedAt.isNull())
            }
            .map { toOrgNode(it) }
    }

    /** Hanya mengembalikan karyawan AKTIF di satu divisi. */
    override suspend fun findByDepartment(tenantId: TenantId, departmentId: DepartmentId): List<OrgNode> = DatabaseFactory.dbQuery(tenantId) {
        (EmployeesTable leftJoin DepartmentsTable)
            .selectAll()
            .where {
                (EmployeesTable.tenantId eq tenantId.value) and
                (EmployeesTable.departmentId eq departmentId.value) and
                (EmployeesTable.archivedAt.isNull())
            }
            .map { toOrgNode(it) }
    }

    /** Mengembalikan semua karyawan yang sudah DIARSIPKAN (archived_at IS NOT NULL). */
    override suspend fun findAllArchived(tenantId: TenantId): List<OrgNode> = DatabaseFactory.dbQuery(tenantId) {
        (EmployeesTable leftJoin DepartmentsTable)
            .selectAll()
            .where {
                (EmployeesTable.tenantId eq tenantId.value) and
                (EmployeesTable.archivedAt.isNotNull())
            }
            .map { toOrgNode(it) }
    }

    override suspend fun save(tenantId: TenantId, employee: OrgNode): Result<OrgNode> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            val exists = EmployeesTable.selectAll()
                .where { (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.id eq employee.id.value) }
                .count() > 0

            if (exists) {
                EmployeesTable.update({ (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.id eq employee.id.value) }) {
                    it[name] = employee.name
                    it[email] = employee.email
                    it[departmentId] = employee.department?.id?.value
                    it[level] = employee.level.name
                    it[roleTitle] = employee.roleTitle
                    it[reportsToId] = employee.reportsToId?.value
                    it[phone] = employee.phone
                }
            } else {
                EmployeesTable.insert {
                    it[EmployeesTable.id] = employee.id.value
                    it[EmployeesTable.tenantId] = tenantId.value
                    it[EmployeesTable.name] = employee.name
                    it[email] = employee.email
                    it[departmentId] = employee.department?.id?.value
                    it[level] = employee.level.name
                    it[roleTitle] = employee.roleTitle
                    it[reportsToId] = employee.reportsToId?.value
                    it[phone] = employee.phone
                    // archivedAt defaults to NULL (active)
                }
            }
            if (employee.tenantId == null) employee.copy(tenantId = tenantId) else employee
        }
    }

    override suspend fun saveAll(tenantId: TenantId, employees: List<OrgNode>): Result<List<OrgNode>> = runCatching {
        employees.forEach { save(tenantId, it).getOrThrow() }
        employees
    }

    /** Soft-delete: set archived_at = NOW(). */
    override suspend fun archive(tenantId: TenantId, id: OrgNodeId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            EmployeesTable.update({
                (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.id eq id.value)
            }) {
                it[archivedAt] = java.time.Instant.now().toString()
            }
        }
    }

    /** Unarchive: set archived_at = NULL (kembali aktif). */
    override suspend fun restore(tenantId: TenantId, id: OrgNodeId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            EmployeesTable.update({
                (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.id eq id.value)
            }) {
                it[archivedAt] = null
            }
        }
    }

    /** Hard delete — hanya untuk admin reset & restore-presets. */
    override suspend fun delete(tenantId: TenantId, id: OrgNodeId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            EmployeesTable.deleteWhere {
                (EmployeesTable.tenantId eq tenantId.value) and (EmployeesTable.id eq id.value)
            }
        }
    }

    override suspend fun restoreDefaultPresets(
        tenantId: TenantId,
        departments: List<Department>
    ): Result<List<OrgNode>> = runCatching {
        val deptMap = departments.associateBy { it.code }
        val sampleNodes = OrgNode.createSampleEmployees(tenantId).map { emp ->
            val resolvedDept = emp.department?.let { deptMap[it.code] ?: it }
            emp.copy(tenantId = tenantId, department = resolvedDept)
        }
        saveAll(tenantId, sampleNodes).getOrThrow()
        sampleNodes
    }

    private fun toOrgNode(row: ResultRow): OrgNode {
        val dept = if (row.getOrNull(DepartmentsTable.id) != null) {
            Department(
                id = DepartmentId(row[DepartmentsTable.id]),
                code = row[DepartmentsTable.code],
                displayName = row[DepartmentsTable.displayName],
                shortName = row[DepartmentsTable.shortName],
                colorHex = row[DepartmentsTable.colorHex],
                isCustom = row[DepartmentsTable.isCustom],
                tenantId = TenantId(row[DepartmentsTable.tenantId]),
                archivedAt = row[DepartmentsTable.archivedAt]
            )
        } else {
            null
        }

        return OrgNode(
            id = OrgNodeId(row[EmployeesTable.id]),
            name = row[EmployeesTable.name],
            email = row[EmployeesTable.email],
            department = dept,
            level = runCatching { HierarchyLevel.valueOf(row[EmployeesTable.level]) }.getOrDefault(HierarchyLevel.STAFF_OPERATOR),
            roleTitle = row[EmployeesTable.roleTitle],
            reportsToId = row[EmployeesTable.reportsToId]?.let { OrgNodeId(it) },
            phone = row[EmployeesTable.phone],
            tenantId = TenantId(row[EmployeesTable.tenantId]),
            archivedAt = row[EmployeesTable.archivedAt]
        )
    }
}
