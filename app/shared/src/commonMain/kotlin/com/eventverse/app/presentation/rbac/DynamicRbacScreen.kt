package com.eventverse.app.presentation.rbac

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.ModuleCategory
import com.eventverse.app.presentation.rbac.components.AssignDepartmentModal
import com.eventverse.app.presentation.rbac.components.CreateRoleModal
import com.eventverse.app.presentation.rbac.components.ModuleCardList
import com.eventverse.app.presentation.rbac.components.ModuleMatrixRow
import com.eventverse.app.presentation.rbac.components.RoleListSidebar
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun DynamicRbacScreen(
    viewModel: DynamicRbacViewModel = remember { DynamicRbacViewModel() },
    onBackToLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // 1. Top Navigation & Header
            ScreenHeader(
                totalRoles = state.roles.size,
                totalModules = state.totalActiveModules,
                totalUsers = state.totalUsers,
                onBackToLogin = onBackToLogin
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Toast Alerts (Success / Error)
            ToastAlertBanner(
                successMessage = state.successToast,
                errorMessage = state.errorToast,
                onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.DismissToast) }
            )

            // 2. View Mode Switcher Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Switcher (1 Modul 1 Card vs Matriks Jabatan)
                Row(
                    modifier = Modifier
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RbacViewMode.entries.forEach { mode ->
                        val isSelected = state.viewMode == mode
                        Box(
                            modifier = Modifier
                                .then(
                                    if (isSelected) {
                                        Modifier.clayFlat(
                                            shape = RoundedCornerShape(8.dp),
                                            background = WeMadeColors.Surface,
                                            outline = WeMadeColors.Outline,
                                            borderWidth = 1.5.dp
                                        )
                                    } else {
                                        Modifier.clip(RoundedCornerShape(8.dp))
                                    }
                                )
                                .clickable { viewModel.onEvent(DynamicRbacUiEvent.SetViewMode(mode)) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (mode == RbacViewMode.PER_MODULE) {
                                    IconLayers(
                                        modifier = Modifier.size(14.dp),
                                        color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                                    )
                                } else {
                                    IconUser(
                                        modifier = Modifier.size(14.dp),
                                        color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                                    )
                                }
                                Text(
                                    text = when (mode) {
                                        RbacViewMode.PER_MODULE -> "1 Modul 1 Card"
                                        RbacViewMode.PER_ROLE -> "Matriks Jabatan"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }

                // Filter kategori jika mode 1 Modul 1 Card
                if (state.viewMode == RbacViewMode.PER_MODULE) {
                    CategoryFilterBar(
                        selectedCategory = state.selectedCategoryFilter,
                        onCategorySelect = { viewModel.onEvent(DynamicRbacUiEvent.SetModuleCategoryFilter(it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Main Work Area
            if (state.viewMode == RbacViewMode.PER_MODULE) {
                // 1 Modul 1 Card Workspace
                val filteredModules = BusinessModule.entries.filter { module ->
                    (state.selectedCategoryFilter == null || module.category == state.selectedCategoryFilter) &&
                    (state.searchQuery.isBlank() || module.displayName.contains(state.searchQuery, ignoreCase = true) ||
                     module.description.contains(state.searchQuery, ignoreCase = true))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Educational Banner for 1 Modul 1 Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SuccessBg,
                                outline = WeMadeColors.Success
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconShield(modifier = Modifier.size(18.dp), color = WeMadeColors.Success)
                            Column {
                                Text(
                                    text = "Desain 1 Modul 1 Card & Jangkauan Dinamis:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = WeMadeColors.Success
                                )
                                Text(
                                    text = "Tugaskan modul ke divisi pabrik. Modul inventaris, kalkulasi HPP, dan mesin otomatis berlaku seragam satu pabrik (Shared Resource), sedangkan modul CRM, sampling, dan operator mendukung isolasi hirarkis (Sendiri / Bawahan / Semua Data).",
                                    fontSize = 11.5.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ModuleCardList(
                        modules = filteredModules,
                        assignments = state.moduleAssignments,
                        departments = state.departments,
                        roles = state.roles,
                        onOpenAssignModal = { module, existing ->
                            viewModel.onEvent(DynamicRbacUiEvent.OpenAssignModal(module, existing))
                        },
                        onRemoveAssignment = { module, assignKey ->
                            viewModel.onEvent(DynamicRbacUiEvent.RemoveDepartmentAssignment(module, assignKey))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Matriks Jabatan Workspace
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left Sidebar: Role List
                    RoleListSidebar(
                        roles = state.roles,
                        selectedRoleId = state.selectedRoleId,
                        onSelectRole = { viewModel.onEvent(DynamicRbacUiEvent.SelectRole(it)) },
                        onOpenCreateModal = { viewModel.onEvent(DynamicRbacUiEvent.OpenCreateModal()) },
                        modifier = Modifier.width(320.dp)
                    )

                    // Right Panel: Selected Role Permissions Matrix
                    val activeRole = state.selectedRole
                    if (activeRole != null) {
                        RoleMatrixDetailPanel(
                            role = activeRole,
                            isDirty = state.isDirty,
                            isSaving = state.isSaving,
                            selectedCategory = state.selectedCategoryFilter,
                            searchQuery = state.searchQuery,
                            onCategorySelect = { viewModel.onEvent(DynamicRbacUiEvent.SetModuleCategoryFilter(it)) },
                            onSearchChange = { viewModel.onEvent(DynamicRbacUiEvent.UpdateSearchQuery(it)) },
                            onAccessChanged = { module, level, scope ->
                                viewModel.onEvent(DynamicRbacUiEvent.ChangeModuleAccess(module, level, scope))
                            },
                            onReset = { viewModel.onEvent(DynamicRbacUiEvent.ResetChanges) },
                            onSave = { viewModel.onEvent(DynamicRbacUiEvent.SaveChanges) },
                            onDeleteRole = { viewModel.onEvent(DynamicRbacUiEvent.DeleteRole(activeRole.id.value)) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pilih jabatan di sebelah kiri untuk melihat hak akses",
                                style = MaterialTheme.typography.bodyLarge,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }

        // Create Role Modal Dialog
        CreateRoleModal(
            isOpen = state.isCreateModalOpen,
            nameInput = state.newRoleNameInput,
            descInput = state.newRoleDescInput,
            selectedTemplateId = state.selectedTemplateRoleId,
            roles = state.roles,
            onInputsChanged = { name, desc, template ->
                viewModel.onEvent(DynamicRbacUiEvent.UpdateNewRoleInputs(name, desc, template))
            },
            onConfirm = { viewModel.onEvent(DynamicRbacUiEvent.ConfirmCreateRole) },
            onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.CloseCreateModal) }
        )

        // Assign Department Modal Dialog (1 Modul 1 Card)
        AssignDepartmentModal(
            isOpen = state.isAssignModalOpen,
            module = state.activeAssignModule,
            departments = state.departments,
            roles = state.roles,
            initialAssignment = state.editingAssignment,
            onConfirm = { assignment ->
                val activeMod = state.activeAssignModule ?: return@AssignDepartmentModal
                viewModel.onEvent(
                    DynamicRbacUiEvent.SaveDepartmentAssignment(
                        module = activeMod,
                        assignment = assignment,
                        existingAssignmentKey = state.editingAssignment?.assignmentKey
                    )
                )
            },
            onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.CloseAssignModal) }
        )
    }
}

@Composable
private fun ScreenHeader(
    totalRoles: Int,
    totalModules: Int,
    totalUsers: Int,
    onBackToLogin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pengaturan Hak Akses & Jabatan Pabrik",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayTag(
                    text = "Dynamic Module RBAC",
                    tint = WeMadeColors.Primary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Kelola struktur peran dan batasan modul konveksi tanpa terminologi teknis yang rumit.",
                style = MaterialTheme.typography.bodyMedium,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Quick Metric Badges & Back Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderStatChip(label = "Jabatan", value = "$totalRoles")
            HeaderStatChip(label = "Modul SaaS", value = "$totalModules")
            HeaderStatChip(label = "Total Karyawan", value = "$totalUsers")

            ClayButton(
                text = "Ke Halaman Login",
                onClick = onBackToLogin,
                style = ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun HeaderStatChip(label: String, value: String) {
    Box(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Border
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = WeMadeColors.PrimaryDark
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun RoleMatrixDetailPanel(
    role: CustomRole,
    isDirty: Boolean,
    isSaving: Boolean,
    selectedCategory: ModuleCategory?,
    searchQuery: String,
    onCategorySelect: (ModuleCategory?) -> Unit,
    onSearchChange: (String) -> Unit,
    onAccessChanged: (BusinessModule, AccessLevel, com.eventverse.app.domain.rbac.DataScope) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onDeleteRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxHeight(),
        shape = ClayShapes.Panel,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        shadowColor = WeMadeColors.Outline,
        offset = ClayOffset.Rest,
        borderWidth = ClayBorder.Thick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Selected Role Header Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = role.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )

                        if (role.isSystemDefault) {
                            ClayTag(
                                text = "Bawaan Sistem",
                                tint = WeMadeColors.Secondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = role.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                if (!role.isSystemDefault) {
                    ClayButton(
                        text = "Hapus Jabatan",
                        onClick = onDeleteRole,
                        style = ClayButtonStyle.Danger,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Natural Language Summary Card
            NaturalLanguageSummaryCard(role = role)

            Spacer(modifier = Modifier.height(16.dp))

            // Category Filter Pills
            CategoryFilterBar(
                selectedCategory = selectedCategory,
                onCategorySelect = onCategorySelect
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Module Matrix Rows
            val filteredModules = BusinessModule.entries.filter { module ->
                (selectedCategory == null || module.category == selectedCategory) &&
                (searchQuery.isBlank() || module.displayName.contains(searchQuery, ignoreCase = true) ||
                 module.description.contains(searchQuery, ignoreCase = true))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredModules, key = { it.code }) { module ->
                    val config = role.getAccess(module)
                    ModuleMatrixRow(
                        module = module,
                        config = config,
                        onAccessChanged = { newLevel, newScope ->
                            onAccessChanged(module, newLevel, newScope)
                        }
                    )
                }
            }

            // Bottom Sticky Save Bar
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = WeMadeColors.Border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SaveActionBar(
                isDirty = isDirty,
                isSaving = isSaving,
                onReset = onReset,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun NaturalLanguageSummaryCard(role: CustomRole) {
    val accessibleModules = BusinessModule.entries.filter { role.getAccess(it).isAccessible }
    val manageModules = BusinessModule.entries.filter { role.getAccess(it).level == AccessLevel.MANAGE }
    val operateModules = BusinessModule.entries.filter { role.getAccess(it).level == AccessLevel.OPERATE }
    val blockedModules = BusinessModule.entries.filter { role.getAccess(it).level == AccessLevel.NONE }

    val summaryText = buildString {
        append("Ringkasan Wewenang: ")
        if (manageModules.size == BusinessModule.entries.size) {
            append("Memiliki wewenang penuh di seluruh modul sistem.")
        } else {
            if (manageModules.isNotEmpty()) {
                append("Wewenang penuh pada ${manageModules.size} modul (${manageModules.take(2).joinToString { it.displayName }}). ")
            }
            if (operateModules.isNotEmpty()) {
                append("Dapat input & kerja harian pada ${operateModules.size} modul. ")
            }
            if (blockedModules.isNotEmpty()) {
                append("Akses ditutup pada ${blockedModules.size} modul.")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SuccessBg,
                outline = WeMadeColors.Success
            )
            .padding(12.dp)
    ) {
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.Success,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun CategoryFilterBar(
    selectedCategory: ModuleCategory?,
    onCategorySelect: (ModuleCategory?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isAllSelected = selectedCategory == null
        Box(
            modifier = Modifier
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = if (isAllSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                    outline = if (isAllSelected) WeMadeColors.PrimaryDark else WeMadeColors.Border
                )
                .clickable { onCategorySelect(null) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Semua Modul",
                fontSize = 12.sp,
                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isAllSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
            )
        }

        ModuleCategory.entries.forEach { category ->
            val isSelected = selectedCategory == category
            Box(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                        outline = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.Border
                    )
                    .clickable { onCategorySelect(category) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = category.displayName.substringBefore("&").trim(),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
                )
            }
        }
    }
}

@Composable
private fun SaveActionBar(
    isDirty: Boolean,
    isSaving: Boolean,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDirty) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WeMadeColors.Accent)
                )
                Text(
                    text = "Ada perubahan hak akses yang belum disimpan",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.Accent,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Semua konfigurasi peran tersimpan",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClayButton(
                text = "Batal",
                onClick = onReset,
                enabled = isDirty && !isSaving,
                style = ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            )

            ClayButton(
                text = if (isSaving) "Menyimpan..." else "Simpan Perubahan",
                onClick = onSave,
                enabled = isDirty && !isSaving,
                style = ClayButtonStyle.Primary,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ToastAlertBanner(
    successMessage: String?,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = successMessage != null || errorMessage != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val (bg, textColor, text) = when {
            successMessage != null -> Triple(WeMadeColors.SuccessBg, WeMadeColors.Success, successMessage)
            errorMessage != null -> Triple(WeMadeColors.ErrorBg, WeMadeColors.Error, errorMessage)
            else -> Triple(Color.Transparent, Color.Transparent, "")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clayFlat(
                    shape = ClayShapes.Card,
                    background = bg,
                    outline = textColor
                )
                .clickable { onDismiss() }
                .padding(12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
