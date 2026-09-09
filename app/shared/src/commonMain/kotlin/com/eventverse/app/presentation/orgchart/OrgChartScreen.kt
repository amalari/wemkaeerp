package com.eventverse.app.presentation.orgchart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.eventverse.app.infrastructure.api.OrgChartApiClient
import com.eventverse.app.presentation.orgchart.components.TShapeChartView
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun OrgChartScreen(
    tenantSlug: String = "wemade-demo",
    viewModel: OrgChartViewModel = remember(tenantSlug) {
        OrgChartViewModel(tenantSlug = tenantSlug, apiClient = OrgChartApiClient())
    },
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
            // 1. Top Header Bar
            OrgChartHeader(
                totalEmployees = state.employees.size,
                totalDepartments = state.departments.size,
                isResetMenuOpen = state.isResetMenuOpen,
                onToggleResetMenu = { viewModel.onEvent(OrgChartUiEvent.ToggleResetMenu) },
                onAddNewEmployee = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
                onAddNewDepartment = { viewModel.onEvent(OrgChartUiEvent.OpenCreateDeptModal) },
                onClearAllData = { viewModel.onEvent(OrgChartUiEvent.ClearAllDataToEmpty) },
                onRestorePresets = { viewModel.onEvent(OrgChartUiEvent.RestoreDefaultPresets) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Toast Alert Banner
            ToastBanner(
                message = state.toastMessage,
                onDismiss = { viewModel.onEvent(OrgChartUiEvent.DismissToast) }
            )

            // 2. Main Split-View Layout (Form Left, Live Chart Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // SISI KIRI: Form Input Karyawan (40% width)
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
                    modifier = Modifier.width(420.dp)
                )

                // SISI KANAN: Live Org Chart Preview (60% weight)
                ChartPreviewPanel(
                    state = state,
                    onSelectNode = { viewModel.onEvent(OrgChartUiEvent.SelectExistingEmployee(it)) },
                    onAddNewEmployee = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
                    onRestorePresets = { viewModel.onEvent(OrgChartUiEvent.RestoreDefaultPresets) },
                    onToggleArchived = { viewModel.onEvent(OrgChartUiEvent.ToggleArchivedPanel) },
                    showArchivedPanel = state.showArchivedPanel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

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
                onClose = { viewModel.onEvent(OrgChartUiEvent.ToggleArchivedPanel) }
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
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦 Data Terarsip", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onClose) { Text("✕") }
                    }

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (archivedEmployees.isNotEmpty()) {
                                item {
                                    Text(
                                        "Karyawan (${archivedEmployees.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = WeMadeColors.OnSurfaceMuted,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                items(archivedEmployees) { emp ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(emp.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text(emp.roleTitle, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                                Text(emp.department?.displayName ?: "Direksi", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                            }
                                            TextButton(onClick = { onRestoreEmployee(emp.id.value) }) {
                                                Text("Pulihkan", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                            }
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
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                                items(archivedDepartments) { dept ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(dept.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text(dept.shortName, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                            }
                                            TextButton(onClick = { onRestoreDepartment(dept.id.value) }) {
                                                Text("Pulihkan", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                            }
                                        }
                                    }
                                }
                            }

                            if (archivedEmployees.isEmpty() && archivedDepartments.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📭", fontSize = 40.sp)
                                            Spacer(Modifier.height(8.dp))
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
private fun OrgChartHeader(
    totalEmployees: Int,
    totalDepartments: Int,
    isResetMenuOpen: Boolean,
    onToggleResetMenu: () -> Unit,
    onAddNewEmployee: () -> Unit,
    onAddNewDepartment: () -> Unit,
    onClearAllData: () -> Unit,
    onRestorePresets: () -> Unit
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
                    text = "Bagan Struktur Organisasi & Karyawan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "T-Shape Dynamic Org",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.PrimaryDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kelola struktur pelaporan, atur divisi fleksibel sesuai kebutuhan pabrik, atau mulai dari struktur kosong.",
                style = MaterialTheme.typography.bodyMedium,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeaderBadge(label = "Total Karyawan", value = "$totalEmployees Orang")
            HeaderBadge(label = "Divisi Aktif", value = "$totalDepartments Divisi")

            // Tombol Opsi Preset / Mulai Kosong
            Box {
                OutlinedButton(
                    onClick = onToggleResetMenu,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                    border = BorderStroke(1.dp, WeMadeColors.Border),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Opsi Struktur", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                DropdownMenu(
                    expanded = isResetMenuOpen,
                    onDismissRequest = onToggleResetMenu
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Mulai dari Kosong", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFDC2626))
                                Text("Kosongkan semua karyawan & divisi", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        },
                        onClick = onClearAllData
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Muat Template Konveksi", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WeMadeColors.PrimaryDark)
                                Text("Isi dengan 5 divisi & staf contoh", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        },
                        onClick = onRestorePresets
                    )
                }
            }

            // Tombol Tambah Divisi Baru
            OutlinedButton(
                onClick = onAddNewDepartment,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WeMadeColors.PrimaryDark),
                border = BorderStroke(1.dp, WeMadeColors.Primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("+ Divisi Baru", fontWeight = FontWeight.SemiBold)
            }

            // Tombol Tambah Karyawan Baru
            Button(
                onClick = onAddNewEmployee,
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("+ Tambah Karyawan", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeaderBadge(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
        border = BorderStroke(1.dp, WeMadeColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WeMadeColors.PrimaryDark)
            Text(text = label, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
        border = BorderStroke(1.dp, WeMadeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
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
                    TextButton(onClick = onReset) {
                        Text("+ Karyawan Baru", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Input Nama & Kontak
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                label = { Text("Nama Lengkap Karyawan") },
                placeholder = { Text("Contoh: Budi Santoso", color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.emailInput,
                    onValueChange = onEmailChange,
                    label = { Text("Email Perusahaan") },
                    placeholder = { Text("contoh@wemade.id", color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = state.phoneInput,
                    onValueChange = onPhoneChange,
                    label = { Text("No. WhatsApp") },
                    placeholder = { Text("08123456789", color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── JALUR CEPAT: PILIH UNDER SIAPA (OTOMATIS ISI DIVISI & WEWENANG) ───
            var isUnderSiapaMenuOpen by remember { mutableStateOf(false) }
            val currentSuperiorNode = state.employees.find { it.id.value == state.selectedReportsToId }

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚡", fontSize = 13.sp)
                            Text(
                                text = "Jalur Cepat: Under Siapa?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }
                        Text(
                            text = "Otomatis isi divisi & wewenang",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isUnderSiapaMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, if (currentSuperiorNode != null) WeMadeColors.Accent else Color(0xFFCBD5E1)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
                                            color = WeMadeColors.Accent
                                        )
                                    } else if (state.selectedLevel == HierarchyLevel.EXECUTIVE) {
                                        Text(
                                            text = "Level Puncak (Tanpa Atasan / Direksi)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA)
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
                                Text("▼", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
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
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    leader.name,
                                                    fontWeight = if (isLeaderSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = if (isLeaderSelected) WeMadeColors.Accent else WeMadeColors.OnSurface
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isLeaderExec) Color(0xFFEEF2FF) else Color(0xFFFEF3C7))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = if (isLeaderExec) "DIREKSI" else (leader.department?.shortName ?: ""),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isLeaderExec) Color(0xFF4338CA) else Color(0xFFB45309)
                                                    )
                                                }
                                            }
                                            val subtitle = if (isLeaderExec) {
                                                "${leader.roleTitle} → Melapor ke Direksi (Bebas pilih divisi)"
                                            } else {
                                                "${leader.roleTitle} → Otomatis Divisi ${leader.department?.shortName ?: ""}"
                                            }
                                            Text(
                                                subtitle,
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSuperiorChange(leader.id.value)
                                        isUnderSiapaMenuOpen = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "Tanpa Atasan (Direksi / Puncak)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF4338CA)
                                        )
                                        Text("Otomatis ubah wewenang menjadi Direksi", fontSize = 10.sp, color = Color(0xFF64748B))
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

            Spacer(modifier = Modifier.height(14.dp))

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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val currentDept = state.selectedDepartment
                    if (currentDept != null) {
                        TextButton(
                            onClick = { onEditDepartmentClick(currentDept) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2563EB))
                        ) {
                            Text("✏️ Edit Divisi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (state.departments.size > 1) {
                            TextButton(
                                onClick = { onDeleteDepartment(currentDept) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD97706))
                            ) {
                                Text("📦 Arsipkan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    TextButton(
                        onClick = onAddDepartmentClick,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("+ Divisi Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Tampilan Pilihan Divisi (Selalu Aktif & Editable)
            if (state.departments.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada divisi terdaftar.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onAddDepartmentClick,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("+ Buat Divisi Pertama", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showArrows) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (canScrollBack) Color(0xFFF1F5F9) else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (canScrollBack) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
                                    CircleShape
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
                                val strokeColor = if (canScrollBack) Color(0xFF334155) else Color(0xFFCBD5E1)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chip Opsi: Direksi (Tanpa Divisi)
                        val isDireksiSelected = state.selectedDepartment == null && state.selectedLevel == HierarchyLevel.EXECUTIVE
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDireksiSelected) Color(0xFF4338CA) else Color(0xFFF1F5F9))
                                .border(
                                    1.dp,
                                    if (isDireksiSelected) Color(0xFF4338CA) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectDireksi() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Direksi (Non-Divisi)",
                                fontSize = 11.sp,
                                fontWeight = if (isDireksiSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isDireksiSelected) Color.White else Color(0xFF475569)
                            )
                        }

                        state.departments.forEach { dept ->
                            val isSelected = !isDireksiSelected && dept.id == (state.selectedDepartment?.id ?: state.activeDepartment.id)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(dept.colorHex) else Color(0xFFF1F5F9))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(dept.colorHex) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onDeptChange(dept) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dept.shortName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF475569)
                                )
                            }
                        }
                    }

                    if (showArrows) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (canScrollFwd) Color(0xFFF1F5F9) else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (canScrollFwd) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
                                    CircleShape
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
                                val strokeColor = if (canScrollFwd) Color(0xFF334155) else Color(0xFFCBD5E1)
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

            Spacer(modifier = Modifier.height(16.dp))

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
                    TextButton(
                        onClick = onAddTierClick,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("+ Tambah Tingkat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Opsi 1: Direksi (Level Puncak Organisasi)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDireksiSelected) WeMadeColors.PrimaryContainer else Color(0xFFFAFAFA))
                        .border(1.dp, if (isDireksiSelected) WeMadeColors.Primary else WeMadeColors.Border, RoundedCornerShape(8.dp))
                        .clickable { onSelectDireksi() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF4338CA).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DIREKSI",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA)
                            )
                        }
                        Column {
                            Text(
                                text = "Direksi (Level Puncak Organisasi)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isDireksiSelected) FontWeight.Bold else FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "Membawahi seluruh jajaran divisi dan manajemen pabrik.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    RadioButton(selected = isDireksiSelected, onClick = { onSelectDireksi() })
                }

                // Opsi Tingkat Wewenang Divisi
                availableTiers.forEach { tier ->
                    val isSelected = !isDireksiSelected && state.selectedTierName == tier.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) WeMadeColors.PrimaryContainer else Color(0xFFFAFAFA))
                            .border(1.dp, if (isSelected) WeMadeColors.Primary else WeMadeColors.Border, RoundedCornerShape(8.dp))
                            .clickable { onTierChange(tier) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val badgeColor = when {
                                tier.isHead -> Color(0xFFF59E0B)
                                tier.id == "team_lead" || tier.rank == 2 -> Color(0xFF10B981)
                                else -> Color(0xFF64748B)
                            }
                            val badgeLabel = when {
                                tier.isHead -> "KEPALA"
                                tier.id == "team_lead" || tier.rank == 2 -> "LEAD / SPV"
                                else -> "STAF"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                            }
                            Text(
                                text = tier.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .clickable { onEditTierClick(currentDept.id.value, tier) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✏️", fontSize = 11.sp)
                            }
                            RadioButton(selected = isSelected, onClick = { onTierChange(tier) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── BANNER PERALIHAN SUKSESI KEPALA DIVISI (SINGLE ACTIVE HEAD CONSTRAINT) ───
            val existingHead = state.existingHeadOfSelectedDept
            if (existingHead != null && state.selectedLevel == HierarchyLevel.HEAD_OF_DEPARTMENT) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF59E0B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PERHATIAN SUKSESI",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Hanya boleh ada 1 Kepala Divisi aktif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val currentDeptName = state.selectedDepartment?.displayName ?: state.activeDepartment.displayName
                        Text(
                            text = "Divisi $currentDeptName saat ini dipimpin oleh '${existingHead.name}'. Pilih tindakan suksesi:",
                            fontSize = 11.sp,
                            color = Color(0xFF78350F)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Opsi 1: Turunkan ke Staf Senior
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (state.successionAction == HeadSuccessionAction.DEMOTE_TO_STAFF) Color(0xFFFEF3C7) else Color.Transparent)
                                    .clickable { onSuccessionActionChange(HeadSuccessionAction.DEMOTE_TO_STAFF) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Alihkan Jabatan Menjadi Staf Senior",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "'${existingHead.name}' tetap di divisi ini dan melapor ke kepala divisi baru.",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                                RadioButton(
                                    selected = state.successionAction == HeadSuccessionAction.DEMOTE_TO_STAFF,
                                    onClick = { onSuccessionActionChange(HeadSuccessionAction.DEMOTE_TO_STAFF) }
                                )
                            }

                            // Opsi 2: Nonaktifkan Akun
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (state.successionAction == HeadSuccessionAction.DEACTIVATE) Color(0xFFFEF3C7) else Color.Transparent)
                                    .clickable { onSuccessionActionChange(HeadSuccessionAction.DEACTIVATE) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Nonaktifkan / Lepas dari Jabatan",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "'${existingHead.name}' dipensiunkan/dinonaktifkan dari bagan aktif.",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                                RadioButton(
                                    selected = state.successionAction == HeadSuccessionAction.DEACTIVATE,
                                    onClick = { onSuccessionActionChange(HeadSuccessionAction.DEACTIVATE) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 5. Judul Jabatan Operasional
            OutlinedTextField(
                value = state.roleTitleInput,
                onValueChange = onRoleTitleChange,
                label = { Text("Nama Jabatan / Posisi") },
                placeholder = { Text("Contoh: Staff Penjualan & Sampling", color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.isCreatingNew && state.selectedEmployeeId != null) {
                    OutlinedButton(
                        onClick = { onDeleteEmployee(state.selectedEmployeeId) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "📦 Arsipkan",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = state.nameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(if (!state.isCreatingNew && state.selectedEmployeeId != null) 1.2f else 1f)
                ) {
                    Text(
                        text = if (state.isCreatingNew) "Simpan ke Bagan Organisasi" else "Perbarui Karyawan",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartPreviewPanel(
    state: OrgChartUiState,
    onSelectNode: (String) -> Unit,
    onAddNewEmployee: () -> Unit,
    onRestorePresets: () -> Unit,
    onToggleArchived: () -> Unit,
    showArchivedPanel: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
        border = BorderStroke(1.dp, WeMadeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Chart Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pratinjau Struktur Organisasi (Live Org Chart)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Menampilkan fokus: 1 Tingkat ke Atas (Approval) & Seluruh Anggota Divisi",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onToggleArchived) { Text(if (showArchivedPanel) "Tutup Arsip" else "Lihat Arsip") }
                    LegendTag("Direksi", Color(0xFF6366F1))
                    LegendTag("Head", Color(0xFFF59E0B))
                    LegendTag("Staf", Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = WeMadeColors.Border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // The Rendered T-Shape Tree View or Empty State
            if (state.employees.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Bagan Organisasi Masih Kosong",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Belum ada karyawan yang terdaftar. Anda dapat memulai dengan struktur kosong atau menggunakan template konveksi bawaan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WeMadeColors.OnSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onRestorePresets,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Muat Template Konveksi (5 Divisi)")
                            }
                            Button(
                                onClick = onAddNewEmployee,
                                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+ Tambah Karyawan Pertama")
                            }
                        }
                    }
                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Input Nama Lengkap Divisi
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = { Text("Nama Lengkap Divisi") },
                    placeholder = { Text("Cth: Bordir & Sablon Printing") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // Input Nama Singkat / Label Badge
                OutlinedTextField(
                    value = shortNameInput,
                    onValueChange = onShortNameChange,
                    label = { Text("Label Singkat / Badge (Maks. 10 karakter)") },
                    placeholder = { Text("Cth: Bordir") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // Palet Pilihan Warna Tema Divisi (Dinamis: warna yang sudah dipakai divisi lain tidak muncul)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { deptColor ->
                            val isColorSelected = selectedColorHex == deptColor.hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(deptColor.hex))
                                    .border(
                                        width = if (isColorSelected) 3.dp else 1.dp,
                                        color = if (isColorSelected) WeMadeColors.OnSurface else Color(0x33000000),
                                        shape = CircleShape
                                    )
                                    .clickable { onColorSelect(deptColor.hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pratinjau Tampilan Badge:",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(selectedColorHex))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = shortNameInput.ifBlank { "Label" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = nameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Buat Divisi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(16.dp),
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = tierNameInput,
                    onValueChange = onNameChange,
                    label = { Text("Nama Tingkat Wewenang") },
                    placeholder = { Text("Cth: Mandor Jahit / QC Lead / Helper") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text(
                    text = "💡 Tingkat ini akan otomatis terdaftar sebagai opsi hirarki dinamis khusus pada divisi $departmentName.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = tierNameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simpan Tingkat", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(16.dp),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isBlocked) Color(0xFFFEF2F2) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBlocked) "⚠️" else "📦",
                        fontSize = 18.sp
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isBlocked) Color(0xFFB91C1C) else WeMadeColors.OnSurface
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBlocked) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isBlocked) Color(0xFFFCA5A5) else Color(0xFFFDE68A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBlocked) Color(0xFFDC2626) else Color(0xFFD97706))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isBlocked) "ATURAN INTEGRITAS ERP" else "POLA ODOO ARCHIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = warningNote,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = if (isBlocked) Color(0xFF7F1D1D) else Color(0xFF78350F)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isBlocked) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Ya, Arsipkan", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isBlocked) "Tutup / Mengerti" else "Batal")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = WeMadeColors.Surface
    )
}

@Composable
private fun LegendTag(text: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF1F5F9))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(text = text, fontSize = 10.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToastBanner(message: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
        if (message != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WeMadeColors.SuccessBg)
                    .clickable { onDismiss() }
                    .padding(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.Success,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isArchived) "📦" else "⚠️",
                    fontSize = 20.sp
                )
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = conflict.existingEmployeeName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isArchived) Color(0xFFFEF3C7) else Color(0xFFDCFCE7))
                                    .border(
                                        1.dp,
                                        if (isArchived) Color(0xFFFCD34D) else Color(0xFF86EFAC),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isArchived) "📦 DIARSIPKAN" else "🟢 AKTIF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isArchived) Color(0xFF92400E) else Color(0xFF166534)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bagian / Divisi:", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(conflict.existingDepartmentName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Jabatan:", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(conflict.existingRoleTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                        }
                    }
                }

                // Kotak Saran / Petunjuk Tindakan
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isArchived) Color(0xFFFFFBEB) else Color(0xFFEFF6FF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isArchived) Color(0xFFFDE68A) else Color(0xFFBFDBFE)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(if (isArchived) "💡" else "ℹ️", fontSize = 14.sp)
                        Text(
                            text = if (isArchived) {
                                "Anda dapat langsung memulihkan karyawan ini kembali ke bagan organisasi, atau ganti email di formulir jika ini individu baru."
                            } else {
                                "Satu email hanya dapat digunakan oleh satu akun karyawan. Silakan gunakan alamat email lain pada formulir."
                            },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = if (isArchived) Color(0xFF78350F) else Color(0xFF1E40AF)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isArchived) {
                Button(
                    onClick = { onRestore(conflict.existingEmployeeId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔄 Pulihkan Karyawan Ini", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.PrimaryDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Ganti Email Lain", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (isArchived) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Gunakan Email Lain")
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = { Text("Nama Lengkap Divisi") },
                    placeholder = { Text("Cth: Penjualan & Pemasaran") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = shortNameInput,
                    onValueChange = onShortNameChange,
                    label = { Text("Nama Singkat / Label Badge") },
                    placeholder = { Text("Cth: Sales") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // Palet Pilihan Warna
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { deptColor ->
                            val isColorSelected = selectedColorHex == deptColor.hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(deptColor.hex))
                                    .border(
                                        width = if (isColorSelected) 3.dp else 1.dp,
                                        color = if (isColorSelected) WeMadeColors.OnSurface else Color(0x33000000),
                                        shape = CircleShape
                                    )
                                    .clickable { onColorSelect(deptColor.hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pratinjau Tampilan Badge:",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(selectedColorHex))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = shortNameInput.ifBlank { "Label" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = nameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(16.dp),
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = tierNameInput,
                    onValueChange = onNameChange,
                    label = { Text("Nama Tingkat Wewenang") },
                    placeholder = { Text("Cth: Mandor Jahit / QC Lead") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text(
                    text = "💡 Perubahan nama tingkat wewenang akan diterapkan pada struktur hirarki divisi ini.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = tierNameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = WeMadeColors.Surface
    )
}

