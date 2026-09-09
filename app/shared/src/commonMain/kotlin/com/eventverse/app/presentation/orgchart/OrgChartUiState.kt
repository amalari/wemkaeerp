package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.*

data class OrgChartUiState(
    val employees: List<OrgNode> = emptyList(),
    val departments: List<Department> = Department.defaultPresets(),
    val selectedEmployeeId: String? = null,
    val isCreatingNew: Boolean = true,
    // Form Inputs
    val nameInput: String = "",
    val emailInput: String = "",
    val phoneInput: String = "",
    val selectedDepartment: Department? = Department.SALES,
    val isDepartmentLocked: Boolean = false,
    val selectedLevel: HierarchyLevel = HierarchyLevel.STAFF_OPERATOR,
    val selectedTierName: String? = "Staf Pelaksana / Operator",
    val selectedReportsToId: String? = null,
    val roleTitleInput: String = "",
    // Leadership Succession Policy
    val successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF,
    // Modal & Dynamic Department & Tier Creation
    val isCreateDeptModalOpen: Boolean = false,
    val newDeptNameInput: String = "",
    val newDeptShortNameInput: String = "",
    val newDeptColorHex: Long = 0xFF2563EB,
    val isAddTierModalOpen: Boolean = false,
    val newTierNameInput: String = "",
    val isResetMenuOpen: Boolean = false,
    // Feedback & Filter
    val toastMessage: String? = null,
    val filterDepartment: Department? = null,
    // Delete/Archive Confirmation Modal
    val isDeleteDialogOpen: Boolean = false,
    val deleteTargetType: DeleteTargetType = DeleteTargetType.EMPLOYEE,
    val deleteTargetId: String = "",
    val deleteTargetName: String = "",
    val deleteWarningNote: String = "",
    val isDeleteBlocked: Boolean = false,
    // Archive Panel (Odoo-style)
    val showArchivedPanel: Boolean = false,
    val archivedEmployees: List<OrgNode> = emptyList(),
    val archivedDepartments: List<Department> = emptyList(),
    val isLoadingArchived: Boolean = false,
    // Email Conflict Modal
    val emailConflictModal: EmailConflictInfo? = null
) {
    /**
     * Curated colors that are NOT currently used by any active department.
     */
    val availableColorsForNewDept: List<DepartmentColor>
        get() = Department.availableColors(departments)

    /**
     * Active department currently targeted by the form.
     */
    val activeDepartment: Department
        get() = selectedDepartment ?: departments.firstOrNull() ?: Department.SALES

    /**
     * Daftar tingkatan wewenang yang tersedia pada divisi yang sedang aktif/dipilih.
     */
    val availableTiersForSelectedDept: List<DepartmentTier>
        get() = (selectedDepartment ?: activeDepartment).tiers

    /**
     * Virtual draft node generated from current form inputs.
     */
    val draftNode: OrgNode
        get() = OrgNode(
            id = OrgNodeId(if (isCreatingNew) "emp-new-draft" else (selectedEmployeeId ?: "emp-draft")),
            name = nameInput.ifBlank { "Nama Karyawan Baru" },
            email = emailInput.ifBlank { "email@wemade.id" },
            department = if (selectedLevel == HierarchyLevel.EXECUTIVE) null else (selectedDepartment ?: activeDepartment),
            level = selectedLevel,
            tierName = selectedTierName,
            roleTitle = roleTitleInput.ifBlank { selectedTierName ?: selectedLevel.displayName },
            reportsToId = selectedReportsToId?.let { OrgNodeId(it) },
            phone = phoneInput
        )

    /**
     * Finds existing head of department (if any) that would be displaced if current form promotes a new head.
     */
    val existingHeadOfSelectedDept: OrgNode?
        get() {
            if (selectedLevel != HierarchyLevel.HEAD_OF_DEPARTMENT) return null
            val targetDept = selectedDepartment ?: return null
            return employees.find {
                it.department?.id == targetDept.id &&
                it.level == HierarchyLevel.HEAD_OF_DEPARTMENT &&
                it.id.value != selectedEmployeeId
            }
        }

    /**
     * Recomputed T-Shape hierarchy preview based on the current draft node and succession action.
     */
    val resolvedHierarchy: TShapeHierarchyResult
        get() {
            val focus = if (isCreatingNew) draftNode else (employees.find { it.id.value == selectedEmployeeId } ?: draftNode)
            return OrgNode.resolveTShapeView(
                nodes = employees,
                focusNode = focus,
                isDraft = isCreatingNew,
                successionAction = successionAction
            )
        }

    /**
     * Semua pemimpin / atasan potensial di seluruh perusahaan (Direksi, Kepala Divisi, & Kepala Tim).
     * Digunakan untuk Jalur 1: "Pilih under siapa langsung keisi divisi & wewenang".
     */
    val companyLeaders: List<OrgNode>
        get() = employees.filter { candidate ->
            (candidate.level == HierarchyLevel.EXECUTIVE || candidate.level == HierarchyLevel.HEAD_OF_DEPARTMENT || candidate.level == HierarchyLevel.TEAM_LEAD) &&
                    (selectedEmployeeId == null || candidate.id.value != selectedEmployeeId)
        }.sortedWith(compareBy({ it.level.ordinal }, { it.department?.displayName ?: "" }))

    val availableSuperiors: List<OrgNode>
        get() {
            val dept = selectedDepartment ?: activeDepartment
            return employees.filter { superiorCandidate ->
                if (selectedEmployeeId != null && superiorCandidate.id.value == selectedEmployeeId) {
                    return@filter false
                }
                when (selectedLevel) {
                    HierarchyLevel.EXECUTIVE -> false
                    HierarchyLevel.HEAD_OF_DEPARTMENT -> superiorCandidate.level == HierarchyLevel.EXECUTIVE
                    HierarchyLevel.TEAM_LEAD -> {
                        (superiorCandidate.level == HierarchyLevel.HEAD_OF_DEPARTMENT && superiorCandidate.department?.id == dept.id) ||
                                superiorCandidate.level == HierarchyLevel.EXECUTIVE
                    }
                    HierarchyLevel.STAFF_OPERATOR -> {
                        ((superiorCandidate.level == HierarchyLevel.HEAD_OF_DEPARTMENT || superiorCandidate.level == HierarchyLevel.TEAM_LEAD) &&
                                superiorCandidate.department?.id == dept.id) ||
                                superiorCandidate.level == HierarchyLevel.EXECUTIVE
                    }
                }
            }.sortedBy { candidate ->
                if (candidate.department?.id == dept.id && candidate.level == HierarchyLevel.HEAD_OF_DEPARTMENT) 0
                else if (candidate.department?.id == dept.id && candidate.level == HierarchyLevel.TEAM_LEAD) 1
                else 2
            }
        }
}

