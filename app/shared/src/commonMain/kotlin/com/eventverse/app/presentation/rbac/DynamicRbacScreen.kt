package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.presentation.pack.ActiveTenantPack
import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.rbac.components.AssignDepartmentModal
import com.eventverse.app.presentation.rbac.components.AssignModuleModal
import com.eventverse.app.presentation.rbac.components.CreateRoleModal
import com.eventverse.app.presentation.rbac.components.DepartmentCardList
import com.eventverse.app.presentation.rbac.components.ModuleCardList
import com.eventverse.app.presentation.rbac.components.RoleCardList
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun DynamicRbacScreen(
    /** Tenant sesi aktif; WAJIB (tanpa default) supaya layar ini tidak pernah memakai tenant demo diam-diam. */
    tenantId: TenantId,
    tenantSlug: String,
    // Dibuat sekali per tenant; berpindah tenant membuat VM baru, selain itu VM bertahan.
    viewModel: DynamicRbacViewModel = remember(tenantId, tenantSlug) { DynamicRbacViewModel(tenantId, tenantSlug) },
    onBackToLogin: () -> Unit = {},
    /**
     * Wewenang efektif atas modul Hak Akses itu sendiri.
     *
     * Mengubah matriks wewenang bukan pekerjaan harian: satu perubahan di sini berlaku bagi seluruh
     * orang di pabrik. Karena itu seluruh aksi tulis di layar ini menuntut `MANAGE`, tanpa tingkatan
     * `OPERATE` di tengahnya — "setengah boleh mengatur hak akses" bukan keadaan yang bermakna.
     */
    access: ModuleAccessConfig = ModuleAccessConfig(AccessLevel.MANAGE),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val canManage = access.canManage

    // Satu gerbang untuk seluruh aksi tulis layar ini. Menyalurkan setiap event lewat sini lebih
    // aman daripada menonaktifkan tombol satu per satu di tiga file kartu yang berbeda: tombol yang
    // terlewat akan tetap tidak berefek, bukan diam-diam menembus wewenang.
    val onWriteEvent: (DynamicRbacUiEvent) -> Unit = { event ->
        if (canManage) viewModel.onEvent(event)
    }

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
            RbacScreenHeader(
                chips = RbacHeaderChips.forState(
                    loadState = state.loadState,
                    totalRoles = state.roles.size,
                    totalModules = state.totalActiveModules,
                    totalUsers = state.totalUsers
                ),
                onBackToLogin = onBackToLogin
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Toast Alerts (Success / Error)
            ToastAlertBanner(
                successMessage = state.successToast,
                errorMessage = state.errorToast,
                onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.DismissToast) }
            )

            // Menyatakan mode baca-saja secara terbuka.
            //
            // Tanpa keterangan ini, seluruh tombol penugasan tetap terlihat tetapi tidak melakukan
            // apa-apa saat diklik — gejala yang selalu dilaporkan sebagai aplikasi rusak, bukan
            // sebagai wewenang yang memang dibatasi.
            if (!canManage) {
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = WeMadeColors.WarningBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mode Baca Saja — matriks wewenang hanya dapat diubah oleh " +
                                "jabatan dengan akses ${AccessLevel.MANAGE.displayName}.",
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                        ClayBadge(text = access.level.displayName, tint = WeMadeColors.Warning)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. View Mode Switcher Bar (3 Tabs: Per Modul, Per Divisi, Per Jabatan)
            RbacViewModeTabs(
                selected = state.viewMode,
                onSelect = { viewModel.onEvent(DynamicRbacUiEvent.SetViewMode(it)) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Main Work Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                when (val load = state.loadState) {
                    RbacLoadState.Loading -> RbacLoadingView()
                    RbacLoadState.Empty -> RbacEmptyView(
                        message = "Belum ada jabatan di ${ActiveTenantPack.current.term(VocabularyKey.WORKPLACE)} ini.",
                        createLabel = "Buat jabatan",
                        canCreate = canManage,
                        onCreate = { onWriteEvent(DynamicRbacUiEvent.OpenCreateModal()) }
                    )
                    is RbacLoadState.Failed -> RbacFailedView(
                        message = load.message,
                        onRetry = { viewModel.onEvent(DynamicRbacUiEvent.Reload) }
                    )
                    RbacLoadState.Loaded -> when (state.viewMode) {
                    RbacViewMode.PER_MODULE -> {
                        val filteredModules = ActiveTenantPack.current.moduleIds.filter { module ->
                            state.searchQuery.isBlank() ||
                                    module.displayName.contains(state.searchQuery, ignoreCase = true) ||
                                    module.description.contains(state.searchQuery, ignoreCase = true)
                        }

                        ModuleCardList(
                            modules = filteredModules,
                            assignments = state.moduleAssignments,
                            departments = state.departments,
                            roles = state.roles,
                            onOpenAssignModal = { module, existing ->
                                onWriteEvent(DynamicRbacUiEvent.OpenAssignModal(module, existing))
                            },
                            onRemoveAssignment = { module, assignKey ->
                                onWriteEvent(DynamicRbacUiEvent.RemoveDepartmentAssignment(module, assignKey))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    RbacViewMode.PER_DEPARTMENT -> {
                        DepartmentCardList(
                            departments = state.departments,
                            assignments = state.moduleAssignments,
                            roles = state.roles,
                            onOpenAssignModal = { dept, existing, initialMod ->
                                onWriteEvent(
                                    DynamicRbacUiEvent.OpenAssignModuleModal(
                                        department = dept,
                                        existing = existing,
                                        initialModule = initialMod
                                    )
                                )
                            },
                            onRemoveAssignment = { module, assignKey ->
                                onWriteEvent(DynamicRbacUiEvent.RemoveDepartmentAssignment(module, assignKey))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    RbacViewMode.PER_ROLE -> {
                        val filteredRoles = state.roles.filter { role ->
                            state.searchQuery.isBlank() ||
                                role.name.contains(state.searchQuery, ignoreCase = true) ||
                                role.description.contains(state.searchQuery, ignoreCase = true)
                        }

                        RoleCardList(
                            roles = filteredRoles,
                            departments = state.departments,
                            assignments = state.moduleAssignments,
                            onOpenAssignModal = { role, dept, existing, initialMod ->
                                onWriteEvent(
                                    DynamicRbacUiEvent.OpenAssignModuleModal(
                                        role = role,
                                        department = dept,
                                        existing = existing,
                                        initialModule = initialMod
                                    )
                                )
                            },
                            onRemoveAssignment = { module, assignKey ->
                                onWriteEvent(DynamicRbacUiEvent.RemoveDepartmentAssignment(module, assignKey))
                            },
                            modifier = Modifier.weight(1f)
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
            departments = state.departments,
            selectedDepartmentId = state.newRoleDepartmentId,
            onInputsChanged = { name, desc, template, department ->
                viewModel.onEvent(
                    DynamicRbacUiEvent.UpdateNewRoleInputs(name, desc, template, department)
                )
            },
            onConfirm = { onWriteEvent(DynamicRbacUiEvent.ConfirmCreateRole) },
            onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.CloseCreateModal) }
        )

        // Assign Department Modal Dialog (Dibuka dari Tab Per Modul)
        AssignDepartmentModal(
            isOpen = state.isAssignModalOpen,
            module = state.activeAssignModule,
            departments = state.departments,
            roles = state.roles,
            initialAssignment = state.editingAssignment,
            onConfirm = { assignment ->
                val activeMod = state.activeAssignModule ?: return@AssignDepartmentModal
                onWriteEvent(
                    DynamicRbacUiEvent.SaveDepartmentAssignment(
                        module = activeMod,
                        assignment = assignment,
                        existingAssignmentKey = state.editingAssignment?.assignmentKey
                    )
                )
            },
            onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.CloseAssignModal) }
        )

        // Assign Module Modal Dialog (Dibuka dari Tab Per Divisi atau Tab Per Jabatan)
        AssignModuleModal(
            isOpen = state.isAssignModuleModalOpen,
            department = state.activeAssignDepartment,
            role = state.activeAssignRole,
            departments = state.departments,
            roles = state.roles,
            initialAssignment = state.editingAssignment,
            initialModule = state.editingAssignModule,
            onConfirm = { module, assignment ->
                onWriteEvent(
                    DynamicRbacUiEvent.SaveDepartmentAssignment(
                        module = module,
                        assignment = assignment,
                        existingAssignmentKey = state.editingAssignment?.assignmentKey
                    )
                )
            },
            onDismiss = { viewModel.onEvent(DynamicRbacUiEvent.CloseAssignModuleModal) }
        )
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
