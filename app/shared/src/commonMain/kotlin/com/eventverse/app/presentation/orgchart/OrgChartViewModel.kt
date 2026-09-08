package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OrgChartViewModel {

    private val _uiState = MutableStateFlow(OrgChartUiState())
    val uiState: StateFlow<OrgChartUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val sampleList = OrgNode.createSampleEmployees()
        _uiState.update {
            it.copy(
                employees = sampleList,
                departments = Department.defaultPresets(),
                selectedDepartment = Department.SALES,
                selectedReportsToId = "emp-budi"
            )
        }
    }

    fun onEvent(event: OrgChartUiEvent) {
        when (event) {
            is OrgChartUiEvent.UpdateName -> {
                _uiState.update { it.copy(nameInput = event.name) }
            }

            is OrgChartUiEvent.UpdateEmail -> {
                _uiState.update { it.copy(emailInput = event.email) }
            }

            is OrgChartUiEvent.UpdatePhone -> {
                _uiState.update { it.copy(phoneInput = event.phone) }
            }

            is OrgChartUiEvent.SelectDepartment -> {
                _uiState.update { state ->
                    // Auto-adjust default superior to the head of this department if staff
                    val defaultSuperior = if (state.selectedLevel == HierarchyLevel.STAFF_OPERATOR) {
                        state.employees.find { it.department.id == event.dept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }?.id?.value
                    } else if (state.selectedLevel == HierarchyLevel.HEAD_OF_DEPARTMENT) {
                        state.employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                    } else null

                    state.copy(
                        selectedDepartment = event.dept,
                        selectedReportsToId = defaultSuperior
                    )
                }
            }

            is OrgChartUiEvent.SelectLevel -> {
                _uiState.update { state ->
                    val defaultSuperior = when (event.level) {
                        HierarchyLevel.EXECUTIVE -> null
                        HierarchyLevel.HEAD_OF_DEPARTMENT -> state.employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                        HierarchyLevel.STAFF_OPERATOR -> state.employees.find {
                            it.department.id == state.activeDepartment.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
                        }?.id?.value
                    }

                    state.copy(
                        selectedLevel = event.level,
                        selectedReportsToId = defaultSuperior
                    )
                }
            }

            is OrgChartUiEvent.SelectReportsTo -> {
                _uiState.update { it.copy(selectedReportsToId = event.superiorId) }
            }

            is OrgChartUiEvent.UpdateRoleTitle -> {
                _uiState.update { it.copy(roleTitleInput = event.title) }
            }

            is OrgChartUiEvent.SelectSuccessionAction -> {
                _uiState.update { it.copy(successionAction = event.action) }
            }

            is OrgChartUiEvent.SelectExistingEmployee -> {
                val emp = _uiState.value.employees.find { it.id.value == event.id } ?: return
                _uiState.update {
                    it.copy(
                        selectedEmployeeId = emp.id.value,
                        isCreatingNew = false,
                        nameInput = emp.name,
                        emailInput = emp.email,
                        phoneInput = emp.phone,
                        selectedDepartment = emp.department,
                        selectedLevel = emp.level,
                        selectedReportsToId = emp.reportsToId?.value,
                        roleTitleInput = emp.roleTitle
                    )
                }
            }

            is OrgChartUiEvent.StartCreateNewEmployee -> {
                _uiState.update { state ->
                    val currentDept = state.selectedDepartment ?: state.departments.firstOrNull()
                    val defaultSuperior = state.employees.find {
                        it.department.id == currentDept?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
                    }?.id?.value ?: state.employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value

                    state.copy(
                        selectedEmployeeId = null,
                        isCreatingNew = true,
                        nameInput = "",
                        emailInput = "",
                        phoneInput = "",
                        selectedDepartment = currentDept,
                        selectedLevel = HierarchyLevel.STAFF_OPERATOR,
                        selectedReportsToId = defaultSuperior,
                        roleTitleInput = ""
                    )
                }
            }

            is OrgChartUiEvent.SaveEmployee -> {
                handleSaveEmployee()
            }

            is OrgChartUiEvent.DismissToast -> {
                _uiState.update { it.copy(toastMessage = null) }
            }

            // Dynamic Department Event Handlers
            is OrgChartUiEvent.OpenCreateDeptModal -> {
                val availableColors = Department.availableColors(_uiState.value.departments)
                val initialColor = availableColors.firstOrNull()?.hex ?: 0xFF2563EB
                _uiState.update {
                    it.copy(
                        isCreateDeptModalOpen = true,
                        newDeptNameInput = "",
                        newDeptShortNameInput = "",
                        newDeptColorHex = initialColor
                    )
                }
            }

            is OrgChartUiEvent.CloseCreateDeptModal -> {
                _uiState.update { it.copy(isCreateDeptModalOpen = false) }
            }

            is OrgChartUiEvent.UpdateNewDeptName -> {
                _uiState.update {
                    it.copy(
                        newDeptNameInput = event.name,
                        // Auto-fill short name if user hasn't typed custom short name
                        newDeptShortNameInput = if (it.newDeptShortNameInput.isBlank() || it.newDeptShortNameInput == it.newDeptNameInput.take(8)) {
                            event.name.take(10)
                        } else {
                            it.newDeptShortNameInput
                        }
                    )
                }
            }

            is OrgChartUiEvent.UpdateNewDeptShortName -> {
                _uiState.update { it.copy(newDeptShortNameInput = event.shortName.take(12)) }
            }

            is OrgChartUiEvent.SelectNewDeptColor -> {
                _uiState.update { it.copy(newDeptColorHex = event.colorHex) }
            }

            is OrgChartUiEvent.SaveNewDepartment -> {
                val current = _uiState.value
                val name = current.newDeptNameInput.trim()
                val shortName = current.newDeptShortNameInput.trim().ifBlank { name.take(8) }

                if (name.isBlank()) {
                    _uiState.update { it.copy(toastMessage = "Nama divisi tidak boleh kosong.") }
                    return
                }

                val newDept = Department.createCustom(
                    name = name,
                    shortName = shortName,
                    colorHex = current.newDeptColorHex
                )

                _uiState.update { state ->
                    state.copy(
                        departments = state.departments + newDept,
                        selectedDepartment = newDept,
                        isCreateDeptModalOpen = false,
                        toastMessage = "Divisi baru '${newDept.displayName}' berhasil ditambahkan!"
                    )
                }
            }

            // Reset & Preset Operations
            is OrgChartUiEvent.ToggleResetMenu -> {
                _uiState.update { it.copy(isResetMenuOpen = !it.isResetMenuOpen) }
            }

            is OrgChartUiEvent.ClearAllDataToEmpty -> {
                _uiState.update { state ->
                    state.copy(
                        employees = emptyList(),
                        departments = emptyList(),
                        selectedDepartment = null,
                        selectedEmployeeId = null,
                        isCreatingNew = true,
                        nameInput = "",
                        emailInput = "",
                        phoneInput = "",
                        selectedReportsToId = null,
                        roleTitleInput = "",
                        isResetMenuOpen = false,
                        toastMessage = "Struktur organisasi berhasil dikosongkan. Anda dapat mulai menyusun dari awal!"
                    )
                }
            }

            is OrgChartUiEvent.RestoreDefaultPresets -> {
                val defaultDepts = Department.defaultPresets()
                val defaultEmployees = OrgNode.createSampleEmployees()
                _uiState.update { state ->
                    state.copy(
                        employees = defaultEmployees,
                        departments = defaultDepts,
                        selectedDepartment = Department.SALES,
                        selectedEmployeeId = null,
                        isCreatingNew = true,
                        nameInput = "Dimas Pratama",
                        emailInput = "dimas.sales@wemade.id",
                        phoneInput = "082155667788",
                        selectedLevel = HierarchyLevel.STAFF_OPERATOR,
                        selectedReportsToId = "emp-budi",
                        roleTitleInput = "Sales Eksekutif Baju Seragam",
                        isResetMenuOpen = false,
                        toastMessage = "Preset template konveksi berhasil dimuat kembali!"
                    )
                }
            }
        }
    }

    private fun handleSaveEmployee() {
        val state = _uiState.value
        val name = state.nameInput.trim()
        if (name.isBlank()) return

        val targetDept = state.selectedDepartment ?: state.activeDepartment

        val newId = if (state.isCreatingNew) {
            OrgNodeId("emp-${name.lowercase().replace("\\s+".toRegex(), "-")}-${(100..999).random()}")
        } else {
            OrgNodeId(state.selectedEmployeeId ?: "emp-${(100..999).random()}")
        }

        val newNode = OrgNode(
            id = newId,
            name = name,
            email = state.emailInput.trim().ifBlank { "${name.lowercase().replace("\\s+".toRegex(), ".")}@wemade.id" },
            department = targetDept,
            level = state.selectedLevel,
            roleTitle = state.roleTitleInput.trim().ifBlank { state.selectedLevel.displayName },
            reportsToId = state.selectedReportsToId?.let { OrgNodeId(it) },
            phone = state.phoneInput.trim()
        )

        _uiState.update { current ->
            val existingHead = current.existingHeadOfSelectedDept
            var toast = "Karyawan '${newNode.name}' berhasil disimpan ke bagan struktur!"

            val updatedEmployees = if (newNode.level == HierarchyLevel.HEAD_OF_DEPARTMENT && existingHead != null) {
                // ─── SUKSESI KEPALA DIVISI (SINGLE ACTIVE HEAD CONSTRAINT) ───
                when (current.successionAction) {
                    HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                        val demotedHead = existingHead.copy(
                            level = HierarchyLevel.STAFF_OPERATOR,
                            roleTitle = "Staf Senior ${newNode.department.shortName}",
                            reportsToId = newNode.id
                        )
                        toast = "Suksesi berhasil! '${newNode.name}' diangkat sebagai Kepala Divisi ${newNode.department.shortName}, dan '${existingHead.name}' dialihkan menjadi Staf."
                        current.employees.map { emp ->
                            when (emp.id) {
                                existingHead.id -> demotedHead
                                newNode.id -> newNode
                                else -> {
                                    // Staf divisi yang lama otomatis melapor ke Kepala Divisi baru
                                    if (emp.reportsToId == existingHead.id) emp.copy(reportsToId = newNode.id)
                                    else emp
                                }
                            }
                        }.let { list ->
                            if (current.isCreatingNew) list + newNode else list
                        }
                    }

                    HeadSuccessionAction.DEACTIVATE -> {
                        toast = "Suksesi berhasil! '${newNode.name}' diangkat sebagai Kepala Divisi ${newNode.department.shortName}, dan akun '${existingHead.name}' dinonaktifkan."
                        current.employees
                            .filter { it.id != existingHead.id }
                            .map { emp ->
                                if (emp.id == newNode.id) newNode
                                else if (emp.reportsToId == existingHead.id) emp.copy(reportsToId = newNode.id)
                                else emp
                            }.let { list ->
                                if (current.isCreatingNew) list + newNode else list
                            }
                    }
                }
            } else {
                if (current.isCreatingNew) {
                    current.employees + newNode
                } else {
                    current.employees.map { if (it.id == newNode.id) newNode else it }
                }
            }

            current.copy(
                employees = updatedEmployees,
                selectedEmployeeId = newNode.id.value,
                isCreatingNew = false,
                toastMessage = toast
            )
        }
    }
}
