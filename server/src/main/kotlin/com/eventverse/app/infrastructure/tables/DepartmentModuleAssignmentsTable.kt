package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

/**
 * Penugasan modul ke divisi. Lihat `V17__add_persona_testing_and_module_assignments.sql`.
 *
 * [specificRoleIds] kosong (`[]`) berarti "seluruh jabatan di divisi ini" — padanan
 * `DepartmentModuleAssignment.appliesToAllRoles` di domain.
 */
object DepartmentModuleAssignmentsTable : Table("dynamic_rbac.department_module_assignments") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val departmentId = varchar("department_id", 64).references(DepartmentsTable.id)
    val module = varchar("module", 50)
    val accessLevel = varchar("access_level", 20).default("OPERATE")
    val dataScope = varchar("data_scope", 30).default("ALL_TENANT_DATA")

    /** Daftar id jabatan sebagai array JSON. `JSONB` di schema — lihat [jsonbText]. */
    val specificRoleIds = jsonbText("specific_role_ids").default("[]")

    /**
     * Meja lantai produksi yang boleh diakses (khusus `OPERATOR_EXEC`), array JSON berisi nama
     * tahap. `[]` berarti seluruh meja — nilai default, kompatibel dengan baris lama.
     */
    val allowedDesks = jsonbText("allowed_desks").default("[]")

    override val primaryKey = PrimaryKey(id)
}
