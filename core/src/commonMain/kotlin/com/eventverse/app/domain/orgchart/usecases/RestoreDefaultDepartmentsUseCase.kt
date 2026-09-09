package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.tenant.TenantId

class RestoreDefaultDepartmentsUseCase(
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<List<Department>> = runCatching {
        departmentRepository.restoreDefaultPresets(tenantId).getOrThrow()
    }
}
