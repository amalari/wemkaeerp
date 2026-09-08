package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.*

data class OrgChartUiState(
    val employees: List<OrgNode> = emptyList(),
    val departments: List<Department> = Department.defaultPresets(),
    val selectedEmployeeId: String? = null,
    val isCreatingNew: Boolean = true,
    // Form Inputs
    val nameInput: String = "Dimas Pratama",
    val emailInput: String = "dimas.sales@wemade.id",
    val phoneInput: String = "082155667788",
    val selectedDepartment: Department? = Department.SALES,
    val selectedLevel: HierarchyLevel = HierarchyLevel.STAFF_OPERATOR,
    val selectedReportsToId: String? = "emp-budi",
    val roleTitleInput: String = "Sales Eksekutif Baju Seragam",
    // Leadership Succession Policy
    val successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF,
    // Modal & Dynamic Department Creation
    val isCreateDeptModalOpen: Boolean = false,
    val newDeptNameInput: String = "",
    val newDeptShortNameInput: String = "",
    val newDeptColorHex: Long = 0xFF2563EB,
    val isResetMenuOpen: Boolean = false,
    // Feedback & Filter
    val toastMessage: String? = null,
    val filterDepartment: Department? = null
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
     * Virtual draft node generated from current form inputs.
     */
    val draftNode: OrgNode
        get() = OrgNode(
            id = OrgNodeId(if (isCreatingNew) "emp-new-draft" else (selectedEmployeeId ?: "emp-draft")),
            name = nameInput.ifBlank { "Nama Karyawan Baru" },
            email = emailInput.ifBlank { "email@wemade.id" },
            department = activeDepartment,
            level = selectedLevel,
            roleTitle = roleTitleInput.ifBlank { selectedLevel.displayName },
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
                it.department.id == targetDept.id &&
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

    val availableSuperiors: List<OrgNode>
        get() = employees.filter { superiorCandidate ->
            when (selectedLevel) {
                HierarchyLevel.EXECUTIVE -> false
                HierarchyLevel.HEAD_OF_DEPARTMENT -> superiorCandidate.level == HierarchyLevel.EXECUTIVE
                HierarchyLevel.STAFF_OPERATOR -> superiorCandidate.level == HierarchyLevel.HEAD_OF_DEPARTMENT &&
                        superiorCandidate.department.id == activeDepartment.id
            }
        }
}

sealed interface OrgChartUiEvent {
    data class UpdateName(val name: String) : OrgChartUiEvent
    data class UpdateEmail(val email: String) : OrgChartUiEvent
    data class UpdatePhone(val phone: String) : OrgChartUiEvent
    data class SelectDepartment(val dept: Department) : OrgChartUiEvent
    data class SelectLevel(val level: HierarchyLevel) : OrgChartUiEvent
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

    // Reset / Blank Slate & Preset Restores
    data object ToggleResetMenu : OrgChartUiEvent
    data object ClearAllDataToEmpty : OrgChartUiEvent
    data object RestoreDefaultPresets : OrgChartUiEvent
}
