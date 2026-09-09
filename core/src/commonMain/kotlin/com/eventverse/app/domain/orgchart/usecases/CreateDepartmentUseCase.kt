package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.tenant.TenantId

data class CreateDepartmentCommand(
    val tenantId: TenantId,
    val displayName: String,
    val shortName: String = "",
    val colorHex: Long
)

class CreateDepartmentUseCase(
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(command: CreateDepartmentCommand): Result<Department> = runCatching {
        val trimmedName = command.displayName.trim()
        require(trimmedName.isNotBlank()) { "Nama divisi tidak boleh kosong" }

        val newDept = Department.createCustom(
            name = trimmedName,
            shortName = command.shortName,
            colorHex = command.colorHex,
            tenantId = command.tenantId
        )

        departmentRepository.save(command.tenantId, newDept).getOrThrow()
    }
}
