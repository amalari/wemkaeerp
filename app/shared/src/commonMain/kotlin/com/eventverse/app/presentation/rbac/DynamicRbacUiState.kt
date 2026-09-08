package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.rbac.*

data class DynamicRbacUiState(
    val roles: List<CustomRole> = emptyList(),
    val selectedRoleId: String? = null,
    val draftRole: CustomRole? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val searchQuery: String = "",
    val selectedCategoryFilter: ModuleCategory? = null,
    val isCreateModalOpen: Boolean = false,
    val newRoleNameInput: String = "",
    val newRoleDescInput: String = "",
    val selectedTemplateRoleId: String? = null,
    val successToast: String? = null,
    val errorToast: String? = null
) {
    val selectedRole: CustomRole?
        get() = draftRole ?: roles.find { it.id.value == selectedRoleId }

    val totalUsers: Int
        get() = roles.sumOf { it.userCount }

    val totalActiveModules: Int
        get() = BusinessModule.entries.size
}

sealed interface DynamicRbacUiEvent {
    data class SelectRole(val roleId: String) : DynamicRbacUiEvent
    data class UpdateRoleMetadata(val name: String, val description: String) : DynamicRbacUiEvent
    data class ChangeModuleAccess(
        val module: BusinessModule,
        val level: AccessLevel,
        val scope: DataScope = DataScope.ALL_TENANT_DATA
    ) : DynamicRbacUiEvent
    data class SetModuleCategoryFilter(val category: ModuleCategory?) : DynamicRbacUiEvent
    data class UpdateSearchQuery(val query: String) : DynamicRbacUiEvent
    data object ResetChanges : DynamicRbacUiEvent
    data object SaveChanges : DynamicRbacUiEvent
    data class OpenCreateModal(val templateRoleId: String? = null) : DynamicRbacUiEvent
    data object CloseCreateModal : DynamicRbacUiEvent
    data class UpdateNewRoleInputs(val name: String, val desc: String, val templateId: String?) : DynamicRbacUiEvent
    data object ConfirmCreateRole : DynamicRbacUiEvent
    data class DeleteRole(val roleId: String) : DynamicRbacUiEvent
    data object DismissToast : DynamicRbacUiEvent
}

sealed interface DynamicRbacUiEffect {
    data class ShowToast(val message: String, val isError: Boolean = false) : DynamicRbacUiEffect
}
