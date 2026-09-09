package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.tenant.TenantId

class RestoreDefaultEmployeesUseCase(
    private val employeeRepository: EmployeeRepository,
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<List<OrgNode>> = runCatching {
        var departments = departmentRepository.findAllByTenant(tenantId)
        if (departments.isEmpty()) {
            departments = departmentRepository.restoreDefaultPresets(tenantId).getOrThrow()
        }

        employeeRepository.restoreDefaultPresets(tenantId, departments).getOrThrow()
    }
}
