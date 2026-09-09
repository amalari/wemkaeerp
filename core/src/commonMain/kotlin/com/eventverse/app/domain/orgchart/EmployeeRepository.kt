package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.tenant.TenantId

/**
 * Domain repository interface for OrgNode / Employee entity.
 * Pure Kotlin contract adhering to Domain-Driven Design rules.
 */
interface EmployeeRepository {
    suspend fun findById(tenantId: TenantId, id: OrgNodeId): OrgNode?
    suspend fun findByEmail(tenantId: TenantId, email: String): OrgNode?
    /** Hanya mengembalikan karyawan AKTIF (archived_at IS NULL). */
    suspend fun findAllByTenant(tenantId: TenantId): List<OrgNode>
    /** Hanya mengembalikan karyawan AKTIF di satu divisi. */
    suspend fun findByDepartment(tenantId: TenantId, departmentId: DepartmentId): List<OrgNode>
    /** Mengembalikan semua karyawan yang sudah DIARSIPKAN (archived_at IS NOT NULL). */
    suspend fun findAllArchived(tenantId: TenantId): List<OrgNode>
    suspend fun save(tenantId: TenantId, employee: OrgNode): Result<OrgNode>
    suspend fun saveAll(tenantId: TenantId, employees: List<OrgNode>): Result<List<OrgNode>>
    /** Soft-delete: set archived_at = NOW(). Reparenting dilakukan di use case. */
    suspend fun archive(tenantId: TenantId, id: OrgNodeId): Result<Unit>
    /** Unarchive: set archived_at = NULL. */
    suspend fun restore(tenantId: TenantId, id: OrgNodeId): Result<Unit>
    /** Hard delete — hanya untuk admin reset & restore-presets. */
    suspend fun delete(tenantId: TenantId, id: OrgNodeId): Result<Unit>
    suspend fun restoreDefaultPresets(tenantId: TenantId, departments: List<Department>): Result<List<OrgNode>>
}
