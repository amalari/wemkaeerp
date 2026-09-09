package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.*
import com.eventverse.app.domain.tenant.TenantId

data class CreateEmployeeCommand(
    val tenantId: TenantId,
    val name: String,
    val email: String,
    val departmentId: DepartmentId? = null,
    val level: HierarchyLevel,
    val roleTitle: String = "",
    val reportsToId: OrgNodeId? = null,
    val phone: String = "",
    val tierName: String? = null,
    val successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
)

class CreateEmployeeUseCase(
    private val employeeRepository: EmployeeRepository,
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(command: CreateEmployeeCommand): Result<OrgNode> = runCatching {
        val trimmedName = command.name.trim()
        require(trimmedName.isNotBlank()) { "Nama karyawan tidak boleh kosong" }

        val department = if (command.departmentId != null) {
            departmentRepository.findById(command.tenantId, command.departmentId)
                ?: error("Divisi dengan ID '${command.departmentId.value}' tidak ditemukan")
        } else if (command.level != HierarchyLevel.EXECUTIVE) {
            error("Divisi wajib dipilih untuk karyawan")
        } else null

        val allEmployees = employeeRepository.findAllByTenant(command.tenantId)

        val slug = trimmedName.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
        val prefix = if (command.tenantId.value == "ten-demo-001") "" else "${command.tenantId.value}-"
        val newId = OrgNodeId("emp-${prefix}$slug-${(100..999).random()}")

        val resolvedReportsTo = if (command.reportsToId != null) {
            command.reportsToId
        } else when (command.level) {
            HierarchyLevel.EXECUTIVE -> null
            HierarchyLevel.HEAD_OF_DEPARTMENT -> allEmployees.find { it.level == HierarchyLevel.EXECUTIVE }?.id
            HierarchyLevel.TEAM_LEAD, HierarchyLevel.STAFF_OPERATOR -> allEmployees.find {
                it.department?.id == department?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
            }?.id ?: allEmployees.find { it.level == HierarchyLevel.EXECUTIVE }?.id
        }

        val targetEmail = command.email.trim().ifBlank { "${slug}@wemade.id" }
        val existingWithEmail = employeeRepository.findByEmail(command.tenantId, targetEmail)
        if (existingWithEmail != null) {
            throw EmailConflictException(
                email = targetEmail,
                existingEmployeeId = existingWithEmail.id.value,
                existingEmployeeName = existingWithEmail.name,
                existingDepartmentName = existingWithEmail.department?.displayName ?: "Direksi",
                existingRoleTitle = existingWithEmail.roleTitle,
                isArchived = existingWithEmail.archivedAt != null
            )
        }

        val newNode = OrgNode(
            id = newId,
            name = trimmedName,
            email = targetEmail,
            department = department,
            level = command.level,
            tierName = command.tierName ?: if (command.level == HierarchyLevel.EXECUTIVE) "Direksi" else "Staf",
            roleTitle = command.roleTitle.trim().ifBlank { command.level.displayName },
            reportsToId = resolvedReportsTo,
            phone = command.phone.trim(),
            tenantId = command.tenantId
        )

        // Single Active Head Rule and Succession Management
        if (newNode.level == HierarchyLevel.HEAD_OF_DEPARTMENT && department != null) {
            val existingHead = allEmployees.find {
                it.department?.id == department.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
            }

            if (existingHead != null) {
                when (command.successionAction) {
                    HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                        val demotedHead = existingHead.copy(
                            level = HierarchyLevel.STAFF_OPERATOR,
                            roleTitle = "Staf Senior ${department.shortName}",
                            reportsToId = newNode.id
                        )
                        val staffToUpdate = allEmployees.filter {
                            it.reportsToId == existingHead.id && it.id != existingHead.id
                        }.map { it.copy(reportsToId = newNode.id) }

                        val toSave = listOf(demotedHead) + staffToUpdate
                        employeeRepository.saveAll(command.tenantId, toSave).getOrThrow()
                    }
                    HeadSuccessionAction.DEACTIVATE -> {
                        employeeRepository.delete(command.tenantId, existingHead.id).getOrThrow()
                        val staffToUpdate = allEmployees.filter {
                            it.reportsToId == existingHead.id && it.id != existingHead.id
                        }.map { it.copy(reportsToId = newNode.id) }

                        if (staffToUpdate.isNotEmpty()) {
                            employeeRepository.saveAll(command.tenantId, staffToUpdate).getOrThrow()
                        }
                    }
                }
            }
        }

        employeeRepository.save(command.tenantId, newNode).getOrThrow()
    }
}
