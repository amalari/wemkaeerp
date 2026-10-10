package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import kotlin.test.*

class OrgChartViewModelScopingTest {

    @Test
    fun whenAccessIsViewAndSubordinateData_shouldScopeToViewerDepartmentAndLockDepartment() {
        val access = ModuleAccessConfig(
            level = AccessLevel.VIEW,
            scope = DataScope.SUBORDINATE_DATA
        )
        val viewModel = OrgChartViewModel(
            tenantSlug = "wemade-demo",
            access = access,
            viewerDepartmentId = "dept-warehouse",
            viewerEmployeeId = "emp-sl-1",
            seed = OrgChartSeed.GarmentSample
        )
        val state = viewModel.uiState.value

        // 1. Department must be locked to warehouse
        assertTrue(state.isDepartmentLocked)
        assertNotNull(state.selectedDepartment)
        assertEquals("warehouse", state.selectedDepartment.code)
        assertEquals(1, state.departments.size)
        assertEquals("warehouse", state.departments.first().code)

        // 2. Read-only mode: isCreatingNew must be false and head/first employee auto-selected
        assertFalse(state.isCreatingNew)
        assertNotNull(state.selectedEmployeeId)

        // 3. All visible employees must belong to warehouse
        assertTrue(state.employees.isNotEmpty())
        assertTrue(state.employees.all { it.department?.code == "warehouse" })

        // 4. Switching department should be blocked
        viewModel.onEvent(OrgChartUiEvent.SelectDepartment(Department.SALES))
        assertEquals("warehouse", viewModel.uiState.value.selectedDepartment?.code)

        // 5. Switching to Direksi should be blocked
        viewModel.onEvent(OrgChartUiEvent.SelectDireksi)
        assertEquals("warehouse", viewModel.uiState.value.selectedDepartment?.code)

        // 6. Write operations must be blocked in read-only mode
        viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee)
        assertFalse(viewModel.uiState.value.isCreatingNew)

        viewModel.onEvent(OrgChartUiEvent.RestoreDefaultPresets)
        assertEquals(1, viewModel.uiState.value.departments.size)
    }

    @Test
    fun whenSelectingNodeInReadOnlyMode_shouldSwitchFocusWithoutDraftOverride() {
        val access = ModuleAccessConfig(
            level = AccessLevel.VIEW,
            scope = DataScope.SUBORDINATE_DATA
        )
        val viewModel = OrgChartViewModel(
            tenantSlug = "wemade-demo",
            access = access,
            viewerDepartmentId = "dept-warehouse",
            seed = OrgChartSeed.GarmentSample
        )

        val employees = viewModel.uiState.value.employees
        assertTrue(employees.isNotEmpty())

        val targetEmp = employees.first()
        viewModel.onEvent(OrgChartUiEvent.SelectExistingEmployee(targetEmp.id.value))

        val state = viewModel.uiState.value
        assertEquals(targetEmp.id.value, state.selectedEmployeeId)
        assertFalse(state.isCreatingNew)
        // In read-only mode, form inputs are kept blank so they don't clobber employee info
        assertEquals("", state.nameInput)

        // Resolved hierarchy should focus on the selected employee
        val hierarchy = state.resolvedHierarchy
        assertEquals(targetEmp.id.value, hierarchy.focusNode.id.value)
    }

    @Test
    fun whenAccessIsManageAndAllData_shouldAllowFullAccessAndSwitching() {
        val access = ModuleAccessConfig(
            level = AccessLevel.MANAGE,
            scope = DataScope.ALL_TENANT_DATA
        )
        val viewModel = OrgChartViewModel(
            tenantSlug = "wemade-demo",
            access = access,
            seed = OrgChartSeed.GarmentSample
        )
        val state = viewModel.uiState.value

        assertFalse(state.isDepartmentLocked)
        assertTrue(state.departments.size > 1)
        assertTrue(state.isCreatingNew)

        // Can switch to another department
        viewModel.onEvent(OrgChartUiEvent.SelectDepartment(Department.PRODUCTION_PPIC))
        assertEquals(Department.PRODUCTION_PPIC.id, viewModel.uiState.value.selectedDepartment?.id)
    }

    @Test
    fun whenAccessIsViewAndGlobalOnly_shouldShowFullChartInReadOnlyModeWithoutForm() {
        val access = ModuleAccessConfig(
            level = AccessLevel.VIEW,
            scope = DataScope.ALL_TENANT_DATA
        )
        val viewModel = OrgChartViewModel(
            tenantSlug = "wemade-demo",
            access = access,
            seed = OrgChartSeed.GarmentSample
        )
        val state = viewModel.uiState.value

        // 1. All departments available for inspection
        assertFalse(state.isDepartmentLocked)
        assertTrue(state.departments.size > 1)

        // 2. Read-only mode: isCreatingNew must be false, real employee selected
        assertFalse(state.isCreatingNew)
        assertNotNull(state.selectedEmployeeId)

        // 3. Can switch department view to inspect other divisions
        viewModel.onEvent(OrgChartUiEvent.SelectDepartment(Department.PRODUCTION_PPIC))
        assertEquals(Department.PRODUCTION_PPIC.id, viewModel.uiState.value.selectedDepartment?.id)

        // 4. Form inputs remain blank in read-only mode
        assertEquals("", viewModel.uiState.value.nameInput)

        // 5. Mutating operations blocked
        viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee)
        assertFalse(viewModel.uiState.value.isCreatingNew)
    }

    @Test
    fun whenViewerBelongsToWarehouse_shouldDefaultToWarehouseDepartmentAndAllowSwitchingToDireksiAndOtherDepts() {
        val access = ModuleAccessConfig(
            level = AccessLevel.VIEW,
            scope = DataScope.ALL_TENANT_DATA
        )
        val viewModel = OrgChartViewModel(
            tenantSlug = "wemade-demo",
            access = access,
            viewerDepartmentId = "dept-warehouse",
            viewerEmployeeId = "emp-sl-1",
            seed = OrgChartSeed.GarmentSample
        )
        val state = viewModel.uiState.value

        // 1. Initially defaults to the viewer's department (Gudang & Logistik)
        assertFalse(state.isDepartmentLocked)
        assertNotNull(state.selectedDepartment)
        assertEquals("warehouse", state.selectedDepartment.code)
        assertTrue(state.departments.size > 1) // all departments visible

        // 2. Focused employee belongs to Warehouse
        assertNotNull(state.selectedEmployeeId)
        val focusNode = state.resolvedHierarchy.focusNode
        assertEquals("warehouse", focusNode.department?.code)

        // 3. Switching to Direksi (Executive) is allowed and updates focus to executive
        viewModel.onEvent(OrgChartUiEvent.SelectDireksi)
        val direksiState = viewModel.uiState.value
        assertNull(direksiState.selectedDepartment)
        assertEquals(HierarchyLevel.EXECUTIVE, direksiState.selectedLevel)
        assertEquals(HierarchyLevel.EXECUTIVE, direksiState.resolvedHierarchy.focusNode.level)

        // 4. Switching to PPIC is allowed and updates focus to PPIC
        viewModel.onEvent(OrgChartUiEvent.SelectDepartment(Department.PRODUCTION_PPIC))
        val ppicState = viewModel.uiState.value
        assertEquals("production_ppic", ppicState.selectedDepartment?.code)
        assertEquals("production_ppic", ppicState.resolvedHierarchy.focusNode.department?.code)
    }
}
