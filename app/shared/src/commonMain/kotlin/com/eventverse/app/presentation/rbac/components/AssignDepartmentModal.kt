package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.presentation.theme.WeMadeColors

// Helper to resolve dynamic roles for a selected department
private fun resolveRolesForDepartment(
    dept: Department?,
    allRoles: List<CustomRole>
): List<CustomRole> {
    if (dept == null) return allRoles
    val code = dept.code.lowercase()
    val name = dept.displayName.lowercase()

    // 1. Filter existing custom roles matching this department or owner/direksi
    val matchedFromRoles = allRoles.filter { role ->
        val rName = role.name.lowercase()
        val rDesc = role.description.lowercase()
        val isOwner = rName.contains("owner") || rName.contains("direktur")

        when {
            code.contains("sales") || name.contains("penjualan") ->
                isOwner || rName.contains("sales") || rName.contains("penjualan") || rName.contains("crm")
            code.contains("ppic") || code.contains("production") || name.contains("produksi") ->
                isOwner || rName.contains("ppic") || rName.contains("produksi") || rName.contains("operator") || rName.contains("jahit") || rName.contains("mandor")
            code.contains("warehouse") || name.contains("gudang") ->
                isOwner || rName.contains("gudang") || rName.contains("logistik") || rName.contains("warehouse")
            code.contains("qc") || name.contains("quality") || name.contains("kualitas") ->
                isOwner || rName.contains("qc") || rName.contains("quality") || rName.contains("kualitas")
            code.contains("finance") || name.contains("keuangan") || name.contains("akuntansi") ->
                isOwner || rName.contains("keuangan") || rName.contains("akuntansi") || rName.contains("finance") || rName.contains("kasir")
            else -> isOwner || rName.contains(code) || rDesc.contains(code)
        }
    }

    // 2. Department-specific standard garment presets if not already in custom roles
    val departmentSpecificDefaults = when {
        code.contains("qc") || name.contains("quality") || name.contains("kualitas") -> listOf(
            CustomRole(
                id = RoleId("role-qc-head"),
                tenantId = null,
                name = "Kepala Quality Control (QC)",
                description = "Persetujuan standar mutu bahan baku dan inspeksi hasil jahitan final."
            ),
            CustomRole(
                id = RoleId("role-qc-inspector"),
                tenantId = null,
                name = "QC Inspector (In-Line & End-Line)",
                description = "Pemeriksaan cacat jahitan dan ketepatan ukuran spek baju."
            )
        )
        code.contains("finance") || name.contains("keuangan") || name.contains("akuntansi") -> listOf(
            CustomRole(
                id = RoleId("role-finance-head"),
                tenantId = null,
                name = "Kepala Keuangan & Akuntansi",
                description = "Persetujuan anggaran produksi, arus kas konveksi, dan validasi invoice."
            ),
            CustomRole(
                id = RoleId("role-finance-staff"),
                tenantId = null,
                name = "Staff Akuntansi & Kasir",
                description = "Pencatatan nota pembelian bahan, kas kecil, dan rekapitulasi gaji penjahit."
            )
        )
        code.contains("warehouse") || name.contains("gudang") -> listOf(
            CustomRole(
                id = RoleId("role-warehouse-head"),
                tenantId = null,
                name = "Kepala Gudang & Logistik",
                description = "Penanggung jawab stok roll kain, aksesoris, dan surat jalan pengiriman."
            )
        )
        else -> emptyList()
    }

    // Owner role included across divisions as factory executive
    val ownerRole = allRoles.find { it.name.contains("owner", ignoreCase = true) || it.name.contains("direktur", ignoreCase = true) }

    val combined = (matchedFromRoles + departmentSpecificDefaults).distinctBy { it.id.value }
    val withOwner = if (ownerRole != null && combined.none { it.id == ownerRole.id }) {
        listOf(ownerRole) + combined
    } else combined

    return withOwner.ifEmpty { allRoles }
}

