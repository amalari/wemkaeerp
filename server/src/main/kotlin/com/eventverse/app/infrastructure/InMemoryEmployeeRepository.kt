package com.eventverse.app.infrastructure

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

class InMemoryEmployeeRepository : EmployeeRepository {
    private val storage = ConcurrentHashMap<String, OrgNode>()

    override suspend fun findById(tenantId: TenantId, id: OrgNodeId): OrgNode? =
        storage[key(tenantId, id.value)]

    override suspend fun findByEmail(tenantId: TenantId, email: String): OrgNode? =
        storage.values.find { it.tenantId == tenantId && it.email.equals(email, ignoreCase = true) }

    /** Hanya mengembalikan karyawan AKTIF (archivedAt == null). */
    override suspend fun findAllByTenant(tenantId: TenantId): List<OrgNode> =
        storage.values.filter { it.tenantId == tenantId && it.archivedAt == null }

    /** Hanya mengembalikan karyawan AKTIF di satu divisi. */
    override suspend fun findByDepartment(tenantId: TenantId, departmentId: DepartmentId): List<OrgNode> =
        storage.values.filter { it.tenantId == tenantId && it.department?.id == departmentId && it.archivedAt == null }

    /** Mengembalikan semua karyawan yang sudah DIARSIPKAN. */
    override suspend fun findAllArchived(tenantId: TenantId): List<OrgNode> =
        storage.values.filter { it.tenantId == tenantId && it.archivedAt != null }

    override suspend fun save(tenantId: TenantId, employee: OrgNode): Result<OrgNode> {
        val withTenant = if (employee.tenantId == null) employee.copy(tenantId = tenantId) else employee
        storage[key(tenantId, withTenant.id.value)] = withTenant
        return Result.success(withTenant)
    }

    override suspend fun saveAll(tenantId: TenantId, employees: List<OrgNode>): Result<List<OrgNode>> {
        val list = employees.map { emp ->
            val withTenant = if (emp.tenantId == null) emp.copy(tenantId = tenantId) else emp
            storage[key(tenantId, withTenant.id.value)] = withTenant
            withTenant
        }
        return Result.success(list)
    }

    /** Soft-delete: set archivedAt = now. */
    override suspend fun archive(tenantId: TenantId, id: OrgNodeId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalArgumentException("Employee not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = java.time.Instant.now().toString())
        return Result.success(Unit)
    }

    /** Unarchive: set archivedAt = null. */
    override suspend fun restore(tenantId: TenantId, id: OrgNodeId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalArgumentException("Employee not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = null)
        return Result.success(Unit)
    }

    /** Hard delete — hanya untuk admin reset & restore-presets. */
    override suspend fun delete(tenantId: TenantId, id: OrgNodeId): Result<Unit> {
        storage.remove(key(tenantId, id.value))
        return Result.success(Unit)
    }

    override suspend fun restoreDefaultPresets(
        tenantId: TenantId,
        departments: List<Department>
    ): Result<List<OrgNode>> {
        val deptMap = departments.associateBy { it.code }
        val sampleNodes = OrgNode.createSampleEmployees(tenantId).map { emp ->
            val resolvedDept = emp.department?.let { deptMap[it.code] ?: it }
            emp.copy(tenantId = tenantId, department = resolvedDept)
        }
        saveAll(tenantId, sampleNodes)
        return Result.success(sampleNodes)
    }

    private fun key(tenantId: TenantId, id: String) = "${tenantId.value}:$id"
}
