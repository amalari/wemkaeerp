package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.*
import com.eventverse.app.domain.tenant.TenantId

class GetEmployeeTShapeHierarchyUseCase(
    private val employeeRepository: EmployeeRepository
) {
    suspend operator fun invoke(tenantId: TenantId, employeeId: OrgNodeId): Result<TShapeHierarchyResult> = runCatching {
        val allEmployees = employeeRepository.findAllByTenant(tenantId)
        val focusNode = allEmployees.find { it.id == employeeId }
            ?: error("Karyawan dengan ID '${employeeId.value}' tidak ditemukan")

        OrgNode.resolveTShapeView(allEmployees, focusNode)
    }
}
