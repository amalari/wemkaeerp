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
import com.eventverse.app.domain.orgchart.HeadSuccessionAction
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.presentation.orgchart.components.TShapeChartView
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun OrgChartScreen(
    viewModel: OrgChartViewModel = remember { OrgChartViewModel() },
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
                    onLevelChange = { viewModel.onEvent(OrgChartUiEvent.SelectLevel(it)) },
                    onSuperiorChange = { viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(it)) },
                    onRoleTitleChange = { viewModel.onEvent(OrgChartUiEvent.UpdateRoleTitle(it)) },
                    onSuccessionActionChange = { viewModel.onEvent(OrgChartUiEvent.SelectSuccessionAction(it)) },
                    onSave = { viewModel.onEvent(OrgChartUiEvent.SaveEmployee) },
                    onReset = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
                    onAddDepartmentClick = { viewModel.onEvent(OrgChartUiEvent.OpenCreateDeptModal) },
                    modifier = Modifier.width(420.dp)
                )

                // SISI KANAN: Live Org Chart Preview (60% weight)
                ChartPreviewPanel(
                    state = state,
                    onSelectNode = { viewModel.onEvent(OrgChartUiEvent.SelectExistingEmployee(it)) },
                    onAddNewEmployee = { viewModel.onEvent(OrgChartUiEvent.StartCreateNewEmployee) },
                    onRestorePresets = { viewModel.onEvent(OrgChartUiEvent.RestoreDefaultPresets) },
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
    onLevelChange: (HierarchyLevel) -> Unit,
    onSuperiorChange: (String?) -> Unit,
    onRoleTitleChange: (String) -> Unit,
    onSuccessionActionChange: (HeadSuccessionAction) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onAddDepartmentClick: () -> Unit,
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
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = state.phoneInput,
                    onValueChange = onPhoneChange,
                    label = { Text("No. WhatsApp") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Pilih Divisi Pabrik (Dinamis dengan Horizontal Scroll)
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

                TextButton(
                    onClick = onAddDepartmentClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+ Divisi Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

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
                    // Panah Navigasi Kiri (Scroll Backward)
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

                    // Deretan Chip Divisi (Dapat di-swipe dan di-scroll horizontal)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(deptScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.departments.forEach { dept ->
                            val isSelected = dept.id == (state.selectedDepartment?.id ?: state.activeDepartment.id)
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

                    // Panah Navigasi Kanan (Scroll Forward)
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

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Tingkat Jabatan / Hirarki
            Text(
                text = "Tingkat Wewenang / Hirarki:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HierarchyLevel.entries.forEach { level ->
                    val isSelected = level == state.selectedLevel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) WeMadeColors.PrimaryContainer else Color(0xFFFAFAFA))
                            .border(1.dp, if (isSelected) WeMadeColors.Primary else WeMadeColors.Border, RoundedCornerShape(8.dp))
                            .clickable { onLevelChange(level) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (level) {
                                            HierarchyLevel.EXECUTIVE -> Color(0xFF6366F1).copy(alpha = 0.15f)
                                            HierarchyLevel.HEAD_OF_DEPARTMENT -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                            HierarchyLevel.STAFF_OPERATOR -> Color(0xFF64748B).copy(alpha = 0.15f)
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = when (level) {
                                        HierarchyLevel.EXECUTIVE -> "DIR"
                                        HierarchyLevel.HEAD_OF_DEPARTMENT -> "HEAD"
                                        HierarchyLevel.STAFF_OPERATOR -> "STAF"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (level) {
                                        HierarchyLevel.EXECUTIVE -> Color(0xFF4338CA)
                                        HierarchyLevel.HEAD_OF_DEPARTMENT -> Color(0xFFB45309)
                                        HierarchyLevel.STAFF_OPERATOR -> Color(0xFF334155)
                                    }
                                )
                            }
                            Text(
                                text = level.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = WeMadeColors.OnSurface
                            )
                        }
                        RadioButton(selected = isSelected, onClick = { onLevelChange(level) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── BANNER PERALIHAN SUKSESI KEPALA DIVISI (SINGLE ACTIVE HEAD CONSTRAINT) ───
            val existingHead = state.existingHeadOfSelectedDept
            if (existingHead != null) {
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

                        Text(
                            text = "Divisi ${state.activeDepartment.displayName} saat ini dipimpin oleh '${existingHead.name}'. Pilih tindakan suksesi:",
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

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 4. Atasan Langsung (Reports To)
            if (state.selectedLevel != HierarchyLevel.EXECUTIVE) {
                Text(
                    text = "Atasan Langsung (Jalur Approval & Laporan):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                val superiors = state.availableSuperiors
                if (superiors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        superiors.forEach { sup ->
                            val isSelected = sup.id.value == state.selectedReportsToId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) WeMadeColors.AccentLight else Color(0xFFF8FAFC))
                                    .border(1.dp, if (isSelected) WeMadeColors.Accent else WeMadeColors.Border, RoundedCornerShape(8.dp))
                                    .clickable { onSuperiorChange(sup.id.value) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = sup.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) WeMadeColors.Accent else WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "${sup.roleTitle} (${sup.department.shortName})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                                RadioButton(selected = isSelected, onClick = { onSuperiorChange(sup.id.value) })
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Tidak ada kandidat atasan yang sesuai untuk kriteria ini.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 5. Judul Jabatan Operasional
            OutlinedTextField(
                value = state.roleTitleInput,
                onValueChange = onRoleTitleChange,
                label = { Text("Nama Jabatan (contoh: Sales Eksekutif Seragam)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onSave,
                    enabled = state.nameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
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