sealed interface OrgChartUiEvent {
    data class UpdateName(val name: String) : OrgChartUiEvent
    data class UpdateEmail(val email: String) : OrgChartUiEvent
    data class UpdatePhone(val phone: String) : OrgChartUiEvent
    data class SelectDepartment(val dept: Department) : OrgChartUiEvent
    data class SelectLevel(val level: HierarchyLevel) : OrgChartUiEvent
    data class SelectTier(val tier: DepartmentTier) : OrgChartUiEvent
    data class SelectReportsTo(val superiorId: String?) : OrgChartUiEvent
    data class UpdateRoleTitle(val title: String) : OrgChartUiEvent
    data class SelectSuccessionAction(val action: HeadSuccessionAction) : OrgChartUiEvent
    data class SelectExistingEmployee(val id: String) : OrgChartUiEvent
    data object StartCreateNewEmployee : OrgChartUiEvent
    data object SaveEmployee : OrgChartUiEvent
    data object DismissToast : OrgChartUiEvent

    // Dynamic Department Events
    data object OpenCreateDeptModal : OrgChartUiEvent
    data object CloseCreateDeptModal : OrgChartUiEvent
    data class UpdateNewDeptName(val name: String) : OrgChartUiEvent
    data class UpdateNewDeptShortName(val shortName: String) : OrgChartUiEvent
    data class SelectNewDeptColor(val colorHex: Long) : OrgChartUiEvent
    data object SaveNewDepartment : OrgChartUiEvent

    // Dynamic Department Tier Events
    data object OpenAddTierModal : OrgChartUiEvent
    data object CloseAddTierModal : OrgChartUiEvent
    data class UpdateNewTierName(val name: String) : OrgChartUiEvent
    data object SaveNewDepartmentTier : OrgChartUiEvent

    // Reset / Blank Slate & Preset Restores
    data object ToggleResetMenu : OrgChartUiEvent
    data object ClearAllDataToEmpty : OrgChartUiEvent
    data object RestoreDefaultPresets : OrgChartUiEvent

    // Archive Operations (menggantikan hard-delete, pola Odoo)
    data class RequestArchiveEmployee(val id: String) : OrgChartUiEvent
    data class RequestArchiveDepartment(val dept: Department) : OrgChartUiEvent
    data object ConfirmDelete : OrgChartUiEvent
    data object CancelDelete : OrgChartUiEvent

    // Archive Panel
    data object ToggleArchivedPanel : OrgChartUiEvent
    data class RestoreEmployee(val id: String) : OrgChartUiEvent
    data class RestoreDepartment(val id: String) : OrgChartUiEvent

    // Email Conflict Modal
    data object DismissEmailConflictModal : OrgChartUiEvent
    data class RestoreEmployeeFromConflictModal(val id: String) : OrgChartUiEvent
}

enum class DeleteTargetType {
    EMPLOYEE,
    DEPARTMENT
}

data class EmailConflictInfo(
    val email: String,
    val existingEmployeeId: String,
    val existingEmployeeName: String,
    val existingDepartmentName: String,
    val existingRoleTitle: String,
    val isArchived: Boolean
)

