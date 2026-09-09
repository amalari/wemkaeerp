package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import kotlin.test.*

class OrgChartViewModelSuperiorAutoFillTest {

    @Test
    fun initialState_shouldHaveDefaultSuperiorAutomaticallySelected() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")
        val state = viewModel.uiState.value

        assertEquals(Department.SALES.id, state.selectedDepartment?.id)
        assertEquals(HierarchyLevel.STAFF_OPERATOR, state.selectedLevel)
        // Default superior for Sales staff should be the head of sales (emp-budi)
        assertNotNull(state.selectedReportsToId)
        val superior = state.employees.find { it.id.value == state.selectedReportsToId }
        assertNotNull(superior)
        assertEquals(Department.SALES.id, superior.department?.id)
        assertEquals(HierarchyLevel.HEAD_OF_DEPARTMENT, superior.level)
    }

    @Test
    fun startCreateNewEmployee_shouldResetWithPreselectedSuperior() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")

        // First select an existing executive
        val hendra = viewModel.uiState.value.employees.find { it.level == HierarchyLevel.EXECUTIVE }
        assertNotNull(hendra)
        assertNull(hendra.department) // Direksi has no department
        viewModel.onEvent(OrgChartUiEvent.SelectExistingEmployee(hendra.id.value))
        assertEquals(HierarchyLevel.EXECUTIVE, viewModel.uiState.value.selectedLevel)
        assertNull(viewModel.uiState.value.selectedReportsToId)

        // Now start create new employee
        viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee)
        val newState = viewModel.uiState.value
        assertTrue(newState.isCreatingNew)
        assertEquals("", newState.nameInput)
        assertEquals(HierarchyLevel.STAFF_OPERATOR, newState.selectedLevel)
        assertNotNull(newState.selectedReportsToId)
    }

    @Test
    fun jalur1_selectReportsTo_shouldAutomaticallyFillDepartmentAndAdjustLevel() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")

        // Case A: Select Joko Susilo (Kepala Produksi & PPIC)
        val joko = viewModel.uiState.value.employees.find {
            it.department?.id == Department.PRODUCTION_PPIC.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
        }
        assertNotNull(joko)

        viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(joko.id.value))
        val stateA = viewModel.uiState.value
        assertEquals(joko.id.value, stateA.selectedReportsToId)
        assertEquals(Department.PRODUCTION_PPIC.id, stateA.selectedDepartment?.id)
        assertTrue(stateA.isDepartmentLocked) // Superior has dept -> locked!

        // Case B: Select Siti Rahma (Kepala Gudang & Logistik)
        val siti = viewModel.uiState.value.employees.find {
            it.department?.id == Department.WAREHOUSE.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
        }
        assertNotNull(siti)

        viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(siti.id.value))
        val stateB = viewModel.uiState.value
        assertEquals(siti.id.value, stateB.selectedReportsToId)
        assertEquals(Department.WAREHOUSE.id, stateB.selectedDepartment?.id)
        assertEquals(HierarchyLevel.STAFF_OPERATOR, stateB.selectedLevel)
        assertTrue(stateB.isDepartmentLocked)

        // Case C: Select Hendra Setiawan (Direksi / Executive - no department)
        val hendra = viewModel.uiState.value.employees.find { it.level == HierarchyLevel.EXECUTIVE }
        assertNotNull(hendra)
        assertNull(hendra.department)

        viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(hendra.id.value))
        val stateC = viewModel.uiState.value
        assertEquals(hendra.id.value, stateC.selectedReportsToId)
        assertEquals(HierarchyLevel.HEAD_OF_DEPARTMENT, stateC.selectedLevel)
        assertFalse(stateC.isDepartmentLocked) // Direksi has no dept -> NOT locked!

        // Case D: Select Tanpa Atasan (null)
        viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(null))
        val stateD = viewModel.uiState.value
        assertNull(stateD.selectedReportsToId)
        assertNull(stateD.selectedDepartment)
        assertTrue(stateD.isDepartmentLocked)
        assertEquals(HierarchyLevel.EXECUTIVE, stateD.selectedLevel)
    }

    @Test
    fun jalur2_selectDepartmentAndLevel_shouldAutomaticallySelectSuperior() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")

        // 1. Change department to PRODUCTION_PPIC
        viewModel.onEvent(OrgChartUiEvent.SelectDepartment(Department.PRODUCTION_PPIC))
        val state1 = viewModel.uiState.value
        assertEquals(Department.PRODUCTION_PPIC.id, state1.selectedDepartment?.id)
        val superior1 = state1.employees.find { it.id.value == state1.selectedReportsToId }
        assertNotNull(superior1)
        // Staff in Production automatically reports to Team Lead (Agus Setiawan)
        assertEquals("Agus Setiawan", superior1.name)
        assertEquals(Department.PRODUCTION_PPIC.id, superior1.department?.id)

        // 2. Change level to HEAD_OF_DEPARTMENT
        viewModel.onEvent(OrgChartUiEvent.SelectLevel(HierarchyLevel.HEAD_OF_DEPARTMENT))
        val state2 = viewModel.uiState.value
        assertEquals(HierarchyLevel.HEAD_OF_DEPARTMENT, state2.selectedLevel)
        val superior2 = state2.employees.find { it.id.value == state2.selectedReportsToId }
        assertNotNull(superior2)
        assertEquals(HierarchyLevel.EXECUTIVE, superior2.level)
        assertEquals("Bpk. Hendra Kusuma", superior2.name)

        // 3. Change level to EXECUTIVE
        viewModel.onEvent(OrgChartUiEvent.SelectLevel(HierarchyLevel.EXECUTIVE))
        val state3 = viewModel.uiState.value
        assertEquals(HierarchyLevel.EXECUTIVE, state3.selectedLevel)
        assertNull(state3.selectedReportsToId)

        // 4. Change level back to STAFF_OPERATOR
        viewModel.onEvent(OrgChartUiEvent.SelectLevel(HierarchyLevel.STAFF_OPERATOR))
        val state4 = viewModel.uiState.value
        assertEquals(HierarchyLevel.STAFF_OPERATOR, state4.selectedLevel)
        val superior4 = state4.employees.find { it.id.value == state4.selectedReportsToId }
        assertNotNull(superior4)
    }

    @Test
    fun companyLeaders_shouldListExecutivesAndDepartmentHeads() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")
        val state = viewModel.uiState.value

        val leaders = state.companyLeaders
        assertTrue(leaders.isNotEmpty())
        assertTrue(leaders.all { it.level == HierarchyLevel.EXECUTIVE || it.level == HierarchyLevel.HEAD_OF_DEPARTMENT || it.level == HierarchyLevel.TEAM_LEAD })
        assertTrue(leaders.any { it.level == HierarchyLevel.EXECUTIVE && it.department == null })
        assertTrue(leaders.any { it.department?.id == Department.SALES.id })
    }

    @Test
    fun dynamicTiers_shouldAddTierToSelectedDepartment() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")

        // Select Production department
        viewModel.onEvent(OrgChartUiEvent.SelectDepartment(Department.PRODUCTION_PPIC))
        val initialTiers = viewModel.uiState.value.availableTiersForSelectedDept

        // Add custom tier: "Mandor Sablon & Press"
        viewModel.onEvent(OrgChartUiEvent.OpenAddTierModal)
        assertTrue(viewModel.uiState.value.isAddTierModalOpen)

        viewModel.onEvent(OrgChartUiEvent.UpdateNewTierName("Mandor Sablon & Press"))
        viewModel.onEvent(OrgChartUiEvent.SaveNewDepartmentTier)

        assertFalse(viewModel.uiState.value.isAddTierModalOpen)
        assertEquals("Mandor Sablon & Press", viewModel.uiState.value.selectedTierName)

        val updatedTiers = viewModel.uiState.value.availableTiersForSelectedDept
        assertEquals(initialTiers.size + 1, updatedTiers.size)
        assertTrue(updatedTiers.any { it.name == "Mandor Sablon & Press" })
    }
}
