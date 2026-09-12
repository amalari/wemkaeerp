package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.tenant.TenantId

/**
 * Kontrak repository untuk penugasan modul ke divisi ([DepartmentModuleAssignment]).
 *
 * Operasinya sengaja berbentuk domain, bukan CRUD generik: yang dilakukan admin pabrik adalah
 * *"atur wewenang divisi X atas modul Y"* dan *"cabut wewenang itu"* — bukan "update row".
 */
interface ModuleAssignmentRepository {

    /** Seluruh assignment satu tenant, dikelompokkan per modul — bentuk yang dipakai evaluasi hak. */
    suspend fun findAllByTenant(tenantId: TenantId): Map<BusinessModule, List<DepartmentModuleAssignment>>

    /** Menyimpan atau memperbarui satu penugasan. Identitasnya ditentukan `assignmentKey`. */
    suspend fun upsert(
        tenantId: TenantId,
        module: BusinessModule,
        assignment: DepartmentModuleAssignment
    ): Result<DepartmentModuleAssignment>

    /** Mencabut satu penugasan. `assignmentKey` sama dengan yang dipakai [upsert]. */
    suspend fun remove(
        tenantId: TenantId,
        module: BusinessModule,
        assignmentKey: String
    ): Result<Unit>
}
