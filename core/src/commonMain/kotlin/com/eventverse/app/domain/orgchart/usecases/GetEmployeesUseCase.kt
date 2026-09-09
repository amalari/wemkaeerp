package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

class GetEmployeesUseCase(
    private val employeeRepository: EmployeeRepository
) {
    suspend fun getAll(tenantId: TenantId, departmentId: DepartmentId? = null): Result<List<OrgNode>> = runCatching {
        if (departmentId != null) {
            employeeRepository.findByDepartment(tenantId, departmentId)
        } else {
            employeeRepository.findAllByTenant(tenantId)
        }
    }

    suspend fun getById(tenantId: TenantId, id: OrgNodeId): Result<OrgNode?> = runCatching {
        employeeRepository.findById(tenantId, id)
    }
}
