package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.tenant.TenantId

class GetDepartmentsUseCase(
    private val departmentRepository: DepartmentRepository
) {
    suspend fun getAll(tenantId: TenantId): Result<List<Department>> = runCatching {
        departmentRepository.findAllByTenant(tenantId)
    }

    suspend fun getById(tenantId: TenantId, id: DepartmentId): Result<Department?> = runCatching {
        departmentRepository.findById(tenantId, id)
    }
}
