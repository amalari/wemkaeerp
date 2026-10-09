package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment
import com.eventverse.app.infrastructure.api.RbacApiClient

/**
 * Hasil satu kali pemuatan layar Hak Akses. [employeeCount] `null` = jumlah karyawan tidak terbaca
 * (mis. admin RBAC tanpa akses Org Chart), BUKAN nol: layar menyembunyikan chip-nya (TRD-PLAT-010 K6).
 */
internal data class RbacLoadResult(
    val loadState: RbacLoadState,
    val roles: List<CustomRole> = emptyList(),
    val departments: List<Department> = emptyList(),
    val assignments: Map<BusinessModule, List<DepartmentModuleAssignment>> = emptyMap(),
    val employeeCount: Int? = null
)

/**
 * Pemuatan data Hak Akses dari server untuk SATU tenant ([tenantSlug] diberikan pemanggil, tanpa default).
 * Tanpa state sendiri: murni terhadap `ViewModel`, bisa diuji dengan klien palsu. Data server menang
 * penuh; tidak ada sampel lokal dan tidak ada fallback senyap. Klien sudah membungkus galat ke `Result`.
 */
internal object RbacDataLoader {
    suspend fun load(client: RbacApiClient, tenantSlug: String): RbacLoadResult {
        val roles = client.getRoles(tenantSlug)
        val depts = client.getDepartments(tenantSlug)
        val assignments = client.getModuleAssignments(tenantSlug)
        val state = RbacLoadState.from(roles, depts, assignments)
        if (state is RbacLoadState.Failed) return RbacLoadResult(state)
        return RbacLoadResult(
            loadState = state,
            roles = roles.getOrDefault(emptyList()),
            departments = depts.getOrDefault(emptyList()),
            assignments = assignments.getOrDefault(emptyMap()),
            employeeCount = client.getEmployees(tenantSlug).getOrNull()?.size
        )
    }
}
