package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import kotlin.test.*

class OrgChartViewModelDeleteTest {

    @Test
    fun deleteEmployee_shouldReassignSubordinatesAndRemoveNode() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")
        val initialState = viewModel.uiState.value
        val initialCount = initialState.employees.size
        assertTrue(initialCount >= 9)

        // Select Budi (emp-budi has subordinate Rian, and reports to Hendra)
        val targetEmp = initialState.employees.find { it.id.value == "emp-budi" }
        assertNotNull(targetEmp)

        viewModel.onEvent(OrgChartUiEvent.RequestArchiveEmployee("emp-budi"))
        val deleteDialogState = viewModel.uiState.value
        assertTrue(deleteDialogState.isDeleteDialogOpen)
        assertEquals(DeleteTargetType.EMPLOYEE, deleteDialogState.deleteTargetType)
        assertFalse(deleteDialogState.isDeleteBlocked)
        assertTrue(deleteDialogState.deleteWarningNote.contains("bawahan langsung"))

        // Confirm Delete
        viewModel.onEvent(OrgChartUiEvent.ConfirmDelete)
        val afterDeleteState = viewModel.uiState.value
        assertFalse(afterDeleteState.isDeleteDialogOpen)
        assertEquals(initialCount - 1, afterDeleteState.employees.size)
        assertNull(afterDeleteState.employees.find { it.id.value == "emp-budi" })

        // Check subordinate (emp-rian was reporting to emp-budi, now should report to emp-budi's superior: emp-hendra)
        val rian = afterDeleteState.employees.find { it.id.value == "emp-rian" }
        assertNotNull(rian)
        assertEquals("emp-hendra", rian.reportsToId?.value)
    }

    @Test
    fun deleteDepartment_withExistingEmployees_shouldBlockDeletion() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")
        val salesDept = Department.SALES

        viewModel.onEvent(OrgChartUiEvent.RequestArchiveDepartment(salesDept))
        val state = viewModel.uiState.value
        assertTrue(state.isDeleteDialogOpen)
        assertEquals(DeleteTargetType.DEPARTMENT, state.deleteTargetType)
        assertTrue(state.isDeleteBlocked)
        assertTrue(state.deleteWarningNote.contains("masih memiliki"))

        // Attempting ConfirmDelete when blocked should do nothing
        viewModel.onEvent(OrgChartUiEvent.ConfirmDelete)
        val afterState = viewModel.uiState.value
        assertTrue(afterState.departments.any { it.id == salesDept.id })
    }

    @Test
    fun deleteDepartment_withoutEmployees_shouldSucceed() {
        val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")

        // Create an empty custom department
        val emptyDept = Department.createCustom(
            name = "Divisi Riset Khusus",
            shortName = "Riset",
            colorHex = 0xFFEC4899
        )
        // Inject into state by saving department event
        viewModel.onEvent(OrgChartUiEvent.OpenCreateDeptModal)
        viewModel.onEvent(OrgChartUiEvent.UpdateNewDeptName("Divisi Riset Khusus"))
        viewModel.onEvent(OrgChartUiEvent.UpdateNewDeptShortName("Riset"))
        viewModel.onEvent(OrgChartUiEvent.SaveNewDepartment)

        val withDeptState = viewModel.uiState.value
        val createdDept = withDeptState.departments.find { it.displayName == "Divisi Riset Khusus" }
        assertNotNull(createdDept)

        // Request delete on this empty department
        viewModel.onEvent(OrgChartUiEvent.RequestArchiveDepartment(createdDept))
        val dialogState = viewModel.uiState.value
        assertTrue(dialogState.isDeleteDialogOpen)
        assertFalse(dialogState.isDeleteBlocked)
        assertTrue(dialogState.deleteWarningNote.contains("aman untuk diarsipkan"))

        // Confirm delete
        viewModel.onEvent(OrgChartUiEvent.ConfirmDelete)
        val afterState = viewModel.uiState.value
        assertFalse(afterState.isDeleteDialogOpen)
        assertNull(afterState.departments.find { it.id == createdDept.id })
    }
}
