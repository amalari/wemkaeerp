package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.*
import com.eventverse.app.domain.tenant.TenantId

data class UpdateEmployeeCommand(
    val tenantId: TenantId,
    val id: OrgNodeId,
    val name: String? = null,
    val email: String? = null,
    val departmentId: DepartmentId? = null,
    val level: HierarchyLevel? = null,
    val roleTitle: String? = null,
    val reportsToId: OrgNodeId? = null,
    val phone: String? = null,
    val successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
)

class UpdateEmployeeUseCase(
    private val employeeRepository: EmployeeRepository,
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(command: UpdateEmployeeCommand): Result<OrgNode> = runCatching {
        val existing = employeeRepository.findById(command.tenantId, command.id)
            ?: error("Karyawan dengan ID '${command.id.value}' tidak ditemukan")

        val department = if (command.departmentId != null) {
            departmentRepository.findById(command.tenantId, command.departmentId)
                ?: error("Divisi dengan ID '${command.departmentId.value}' tidak ditemukan")
        } else {
            existing.department
        }

        val targetLevel = command.level ?: existing.level
        val allEmployees = employeeRepository.findAllByTenant(command.tenantId)

        // If promoted/assigned as HEAD_OF_DEPARTMENT and not previously head of this dept
        if (targetLevel == HierarchyLevel.HEAD_OF_DEPARTMENT && department != null && (existing.level != HierarchyLevel.HEAD_OF_DEPARTMENT || existing.department?.id != department.id)) {
            val existingHead = allEmployees.find {
                it.department?.id == department.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT && it.id != existing.id
            }

            if (existingHead != null) {
                when (command.successionAction) {
                    HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                        val demotedHead = existingHead.copy(
                            level = HierarchyLevel.STAFF_OPERATOR,
                            roleTitle = "Staf Senior ${department.shortName}",
                            reportsToId = existing.id
                        )
                        val staffToUpdate = allEmployees.filter {
                            it.reportsToId == existingHead.id && it.id != existingHead.id && it.id != existing.id
                        }.map { it.copy(reportsToId = existing.id) }

                        employeeRepository.saveAll(command.tenantId, listOf(demotedHead) + staffToUpdate).getOrThrow()
                    }
                    HeadSuccessionAction.DEACTIVATE -> {
                        employeeRepository.delete(command.tenantId, existingHead.id).getOrThrow()
                        val staffToUpdate = allEmployees.filter {
                            it.reportsToId == existingHead.id && it.id != existingHead.id && it.id != existing.id
                        }.map { it.copy(reportsToId = existing.id) }

                        if (staffToUpdate.isNotEmpty()) {
                            employeeRepository.saveAll(command.tenantId, staffToUpdate).getOrThrow()
                        }
                    }
                }
            }
        }

        val targetEmail = command.email?.trim()?.takeIf { it.isNotBlank() } ?: existing.email
        if (!targetEmail.equals(existing.email, ignoreCase = true)) {
            val existingWithEmail = employeeRepository.findByEmail(command.tenantId, targetEmail)
            if (existingWithEmail != null && existingWithEmail.id != existing.id) {
                throw EmailConflictException(
                    email = targetEmail,
                    existingEmployeeId = existingWithEmail.id.value,
                    existingEmployeeName = existingWithEmail.name,
                    existingDepartmentName = existingWithEmail.department?.displayName ?: "Direksi",
                    existingRoleTitle = existingWithEmail.roleTitle,
                    isArchived = existingWithEmail.archivedAt != null
                )
            }
        }

        val updated = existing.copy(
            name = command.name?.trim()?.takeIf { it.isNotBlank() } ?: existing.name,
            email = targetEmail,
            department = department,
            level = targetLevel,
            roleTitle = command.roleTitle?.trim()?.takeIf { it.isNotBlank() } ?: existing.roleTitle,
            reportsToId = command.reportsToId ?: existing.reportsToId,
            phone = command.phone?.trim() ?: existing.phone
        )

        employeeRepository.save(command.tenantId, updated).getOrThrow()
    }
}
