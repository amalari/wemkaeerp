package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DynamicRbacViewModel(
    private val tenantId: TenantId = TenantId("tenant-wemade-demo"),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(DynamicRbacUiState())
    val uiState: StateFlow<DynamicRbacUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<DynamicRbacUiEffect>()
    val uiEffect: SharedFlow<DynamicRbacUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadInitialRoles()
    }

    private fun loadInitialRoles() {
        val presets = CustomRole.createFactoryPresets(tenantId)
        val defaultDepts = Department.defaultPresets()
        val initialAssignments = createDefaultModuleAssignments(defaultDepts)
        val initialSelected = presets.firstOrNull()
        _uiState.update {
            it.copy(
                roles = presets,
                departments = defaultDepts,
                moduleAssignments = initialAssignments,
                selectedRoleId = initialSelected?.id?.value,
                draftRole = initialSelected,
                isDirty = false
            )
        }
    }

    private fun createDefaultModuleAssignments(depts: List<Department>): Map<BusinessModule, List<DepartmentModuleAssignment>> {
        val salesDept = depts.find { it.code == "SALES" }
        val whDept = depts.find { it.code == "WAREHOUSE" }
        val cutDept = depts.find { it.code == "CUTTING" }
        val sewDept = depts.find { it.code == "SEWING" }
        val qcDept = depts.find { it.code == "QUALITY_CONTROL" }
        val finishDept = depts.find { it.code == "FINISHING" }
        val mgmtDept = depts.find { it.code == "MANAGEMENT" }

        return mapOf(
            BusinessModule.CRM_SALES to listOfNotNull(
                salesDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.SUBORDINATE_DATA
                    )
                }
            ),
            BusinessModule.SAMPLING_ORDER to listOfNotNull(
                salesDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.SUBORDINATE_DATA
                    )
                },
                cutDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.OPERATE,
                        scope = DataScope.OWN_DATA_ONLY
                    )
                }
            ),
            BusinessModule.INVENTORY to listOfNotNull(
                whDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                }
            ),
            BusinessModule.TECH_PACK_BOM to listOfNotNull(
                cutDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                },
                sewDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.VIEW,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                }
            ),
            BusinessModule.COSTING_HPP to listOfNotNull(
                mgmtDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                }
            ),
            BusinessModule.PRODUCTION_MRP to listOfNotNull(
                sewDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                }
            ),
            BusinessModule.OPERATOR_EXEC to listOfNotNull(
                sewDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.OPERATE,
                        scope = DataScope.OWN_DATA_ONLY
                    )
                }
            ),
            BusinessModule.QUALITY_CONTROL to listOfNotNull(
                qcDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                }
            ),
            BusinessModule.FULFILLMENT to listOfNotNull(
                finishDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                },
                whDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.OPERATE,
                        scope = DataScope.ALL_TENANT_DATA
                    )
                }
            )
        )
    }

    fun onEvent(event: DynamicRbacUiEvent) {
        when (event) {
            is DynamicRbacUiEvent.SelectRole -> {
                val targetRole = _uiState.value.roles.find { it.id.value == event.roleId }
                if (targetRole != null) {
                    _uiState.update {
                        it.copy(
                            selectedRoleId = event.roleId,
                            draftRole = targetRole,
                            isDirty = false,
                            successToast = null,
                            errorToast = null
                        )
                    }
                }
            }

            is DynamicRbacUiEvent.UpdateRoleMetadata -> {
                _uiState.update { state ->
                    val currentDraft = state.draftRole ?: return@update state
                    val updated = currentDraft.updateMetadata(event.name, event.description)
                    state.copy(draftRole = updated, isDirty = true)
                }
            }

            is DynamicRbacUiEvent.ChangeModuleAccess -> {
                _uiState.update { state ->
                    val currentDraft = state.draftRole ?: return@update state
                    val updated = currentDraft.updateModuleAccess(event.module, event.level, event.scope)
                    state.copy(draftRole = updated, isDirty = true)
                }
            }

            is DynamicRbacUiEvent.SetModuleCategoryFilter -> {
                _uiState.update { it.copy(selectedCategoryFilter = event.category) }
            }

            is DynamicRbacUiEvent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }

            is DynamicRbacUiEvent.ResetChanges -> {
                val originalRole = _uiState.value.roles.find { it.id.value == _uiState.value.selectedRoleId }
                _uiState.update {
                    it.copy(
                        draftRole = originalRole,
                        isDirty = false,
                        successToast = "Perubahan telah dikembalikan ke kondisi semula."
                    )
                }
            }

            is DynamicRbacUiEvent.SaveChanges -> {
                handleSaveChanges()
            }

            is DynamicRbacUiEvent.OpenCreateModal -> {
                _uiState.update {
                    it.copy(
                        isCreateModalOpen = true,
                        newRoleNameInput = "",
                        newRoleDescInput = "",
                        selectedTemplateRoleId = event.templateRoleId ?: "role-operator"
                    )
                }
            }

            is DynamicRbacUiEvent.CloseCreateModal -> {
                _uiState.update { it.copy(isCreateModalOpen = false) }
            }

            is DynamicRbacUiEvent.UpdateNewRoleInputs -> {
                _uiState.update {
                    it.copy(
                        newRoleNameInput = event.name,
                        newRoleDescInput = event.desc,
                        selectedTemplateRoleId = event.templateId
                    )
                }
            }

            is DynamicRbacUiEvent.ConfirmCreateRole -> {
                handleConfirmCreateRole()
            }

            is DynamicRbacUiEvent.DeleteRole -> {
                handleDeleteRole(event.roleId)
            }

            is DynamicRbacUiEvent.SetViewMode -> {
                _uiState.update { it.copy(viewMode = event.mode) }
            }

            is DynamicRbacUiEvent.OpenAssignModal -> {
                _uiState.update {
                    it.copy(
                        isAssignModalOpen = true,
                        activeAssignModule = event.module,
                        editingAssignment = event.existing
                    )
                }
            }

            is DynamicRbacUiEvent.CloseAssignModal -> {
                _uiState.update {
                    it.copy(
                        isAssignModalOpen = false,
                        activeAssignModule = null,
                        editingAssignment = null
                    )
                }
            }

            is DynamicRbacUiEvent.SaveDepartmentAssignment -> {
                _uiState.update { state ->
                    val currentList = state.moduleAssignments[event.module] ?: emptyList()
                    val filtered = currentList.filter { it.departmentId != event.assignment.departmentId }
                    val updatedMap = state.moduleAssignments + (event.module to (filtered + event.assignment))
                    state.copy(
                        moduleAssignments = updatedMap,
                        isAssignModalOpen = false,
                        activeAssignModule = null,
                        editingAssignment = null,
                        isDirty = true,
                        successToast = "Akses modul ${event.module.displayName} untuk ${event.assignment.departmentName} berhasil diperbarui."
                    )
                }
            }

            is DynamicRbacUiEvent.RemoveDepartmentAssignment -> {
                _uiState.update { state ->
                    val currentList = state.moduleAssignments[event.module] ?: emptyList()
                    val updatedList = currentList.filter { it.departmentId != event.departmentId }
                    val updatedMap = state.moduleAssignments + (event.module to updatedList)
                    state.copy(
                        moduleAssignments = updatedMap,
                        isDirty = true,
                        successToast = "Akses divisi berhasil dicabut dari modul ${event.module.displayName}."
                    )
                }
            }

            is DynamicRbacUiEvent.DismissToast -> {
                _uiState.update { it.copy(successToast = null, errorToast = null) }
            }
        }
    }

    private fun handleSaveChanges() {
        val draft = _uiState.value.draftRole ?: return
        scope.launch {
            _uiState.update { it.copy(isSaving = true) }
            delay(300) // Brief feedback

            _uiState.update { state ->
                val updatedRoles = state.roles.map { role ->
                    if (role.id == draft.id) draft else role
                }
                state.copy(
                    roles = updatedRoles,
                    draftRole = draft,
                    isDirty = false,
                    isSaving = false,
                    successToast = "Hak akses jabatan '${draft.name}' berhasil disimpan!"
                )
            }
            _uiEffect.emit(DynamicRbacUiEffect.ShowToast("Hak akses jabatan '${draft.name}' disimpan."))
        }
    }

    private fun handleConfirmCreateRole() {
        val name = _uiState.value.newRoleNameInput.trim()
        val desc = _uiState.value.newRoleDescInput.trim()
        val templateId = _uiState.value.selectedTemplateRoleId

        if (name.isBlank()) {
            _uiState.update { it.copy(errorToast = "Nama jabatan tidak boleh kosong.") }
            return
        }

        val templateRole = _uiState.value.roles.find { it.id.value == templateId }
        val permissions = templateRole?.modulePermissions ?: emptyMap()

        val newId = RoleId("role-custom-${name.lowercase().replace("\\s+".toRegex(), "-")}-${(100..999).random()}")
        val newRole = CustomRole(
            id = newId,
            tenantId = tenantId,
            name = name,
            description = desc.ifBlank { "Jabatan kustom operasional pabrik." },
            isSystemDefault = false,
            modulePermissions = permissions,
            userCount = 0
        )

        _uiState.update { state ->
            val updatedList = state.roles + newRole
            state.copy(
                roles = updatedList,
                selectedRoleId = newRole.id.value,
                draftRole = newRole,
                isDirty = false,
                isCreateModalOpen = false,
                successToast = "Jabatan baru '${newRole.name}' berhasil dibuat!"
            )
        }
    }

    private fun handleDeleteRole(roleId: String) {
        val targetRole = _uiState.value.roles.find { it.id.value == roleId }
        if (targetRole == null || targetRole.isSystemDefault) {
            _uiState.update { it.copy(errorToast = "Jabatan bawaan sistem tidak dapat dihapus.") }
            return
        }

        _uiState.update { state ->
            val filtered = state.roles.filterNot { it.id.value == roleId }
            val fallback = filtered.firstOrNull()
            state.copy(
                roles = filtered,
                selectedRoleId = fallback?.id?.value,
                draftRole = fallback,
                isDirty = false,
                successToast = "Jabatan '${targetRole.name}' berhasil dihapus."
            )
        }
    }
}
