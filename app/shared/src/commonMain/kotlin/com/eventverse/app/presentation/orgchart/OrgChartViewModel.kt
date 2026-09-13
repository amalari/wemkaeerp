package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.*
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.api.OrgChartApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrgChartViewModel(
    private val tenantSlug: String = "wemade-demo",
    private val apiClient: OrgChartApiClient? = null,
    private val access: ModuleAccessConfig = ModuleAccessConfig(AccessLevel.MANAGE),
    private val viewerDepartmentId: String? = null,
    private val viewerEmployeeId: String? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val _uiState = MutableStateFlow(OrgChartUiState())
    val uiState: StateFlow<OrgChartUiState> = _uiState.asStateFlow()

    private val isScoped: Boolean
        get() = access.scope != DataScope.ALL_TENANT_DATA && !viewerDepartmentId.isNullOrBlank()

    init {
        loadInitialData()
    }

    private fun matchDepartment(depts: List<Department>, query: String?): Department? {
        if (query.isNullOrBlank()) return null
        return depts.find { it.id.value.equals(query, ignoreCase = true) }
            ?: depts.find { it.code.equals(query, ignoreCase = true) }
            ?: depts.find { query.contains(it.code, ignoreCase = true) || it.code.contains(query, ignoreCase = true) }
            ?: depts.find { it.displayName.contains(query, ignoreCase = true) || query.contains(it.displayName, ignoreCase = true) }
    }

    private fun filterByScope(nodes: List<OrgNode>, deptId: String?): List<OrgNode> {
        if (!isScoped) return nodes
        return OrgChartVisibility.visibleTo(
            nodes = nodes,
            scope = access.scope,
            viewerEmployeeId = viewerEmployeeId?.let { OrgNodeId(it) },
            viewerDepartmentId = deptId ?: viewerDepartmentId
        )
    }

    private fun loadInitialData() {
        val sampleList = OrgNode.createSampleEmployees()
        val defaultDepts = Department.defaultPresets()
        val matchedDept = matchDepartment(defaultDepts, viewerDepartmentId)
        val targetDept = matchedDept ?: defaultDepts.firstOrNull { it.id.value == "dept-sales" } ?: defaultDepts.firstOrNull() ?: Department.SALES

        val scopedSampleList = filterByScope(sampleList, targetDept.id.value)
        val initialSuperior = resolveDefaultSuperior(scopedSampleList, targetDept, HierarchyLevel.STAFF_OPERATOR)
        val initialSelectedEmpId = if (!access.canWrite) {
            val viewerNode = if (viewerEmployeeId != null) scopedSampleList.find { it.id.value == viewerEmployeeId } else null
            viewerNode?.id?.value
                ?: scopedSampleList.find { it.department?.id == targetDept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }?.id?.value
                ?: scopedSampleList.find { it.department?.id == targetDept.id }?.id?.value
                ?: scopedSampleList.firstOrNull()?.id?.value
        } else {
            null
        }

        _uiState.update {
            it.copy(
                employees = scopedSampleList,
                departments = if (isScoped) listOf(targetDept) else defaultDepts,
                selectedDepartment = targetDept,
                isDepartmentLocked = isScoped,
                isCreatingNew = access.canWrite,
                selectedEmployeeId = initialSelectedEmpId,
                selectedReportsToId = initialSuperior
            )
        }

        // Asynchronously fetch live data from Backend API
        val client = apiClient ?: return
        coroutineScope.launch {
            try {
                val deptsResult = client.getDepartments(tenantSlug)
                val empsResult = client.getEmployees(tenantSlug)

                if (deptsResult.isSuccess && empsResult.isSuccess) {
                    val liveDepts = deptsResult.getOrThrow()
                    val liveEmps = empsResult.getOrThrow()
                    if (liveDepts.isNotEmpty() || liveEmps.isNotEmpty()) {
                        _uiState.update { state ->
                            val matchedLiveDept = matchDepartment(liveDepts, viewerDepartmentId)
                            val activeLiveDept = when {
                                state.selectedDepartment != null -> {
                                    liveDepts.find { it.id == state.selectedDepartment.id }
                                        ?: matchedLiveDept
                                        ?: state.selectedDepartment
                                }
                                viewerDepartmentId.isNullOrBlank() -> null
                                matchedLiveDept != null -> matchedLiveDept
                                else -> liveDepts.firstOrNull()
                            }

                            val effectiveLiveEmps = filterByScope(liveEmps, activeLiveDept?.id?.value)
                            val effectiveLiveDepts = if (isScoped && activeLiveDept != null) {
                                listOf(activeLiveDept)
                            } else if (liveDepts.isNotEmpty()) {
                                liveDepts
                            } else {
                                state.departments
                            }

                            val validSuperior = if (state.selectedReportsToId != null && effectiveLiveEmps.any { it.id.value == state.selectedReportsToId }) {
                                state.selectedReportsToId
                            } else if (activeLiveDept != null) {
                                resolveDefaultSuperior(effectiveLiveEmps, activeLiveDept, state.selectedLevel)
                            } else {
                                null
                            }

                            val effectiveSelectedEmpId = if (!access.canWrite) {
                                if (state.selectedEmployeeId != null && effectiveLiveEmps.any { it.id.value == state.selectedEmployeeId }) {
                                    state.selectedEmployeeId
                                } else if (activeLiveDept == null) {
                                    effectiveLiveEmps.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                                        ?: effectiveLiveEmps.firstOrNull()?.id?.value
                                } else {
                                    val viewerNode = if (viewerEmployeeId != null) effectiveLiveEmps.find { it.id.value == viewerEmployeeId } else null
                                    viewerNode?.id?.value
                                        ?: effectiveLiveEmps.find { it.department?.id == activeLiveDept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }?.id?.value
                                        ?: effectiveLiveEmps.find { it.department?.id == activeLiveDept.id }?.id?.value
                                        ?: effectiveLiveEmps.firstOrNull()?.id?.value
                                }
                            } else {
                                state.selectedEmployeeId
                            }

                            state.copy(
                                departments = effectiveLiveDepts,
                                employees = if (effectiveLiveEmps.isNotEmpty()) effectiveLiveEmps else state.employees,
                                selectedDepartment = activeLiveDept,
                                isDepartmentLocked = isScoped,
                                isCreatingNew = access.canWrite,
                                selectedEmployeeId = effectiveSelectedEmpId,
                                selectedReportsToId = validSuperior
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fallback to presets if backend is offline or starting up
            }
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
                if (isScoped) return
                _uiState.update { state ->
                    val defaultSuperior = resolveDefaultSuperior(
                        employees = state.employees,
                        dept = event.dept,
                        level = if (state.selectedLevel == HierarchyLevel.EXECUTIVE) HierarchyLevel.HEAD_OF_DEPARTMENT else state.selectedLevel
                    )
                    val newLevel = if (state.selectedLevel == HierarchyLevel.EXECUTIVE) HierarchyLevel.HEAD_OF_DEPARTMENT else state.selectedLevel
                    val tier = event.dept.tiers.find { it.isHead && newLevel == HierarchyLevel.HEAD_OF_DEPARTMENT }
                        ?: event.dept.tiers.firstOrNull { newLevel == HierarchyLevel.TEAM_LEAD && it.id == "team_lead" }
                        ?: event.dept.tiers.lastOrNull()

                    val targetEmpId = if (!access.canWrite || state.selectedDepartment?.id != event.dept.id) {
                        state.employees.find { it.department?.id == event.dept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }?.id?.value
                            ?: state.employees.find { it.department?.id == event.dept.id }?.id?.value
                            ?: state.selectedEmployeeId
                    } else {
                        state.selectedEmployeeId
                    }

                    state.copy(
                        selectedDepartment = event.dept,
                        selectedLevel = newLevel,
                        selectedTierName = tier?.name ?: state.selectedTierName,
                        selectedReportsToId = defaultSuperior,
                        isDepartmentLocked = false,
                        selectedEmployeeId = targetEmpId
                    )
                }
            }

            is OrgChartUiEvent.SelectDireksi -> {
                if (isScoped) return
                _uiState.update { state ->
                    val execEmpId = state.employees.find { it.level == HierarchyLevel.EXECUTIVE || it.department == null }?.id?.value
                        ?: state.selectedEmployeeId
                    state.copy(
                        selectedDepartment = null,
                        selectedLevel = HierarchyLevel.EXECUTIVE,
                        selectedTierName = "Direksi",
                        selectedReportsToId = null,
                        isDepartmentLocked = false,
                        selectedEmployeeId = execEmpId
                    )
                }
            }

            is OrgChartUiEvent.SelectLevel -> {
                _uiState.update { state ->
                    val targetDept = state.selectedDepartment ?: state.activeDepartment
                    val defaultSuperior = resolveDefaultSuperior(
                        employees = state.employees,
                        dept = targetDept,
                        level = event.level
                    )
                    val tier = when (event.level) {
                        HierarchyLevel.EXECUTIVE -> "Direksi"
                        HierarchyLevel.HEAD_OF_DEPARTMENT -> targetDept.tiers.find { it.isHead }?.name ?: "Kepala Divisi"
                        HierarchyLevel.TEAM_LEAD -> targetDept.tiers.find { it.id == "team_lead" }?.name ?: "Kepala Tim / Supervisor"
                        HierarchyLevel.STAFF_OPERATOR -> targetDept.tiers.lastOrNull()?.name ?: "Staf Pelaksana / Operator"
                    }

                    state.copy(
                        selectedLevel = event.level,
                        selectedTierName = tier,
                        selectedReportsToId = defaultSuperior,
                        isDepartmentLocked = isScoped || state.isDepartmentLocked
                    )
                }
            }

            is OrgChartUiEvent.SelectTier -> {
                _uiState.update { state ->
                    val level = if (event.tier.isHead) {
                        HierarchyLevel.HEAD_OF_DEPARTMENT
                    } else if (event.tier.id == "team_lead") {
                        HierarchyLevel.TEAM_LEAD
                    } else {
                        HierarchyLevel.STAFF_OPERATOR
                    }
                    val targetDept = state.selectedDepartment ?: state.activeDepartment
                    val defaultSuperior = resolveDefaultSuperior(state.employees, targetDept, level)

                    state.copy(
                        selectedDepartment = targetDept,
                        selectedLevel = level,
                        selectedTierName = event.tier.name,
                        selectedReportsToId = defaultSuperior,
                        isDepartmentLocked = isScoped || state.isDepartmentLocked
                    )
                }
            }

            is OrgChartUiEvent.SelectReportsTo -> {
                _uiState.update { state ->
                    if (isScoped || state.isDepartmentLocked) {
                        state.copy(
                            selectedReportsToId = event.superiorId,
                            isDepartmentLocked = true
                        )
                    } else if (event.superiorId == null) {
                        // Memilih tanpa atasan -> otomatis tingkat wewenang menjadi Direksi (Executive), tanpa divisi
                        state.copy(
                            selectedReportsToId = null,
                            selectedDepartment = null,
                            isDepartmentLocked = false,
                            selectedLevel = HierarchyLevel.EXECUTIVE,
                            selectedTierName = "Direksi"
                        )
                    } else {
                        val superior = state.employees.find { it.id.value == event.superiorId }
                        if (superior != null) {
                            if (superior.level == HierarchyLevel.EXECUTIVE || superior.department == null) {
                                // Upper adalah Direksi (tidak punya divisi) -> user bebas pilih divisi
                                val targetDept = state.selectedDepartment ?: state.departments.firstOrNull()
                                val headTier = targetDept?.tiers?.find { it.isHead }
                                state.copy(
                                    selectedReportsToId = superior.id.value,
                                    selectedDepartment = targetDept,
                                    isDepartmentLocked = false,
                                    selectedLevel = HierarchyLevel.HEAD_OF_DEPARTMENT,
                                    selectedTierName = headTier?.name ?: "Kepala Divisi"
                                )
                            } else {
                                // Upper memiliki divisi -> sarankan divisi itu, tapi tetap editable
                                val dept = superior.department
                                val deptTiers = dept?.tiers ?: emptyList()
                                val autoTier = if (superior.level == HierarchyLevel.HEAD_OF_DEPARTMENT) {
                                    deptTiers.find { it.id == "team_lead" } ?: deptTiers.find { it.rank > 1 } ?: deptTiers.lastOrNull()
                                } else {
                                    deptTiers.find { it.rank > 2 } ?: deptTiers.lastOrNull()
                                }
                                val autoLevel = if (autoTier?.id == "team_lead") HierarchyLevel.TEAM_LEAD else HierarchyLevel.STAFF_OPERATOR
                                state.copy(
                                    selectedReportsToId = superior.id.value,
                                    selectedDepartment = dept,
                                    isDepartmentLocked = false,
                                    selectedLevel = autoLevel,
                                    selectedTierName = autoTier?.name ?: "Staf Pelaksana / Operator"
                                )
                            }
                        } else {
                            state.copy(selectedReportsToId = event.superiorId, isDepartmentLocked = false)
                        }
                    }
                }
            }

            is OrgChartUiEvent.UpdateRoleTitle -> {
                _uiState.update { it.copy(roleTitleInput = event.title) }
            }

            is OrgChartUiEvent.SelectSuccessionAction -> {
                _uiState.update { it.copy(successionAction = event.action) }
            }

            is OrgChartUiEvent.SelectExistingEmployee -> {
                val emp = _uiState.value.employees.find { it.id.value == event.id } ?: return
                _uiState.update { state ->
                    state.copy(
                        selectedEmployeeId = emp.id.value,
                        isCreatingNew = false,
                        nameInput = if (access.canWrite) emp.name else "",
                        emailInput = if (access.canWrite) emp.email else "",
                        phoneInput = if (access.canWrite) emp.phone else "",
                        selectedDepartment = if (isScoped) state.selectedDepartment else emp.department,
                        isDepartmentLocked = isScoped,
                        selectedLevel = emp.level,
                        selectedTierName = emp.tierName ?: (if (emp.level == HierarchyLevel.EXECUTIVE) "Direksi" else if (emp.level == HierarchyLevel.HEAD_OF_DEPARTMENT) "Kepala Divisi" else "Staf"),
                        selectedReportsToId = emp.reportsToId?.value,
                        roleTitleInput = if (access.canWrite) emp.roleTitle else ""
                    )
                }
            }

            is OrgChartUiEvent.StartCreateNewEmployee -> {
                if (!access.canWrite) return
                _uiState.update { state ->
                    val currentDept = state.selectedDepartment ?: state.departments.firstOrNull()
                    val defaultSuperior = resolveDefaultSuperior(
                        employees = state.employees,
                        dept = currentDept,
                        level = HierarchyLevel.STAFF_OPERATOR
                    )
                    val tier = currentDept?.tiers?.lastOrNull()?.name ?: "Staf Pelaksana / Operator"

                    state.copy(
                        selectedEmployeeId = null,
                        isCreatingNew = true,
                        nameInput = "",
                        emailInput = "",
                        phoneInput = "",
                        selectedDepartment = currentDept,
                        isDepartmentLocked = isScoped || state.isDepartmentLocked,
                        selectedLevel = HierarchyLevel.STAFF_OPERATOR,
                        selectedTierName = tier,
                        selectedReportsToId = defaultSuperior,
                        roleTitleInput = ""
                    )
                }
            }

            is OrgChartUiEvent.SaveEmployee -> {
                if (!access.canWrite) return
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

                // Call Backend API
                val client = apiClient ?: return
                coroutineScope.launch {
                    try {
                        val result = client.createDepartment(
                            tenantSlug = tenantSlug,
                            name = name,
                            shortName = shortName,
                            colorHex = current.newDeptColorHex
                        )
                        if (result.isSuccess) {
                            val serverDepts = client.getDepartments(tenantSlug).getOrNull()
                            if (serverDepts != null && serverDepts.isNotEmpty()) {
                                _uiState.update { state ->
                                    state.copy(
                                        departments = serverDepts,
                                        selectedDepartment = serverDepts.find { it.displayName == name } ?: state.selectedDepartment
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("API Create Department exception: ${e.message}")
                    }
                }
            }

            // Edit Department Event Handlers
            is OrgChartUiEvent.OpenEditDeptModal -> {
                _uiState.update {
                    it.copy(
                        isEditDeptModalOpen = true,
                        editDeptId = event.dept.id.value,
                        editDeptNameInput = event.dept.displayName,
                        editDeptShortNameInput = event.dept.shortName,
                        editDeptColorHex = event.dept.colorHex
                    )
                }
            }

            is OrgChartUiEvent.CloseEditDeptModal -> {
                _uiState.update { it.copy(isEditDeptModalOpen = false) }
            }

            is OrgChartUiEvent.UpdateEditDeptName -> {
                _uiState.update { it.copy(editDeptNameInput = event.name) }
            }

            is OrgChartUiEvent.UpdateEditDeptShortName -> {
                _uiState.update { it.copy(editDeptShortNameInput = event.shortName) }
            }

            is OrgChartUiEvent.SelectEditDeptColor -> {
                _uiState.update { it.copy(editDeptColorHex = event.colorHex) }
            }

            is OrgChartUiEvent.SaveEditedDepartment -> {
                val current = _uiState.value
                val deptId = current.editDeptId
                val name = current.editDeptNameInput.trim()
                val shortName = current.editDeptShortNameInput.trim()
                if (name.isBlank()) {
                    _uiState.update { it.copy(toastMessage = "Nama divisi tidak boleh kosong.") }
                    return
                }

                val existingDept = current.departments.find { it.id.value == deptId }
                val updatedDept = (existingDept ?: Department.SALES).copy(
                    displayName = name,
                    shortName = shortName.ifBlank { name.take(8) },
                    colorHex = current.editDeptColorHex
                )

                _uiState.update { state ->
                    val updatedList = state.departments.map { if (it.id.value == deptId) updatedDept else it }
                    state.copy(
                        departments = updatedList,
                        selectedDepartment = if (state.selectedDepartment?.id?.value == deptId) updatedDept else state.selectedDepartment,
                        isEditDeptModalOpen = false,
                        toastMessage = "Divisi '${updatedDept.displayName}' berhasil diperbarui!"
                    )
                }

                val client = apiClient ?: return
                coroutineScope.launch {
                    try {
                        val result = client.updateDepartment(tenantSlug, updatedDept)
                        if (result.isSuccess) {
                            val serverDepts = client.getDepartments(tenantSlug).getOrNull()
                            if (serverDepts != null && serverDepts.isNotEmpty()) {
                                _uiState.update { state ->
                                    state.copy(
                                        departments = serverDepts,
                                        selectedDepartment = serverDepts.find { it.id.value == deptId } ?: state.selectedDepartment
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("API Update Department exception: ${e.message}")
                    }
                }
            }

            // Dynamic Department Tier Event Handlers
            is OrgChartUiEvent.OpenAddTierModal -> {
                _uiState.update {
                    it.copy(
                        isAddTierModalOpen = true,
                        newTierNameInput = ""
                    )
                }
            }

            is OrgChartUiEvent.CloseAddTierModal -> {
                _uiState.update { it.copy(isAddTierModalOpen = false) }
            }

            is OrgChartUiEvent.UpdateNewTierName -> {
                _uiState.update { it.copy(newTierNameInput = event.name) }
            }

            is OrgChartUiEvent.SaveNewDepartmentTier -> {
                val current = _uiState.value
                val targetDept = current.selectedDepartment ?: current.activeDepartment
                val tierName = current.newTierNameInput.trim()

                if (tierName.isBlank()) {
                    _uiState.update { it.copy(toastMessage = "Nama tingkat wewenang tidak boleh kosong.") }
                    return
                }

                val updatedDept = targetDept.addTier(tierName)
                val newTier = updatedDept.tiers.lastOrNull { it.name.equals(tierName, ignoreCase = true) }

                _uiState.update { state ->
                    val updatedList = state.departments.map { dept ->
                        if (dept.id == targetDept.id) updatedDept else dept
                    }
                    state.copy(
                        departments = updatedList,
                        selectedDepartment = updatedDept,
                        selectedTierName = newTier?.name ?: tierName,
                        isAddTierModalOpen = false,
                        toastMessage = "Tingkat wewenang '${tierName}' berhasil ditambahkan ke divisi ${updatedDept.displayName}!"
                    )
                }
            }

            // Edit Department Tier Event Handlers
            is OrgChartUiEvent.OpenEditTierModal -> {
                _uiState.update {
                    it.copy(
                        isEditTierModalOpen = true,
                        editTierDeptId = event.deptId,
                        editTierId = event.tier.id,
                        editTierNameInput = event.tier.name
                    )
                }
            }

            is OrgChartUiEvent.CloseEditTierModal -> {
                _uiState.update { it.copy(isEditTierModalOpen = false) }
            }

            is OrgChartUiEvent.UpdateEditTierName -> {
                _uiState.update { it.copy(editTierNameInput = event.name) }
            }

            is OrgChartUiEvent.SaveEditedDepartmentTier -> {
                val current = _uiState.value
                val deptId = current.editTierDeptId
                val tierId = current.editTierId
                val newTierName = current.editTierNameInput.trim()

                if (newTierName.isBlank()) {
                    _uiState.update { it.copy(toastMessage = "Nama tingkat wewenang tidak boleh kosong.") }
                    return
                }

                val targetDept = current.departments.find { it.id.value == deptId }
                    ?: current.selectedDepartment
                    ?: current.activeDepartment
                val updatedDept = targetDept.updateTier(tierId, newTierName)

                _uiState.update { state ->
                    val updatedList = state.departments.map { dept ->
                        if (dept.id.value == targetDept.id.value) updatedDept else dept
                    }
                    val isCurrentSelectedTier = state.selectedTierName == targetDept.tiers.find { it.id == tierId }?.name
                    state.copy(
                        departments = updatedList,
                        selectedDepartment = if (state.selectedDepartment?.id?.value == targetDept.id.value) updatedDept else state.selectedDepartment,
                        selectedTierName = if (isCurrentSelectedTier) newTierName else state.selectedTierName,
                        isEditTierModalOpen = false,
                        toastMessage = "Tingkat wewenang berhasil diperbarui menjadi '$newTierName'!"
                    )
                }
            }

            // Reset & Preset Operations
            is OrgChartUiEvent.ToggleResetMenu -> {
                _uiState.update { it.copy(isResetMenuOpen = !it.isResetMenuOpen) }
            }

            is OrgChartUiEvent.ClearAllDataToEmpty -> {
                if (!access.canManage || isScoped) return
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
                if (!access.canManage || isScoped) return
                val defaultDepts = Department.defaultPresets()
                val defaultEmployees = OrgNode.createSampleEmployees()
                _uiState.update { state ->
                    state.copy(
                        employees = defaultEmployees,
                        departments = defaultDepts,
                        selectedDepartment = Department.SALES,
                        selectedEmployeeId = null,
                        isCreatingNew = true,
                        nameInput = "",
                        emailInput = "",
                        phoneInput = "",
                        selectedLevel = HierarchyLevel.STAFF_OPERATOR,
                        selectedReportsToId = null,
                        roleTitleInput = "",
                        isResetMenuOpen = false,
                        toastMessage = "Preset template konveksi berhasil dimuat kembali!"
                    )
                }

                // Call Backend API
                val client = apiClient ?: return
                coroutineScope.launch {
                    try {
                        client.restoreDepartmentPresets(tenantSlug)
                        client.restoreEmployeePresets(tenantSlug)
                        val serverDepts = client.getDepartments(tenantSlug).getOrNull()
                        val serverEmps = client.getEmployees(tenantSlug).getOrNull()
                        if (serverDepts != null && serverEmps != null) {
                            _uiState.update { state ->
                                state.copy(departments = serverDepts, employees = serverEmps)
                            }
                        }
                    } catch (e: Exception) {
                        println("API Restore Presets exception: ${e.message}")
                    }
                }
            }

            // Archive Operations (Soft-Delete — pola Odoo)
            is OrgChartUiEvent.RequestArchiveEmployee -> {
                val emp = _uiState.value.employees.find { it.id.value == event.id } ?: return
                val subordinates = _uiState.value.employees.filter { it.reportsToId?.value == event.id }
                val superiorName = _uiState.value.employees.find { it.id == emp.reportsToId }?.name ?: "Direksi / Atasan Tertinggi"

                val warning = if (subordinates.isNotEmpty()) {
                    "Karyawan ini memiliki ${subordinates.size} bawahan langsung (${subordinates.joinToString { it.name }}). Jika diarsipkan, seluruh bawahan akan dialihkan secara otomatis ke '$superiorName'. Data bisa dipulihkan kapan saja."
                } else {
                    "Data karyawan '${emp.name}' (${emp.roleTitle}) akan diarsipkan. Data tetap tersimpan di database dan bisa dipulihkan kapan saja."
                }

                _uiState.update {
                    it.copy(
                        isDeleteDialogOpen = true,
                        deleteTargetType = DeleteTargetType.EMPLOYEE,
                        deleteTargetId = emp.id.value,
                        deleteTargetName = emp.name,
                        deleteWarningNote = warning,
                        isDeleteBlocked = false
                    )
                }
            }

            is OrgChartUiEvent.RequestArchiveDepartment -> {
                val assignedEmployees = _uiState.value.employees.filter { it.department?.id == event.dept.id }
                val isBlocked = assignedEmployees.isNotEmpty()

                val warning = if (isBlocked) {
                    "Tidak dapat mengarsipkan divisi '${event.dept.displayName}' karena masih memiliki ${assignedEmployees.size} karyawan aktif (${assignedEmployees.joinToString { it.name }}).\n\nPindahkan atau arsipkan seluruh karyawan di divisi ini terlebih dahulu."
                } else {
                    "Divisi '${event.dept.displayName}' tidak memiliki karyawan aktif dan aman untuk diarsipkan. Data bisa dipulihkan kapan saja."
                }

                _uiState.update {
                    it.copy(
                        isDeleteDialogOpen = true,
                        deleteTargetType = DeleteTargetType.DEPARTMENT,
                        deleteTargetId = event.dept.id.value,
                        deleteTargetName = event.dept.displayName,
                        deleteWarningNote = warning,
                        isDeleteBlocked = isBlocked
                    )
                }
            }

            is OrgChartUiEvent.CancelDelete -> {
                _uiState.update {
                    it.copy(
                        isDeleteDialogOpen = false,
                        deleteTargetId = "",
                        deleteTargetName = "",
                        deleteWarningNote = "",
                        isDeleteBlocked = false
                    )
                }
            }

            is OrgChartUiEvent.ConfirmDelete -> {
                val state = _uiState.value
                if (state.isDeleteBlocked) return

                val targetId = state.deleteTargetId
                val targetName = state.deleteTargetName
                val targetType = state.deleteTargetType

                when (targetType) {
                    DeleteTargetType.EMPLOYEE -> {
                        val deletedEmp = state.employees.find { it.id.value == targetId }
                        val superiorId = deletedEmp?.reportsToId

                        // Optimistic local update: remove employee and reassign subordinates to parent superior
                        val updatedEmployees = state.employees
                            .filterNot { it.id.value == targetId }
                            .map { emp ->
                                if (emp.reportsToId?.value == targetId) {
                                    emp.copy(reportsToId = superiorId)
                                } else emp
                            }

                        _uiState.update {
                            it.copy(
                                employees = updatedEmployees,
                                selectedEmployeeId = null,
                                isCreatingNew = true,
                                nameInput = "",
                                emailInput = "",
                                phoneInput = "",
                                roleTitleInput = "",
                                isDeleteDialogOpen = false,
                                deleteTargetId = "",
                                deleteTargetName = "",
                                deleteWarningNote = "",
                                toastMessage = "Karyawan '$targetName' berhasil diarsipkan. Bisa dipulihkan di panel Arsip."
                            )
                        }

                        // Call Backend API
                        val client = apiClient
                        if (client != null) {
                            coroutineScope.launch {
                                try {
                                    val result = client.deleteEmployee(tenantSlug, targetId)
                                    if (result.isSuccess) {
                                        val serverEmps = client.getEmployees(tenantSlug).getOrNull()
                                        if (serverEmps != null) {
                                            _uiState.update { it.copy(employees = serverEmps) }
                                        }
                                    } else {
                                        _uiState.update {
                                            it.copy(toastMessage = "Peringatan API: ${result.exceptionOrNull()?.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("API Delete Employee exception: ${e.message}")
                                }
                            }
                        }
                    }

                    DeleteTargetType.DEPARTMENT -> {
                        val remainingDepts = state.departments.filterNot { it.id.value == targetId }
                        val newSelectedDept = if (state.selectedDepartment?.id?.value == targetId) {
                            remainingDepts.firstOrNull()
                        } else {
                            state.selectedDepartment
                        }

                        _uiState.update {
                            it.copy(
                                departments = remainingDepts,
                                selectedDepartment = newSelectedDept,
                                isDeleteDialogOpen = false,
                                deleteTargetId = "",
                                deleteTargetName = "",
                                deleteWarningNote = "",
                                toastMessage = "Divisi '$targetName' berhasil diarsipkan. Bisa dipulihkan di panel Arsip."
                            )
                        }

                        // Call Backend API
                        val client = apiClient
                        if (client != null) {
                            coroutineScope.launch {
                                try {
                                    val result = client.deleteDepartment(tenantSlug, targetId)
                                    if (result.isSuccess) {
                                        val serverDepts = client.getDepartments(tenantSlug).getOrNull()
                                        if (serverDepts != null) {
                                            _uiState.update { it.copy(departments = serverDepts) }
                                        }
                                    } else {
                                        _uiState.update {
                                            it.copy(toastMessage = "Peringatan API: ${result.exceptionOrNull()?.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("API Delete Department exception: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            is OrgChartUiEvent.ToggleArchivedPanel -> {
                val nowOpen = !_uiState.value.showArchivedPanel
                _uiState.update { it.copy(showArchivedPanel = nowOpen) }
                if (nowOpen) {
                    val client = apiClient ?: return
                    _uiState.update { it.copy(isLoadingArchived = true) }
                    coroutineScope.launch {
                        try {
                            val emps = client.getArchivedEmployees(tenantSlug).getOrNull() ?: emptyList()
                            val depts = client.getArchivedDepartments(tenantSlug).getOrNull() ?: emptyList()
                            _uiState.update { it.copy(archivedEmployees = emps, archivedDepartments = depts, isLoadingArchived = false) }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(isLoadingArchived = false, toastMessage = "Gagal memuat arsip: ${e.message}") }
                        }
                    }
                }
            }

            is OrgChartUiEvent.RestoreEmployee -> {
                val client = apiClient ?: return
                coroutineScope.launch {
                    try {
                        val result = client.restoreEmployee(tenantSlug, event.id)
                        if (result.isSuccess) {
                            // Refresh both active and archived lists
                            val activeEmps = client.getEmployees(tenantSlug).getOrNull() ?: _uiState.value.employees
                            val archivedEmps = client.getArchivedEmployees(tenantSlug).getOrNull() ?: emptyList()
                            _uiState.update {
                                it.copy(
                                    employees = activeEmps,
                                    archivedEmployees = archivedEmps,
                                    toastMessage = "Karyawan berhasil dipulihkan ke daftar aktif."
                                )
                            }
                        } else {
                            _uiState.update { it.copy(toastMessage = "Gagal memulihkan: ${result.exceptionOrNull()?.message}") }
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(toastMessage = "Error: ${e.message}") }
                    }
                }
            }

            is OrgChartUiEvent.RestoreDepartment -> {
                val client = apiClient ?: return
                coroutineScope.launch {
                    try {
                        val result = client.restoreDepartment(tenantSlug, event.id)
                        if (result.isSuccess) {
                            val activeDepts = client.getDepartments(tenantSlug).getOrNull() ?: _uiState.value.departments
                            val archivedDepts = client.getArchivedDepartments(tenantSlug).getOrNull() ?: emptyList()
                            _uiState.update {
                                it.copy(
                                    departments = activeDepts,
                                    archivedDepartments = archivedDepts,
                                    toastMessage = "Divisi berhasil dipulihkan ke daftar aktif."
                                )
                            }
                        } else {
                            _uiState.update { it.copy(toastMessage = "Gagal memulihkan divisi: ${result.exceptionOrNull()?.message}") }
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(toastMessage = "Error: ${e.message}") }
                    }
                }
            }

            is OrgChartUiEvent.DismissEmailConflictModal -> {
                _uiState.update { it.copy(emailConflictModal = null) }
            }

            is OrgChartUiEvent.RestoreEmployeeFromConflictModal -> {
                val client = apiClient ?: return
                coroutineScope.launch {
                    try {
                        val result = client.restoreEmployee(tenantSlug, event.id)
                        if (result.isSuccess) {
                            val activeEmps = client.getEmployees(tenantSlug).getOrNull() ?: _uiState.value.employees
                            val archivedEmps = client.getArchivedEmployees(tenantSlug).getOrNull() ?: emptyList()
                            val restoredEmp = activeEmps.find { it.id.value == event.id }
                            _uiState.update {
                                it.copy(
                                    employees = activeEmps,
                                    archivedEmployees = archivedEmps,
                                    emailConflictModal = null,
                                    selectedEmployeeId = event.id,
                                    isCreatingNew = false,
                                    nameInput = restoredEmp?.name ?: it.nameInput,
                                    emailInput = restoredEmp?.email ?: it.emailInput,
                                    phoneInput = restoredEmp?.phone ?: it.phoneInput,
                                    selectedDepartment = restoredEmp?.department ?: it.selectedDepartment,
                                    selectedLevel = restoredEmp?.level ?: it.selectedLevel,
                                    selectedReportsToId = restoredEmp?.reportsToId?.value,
                                    roleTitleInput = restoredEmp?.roleTitle ?: it.roleTitleInput,
                                    toastMessage = "Karyawan '${restoredEmp?.name ?: "terpilih"}' berhasil dipulihkan dari arsip!"
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(toastMessage = "Gagal memulihkan karyawan: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(toastMessage = "Error: ${e.message}") }
                    }
                }
            }
        }
    }

    private fun handleSaveEmployee() {
        val state = _uiState.value
        val name = state.nameInput.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(toastMessage = "Nama karyawan tidak boleh kosong.") }
            return
        }

        val targetDept = if (state.selectedLevel == HierarchyLevel.EXECUTIVE) null else (state.selectedDepartment ?: state.activeDepartment)

        val newId = if (state.isCreatingNew) {
            OrgNodeId("emp-${name.lowercase().replace("\\s+".toRegex(), "-")}-${(100..999).random()}")
        } else {
            OrgNodeId(state.selectedEmployeeId ?: "emp-${(100..999).random()}")
        }

        val resolvedTier = if (state.selectedLevel == HierarchyLevel.EXECUTIVE) "Direksi" else state.selectedTierName

        val newNode = OrgNode(
            id = newId,
            name = name,
            email = state.emailInput.trim().ifBlank { "${name.lowercase().replace("\\s+".toRegex(), ".")}@wemade.id" },
            department = targetDept,
            level = state.selectedLevel,
            tierName = resolvedTier,
            roleTitle = state.roleTitleInput.trim().ifBlank { resolvedTier ?: state.selectedLevel.displayName },
            reportsToId = state.selectedReportsToId?.let { OrgNodeId(it) },
            phone = state.phoneInput.trim()
        )

        val isCreating = state.isCreatingNew
        val successionAction = state.successionAction
        val previousEmployees = state.employees
        val previousSelectedEmployeeId = state.selectedEmployeeId
        val previousIsCreatingNew = state.isCreatingNew

        // 1. Optimistic UI update
        applyEmployeeSaveToState(newNode, isCreating, successionAction)

        // 2. Call Backend API
        val client = apiClient ?: return
        coroutineScope.launch {
            try {
                val result = if (isCreating) {
                    client.createEmployee(tenantSlug, newNode, successionAction)
                } else {
                    client.updateEmployee(tenantSlug, newNode, successionAction)
                }

                if (result.isSuccess) {
                    val savedNode = result.getOrThrow()
                    val serverList = client.getEmployees(tenantSlug).getOrNull()
                    _uiState.update { current ->
                        val filteredServerList = serverList?.let { filterByScope(it, current.selectedDepartment?.id?.value) }
                        current.copy(
                            employees = filteredServerList ?: current.employees,
                            selectedEmployeeId = savedNode.id.value,
                            isCreatingNew = false,
                            toastMessage = "Karyawan '${savedNode.name}' berhasil disimpan ke database!"
                        )
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    // Rollback optimistic update: kembalikan state list, ID, dan status isCreatingNew semula
                    _uiState.update { current ->
                        current.copy(
                            employees = previousEmployees,
                            selectedEmployeeId = previousSelectedEmployeeId,
                            isCreatingNew = previousIsCreatingNew
                        )
                    }

                    if (ex is EmailConflictException) {
                        _uiState.update { current ->
                            current.copy(
                                emailConflictModal = EmailConflictInfo(
                                    email = ex.email,
                                    existingEmployeeId = ex.existingEmployeeId,
                                    existingEmployeeName = ex.existingEmployeeName,
                                    existingDepartmentName = ex.existingDepartmentName,
                                    existingRoleTitle = ex.existingRoleTitle,
                                    isArchived = ex.isArchived
                                ),
                                toastMessage = null
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(toastMessage = "Gagal menyimpan: ${ex?.message ?: "Terjadi kesalahan"}")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        employees = previousEmployees,
                        selectedEmployeeId = previousSelectedEmployeeId,
                        isCreatingNew = previousIsCreatingNew,
                        toastMessage = "Gagal menyimpan: ${e.message}"
                    )
                }
            }
        }
    }

    private fun applyEmployeeSaveToState(
        newNode: OrgNode,
        isCreatingNew: Boolean,
        successionAction: HeadSuccessionAction
    ) {
        _uiState.update { current ->
            val existingHead = current.existingHeadOfSelectedDept
            var toast = "Karyawan '${newNode.name}' berhasil disimpan ke bagan struktur!"

            val updatedEmployees = if (newNode.level == HierarchyLevel.HEAD_OF_DEPARTMENT && existingHead != null) {
                // ─── SUKSESI KEPALA DIVISI (SINGLE ACTIVE HEAD CONSTRAINT) ───
                when (successionAction) {
                    HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                        val deptName = newNode.department?.shortName ?: ""
                        val demotedHead = existingHead.copy(
                            level = HierarchyLevel.STAFF_OPERATOR,
                            roleTitle = "Staf Senior $deptName",
                            reportsToId = newNode.id
                        )
                        toast = "Suksesi berhasil! '${newNode.name}' diangkat sebagai Kepala Divisi $deptName, dan '${existingHead.name}' dialihkan menjadi Staf."
                        current.employees.map { emp ->
                            when (emp.id) {
                                existingHead.id -> demotedHead
                                newNode.id -> newNode
                                else -> {
                                    if (emp.reportsToId == existingHead.id) emp.copy(reportsToId = newNode.id)
                                    else emp
                                }
                            }
                        }.let { list ->
                            if (isCreatingNew) list + newNode else list
                        }
                    }

                    HeadSuccessionAction.DEACTIVATE -> {
                        val deptName = newNode.department?.shortName ?: ""
                        toast = "Suksesi berhasil! '${newNode.name}' diangkat sebagai Kepala Divisi $deptName, dan akun '${existingHead.name}' dinonaktifkan."
                        current.employees
                            .filter { it.id != existingHead.id }
                            .map { emp ->
                                if (emp.id == newNode.id) newNode
                                else if (emp.reportsToId == existingHead.id) emp.copy(reportsToId = newNode.id)
                                else emp
                            }.let { list ->
                                if (isCreatingNew) list + newNode else list
                            }
                    }
                }
            } else {
                if (isCreatingNew) {
                    current.employees + newNode
                } else {
                    current.employees.map { if (it.id == newNode.id) newNode else it }
                }
            }

            current.copy(
                employees = updatedEmployees,
                selectedEmployeeId = if (isCreatingNew) current.selectedEmployeeId else newNode.id.value,
                isCreatingNew = isCreatingNew,
                toastMessage = toast
            )
        }
    }

    companion object {
        /**
         * Logika penentuan atasan default otomatis berdasarkan hirarki wewenang dan divisi:
         * - STAFF_OPERATOR -> Kepala Divisi dari divisi tersebut. Fallback ke Direksi jika belum ada kepala divisi.
         * - HEAD_OF_DEPARTMENT -> Direksi (Executive).
         * - EXECUTIVE -> null (tidak memiliki atasan).
         */
        fun resolveDefaultSuperior(
            employees: List<OrgNode>,
            dept: Department?,
            level: HierarchyLevel
        ): String? {
            return when (level) {
                HierarchyLevel.EXECUTIVE -> null
                HierarchyLevel.HEAD_OF_DEPARTMENT -> {
                    employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                }
                HierarchyLevel.TEAM_LEAD -> {
                    employees.find {
                        it.department?.id == dept?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
                    }?.id?.value ?: employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                }
                HierarchyLevel.STAFF_OPERATOR -> {
                    employees.find {
                        it.department?.id == dept?.id && it.level == HierarchyLevel.TEAM_LEAD
                    }?.id?.value
                        ?: employees.find {
                            it.department?.id == dept?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
                        }?.id?.value
                        ?: employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
                }
            }
        }
    }
}

