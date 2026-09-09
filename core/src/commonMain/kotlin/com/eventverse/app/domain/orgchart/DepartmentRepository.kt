package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.tenant.TenantId

/**
 * Domain repository interface for Department entity.
 * Pure Kotlin contract adhering to Domain-Driven Design rules.
 */
interface DepartmentRepository {
    suspend fun findById(tenantId: TenantId, id: DepartmentId): Department?
    suspend fun findByCode(tenantId: TenantId, code: String): Department?
    /** Hanya mengembalikan divisi AKTIF (archived_at IS NULL). */
    suspend fun findAllByTenant(tenantId: TenantId): List<Department>
    /** Mengembalikan semua divisi yang sudah DIARSIPKAN (archived_at IS NOT NULL). */
    suspend fun findAllArchived(tenantId: TenantId): List<Department>
    suspend fun save(tenantId: TenantId, department: Department): Result<Department>
    /** Soft-delete: set archived_at = NOW(). */
    suspend fun archive(tenantId: TenantId, id: DepartmentId): Result<Unit>
    /** Unarchive: set archived_at = NULL. */
    suspend fun restore(tenantId: TenantId, id: DepartmentId): Result<Unit>
    /** Hard delete — hanya untuk admin reset & restore-presets. */
    suspend fun delete(tenantId: TenantId, id: DepartmentId): Result<Unit>
    suspend fun restoreDefaultPresets(tenantId: TenantId): Result<List<Department>>
}
