package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.tenant.TenantId

data class UpdateDepartmentCommand(
    val tenantId: TenantId,
    val id: DepartmentId,
    val displayName: String? = null,
    val shortName: String? = null,
    val colorHex: Long? = null
)

class UpdateDepartmentUseCase(
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(command: UpdateDepartmentCommand): Result<Department> = runCatching {
        val existing = departmentRepository.findById(command.tenantId, command.id)
            ?: error("Divisi dengan ID '${command.id.value}' tidak ditemukan")

        val newDisplayName = command.displayName?.trim()?.takeIf { it.isNotBlank() } ?: existing.displayName
        val newShortName = command.shortName?.trim()?.takeIf { it.isNotBlank() } ?: existing.shortName
        val newColorHex = command.colorHex ?: existing.colorHex

        val updated = existing.copy(
            displayName = newDisplayName,
            shortName = newShortName,
            colorHex = newColorHex
        )

        departmentRepository.save(command.tenantId, updated).getOrThrow()
    }
}
