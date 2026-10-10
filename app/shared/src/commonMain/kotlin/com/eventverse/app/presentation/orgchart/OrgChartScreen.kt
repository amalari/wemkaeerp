package com.eventverse.app.presentation.orgchart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentColor
import com.eventverse.app.domain.orgchart.DepartmentTier
import com.eventverse.app.domain.orgchart.HeadSuccessionAction
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.presentation.pack.ActiveTenantPack
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.api.OrgChartApiClient
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.orgchart.components.TShapeChartView
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.workspace.badgeLabel
import com.eventverse.app.presentation.workspace.tint

@Composable
fun OrgChartScreen(
    tenantSlug: String,
    access: ModuleAccessConfig = ModuleAccessConfig(AccessLevel.MANAGE),
    viewerDepartmentId: String? = null,
    viewerEmployeeId: String? = null,
    viewModel: OrgChartViewModel = remember(tenantSlug, access, viewerDepartmentId, viewerEmployeeId) {
        OrgChartViewModel(
            tenantSlug = tenantSlug,
            apiClient = OrgChartApiClient(),
            access = access,
            viewerDepartmentId = viewerDepartmentId,
            viewerEmployeeId = viewerEmployeeId
        )
    },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var confirmRestore by remember { mutableStateOf(false) }

    // OPERATE boleh menambah dan mengubah; MANAGE juga boleh menghapus, mengarsipkan, dan memulihkan
    // preset. Tanpa pembedaan ini, "Hanya Lihat" hanya berarti menunya terlihat.
    val canWrite = access.canWrite
    val canManage = access.canManage

    val formPanel: @Composable (Modifier) -> Unit = { m ->
        EmployeeFormPanel(
            state = state,
            onNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateName(it)) },
            onEmailChange = { viewModel.onEvent(OrgChartUiEvent.UpdateEmail(it)) },
            onPhoneChange = { viewModel.onEvent(OrgChartUiEvent.UpdatePhone(it)) },
            onDeptChange = { viewModel.onEvent(OrgChartUiEvent.SelectDepartment(it)) },
            onSelectDireksi = { viewModel.onEvent(OrgChartUiEvent.SelectDireksi) },
            onTierChange = { viewModel.onEvent(OrgChartUiEvent.SelectTier(it)) },
            onLevelChange = { viewModel.onEvent(OrgChartUiEvent.SelectLevel(it)) },
            onSuperiorChange = { viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(it)) },
            onRoleTitleChange = { viewModel.onEvent(OrgChartUiEvent.UpdateRoleTitle(it)) },
            onSuccessionActionChange = { viewModel.onEvent(OrgChartUiEvent.SelectSuccessionAction(it)) },
            onSave = { viewModel.onEvent(OrgChartUiEvent.SaveEmployee) },
            onReset = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
            onAddDepartmentClick = { viewModel.onEvent(OrgChartUiEvent.OpenCreateDeptModal) },
            onEditDepartmentClick = { viewModel.onEvent(OrgChartUiEvent.OpenEditDeptModal(it)) },
            onAddTierClick = { viewModel.onEvent(OrgChartUiEvent.OpenAddTierModal) },
            onEditTierClick = { deptId, tier -> viewModel.onEvent(OrgChartUiEvent.OpenEditTierModal(deptId, tier)) },
            onDeleteEmployee = { viewModel.onEvent(OrgChartUiEvent.RequestArchiveEmployee(it)) },
            onDeleteDepartment = { viewModel.onEvent(OrgChartUiEvent.RequestArchiveDepartment(it)) },
            canWrite = canWrite,
            canManage = canManage,
            modifier = m
        )
    }
    val chartPanel: @Composable (Modifier) -> Unit = { m ->
        ChartPreviewPanel(
            state = state,
            onSelectNode = { viewModel.onEvent(OrgChartUiEvent.SelectExistingEmployee(it)) },
            onDeptChange = { viewModel.onEvent(OrgChartUiEvent.SelectDepartment(it)) },
            onSelectDireksi = { viewModel.onEvent(OrgChartUiEvent.SelectDireksi) },
            onAddNewEmployee = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
            onAddDepartment = { viewModel.onEvent(OrgChartUiEvent.OpenCreateDeptModal) },
            onRestorePresets = { viewModel.onEvent(OrgChartUiEvent.RestoreDefaultPresets) },
            onToggleArchived = { viewModel.onEvent(OrgChartUiEvent.ToggleArchivedPanel) },
            showArchivedPanel = state.showArchivedPanel,
            canWrite = canWrite,
            canManage = canManage,
            modifier = m
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.Background)
    ) {
        // Sempit: seluruh halaman (header + panel) di-scroll sebagai satu kolom dan panel bertinggi tetap.
        // Dua panel berdampingan butuh ~880dp (form 420 + bagan); header yang membungkus bisa makan >400dp
        // di layar telepon, jadi men-scroll hanya area panel menyisakan jendela kecil.
        val compact = maxWidth < OrgChartSideBySideMinWidth + 48.dp
        val bodySlot = Modifier.fillMaxWidth()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(24.dp)
        ) {
            // 1. Top Header Bar
            OrgChartHeader(
                // K6: angka hanya bila terbaca dari server; selama memuat/galat chip disembunyikan, bukan 0.
                totalEmployees = state.employees.size.takeIf { state.loadState.isResolved },
                totalDepartments = state.departments.size.takeIf { state.loadState.isResolved },
                isResetMenuOpen = state.isResetMenuOpen,
                accessLevel = access.level,
                isDepartmentLocked = state.isDepartmentLocked,
                lockedDepartmentName = state.selectedDepartment?.displayName,
                onToggleResetMenu = { viewModel.onEvent(OrgChartUiEvent.ToggleResetMenu) },
                onAddNewEmployee = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
                onAddNewDepartment = { viewModel.onEvent(OrgChartUiEvent.OpenCreateDeptModal) },
                onRestorePresets = { confirmRestore = true },
                isLoadFailed = state.loadState is OrgChartLoadState.Failed
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Toast Alert Banner
            OrgChartToastBanner(
                message = state.toastMessage,
                onDismiss = { viewModel.onEvent(OrgChartUiEvent.DismissToast) }
            )

            // 2. Main Split-View Layout (Form Left, Live Chart Right); memuat/galat menggantikan seluruhnya
            val loadState = state.loadState
            if (loadState is OrgChartLoadState.Loading) {
                OrgChartLoadingView(modifier = if (compact) bodySlot.height(OrgChartStackedPanelHeight / 2) else Modifier.weight(1f))
            } else if (loadState is OrgChartLoadState.Failed) {
                OrgChartFailedView(
                    message = loadState.message,
                    onRetry = { viewModel.onEvent(OrgChartUiEvent.Reload) },
                    modifier = if (compact) bodySlot.height(OrgChartStackedPanelHeight / 2) else Modifier.weight(1f)
                )
            } else if (!compact) Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (canWrite) formPanel(Modifier.width(420.dp))
                chartPanel(Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Panel bertinggi tetap: isinya scroll sendiri, dan scroll bersarang tanpa tinggi tetap akan crash.
                chartPanel(bodySlot.height(OrgChartStackedPanelHeight))
                if (canWrite) formPanel(bodySlot.height(OrgChartStackedPanelHeight))
            }
        }

        OrgChartRestoreConfirmDialog(
            isOpen = confirmRestore,
            onConfirm = {
                confirmRestore = false
                viewModel.onEvent(OrgChartUiEvent.RestoreDefaultPresets)
            },
            onDismiss = { confirmRestore = false }
        )

        // Modal Dialog: Buat Divisi Baru
        CreateDepartmentDialog(
            isOpen = state.isCreateDeptModalOpen,
            nameInput = state.newDeptNameInput,
            shortNameInput = state.newDeptShortNameInput,
            selectedColorHex = state.newDeptColorHex,
            availableColors = state.availableColorsForNewDept,
            onNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateNewDeptName(it)) },
            onShortNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateNewDeptShortName(it)) },
            onColorSelect = { viewModel.onEvent(OrgChartUiEvent.SelectNewDeptColor(it)) },
            onSave = { viewModel.onEvent(OrgChartUiEvent.SaveNewDepartment) },
            onDismiss = { viewModel.onEvent(OrgChartUiEvent.CloseCreateDeptModal) }
        )

        // Modal Dialog: Tambah Tingkat Wewenang Baru
        AddTierDialog(
            isOpen = state.isAddTierModalOpen,
            tierNameInput = state.newTierNameInput,
            departmentName = state.selectedDepartment?.displayName ?: state.activeDepartment.displayName,
            onNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateNewTierName(it)) },
            onSave = { viewModel.onEvent(OrgChartUiEvent.SaveNewDepartmentTier) },
            onDismiss = { viewModel.onEvent(OrgChartUiEvent.CloseAddTierModal) }
        )

        // Modal Dialog: Edit Divisi
        EditDepartmentDialog(
            isOpen = state.isEditDeptModalOpen,
            nameInput = state.editDeptNameInput,
            shortNameInput = state.editDeptShortNameInput,
            selectedColorHex = state.editDeptColorHex,
            availableColors = state.availableColorsForNewDept,
            onNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateEditDeptName(it)) },
            onShortNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateEditDeptShortName(it)) },
            onColorSelect = { viewModel.onEvent(OrgChartUiEvent.SelectEditDeptColor(it)) },
            onSave = { viewModel.onEvent(OrgChartUiEvent.SaveEditedDepartment) },
            onDismiss = { viewModel.onEvent(OrgChartUiEvent.CloseEditDeptModal) }
        )

        // Modal Dialog: Edit Tingkat Wewenang
        EditTierDialog(
            isOpen = state.isEditTierModalOpen,
            tierNameInput = state.editTierNameInput,
            onNameChange = { viewModel.onEvent(OrgChartUiEvent.UpdateEditTierName(it)) },
            onSave = { viewModel.onEvent(OrgChartUiEvent.SaveEditedDepartmentTier) },
            onDismiss = { viewModel.onEvent(OrgChartUiEvent.CloseEditTierModal) }
        )

        // Modal Dialog: Konfirmasi Arsip Karyawan / Divisi
        DeleteConfirmationDialog(
            isOpen = state.isDeleteDialogOpen,
            targetType = state.deleteTargetType,
            targetName = state.deleteTargetName,
            warningNote = state.deleteWarningNote,
            isBlocked = state.isDeleteBlocked,
            onConfirm = { viewModel.onEvent(OrgChartUiEvent.ConfirmDelete) },
            onDismiss = { viewModel.onEvent(OrgChartUiEvent.CancelDelete) }
        )

        // Modal Dialog: Konflik Email Karyawan (Aktif / Arsip)
        EmailConflictDialog(
            conflict = state.emailConflictModal,
            onRestore = { empId -> viewModel.onEvent(OrgChartUiEvent.RestoreEmployeeFromConflictModal(empId)) },
            onDismiss = { viewModel.onEvent(OrgChartUiEvent.DismissEmailConflictModal) }
        )

        // Panel Arsip (Odoo-style, dimunculkan saat showArchivedPanel = true)
        if (state.showArchivedPanel) {
            ArchivedPanel(
                archivedEmployees = state.archivedEmployees,
                archivedDepartments = state.archivedDepartments,
                isLoading = state.isLoadingArchived,
                onRestoreEmployee = { viewModel.onEvent(OrgChartUiEvent.RestoreEmployee(it)) },
                onRestoreDepartment = { viewModel.onEvent(OrgChartUiEvent.RestoreDepartment(it)) },
                onClose = { viewModel.onEvent(OrgChartUiEvent.ToggleArchivedPanel) },
                canManage = canManage
            )
        }
    }
}