@Composable
fun AssignDepartmentModal(
    isOpen: Boolean,
    module: BusinessModule?,
    departments: List<Department>,
    roles: List<CustomRole>,
    initialAssignment: DepartmentModuleAssignment? = null,
    onConfirm: (DepartmentModuleAssignment) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen || module == null) return

    var selectedDeptId by remember(initialAssignment, departments) {
        mutableStateOf(initialAssignment?.departmentId ?: departments.firstOrNull()?.id?.value ?: "")
    }
    var selectedAccessLevel by remember(initialAssignment) {
        mutableStateOf(initialAssignment?.accessLevel ?: AccessLevel.OPERATE)
    }
    var isSpecificRolesMode by remember(initialAssignment) {
        mutableStateOf(initialAssignment?.specificRoleIds?.isNotEmpty() == true)
    }
    var selectedRoleIds by remember(initialAssignment) {
        mutableStateOf(initialAssignment?.specificRoleIds ?: emptySet())
    }
    var selectedScope by remember(initialAssignment, module) {
        mutableStateOf(
            if (module.isGlobalOnly) DataScope.ALL_TENANT_DATA
            else initialAssignment?.scope ?: DataScope.ALL_TENANT_DATA
        )
    }

    val selectedDept = departments.find { it.id.value == selectedDeptId }
    val rolesScrollState = rememberScrollState()

    // Dynamically resolve roles for the currently selected department
    val currentDeptRoles = remember(selectedDeptId, departments, roles) {
        resolveRolesForDepartment(selectedDept, roles)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
            border = BorderStroke(1.dp, WeMadeColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header Dialog (Fixed at top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (initialAssignment != null) "Edit Penugasan Divisi" else "Tugaskan Divisi ke Modul",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Modul: ${module.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.PrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WeMadeColors.PrimaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = module.category.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.PrimaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = WeMadeColors.Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Pilih Divisi Pabrik
                Text(
                    text = "1. Pilih Divisi Pabrik",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    departments.forEach { dept ->
                        val isSelected = dept.id.value == selectedDeptId
                        val deptColor = Color(dept.colorHex)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (selectedDeptId != dept.id.value) {
                                        selectedDeptId = dept.id.value
                                        // Dynamically reset selected roles when changing department
                                        selectedRoleIds = emptySet()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(deptColor)
                                )
                                Text(
                                    text = dept.displayName,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = WeMadeColors.OnSurface
                                )
                            }

                            if (isSelected) {
                                Text(
                                    text = "Terpilih ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.PrimaryDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Lingkup Jabatan di Divisi Ini
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Lingkup Jabatan di Divisi Ini",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    if (isSpecificRolesMode && selectedDept != null) {
                        Text(
                            text = "Divisi: ${selectedDept.shortName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.PrimaryDark
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option A: Semua Jabatan
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isSpecificRolesMode) WeMadeColors.PrimaryContainer else Color(0xFFF1F5F9))
                            .border(
                                1.dp,
                                if (!isSpecificRolesMode) WeMadeColors.Primary else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                isSpecificRolesMode = false
                                selectedRoleIds = emptySet()
                            }
                            .padding(9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Semua Jabatan (Full Divisi)",
                            fontSize = 12.sp,
                            fontWeight = if (!isSpecificRolesMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isSpecificRolesMode) WeMadeColors.PrimaryDark else Color(0xFF475569)
                        )
                    }

                    // Option B: Spesifik Jabatan Tertentu
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSpecificRolesMode) WeMadeColors.PrimaryContainer else Color(0xFFF1F5F9))
                            .border(
                                1.dp,
                                if (isSpecificRolesMode) WeMadeColors.Primary else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isSpecificRolesMode = true }
                            .padding(9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pilih Jabatan Spesifik",
                            fontSize = 12.sp,
                            fontWeight = if (isSpecificRolesMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSpecificRolesMode) WeMadeColors.PrimaryDark else Color(0xFF475569)
                        )
                    }
                }

                // If specific roles mode, show scrollable checklist container with clear visual indicators
                if (isSpecificRolesMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, WeMadeColors.Border, RoundedCornerShape(8.dp))
                            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 4.dp)
                    ) {
                        // Quick Action Toolbar: Count, Scrollable Badge, and Select All / Reset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Daftar Jabatan (${selectedRoleIds.size} dipilih):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE2E8F0))
                                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = "↕ Scrollable",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Pilih Semua",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.PrimaryDark,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            selectedRoleIds = currentDeptRoles.map { it.id.value }.toSet()
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = "Reset",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            selectedRoleIds = emptySet()
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = WeMadeColors.Border.copy(alpha = 0.6f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Scrollable list of roles with bounded height
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp)
                                .verticalScroll(rolesScrollState),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            currentDeptRoles.forEach { role ->
                                val isChecked = selectedRoleIds.contains(role.id.value)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isChecked) Color(0xFFEFF6FF) else Color.Transparent)
                                        .clickable {
                                            selectedRoleIds = if (isChecked) {
                                                selectedRoleIds - role.id.value
                                            } else {
                                                selectedRoleIds + role.id.value
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedRoleIds = if (checked) {
                                                selectedRoleIds + role.id.value
                                            } else {
                                                selectedRoleIds - role.id.value
                                            }
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = role.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium,
                                        color = WeMadeColors.OnSurface
                                    )
                                }
                            }
                        }

                        // Bottom visual indicator if more items can be scrolled
                        if (rolesScrollState.canScrollForward) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "▼ Gulir ke bawah untuk melihat jabatan lainnya",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.PrimaryDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Level Akses & Jangkauan Data (FIXED & FULL SELALU ADA - TIDAK IKUT TER-SCROLL)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Level Akses
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Level Akses",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(AccessLevel.VIEW, AccessLevel.OPERATE, AccessLevel.MANAGE).forEach { level ->
                                val isSelected = level == selectedAccessLevel
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) WeMadeColors.PrimaryDark else Color.Transparent)
                                        .clickable { selectedAccessLevel = level }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = level.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }

                    // Jangkauan Data (Dynamic)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jangkauan Data",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        if (module.isGlobalOnly) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                                    .padding(vertical = 7.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🌐 Seluruh Pabrik (Shared)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                module.supportedScopes.forEach { scope ->
                                    val isSelected = scope == selectedScope
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) WeMadeColors.PrimaryDark else Color.Transparent)
                                            .clickable { selectedScope = scope }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = scope.shortLabel,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = WeMadeColors.Border.copy(alpha = 0.6f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Actions Footer (Fixed at bottom - always visible and never cut off)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = WeMadeColors.OnSurfaceMuted)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val deptName = selectedDept?.displayName ?: "Divisi $selectedDeptId"
                            val assignment = DepartmentModuleAssignment(
                                departmentId = selectedDeptId,
                                departmentName = deptName,
                                accessLevel = selectedAccessLevel,
                                specificRoleIds = if (isSpecificRolesMode) selectedRoleIds else emptySet(),
                                scope = selectedScope
                            )
                            onConfirm(assignment)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (initialAssignment != null) "Simpan Perubahan" else "Tugaskan Divisi",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

