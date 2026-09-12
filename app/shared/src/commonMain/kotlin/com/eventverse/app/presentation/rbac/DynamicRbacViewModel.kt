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
    private val tenantSlug: String = "wemade-demo",
    private val apiClient: com.eventverse.app.infrastructure.api.RbacApiClient? = com.eventverse.app.infrastructure.api.RbacApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val policyRepository: RbacAccessPolicyRepository = RbacAccessPolicyRepository.shared
) {
    private val _uiState = MutableStateFlow(DynamicRbacUiState())
    val uiState: StateFlow<DynamicRbacUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<DynamicRbacUiEffect>()
    val uiEffect: SharedFlow<DynamicRbacUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadInitialRoles()
        fetchRemoteData()
    }

    private fun fetchRemoteData() {
        val client = apiClient ?: return
        scope.launch {
            val deptsResult = client.getDepartments(tenantSlug)
            val rolesResult = client.getRoles(tenantSlug)

            // Penugasan divisi kini punya tabelnya sendiri. Seed lokal di loadInitialRoles()
            // turun pangkat jadi cadangan saat backend belum jalan.
            client.getModuleAssignments(tenantSlug).onSuccess { remote ->
                if (remote.isNotEmpty()) {
                    _uiState.update { it.copy(moduleAssignments = remote) }
                    policyRepository.syncAssignments(remote)
                }
            }

            val remoteDepts = deptsResult.getOrNull()
            val remoteRoles = rolesResult.getOrNull()

            if (!remoteRoles.isNullOrEmpty() || !remoteDepts.isNullOrEmpty()) {
                _uiState.update { state ->
                    val finalDepts = if (!remoteDepts.isNullOrEmpty()) remoteDepts else state.departments
                    val finalRoles = if (!remoteRoles.isNullOrEmpty()) remoteRoles else state.roles
                    val selected = finalRoles.find { it.id.value == state.selectedRoleId } ?: finalRoles.firstOrNull()
                    state.copy(
                        departments = finalDepts,
                        roles = finalRoles,
                        selectedRoleId = selected?.id?.value,
                        draftRole = selected
                    )
                }
            }
        }
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
        val salesDept = depts.find { it.code.equals("sales", ignoreCase = true) }
        val whDept = depts.find { it.code.equals("warehouse", ignoreCase = true) }
        val cutDept = depts.find { it.code.equals("cutting", ignoreCase = true) || it.code.contains("ppic", ignoreCase = true) }
        val sewDept = depts.find { it.code.equals("sewing", ignoreCase = true) || it.code.contains("production", ignoreCase = true) || it.code.contains("ppic", ignoreCase = true) }
        val qcDept = depts.find { it.code.equals("qc", ignoreCase = true) || it.code.contains("quality", ignoreCase = true) }
        val finishDept = depts.find { it.code.equals("finishing", ignoreCase = true) || it.code.contains("warehouse", ignoreCase = true) }
        val mgmtDept = depts.find { it.code.equals("management", ignoreCase = true) || it.code.contains("finance", ignoreCase = true) }

        return mapOf(
            BusinessModule.CRM_SALES to listOfNotNull(
                salesDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.OPERATE,
                        scope = DataScope.SUBORDINATE_DATA
                    )
                },
                salesDept?.let {
                    DepartmentModuleAssignment(
                        departmentId = it.id.value,
                        departmentName = it.displayName,
                        accessLevel = AccessLevel.MANAGE,
                        specificRoleIds = setOf("role-sales-head"),
                        scope = DataScope.ALL_TENANT_DATA
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
                        selectedTemplateRoleId = event.templateId,
                        newRoleDepartmentId = event.departmentId
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

            is DynamicRbacUiEvent.OpenAssignModuleModal -> {
                _uiState.update {
                    it.copy(
                        isAssignModuleModalOpen = true,
                        activeAssignDepartment = event.department,
                        activeAssignRole = event.role,
                        editingAssignment = event.existing,
                        editingAssignModule = event.initialModule
                    )
                }
            }

            is DynamicRbacUiEvent.CloseAssignModuleModal -> {
                _uiState.update {
                    it.copy(
                        isAssignModuleModalOpen = false,
                        activeAssignDepartment = null,
                        activeAssignRole = null,
                        editingAssignment = null,
                        editingAssignModule = null
                    )
                }
            }

            is DynamicRbacUiEvent.SaveDepartmentAssignment -> {
                // Simpan ke server dan sebarkan ke repository kebijakan. Sebelumnya perubahan ini
                // hanya menyentuh state layar: matriksnya berubah, menu navigasi tidak, dan
                // seluruhnya hilang saat halaman dimuat ulang.
                persistAssignment(event.module, event.assignment)

                _uiState.update { state ->
                    val currentList = state.moduleAssignments[event.module] ?: emptyList()
                    val keyToRemove = event.existingAssignmentKey ?: event.assignment.assignmentKey
                    val filtered = currentList.filter { it.assignmentKey != keyToRemove }
                    val updatedMap = state.moduleAssignments + (event.module to (filtered + event.assignment))
                    state.copy(
                        moduleAssignments = updatedMap,
                        isAssignModalOpen = false,
                        isAssignModuleModalOpen = false,
                        activeAssignModule = null,
                        activeAssignDepartment = null,
                        activeAssignRole = null,
                        editingAssignment = null,
                        editingAssignModule = null,
                        isDirty = true,
                        successToast = "Akses modul ${event.module.displayName} untuk ${event.assignment.departmentName} berhasil diperbarui."
                    )
                }
            }

            is DynamicRbacUiEvent.RemoveDepartmentAssignment -> {
                removeAssignment(event.module, event.assignmentKey)

                _uiState.update { state ->
                    val currentList = state.moduleAssignments[event.module] ?: emptyList()
                    val updatedList = currentList.filter { it.assignmentKey != event.assignmentKey }
                    val updatedMap = state.moduleAssignments + (event.module to updatedList)
                    state.copy(
                        moduleAssignments = updatedMap,
                        isDirty = true,
                        successToast = "Akses berhasil dicabut dari modul ${event.module.displayName}."
                    )
                }
            }

            is DynamicRbacUiEvent.DismissToast -> {
                _uiState.update { it.copy(successToast = null, errorToast = null) }
            }
        }
    }

    /**
     * Menyimpan satu penugasan divisi ke server, lalu menyebarkannya ke repository kebijakan.
     *
     * State layar diperbarui di pemanggil tanpa menunggu server: kegagalan jaringan tidak boleh
     * membekukan matriks di layar. Yang dilaporkan kalau gagal adalah toast, bukan rollback —
     * membatalkan perubahan yang baru saja diketik lebih membingungkan daripada memberitahunya.
     */
    private fun persistAssignment(module: BusinessModule, assignment: DepartmentModuleAssignment) {
        scope.launch {
            apiClient?.upsertModuleAssignment(tenantSlug, module, assignment)
                ?.onFailure { cause ->
                    _uiState.update {
                        it.copy(errorToast = cause.message ?: "Gagal menyimpan penugasan ke server.")
                    }
                }
            policyRepository.syncAssignments(_uiState.value.moduleAssignments)
        }
    }

    private fun removeAssignment(module: BusinessModule, assignmentKey: String) {
        scope.launch {
            apiClient?.deleteModuleAssignment(tenantSlug, module, assignmentKey)
                ?.onFailure { cause ->
                    _uiState.update {
                        it.copy(errorToast = cause.message ?: "Gagal mencabut penugasan di server.")
                    }
                }
            policyRepository.syncAssignments(_uiState.value.moduleAssignments)
        }
    }

    private fun handleSaveChanges() {
        val draft = _uiState.value.draftRole ?: return
        scope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            // Asynchronous remote persistence to backend (graceful failover to local state)
            val apiResult = apiClient?.updateRole(tenantSlug, draft)
            val finalRole = apiResult?.getOrNull() ?: draft

            delay(200) // Brief feedback
            _uiState.update { state ->
                val updatedRoles = state.roles.map { role ->
                    if (role.id == finalRole.id) finalRole else role
                }
                state.copy(
                    roles = updatedRoles,
                    draftRole = finalRole,
                    isDirty = false,
                    isSaving = false,
                    successToast = "Hak akses jabatan '${finalRole.name}' berhasil disimpan!"
                )
            }
            // Menu navigasi ikut berubah seketika, tanpa perlu login ulang.
            policyRepository.syncRoles(_uiState.value.roles)
            _uiEffect.emit(DynamicRbacUiEffect.ShowToast("Hak akses jabatan '${finalRole.name}' disimpan."))
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

        // Divisi yang dipilih menang; kalau tidak dipilih, warisi dari jabatan template. Template
        // dipakai justru karena mirip dengan jabatan yang ditiru, dan divisi adalah bagian dari
        // kemiripan itu.
        val departmentId = _uiState.value.newRoleDepartmentId?.takeIf { it.isNotBlank() }
            ?: templateRole?.departmentId

        val newId = RoleId("role-custom-${name.lowercase().replace("\\s+".toRegex(), "-")}-${(100..999).random()}")
        val initialNewRole = CustomRole(
            id = newId,
            tenantId = tenantId,
            name = name,
            description = desc.ifBlank { "Jabatan kustom operasional pabrik." },
            isSystemDefault = false,
            modulePermissions = permissions,
            userCount = 0,
            departmentId = departmentId
        )

        scope.launch {
            // Asynchronous backend persistence
            val apiResult = apiClient?.createRole(
                tenantSlug = tenantSlug,
                name = initialNewRole.name,
                description = initialNewRole.description,
                modulePermissions = initialNewRole.modulePermissions,
                departmentId = initialNewRole.departmentId
            )
            val savedRole = apiResult?.getOrNull() ?: initialNewRole

            _uiState.update { state ->
                val updatedList = state.roles + savedRole
                state.copy(
                    roles = updatedList,
                    selectedRoleId = savedRole.id.value,
                    draftRole = savedRole,
                    isDirty = false,
                    isCreateModalOpen = false,
                    newRoleDepartmentId = null,
                    successToast = "Jabatan baru '${savedRole.name}' berhasil dibuat!"
                )
            }
            // Jabatan baru langsung tersedia bagi persona, tanpa memuat ulang halaman.
            policyRepository.syncRoles(_uiState.value.roles)
        }
    }

    private fun handleDeleteRole(roleId: String) {
        val targetRole = _uiState.value.roles.find { it.id.value == roleId }
        if (targetRole == null || targetRole.isSystemDefault) {
            _uiState.update { it.copy(errorToast = "Jabatan bawaan sistem tidak dapat dihapus.") }
            return
        }

        scope.launch {
            // Asynchronous backend deletion
            apiClient?.deleteRole(tenantSlug, roleId)

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

    companion object {
        fun resolveDepartmentForRole(role: CustomRole, departments: List<Department>): Department? {
            if (role.departmentId != null) {
                val found = departments.find { it.id.value == role.departmentId }
                if (found != null) return found
            }
            // Fallback matching by keyword
            val rName = role.name.lowercase()
            return when {
                rName.contains("sales") || rName.contains("penjualan") ->
                    departments.find { it.code.contains("sales") || it.displayName.contains("penjualan", ignoreCase = true) }
                rName.contains("ppic") || rName.contains("produksi") || rName.contains("jahit") || rName.contains("operator") || rName.contains("mandor") ->
                    departments.find { it.code.contains("ppic") || it.code.contains("production") || it.displayName.contains("produksi", ignoreCase = true) }
                rName.contains("gudang") || rName.contains("logistik") || rName.contains("warehouse") ->
                    departments.find { it.code.contains("warehouse") || it.displayName.contains("gudang", ignoreCase = true) }
                rName.contains("qc") || rName.contains("quality") || rName.contains("kualitas") ->
                    departments.find { it.code.contains("qc") || it.displayName.contains("quality", ignoreCase = true) }
                rName.contains("keuangan") || rName.contains("akuntansi") || rName.contains("finance") ->
                    departments.find { it.code.contains("finance") || it.displayName.contains("keuangan", ignoreCase = true) }
                else -> null // e.g. Owner / Direktur yang merupakan lintas divisi / eksekutif
            }
        }
    }
}