@Composable
private fun ArchivedPanel(
    archivedEmployees: List<OrgNode>,
    archivedDepartments: List<Department>,
    isLoading: Boolean,
    onRestoreEmployee: (String) -> Unit,
    onRestoreDepartment: (String) -> Unit,
    canManage: Boolean = true,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(420.dp)
                    .claySurface(
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        shadowX = -ClayOffset.Rest,
                        shadowY = 0.dp,
                        offset = ClayOffset.Rest,
                        borderWidth = ClayBorder.Thick
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(ClaySpacing.Xl)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            IconArchive(modifier = Modifier.size(20.dp), color = WeMadeColors.OnSurface)
                            Text(
                                text = "Data Terarsip",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = WeMadeColors.OnSurface
                            )
                        }
                        ClayIconButton(
                            onClick = onClose,
                            size = 32.dp,
                            offset = ClayOffset.Pressed
                        ) {
                            IconClose(modifier = Modifier.size(14.dp), color = WeMadeColors.OnSurface)
                        }
                    }

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().padding(ClaySpacing.Xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = WeMadeColors.Primary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                        ) {
                            if (archivedEmployees.isNotEmpty()) {
                                item {
                                    Text(
                                        "Karyawan (${archivedEmployees.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = WeMadeColors.OnSurfaceMuted,
                                        modifier = Modifier.padding(top = ClaySpacing.Md)
                                    )
                                }
                                items(archivedEmployees) { emp ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clayFlat(
                                                shape = ClayShapes.Chip,
                                                background = WeMadeColors.WarningBg,
                                                outline = WeMadeColors.Warning.copy(alpha = 0.4f),
                                                borderWidth = ClayBorder.Medium
                                            )
                                            .padding(ClaySpacing.Lg)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(emp.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = WeMadeColors.OnSurface)
                                                Text(emp.roleTitle, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                                Text(emp.department?.displayName ?: "Direksi", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                            }
                                            Spacer(Modifier.width(ClaySpacing.Md))
                                            ClayButton(
                                                text = "Pulihkan",
                                                leading = { IconRestore(modifier = Modifier.size(12.dp), color = WeMadeColors.Primary) },
                                                onClick = { onRestoreEmployee(emp.id.value) },
                                                enabled = canManage,
                                                style = ClayButtonStyle.Secondary,
                                                offset = ClayOffset.Pressed,
                                                fontSize = 11.sp,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (archivedDepartments.isNotEmpty()) {
                                item {
                                    Text(
                                        "Divisi (${archivedDepartments.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = WeMadeColors.OnSurfaceMuted,
                                        modifier = Modifier.padding(top = ClaySpacing.Xl)
                                    )
                                }
                                items(archivedDepartments) { dept ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clayFlat(
                                                shape = ClayShapes.Chip,
                                                background = WeMadeColors.SuccessBg,
                                                outline = WeMadeColors.Success.copy(alpha = 0.4f),
                                                borderWidth = ClayBorder.Medium
                                            )
                                            .padding(ClaySpacing.Lg)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(dept.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = WeMadeColors.OnSurface)
                                                Text(dept.shortName, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                            }
                                            Spacer(Modifier.width(ClaySpacing.Md))
                                            ClayButton(
                                                text = "Pulihkan",
                                                leading = { IconRestore(modifier = Modifier.size(12.dp), color = WeMadeColors.Primary) },
                                                enabled = canManage,
                                                onClick = { onRestoreDepartment(dept.id.value) },
                                                style = ClayButtonStyle.Secondary,
                                                offset = ClayOffset.Pressed,
                                                fontSize = 11.sp,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (archivedEmployees.isEmpty() && archivedDepartments.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Xxl),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconArchive(modifier = Modifier.size(44.dp), color = WeMadeColors.OnSurfaceMuted)
                                            Spacer(Modifier.height(ClaySpacing.Md))
                                            Text("Tidak ada data yang diarsipkan.", color = WeMadeColors.OnSurfaceMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeFormPanel(
    state: OrgChartUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDeptChange: (Department) -> Unit,
    onSelectDireksi: () -> Unit,
    onTierChange: (DepartmentTier) -> Unit,
    onLevelChange: (HierarchyLevel) -> Unit,
    onSuperiorChange: (String?) -> Unit,
    onRoleTitleChange: (String) -> Unit,
    onSuccessionActionChange: (HeadSuccessionAction) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onAddDepartmentClick: () -> Unit,
    onEditDepartmentClick: (Department) -> Unit,
    onAddTierClick: () -> Unit,
    onEditTierClick: (String, DepartmentTier) -> Unit,
    onDeleteEmployee: (String) -> Unit = {},
    onDeleteDepartment: (Department) -> Unit = {},
    /** OPERATE: boleh menambah & mengubah. */
    canWrite: Boolean = true,
    /** MANAGE: boleh mengarsipkan & menghapus. */
    canManage: Boolean = true,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxHeight(),
        shape = ClayShapes.Panel,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        offset = ClayOffset.Rest,
        contentPadding = PaddingValues(ClaySpacing.Xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val activeDeptColor = state.selectedDepartment?.let { Color(it.colorHex) } ?: WeMadeColors.Primary

            // Header Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isCreatingNew) "Input Karyawan Baru" else "Edit Profil Karyawan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                if (!state.isCreatingNew) {
                    ClayButton(
                        text = "+ Karyawan Baru",
                        onClick = onReset,
                        style = ClayButtonStyle.Secondary,
                        offset = ClayOffset.Pressed,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // 1. Input Nama & Kontak
            ClayTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                label = "Nama Lengkap Karyawan",
                placeholder = "Contoh: Budi Santoso",
                leadingIcon = { IconUser(modifier = Modifier.size(16.dp)) },
                focusColor = activeDeptColor,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                ClayTextField(
                    value = state.emailInput,
                    onValueChange = onEmailChange,
                    label = "Email Perusahaan",
                    placeholder = "contoh@wemade.id",
                    leadingIcon = { IconMail(modifier = Modifier.size(16.dp)) },
                    focusColor = activeDeptColor,
                    modifier = Modifier.weight(1f)
                )
                ClayTextField(
                    value = state.phoneInput,
                    onValueChange = onPhoneChange,
                    label = "No. WhatsApp",
                    placeholder = "08123456789",
                    leadingIcon = { IconPhone(modifier = Modifier.size(16.dp)) },
                    focusColor = activeDeptColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // ─── JALUR CEPAT: PILIH UNDER SIAPA (OTOMATIS ISI DIVISI & WEWENANG) ───
            var isUnderSiapaMenuOpen by remember { mutableStateOf(false) }
            val currentSuperiorNode = state.employees.find { it.id.value == state.selectedReportsToId }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(ClaySpacing.Lg)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            IconZap(modifier = Modifier.size(14.dp), color = activeDeptColor)
                            Text(
                                text = "Jalur Cepat: Under Siapa?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }
                        Text(
                            text = "Otomatis isi divisi & wewenang",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(ClaySpacing.Md))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.Surface,
                                    outline = if (currentSuperiorNode != null) activeDeptColor else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Medium
                                )
                                .clickable { isUnderSiapaMenuOpen = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    if (currentSuperiorNode != null) {
                                        Text(
                                            text = "Under: ${currentSuperiorNode.name}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WeMadeColors.OnSurface
                                        )
                                        val supDeptLabel = currentSuperiorNode.department?.shortName ?: "Direksi"
                                        Text(
                                            text = "${currentSuperiorNode.roleTitle} • Divisi $supDeptLabel",
                                            fontSize = 10.sp,
                                            color = activeDeptColor
                                        )
                                    } else if (state.selectedLevel == HierarchyLevel.EXECUTIVE) {
                                        Text(
                                            text = "Level Puncak (Tanpa Atasan / Direksi)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WeMadeColors.Primary
                                        )
                                        Text(
                                            text = "Berdiri sendiri di puncak hirarki (Tanpa Divisi)",
                                            fontSize = 10.sp,
                                            color = WeMadeColors.OnSurfaceMuted
                                        )
                                    } else {
                                        Text(
                                            text = "Pilih Atasan / Under Siapa...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = WeMadeColors.OnSurfaceMuted
                                        )
                                    }
                                }
                                IconChevronDown(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
                            }
                        }

                        DropdownMenu(
                            expanded = isUnderSiapaMenuOpen,
                            onDismissRequest = { isUnderSiapaMenuOpen = false }
                        ) {
                            state.companyLeaders.forEach { leader ->
                                val isLeaderSelected = leader.id.value == state.selectedReportsToId
                                val isLeaderExec = leader.level == HierarchyLevel.EXECUTIVE || leader.department == null
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                                            ) {
                                                Text(
                                                    leader.name,
                                                    fontWeight = if (isLeaderSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = if (isLeaderSelected) activeDeptColor else WeMadeColors.OnSurface
                                                )
                                                ClayTag(
                                                    text = if (isLeaderExec) "DIREKSI" else (leader.department?.shortName ?: ""),
                                                    tint = if (isLeaderExec) WeMadeColors.Primary else WeMadeColors.Warning,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            val subtitle = if (isLeaderExec) {
                                                "${leader.roleTitle} → Melapor ke Direksi (Bebas pilih divisi)"
                                            } else {
                                                "${leader.roleTitle} → Otomatis Divisi ${leader.department?.shortName ?: ""}"
                                            }
                                            Text(
                                                subtitle,
                                                fontSize = 10.sp,
                                                color = WeMadeColors.OnSurfaceMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSuperiorChange(leader.id.value)
                                        isUnderSiapaMenuOpen = false
                                    }
                                )
                            }
                            HorizontalDivider(color = WeMadeColors.Border)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "Tanpa Atasan (Direksi / Puncak)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = WeMadeColors.Primary
                                        )
                                        Text("Otomatis ubah wewenang menjadi Direksi", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                                    }
                                },
                                onClick = {
                                    onSuperiorChange(null)
                                    isUnderSiapaMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // 3. Divisi Penempatan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Divisi Penempatan:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    val currentDept = state.selectedDepartment
                    if (currentDept != null) {
                        ClayButton(
                            text = "Edit Divisi",
                            leading = { IconEdit(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurface) },
                            onClick = { onEditDepartmentClick(currentDept) },
                            enabled = canWrite,
                            style = ClayButtonStyle.Secondary,
                            offset = ClayOffset.Pressed,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        )
                        if (state.departments.size > 1) {
                            ClayButton(
                                text = "Arsipkan",
                                leading = { IconArchive(modifier = Modifier.size(12.dp), color = WeMadeColors.Warning) },
                                onClick = { onDeleteDepartment(currentDept) },
                                enabled = canManage,
                                style = ClayButtonStyle.Secondary,
                                offset = ClayOffset.Pressed,
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    ClayButton(
                        text = "+ Divisi Baru",
                        onClick = onAddDepartmentClick,
                        enabled = canWrite,
                        style = ClayButtonStyle.Secondary,
                        offset = ClayOffset.Pressed,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            // Tampilan Pilihan Divisi (Selalu Aktif & Editable)
            if (state.departments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.BackgroundWarm,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Lg)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada divisi terdaftar.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                        ClayButton(
                            text = "+ Buat Divisi Pertama",
                            onClick = onAddDepartmentClick,
                            style = ClayButtonStyle.Primary,
                            offset = ClayOffset.Small,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                val deptScrollState = rememberScrollState()
                val coroutineScope = rememberCoroutineScope()
                val canScrollBack = deptScrollState.canScrollBackward
                val canScrollFwd = deptScrollState.canScrollForward
                val showArrows = canScrollBack || canScrollFwd || state.departments.size > 4

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    if (showArrows) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clayFlat(
                                    shape = CircleShape,
                                    background = if (canScrollBack) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                                    outline = if (canScrollBack) WeMadeColors.Outline else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Medium
                                )
                                .clickable(enabled = canScrollBack) {
                                    coroutineScope.launch {
                                        deptScrollState.animateScrollTo(
                                            (deptScrollState.value - 140).coerceAtLeast(0)
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                val strokeColor = if (canScrollBack) WeMadeColors.OnSurface else WeMadeColors.Border
                                val path = Path().apply {
                                    moveTo(size.width * 0.65f, 0f)
                                    lineTo(size.width * 0.25f, size.height * 0.5f)
                                    lineTo(size.width * 0.65f, size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }

                    // Deretan Chip Divisi
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(deptScrollState),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chip Opsi: Direksi (Tanpa Divisi)
                        val isDireksiSelected = state.selectedDepartment == null && state.selectedLevel == HierarchyLevel.EXECUTIVE
                        Box(
                            modifier = Modifier
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = if (isDireksiSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                    outline = if (isDireksiSelected) WeMadeColors.Outline else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Medium
                                )
                                .clickable { onSelectDireksi() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Direksi (Non-Divisi)",
                                fontSize = 11.sp,
                                fontWeight = if (isDireksiSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isDireksiSelected) Color.White else WeMadeColors.OnSurface
                            )
                        }

                        state.departments.forEach { dept ->
                            val isSelected = !isDireksiSelected && dept.id == (state.selectedDepartment?.id ?: state.activeDepartment.id)
                            val deptColor = Color(dept.colorHex)
                            Box(
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = if (isSelected) deptColor else WeMadeColors.SurfaceMuted,
                                        outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.Border,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .clickable { onDeptChange(dept) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dept.shortName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else WeMadeColors.OnSurface
                                )
                            }
                        }
                    }

                    if (showArrows) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clayFlat(
                                    shape = CircleShape,
                                    background = if (canScrollFwd) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                                    outline = if (canScrollFwd) WeMadeColors.Outline else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Medium
                                )
                                .clickable(enabled = canScrollFwd) {
                                    coroutineScope.launch {
                                        deptScrollState.animateScrollTo(
                                            (deptScrollState.value + 140).coerceAtMost(deptScrollState.maxValue)
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                val strokeColor = if (canScrollFwd) WeMadeColors.OnSurface else WeMadeColors.Border
                                val path = Path().apply {
                                    moveTo(size.width * 0.35f, 0f)
                                    lineTo(size.width * 0.75f, size.height * 0.5f)
                                    lineTo(size.width * 0.35f, size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xl))

            // ─── 4. TINGKAT WEWENANG DINAMIS (SELALU EDITABLE) ───
            val currentDept = state.selectedDepartment ?: state.activeDepartment
            val availableTiers = state.availableTiersForSelectedDept
            val isDireksiSelected = state.selectedLevel == HierarchyLevel.EXECUTIVE

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.selectedDepartment != null) "Tingkat Wewenang (${currentDept.shortName}):" else "Tingkat Wewenang (Direksi):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
                if (state.selectedDepartment != null) {
                    ClayButton(
                        text = "+ Tambah Tingkat",
                        onClick = onAddTierClick,
                        enabled = canWrite,
                        style = ClayButtonStyle.Secondary,
                        offset = ClayOffset.Pressed,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                // Opsi 1: Direksi (Level Puncak Organisasi)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (isDireksiSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                            outline = if (isDireksiSelected) WeMadeColors.Primary else WeMadeColors.Border,
                            borderWidth = ClayBorder.Medium
                        )
                        .clickable { onSelectDireksi() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        ClayTag(
                            text = "DIREKSI",
                            tint = WeMadeColors.Primary,
                            fontSize = 9.sp
                        )
                        Column {
                            Text(
                                text = "Direksi (Level Puncak Organisasi)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isDireksiSelected) FontWeight.Bold else FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "Membawahi seluruh jajaran divisi dan manajemen pabrik.",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                    RadioButton(
                        selected = isDireksiSelected,
                        onClick = { onSelectDireksi() },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = WeMadeColors.Primary,
                            unselectedColor = WeMadeColors.Border
                        )
                    )
                }

                // Opsi Tingkat Wewenang Divisi
                availableTiers.forEach { tier ->
                    val isSelected = !isDireksiSelected && state.selectedTierName == tier.name
                    val badgeColor = when {
                        tier.isHead -> WeMadeColors.Warning
                        tier.id == "team_lead" || tier.rank == 2 -> WeMadeColors.Success
                        else -> WeMadeColors.OnSurfaceMuted
                    }
                    val badgeLabel = when {
                        tier.isHead -> "KEPALA"
                        tier.id == "team_lead" || tier.rank == 2 -> "LEAD / SPV"
                        else -> "STAF"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                borderWidth = ClayBorder.Medium
                            )
                            .clickable { onTierChange(tier) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                        ) {
                            ClayTag(
                                text = badgeLabel,
                                tint = badgeColor,
                                fontSize = 9.sp
                            )
                            Text(
                                text = tier.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            ClayIconButton(
                                onClick = { onEditTierClick(currentDept.id.value, tier) },
                                size = 26.dp,
                                offset = ClayOffset.Pressed
                            ) {
                                IconEdit(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurface)
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { onTierChange(tier) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = WeMadeColors.Primary,
                                    unselectedColor = WeMadeColors.Border
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // ─── BANNER PERALIHAN SUKSESI KEPALA DIVISI (SINGLE ACTIVE HEAD CONSTRAINT) ───
            val existingHead = state.existingHeadOfSelectedDept
            if (existingHead != null && state.selectedLevel == HierarchyLevel.HEAD_OF_DEPARTMENT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.WarningBg,
                            outline = WeMadeColors.Warning,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Lg)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            ClayTag(
                                text = "PERHATIAN SUKSESI",
                                tint = WeMadeColors.Warning,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "Hanya boleh ada 1 Kepala Divisi aktif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                        val currentDeptName = state.selectedDepartment?.displayName ?: state.activeDepartment.displayName
                        Text(
                            text = "Divisi $currentDeptName saat ini dipimpin oleh '${existingHead.name}'. Pilih tindakan suksesi:",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurface
                        )

                        Spacer(modifier = Modifier.height(ClaySpacing.Md))

                        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                            // Opsi 1: Turunkan ke Staf Senior
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = if (state.successionAction == HeadSuccessionAction.DEMOTE_TO_STAFF) WeMadeColors.Surface else Color.Transparent,
                                        outline = if (state.successionAction == HeadSuccessionAction.DEMOTE_TO_STAFF) WeMadeColors.Warning else Color.Transparent,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .clickable { onSuccessionActionChange(HeadSuccessionAction.DEMOTE_TO_STAFF) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "Alihkan Jabatan Menjadi Staf Senior",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "'${existingHead.name}' tetap di divisi ini dan melapor ke kepala divisi baru.",
                                        fontSize = 10.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                                RadioButton(
                                    selected = state.successionAction == HeadSuccessionAction.DEMOTE_TO_STAFF,
                                    onClick = { onSuccessionActionChange(HeadSuccessionAction.DEMOTE_TO_STAFF) },
                                    colors = RadioButtonDefaults.colors(selectedColor = WeMadeColors.Warning)
                                )
                            }

                            // Opsi 2: Nonaktifkan Akun
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = if (state.successionAction == HeadSuccessionAction.DEACTIVATE) WeMadeColors.Surface else Color.Transparent,
                                        outline = if (state.successionAction == HeadSuccessionAction.DEACTIVATE) WeMadeColors.Warning else Color.Transparent,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .clickable { onSuccessionActionChange(HeadSuccessionAction.DEACTIVATE) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "Nonaktifkan / Lepas dari Jabatan",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "'${existingHead.name}' dipensiunkan/dinonaktifkan dari bagan aktif.",
                                        fontSize = 10.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                                RadioButton(
                                    selected = state.successionAction == HeadSuccessionAction.DEACTIVATE,
                                    onClick = { onSuccessionActionChange(HeadSuccessionAction.DEACTIVATE) },
                                    colors = RadioButtonDefaults.colors(selectedColor = WeMadeColors.Warning)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Lg))
            }

            // 5. Judul Jabatan Operasional
            ClayTextField(
                value = state.roleTitleInput,
                onValueChange = onRoleTitleChange,
                label = "Nama Jabatan / Posisi",
                placeholder = "Contoh: Staff Penjualan & Sampling",
                focusColor = activeDeptColor,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Xl))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.isCreatingNew && state.selectedEmployeeId != null) {
                    ClayButton(
                        text = "Arsipkan",
                        leading = { IconArchive(modifier = Modifier.size(14.dp), color = Color.White) },
                        onClick = { onDeleteEmployee(state.selectedEmployeeId) },
                        enabled = canManage,
                        style = ClayButtonStyle.Danger,
                        offset = ClayOffset.Small,
                        modifier = Modifier.weight(1f)
                    )
                }

                ClayButton(
                    text = if (state.isCreatingNew) "Simpan ke Bagan Organisasi" else "Perbarui Karyawan",
                    onClick = onSave,
                    enabled = canWrite && state.nameInput.isNotBlank(),
                    style = ClayButtonStyle.Primary,
                    offset = ClayOffset.Small,
                    modifier = Modifier.weight(if (!state.isCreatingNew && state.selectedEmployeeId != null) 1.2f else 1f)
                )
            }
        }
    }
}

@Composable
private fun ChartPreviewPanel(
    state: OrgChartUiState,
    onSelectNode: (String) -> Unit,
    onDeptChange: (Department) -> Unit = {},
    onSelectDireksi: () -> Unit = {},
    onAddNewEmployee: () -> Unit,
    onAddDepartment: () -> Unit,
    onRestorePresets: () -> Unit,
    onToggleArchived: () -> Unit,
    showArchivedPanel: Boolean,
    canWrite: Boolean = true,
    canManage: Boolean = true,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxHeight(),
        shape = ClayShapes.Panel,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        offset = ClayOffset.Rest,
        contentPadding = PaddingValues(ClaySpacing.Xl)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Chart Panel Header
            ClayFlowRow(
                modifier = Modifier.fillMaxWidth(),
                spacing = ClaySpacing.Md,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    ClayFlowRow {
                        Text(
                            text = "Pratinjau Struktur Organisasi (Live Org Chart)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        if (state.isDepartmentLocked && state.selectedDepartment != null) {
                            ClayTag(
                                text = "Divisi: ${state.selectedDepartment.displayName}",
                                tint = WeMadeColors.Primary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = if (state.isDepartmentLocked && state.selectedDepartment != null) {
                            "Menampilkan bagan organisasi divisi ${state.selectedDepartment.displayName} (Terkunci sesuai batasan wewenang Anda)"
                        } else {
                            "Menampilkan fokus: 1 Tingkat ke Atas (Approval) & Seluruh Anggota Divisi"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                ClayFlowRow {
                    if (canManage) {
                        ClayButton(
                            text = if (showArchivedPanel) "Tutup Arsip" else "Lihat Arsip",
                            leading = { IconArchive(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurface) },
                            onClick = onToggleArchived,
                            style = ClayButtonStyle.Secondary,
                            offset = ClayOffset.Pressed,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    LegendTag("Direksi", WeMadeColors.Primary)
                    LegendTag("Head", WeMadeColors.Warning)
                    LegendTag("Staf", WeMadeColors.OnSurfaceMuted)
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            // 2. Navigasi Pilihan Divisi & Direksi
            // Tanpa divisi dan tanpa karyawan tidak ada yang bisa dipilih: pill "Direksi" sendirian menyesatkan.
            if (state.departments.isNotEmpty() || state.employees.isNotEmpty()) DivisionSelectorTabs(
                departments = state.departments,
                selectedDepartment = state.selectedDepartment,
                isDireksiSelected = state.selectedDepartment == null && state.selectedLevel == HierarchyLevel.EXECUTIVE,
                employees = state.employees,
                onSelectDireksi = onSelectDireksi,
                onSelectDepartment = onDeptChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Md))
            HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            // The Rendered T-Shape Tree View or Empty State
            if (state.employees.isEmpty()) {
                val workplace = ActiveTenantPack.current.term(VocabularyKey.WORKPLACE)
                OrgChartEmptyState(
                    message = when {
                        state.isDepartmentLocked ->
                            "Belum ada karyawan yang terdaftar di divisi ${state.selectedDepartment?.displayName ?: "ini"}."
                        state.departments.isEmpty() ->
                            "Belum ada divisi dan karyawan di $workplace ini. Buat divisi pertama, atau muat contoh bila tersedia."
                        else -> "Belum ada karyawan yang terdaftar di $workplace ini."
                    },
                    createLabel = if (state.departments.isEmpty()) "+ Buat Divisi Pertama" else "+ Tambah Karyawan Pertama",
                    canCreate = canWrite,
                    canLoadSample = canManage && !state.isDepartmentLocked,
                    isLoadingSample = state.isRestoringPresets,
                    onCreate = if (state.departments.isEmpty()) onAddDepartment else onAddNewEmployee,
                    onLoadSample = onRestorePresets
                )
            } else {
                TShapeChartView(
                    result = state.resolvedHierarchy,
                    onSelectNode = onSelectNode,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DivisionSelectorTabs(
    departments: List<Department>,
    selectedDepartment: Department?,
    isDireksiSelected: Boolean,
    employees: List<OrgNode>,
    onSelectDireksi: () -> Unit,
    onSelectDepartment: (Department) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            IconLayers(modifier = Modifier.size(14.dp), color = WeMadeColors.Primary)
            Text(
                text = "Pilih Bagan Divisi:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        }

        // Chip Direksi (Non-Divisi)
        val execCount = employees.count { it.level == HierarchyLevel.EXECUTIVE || it.department == null }
        Box(
            modifier = Modifier
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = if (isDireksiSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                    outline = if (isDireksiSelected) WeMadeColors.Outline else WeMadeColors.Border,
                    borderWidth = ClayBorder.Medium
                )
                .clickable { onSelectDireksi() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Text(
                    text = "Direksi (Pimpinan Puncak)",
                    fontSize = 11.sp,
                    fontWeight = if (isDireksiSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isDireksiSelected) Color.White else WeMadeColors.OnSurface
                )
                if (execCount > 0) {
                    ClayTag(
                        text = "$execCount",
                        tint = if (isDireksiSelected) Color.White.copy(alpha = 0.9f) else WeMadeColors.Primary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Chips for each Department
        departments.forEach { dept ->
            val isSelected = !isDireksiSelected && dept.id == selectedDepartment?.id
            val deptColor = Color(dept.colorHex)
            val deptCount = employees.count { it.department?.id == dept.id }

            Box(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = if (isSelected) deptColor else WeMadeColors.Surface,
                        outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.Border,
                        borderWidth = ClayBorder.Medium
                    )
                    .clickable { onSelectDepartment(dept) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    if (!isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(deptColor)
                        )
                    }
                    Text(
                        text = dept.displayName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else WeMadeColors.OnSurface
                    )
                    if (deptCount > 0) {
                        ClayTag(
                            text = "$deptCount",
                            tint = if (isSelected) Color.White.copy(alpha = 0.9f) else deptColor,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateDepartmentDialog(
    isOpen: Boolean,
    nameInput: String,
    shortNameInput: String,
    selectedColorHex: Long,
    availableColors: List<DepartmentColor>,
    onNameChange: (String) -> Unit,
    onShortNameChange: (String) -> Unit,
    onColorSelect: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Tambah Divisi Organisasi Baru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Sesuaikan departemen dengan alur kerja pabrik Anda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                // Input Nama Lengkap Divisi
                ClayTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = "Nama Lengkap Divisi",
                    placeholder = "Cth: Bordir & Sablon Printing",
                    modifier = Modifier.fillMaxWidth()
                )

                // Input Nama Singkat / Label Badge
                ClayTextField(
                    value = shortNameInput,
                    onValueChange = onShortNameChange,
                    label = "Label Singkat / Badge (Maks. 10 karakter)",
                    placeholder = "Cth: Bordir",
                    modifier = Modifier.fillMaxWidth()
                )

                // Palet Pilihan Warna Tema Divisi (Dinamis: warna yang sudah dipakai divisi lain tidak muncul)
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pilih Warna Tema Divisi:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${availableColors.size} warna bebas pakai",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { deptColor ->
                            val isColorSelected = selectedColorHex == deptColor.hex
                            val col = Color(deptColor.hex)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clayFlat(
                                        shape = CircleShape,
                                        background = col,
                                        outline = if (isColorSelected) WeMadeColors.Outline else col.copy(alpha = 0.5f),
                                        borderWidth = if (isColorSelected) ClayBorder.Thick else ClayBorder.Hairline
                                    )
                                    .clickable { onColorSelect(deptColor.hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clayFlat(shape = CircleShape, background = Color.White, outline = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Preview Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pratinjau Tampilan Badge:",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

                    ClayTag(
                        text = shortNameInput.ifBlank { "Label" },
                        tint = Color(selectedColorHex),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            ClayButton(
                text = "Buat Divisi",
                onClick = onSave,
                enabled = nameInput.isNotBlank(),
                style = ClayButtonStyle.Primary,
                offset = ClayOffset.Small
            )
        },
        dismissButton = {
            ClayButton(
                text = "Batal",
                onClick = onDismiss,
                style = ClayButtonStyle.Ghost,
                offset = ClayOffset.Pressed
            )
        },
        containerColor = WeMadeColors.Surface
    )
}

@Composable
private fun AddTierDialog(
    isOpen: Boolean,
    tierNameInput: String,
    departmentName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Tambah Tingkat Wewenang",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Tingkat wewenang khusus untuk divisi: $departmentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayTextField(
                    value = tierNameInput,
                    onValueChange = onNameChange,
                    label = "Nama Tingkat Wewenang",
                    placeholder = "Cth: Mandor Jahit / QC Lead / Helper",
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "💡 Tingkat ini akan otomatis terdaftar sebagai opsi hirarki dinamis khusus pada divisi $departmentName.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            ClayButton(
                text = "Simpan Tingkat",
                onClick = onSave,
                enabled = tierNameInput.isNotBlank(),
                style = ClayButtonStyle.Primary,
                offset = ClayOffset.Small
            )
        },
        dismissButton = {
            ClayButton(
                text = "Batal",
                onClick = onDismiss,
                style = ClayButtonStyle.Ghost,
                offset = ClayOffset.Pressed
            )
        },
        containerColor = WeMadeColors.Surface
    )
}

@Composable
private fun DeleteConfirmationDialog(
    isOpen: Boolean,
    targetType: DeleteTargetType,
    targetName: String,
    warningNote: String,
    isBlocked: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val isEmployee = targetType == DeleteTargetType.EMPLOYEE
    val title = if (isEmployee) "Arsipkan Karyawan" else "Arsipkan Divisi"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clayFlat(
                            shape = CircleShape,
                            background = if (isBlocked) WeMadeColors.ErrorBg else WeMadeColors.WarningBg,
                            outline = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBlocked) {
                        IconWarning(modifier = Modifier.size(20.dp), color = WeMadeColors.Error)
                    } else {
                        IconArchive(modifier = Modifier.size(20.dp), color = WeMadeColors.Warning)
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isBlocked) WeMadeColors.Error else WeMadeColors.OnSurface
                    )
                    Text(
                        text = if (isBlocked) "Tindakan Dibatasi Sistem" else "Konfirmasi Pengarsipan",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text(
                    text = if (isEmployee) {
                        "Arsipkan data karyawan \"$targetName\"? Data tetap tersimpan dan bisa dipulihkan kapan saja."
                    } else {
                        "Arsipkan divisi \"$targetName\"? Data tetap tersimpan dan bisa dipulihkan kapan saja."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.OnSurface
                )

                // Warning / Rule Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (isBlocked) WeMadeColors.ErrorBg else WeMadeColors.WarningBg,
                            outline = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                        ClayTag(
                            text = if (isBlocked) "ATURAN INTEGRITAS ERP" else "POLA ODOO ARCHIVE",
                            tint = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning,
                            fontSize = 9.sp
                        )
                        Text(
                            text = warningNote,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isBlocked) {
                ClayButton(
                    onClick = onConfirm,
                    text = "Ya, Arsipkan",
                    style = ClayButtonStyle.Danger
                )
            }
        },
        dismissButton = {
            ClayButton(
                onClick = onDismiss,
                text = if (isBlocked) "Tutup / Mengerti" else "Batal",
                style = ClayButtonStyle.Ghost
            )
        },
        containerColor = WeMadeColors.Surface
    )
}

@Composable
private fun LegendTag(text: String, dotColor: Color) {
    ClayTag(
        text = text,
        tint = dotColor,
        fontSize = 11.sp,
        leading = {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clayFlat(
                        shape = CircleShape,
                        background = dotColor,
                        outline = dotColor
                    )
            )
        }
    )
}

@Composable
private fun EmailConflictDialog(
    conflict: EmailConflictInfo?,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (conflict == null) return

    val isArchived = conflict.isArchived

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                if (isArchived) {
                    IconArchive(modifier = Modifier.size(22.dp), color = WeMadeColors.Warning)
                } else {
                    IconWarning(modifier = Modifier.size(22.dp), color = WeMadeColors.Warning)
                }
                Text(
                    text = if (isArchived) "Email Terdaftar di Arsip" else "Email Sudah Digunakan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = WeMadeColors.OnSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = if (isArchived) {
                        "Alamat email '${conflict.email}' sudah terdaftar dalam sistem, namun karyawan tersebut berstatus DIARSIPKAN (tidak aktif)."
                    } else {
                        "Alamat email '${conflict.email}' saat ini sedang digunakan oleh karyawan aktif lain di tenant ini."
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                // Kartu Profil Pemilik Email
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = conflict.existingEmployeeName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = WeMadeColors.OnSurface
                            )
                            ClayTag(
                                text = if (isArchived) "📦 DIARSIPKAN" else "🟢 AKTIF",
                                tint = if (isArchived) WeMadeColors.Warning else WeMadeColors.Success,
                                fontSize = 10.sp
                            )
                        }

                        HorizontalDivider(color = WeMadeColors.Border)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bagian / Divisi:", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(conflict.existingDepartmentName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurface)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Jabatan:", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(conflict.existingRoleTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurface)
                        }
                    }
                }

                // Kotak Saran / Petunjuk Tindakan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (isArchived) WeMadeColors.WarningBg else WeMadeColors.PrimaryContainer,
                            outline = if (isArchived) WeMadeColors.Warning else WeMadeColors.Info
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconZap(modifier = Modifier.size(14.dp), color = if (isArchived) WeMadeColors.Warning else WeMadeColors.Info)
                        Text(
                            text = if (isArchived) {
                                "Anda dapat langsung memulihkan karyawan ini kembali ke bagan organisasi, atau ganti email di formulir jika ini individu baru."
                            } else {
                                "Satu email hanya dapat digunakan oleh satu akun karyawan. Silakan gunakan alamat email lain pada formulir."
                            },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = if (isArchived) WeMadeColors.Warning else WeMadeColors.Info
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isArchived) {
                ClayButton(
                    onClick = { onRestore(conflict.existingEmployeeId) },
                    text = "Pulihkan Karyawan Ini",
                    leading = { IconRestore(modifier = Modifier.size(14.dp), color = Color.White) },
                    style = ClayButtonStyle.Primary
                )
            } else {
                ClayButton(
                    onClick = onDismiss,
                    text = "Ganti Email Lain",
                    style = ClayButtonStyle.Primary
                )
            }
        },
        dismissButton = {
            if (isArchived) {
                ClayButton(
                    onClick = onDismiss,
                    text = "Gunakan Email Lain",
                    style = ClayButtonStyle.Ghost
                )
            }
        },
        containerColor = WeMadeColors.Surface
    )
}

@Composable
private fun EditDepartmentDialog(
    isOpen: Boolean,
    nameInput: String,
    shortNameInput: String,
    selectedColorHex: Long,
    availableColors: List<DepartmentColor>,
    onNameChange: (String) -> Unit,
    onShortNameChange: (String) -> Unit,
    onColorSelect: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit Divisi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Perbarui nama, nama singkat, dan warna tema divisi",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = "Nama Lengkap Divisi",
                    placeholder = "Cth: Penjualan & Pemasaran",
                    modifier = Modifier.fillMaxWidth()
                )

                ClayTextField(
                    value = shortNameInput,
                    onValueChange = onShortNameChange,
                    label = "Nama Singkat / Label Badge",
                    placeholder = "Cth: Sales",
                    modifier = Modifier.fillMaxWidth()
                )

                // Palet Pilihan Warna
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Text(
                        text = "Pilih Warna Divisi:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { deptColor ->
                            val isColorSelected = selectedColorHex == deptColor.hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clayFlat(
                                        shape = CircleShape,
                                        background = Color(deptColor.hex),
                                        outline = if (isColorSelected) WeMadeColors.OnSurface else WeMadeColors.Outline,
                                        borderWidth = if (isColorSelected) ClayBorder.Thick else ClayBorder.Medium
                                    )
                                    .clickable { onColorSelect(deptColor.hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clayFlat(
                                                shape = CircleShape,
                                                background = WeMadeColors.Surface,
                                                outline = WeMadeColors.Surface
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Preview Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline
                        )
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pratinjau Tampilan Badge:",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

                    ClayTag(
                        text = shortNameInput.ifBlank { "Label" },
                        tint = Color(selectedColorHex),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            ClayButton(
                onClick = onSave,
                text = "Simpan Perubahan",
                style = ClayButtonStyle.Primary,
                enabled = nameInput.isNotBlank()
            )
        },
        dismissButton = {
            ClayButton(
                onClick = onDismiss,
                text = "Batal",
                style = ClayButtonStyle.Ghost
            )
        },
        containerColor = WeMadeColors.Surface
    )
}

@Composable
private fun EditTierDialog(
    isOpen: Boolean,
    tierNameInput: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit Tingkat Wewenang",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Ubah nama tingkat wewenang / jabatan di divisi ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayTextField(
                    value = tierNameInput,
                    onValueChange = onNameChange,
                    label = "Nama Tingkat Wewenang",
                    placeholder = "Cth: Mandor Jahit / QC Lead",
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "💡 Perubahan nama tingkat wewenang akan diterapkan pada struktur hirarki divisi ini.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            ClayButton(
                onClick = onSave,
                text = "Simpan Perubahan",
                style = ClayButtonStyle.Primary,
                enabled = tierNameInput.isNotBlank()
            )
        },
        dismissButton = {
            ClayButton(
                onClick = onDismiss,
                text = "Batal",
                style = ClayButtonStyle.Ghost
            )
        },
        containerColor = WeMadeColors.Surface
    )
}


private val OrgChartSideBySideMinWidth = 880.dp
private val OrgChartStackedPanelHeight = 600.dp
