package com.eventverse.app.infrastructure

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

class InMemoryDepartmentRepository : DepartmentRepository {
    private val storage = ConcurrentHashMap<String, Department>()

    override suspend fun findById(tenantId: TenantId, id: DepartmentId): Department? =
        storage[key(tenantId, id.value)]

    override suspend fun findByCode(tenantId: TenantId, code: String): Department? =
        storage.values.find { it.tenantId == tenantId && it.code == code }

    /** Hanya mengembalikan divisi AKTIF (archivedAt == null). */
    override suspend fun findAllByTenant(tenantId: TenantId): List<Department> =
        storage.values.filter { it.tenantId == tenantId && it.archivedAt == null }

    /** Mengembalikan semua divisi yang sudah DIARSIPKAN. */
    override suspend fun findAllArchived(tenantId: TenantId): List<Department> =
        storage.values.filter { it.tenantId == tenantId && it.archivedAt != null }

    override suspend fun save(tenantId: TenantId, department: Department): Result<Department> {
        val withTenant = if (department.tenantId == null) department.copy(tenantId = tenantId) else department
        storage[key(tenantId, withTenant.id.value)] = withTenant
        return Result.success(withTenant)
    }

    /** Soft-delete: set archivedAt = now ISO string. */
    override suspend fun archive(tenantId: TenantId, id: DepartmentId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalArgumentException("Department not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = java.time.Instant.now().toString())
        return Result.success(Unit)
    }

    /** Unarchive: set archivedAt = null. */
    override suspend fun restore(tenantId: TenantId, id: DepartmentId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalArgumentException("Department not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = null)
        return Result.success(Unit)
    }

    /** Hard delete — hanya untuk admin reset & restore-presets. */
    override suspend fun delete(tenantId: TenantId, id: DepartmentId): Result<Unit> {
        storage.remove(key(tenantId, id.value))
        return Result.success(Unit)
    }

    override suspend fun restoreDefaultPresets(tenantId: TenantId): Result<List<Department>> {
        val presets = Department.defaultPresets(tenantId)
        presets.forEach { save(tenantId, it) }
        return Result.success(presets)
    }

    private fun key(tenantId: TenantId, id: String) = "${tenantId.value}:$id"
}
