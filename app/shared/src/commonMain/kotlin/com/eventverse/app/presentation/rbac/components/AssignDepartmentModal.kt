package com.eventverse.app.presentation.rbac.components

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

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
import com.eventverse.app.presentation.designsystem.*
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
    var selectedDesks by remember(initialAssignment, module) {
        mutableStateOf(if (module == GarmentModules.OPERATOR_EXEC) initialAssignment?.allowedDesks else null)
    }

    var showConfirmationView by remember(initialAssignment, isOpen) { mutableStateOf(false) }

    val selectedDept = departments.find { it.id.value == selectedDeptId }
    val rolesScrollState = rememberScrollState()

    // Dynamically resolve roles for the currently selected department
    val currentDeptRoles = remember(selectedDeptId, departments, roles) {
        resolveRolesForDepartment(selectedDept, roles)
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = ClayShapes.Panel,
            containerColor = WeMadeColors.Surface,
            outlineColor = WeMadeColors.Outline,
            shadowColor = WeMadeColors.Outline,
            offset = ClayOffset.Rest,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            if (showConfirmationView && initialAssignment != null) {
                EditConfirmationContent(
                    module = module,
                    initial = initialAssignment,
                    newDeptName = selectedDept?.displayName ?: initialAssignment.departmentName,
                    newDeptId = selectedDeptId,
                    newAccessLevel = selectedAccessLevel,
                    newScope = selectedScope,
                    newIsSpecificRoles = isSpecificRolesMode,
                    newRoleIds = selectedRoleIds,
                    allRoles = roles,
                    onBackToEdit = { showConfirmationView = false },
                    onConfirmChanges = {
                        val deptName = selectedDept?.displayName ?: initialAssignment.departmentName
                        val assignment = initialAssignment.copy(
                            departmentId = selectedDeptId,
                            departmentName = deptName,
                            accessLevel = selectedAccessLevel,
                            specificRoleIds = if (isSpecificRolesMode) selectedRoleIds else emptySet(),
                            scope = selectedScope,
                            allowedDesks = selectedDesks?.takeIf { it.isNotEmpty() }
                        )
                        showConfirmationView = false
                        onConfirm(assignment)
                    }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Modul: ${module.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.PrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ClayTag(
                        text = module.category.displayName,
                        tint = WeMadeColors.Primary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Md))
                HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
                Spacer(modifier = Modifier.height(ClaySpacing.Md))

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
                                .clayFlat(
                                    shape = ClayShapes.Card,
                                    background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                                    outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                    borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium
                                )
                                .clickable {
                                    if (selectedDeptId != dept.id.value) {
                                        selectedDeptId = dept.id.value
                                        selectedRoleIds = emptySet()
                                    }
                                }
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
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
                                ClayTag(
                                    text = "Terpilih",
                                    tint = WeMadeColors.Primary,
                                    fontSize = 10.sp
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
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = if (!isSpecificRolesMode) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                                outline = if (!isSpecificRolesMode) WeMadeColors.Primary else WeMadeColors.Border
                            )
                            .clickable {
                                isSpecificRolesMode = false
                                selectedRoleIds = emptySet()
                            }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Semua Jabatan (Full Divisi)",
                            fontSize = 12.sp,
                            fontWeight = if (!isSpecificRolesMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isSpecificRolesMode) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                        )
                    }

                    // Option B: Spesifik Jabatan Tertentu
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = if (isSpecificRolesMode) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                                outline = if (isSpecificRolesMode) WeMadeColors.Primary else WeMadeColors.Border
                            )
                            .clickable { isSpecificRolesMode = true }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pilih Jabatan Spesifik",
                            fontSize = 12.sp,
                            fontWeight = if (isSpecificRolesMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSpecificRolesMode) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                // If specific roles mode, show scrollable checklist container with clear visual indicators
                if (isSpecificRolesMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.Surface,
                                outline = WeMadeColors.Border
                            )
                            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 4.dp)
                    ) {
                        // Quick Action Toolbar: Count, Scrollable Badge, and Select All / Reset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
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
                                ClayTag(
                                    text = "↕ Scrollable",
                                    tint = WeMadeColors.Secondary,
                                    fontSize = 9.5.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Pilih Semua",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.PrimaryDark,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { selectedRoleIds = currentDeptRoles.map { it.id.value }.toSet() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = "Reset",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.Error,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp)).clickable { selectedRoleIds = emptySet() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = WeMadeColors.Border.copy(alpha = 0.6f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

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
                                        .then(
                                            if (isChecked) {
                                                Modifier.clayFlat(
                                                    shape = RoundedCornerShape(6.dp),
                                                    background = WeMadeColors.PrimaryContainer,
                                                    outline = WeMadeColors.Primary,
                                                    borderWidth = 1.dp
                                                )
                                            } else {
                                                Modifier.clip(RoundedCornerShape(6.dp))
                                            }
                                        )
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
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = WeMadeColors.Primary,
                                            checkmarkColor = WeMadeColors.Surface
                                        ),
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
                                    .clayFlat(shape = RoundedCornerShape(4.dp), background = WeMadeColors.PrimaryContainer, outline = WeMadeColors.Primary, borderWidth = 1.dp)
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

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Level Akses (Full 1 Row)
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Border
                            )
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(AccessLevel.VIEW, AccessLevel.OPERATE, AccessLevel.MANAGE).forEach { level ->
                            val isSelected = level == selectedAccessLevel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (isSelected) {
                                            Modifier.clayFlat(
                                                shape = RoundedCornerShape(6.dp),
                                                background = WeMadeColors.Primary,
                                                outline = WeMadeColors.PrimaryDark,
                                                borderWidth = 1.5.dp
                                            )
                                        } else {
                                            Modifier.clip(RoundedCornerShape(6.dp))
                                        }
                                    )
                                    .clickable { selectedAccessLevel = level }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level.displayName,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Jangkauan Data (Full 1 Row di bawah Level Akses)
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.PrimaryContainer,
                                    outline = WeMadeColors.Primary
                                )
                                .padding(vertical = 7.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconGlobe(modifier = Modifier.size(13.dp), color = WeMadeColors.PrimaryDark)
                                Text(
                                    text = "Seluruh Pabrik (Shared)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.PrimaryDark
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Border
                                )
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            module.supportedScopes.forEach { scope ->
                                val isSelected = scope == selectedScope
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (isSelected) {
                                                Modifier.clayFlat(
                                                    shape = RoundedCornerShape(6.dp),
                                                    background = WeMadeColors.Primary,
                                                    outline = WeMadeColors.PrimaryDark,
                                                    borderWidth = 1.5.dp
                                                )
                                            } else {
                                                Modifier.clip(RoundedCornerShape(6.dp))
                                            }
                                        )
                                        .clickable { selectedScope = scope }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = scope.shortLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                                    )
                                }
                            }
                        }
                    }
                }

                if (module == GarmentModules.OPERATOR_EXEC && selectedAccessLevel != AccessLevel.NONE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OperatorDeskAccessPicker(
                        selected = selectedDesks,
                        onSelectionChange = { selectedDesks = it }
                    )
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
                    ClayButton(
                        text = "Batal",
                        onClick = onDismiss,
                        style = ClayButtonStyle.Ghost,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    ClayButton(
                        text = if (initialAssignment != null) "Simpan Perubahan" else "Tugaskan Divisi",
                        onClick = {
                            val deptName = selectedDept?.displayName ?: "Divisi $selectedDeptId"
                            val assignment = DepartmentModuleAssignment(
                                departmentId = selectedDeptId,
                                departmentName = deptName,
                                accessLevel = selectedAccessLevel,
                                specificRoleIds = if (isSpecificRolesMode) selectedRoleIds else emptySet(),
                                scope = selectedScope,
                                allowedDesks = selectedDesks?.takeIf { it.isNotEmpty() },
                                id = initialAssignment?.id ?: ""
                            )

                            if (initialAssignment != null) {
                                val hasChanges = selectedDeptId != initialAssignment.departmentId ||
                                    selectedAccessLevel != initialAssignment.accessLevel ||
                                    selectedScope != initialAssignment.scope ||
                                    isSpecificRolesMode != (initialAssignment.specificRoleIds.isNotEmpty()) ||
                                    (isSpecificRolesMode && selectedRoleIds != initialAssignment.specificRoleIds) ||
                                    selectedDesks != initialAssignment.allowedDesks

                                if (hasChanges) {
                                    showConfirmationView = true
                                } else {
                                    onDismiss()
                                }
                            } else {
                                onConfirm(assignment)
                            }
                        },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
}

private fun formatRoleNames(roleIds: Set<String>, allRoles: List<CustomRole>): String {
    if (roleIds.isEmpty()) return "Semua Jabatan"
    return roleIds.mapNotNull { roleId ->
        allRoles.find { it.id.value == roleId }?.name ?: when (roleId) {
            "role-sales-head" -> "Kepala Penjualan & CRM"
            "role-sales-staff" -> "Staff Sales"
            "role-qc-head" -> "Kepala Quality Control (QC)"
            "role-qc-inspector" -> "QC Inspector"
            "role-finance-head" -> "Kepala Keuangan & Akuntansi"
            "role-finance-staff" -> "Staff Akuntansi & Kasir"
            "role-warehouse-head" -> "Kepala Gudang & Logistik"
            "role-sewing-head" -> "Kepala Produksi & Jahit"
            "role-operator" -> "Operator Jahit"
            else -> roleId.removePrefix("role-").replace("-", " ")
        }
    }.joinToString(", ")
}

@Composable
private fun EditConfirmationContent(
    module: BusinessModule,
    initial: DepartmentModuleAssignment,
    newDeptName: String,
    newDeptId: String,
    newAccessLevel: AccessLevel,
    newScope: DataScope,
    newIsSpecificRoles: Boolean,
    newRoleIds: Set<String>,
    allRoles: List<CustomRole>,
    onBackToEdit: () -> Unit,
    onConfirmChanges: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header Konfirmasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clayFlat(
                            shape = ClayShapes.Tile,
                            background = WeMadeColors.WarningBg,
                            outline = WeMadeColors.Warning,
                            borderWidth = ClayBorder.Medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconWarning(modifier = Modifier.size(20.dp), color = WeMadeColors.Warning)
                }
                Column {
                    Text(
                        text = "Konfirmasi Perubahan Hak Akses",
                        style = MaterialTheme.typography.titleMedium,
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
            }

            ClayTag(
                text = "Perlu Konfirmasi",
                tint = WeMadeColors.Warning,
                fontSize = 10.5.sp
            )
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        Text(
            text = "Anda akan mengubah konfigurasi hak akses berikut. Mohon periksa perbedaan sebelum dan sesudah perubahan di bawah ini:",
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // Diff Container: Dua Card (Sebelumnya vs Diubah Menjadi)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Kolom Kiri: Kondisi Sebelumnya
            val initialLevelTint = when (initial.accessLevel) {
                AccessLevel.NONE    -> WeMadeColors.OnSurfaceMuted
                AccessLevel.VIEW    -> WeMadeColors.Info
                AccessLevel.OPERATE -> WeMadeColors.Warning
                AccessLevel.MANAGE  -> WeMadeColors.Success
            }
            val initialRoleText = formatRoleNames(initial.specificRoleIds, allRoles)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kondisi Sebelumnya",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    ClayTag(text = "Lama", tint = WeMadeColors.OnSurfaceMuted, fontSize = 9.sp)
                }

                HorizontalDivider(color = WeMadeColors.Border.copy(alpha = 0.5f), thickness = 1.dp)

                // Divisi
                Text(
                    text = "Divisi:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = initial.departmentName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // Lingkup Jabatan
                Text(
                    text = "Lingkup Jabatan:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = if (initial.appliesToAllRoles) "Semua Jabatan (Full Divisi)" else initialRoleText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )

                // Level Akses
                Text(
                    text = "Level Akses:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                ClayTag(
                    text = initial.accessLevel.displayName,
                    tint = initialLevelTint,
                    fontSize = 10.sp
                )

                // Jangkauan Data
                Text(
                    text = "Jangkauan Data:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = initial.scope.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.OnSurface
                )
            }

            // Kolom Kanan: Diubah Menjadi
            val newLevelTint = when (newAccessLevel) {
                AccessLevel.NONE    -> WeMadeColors.OnSurfaceMuted
                AccessLevel.VIEW    -> WeMadeColors.Info
                AccessLevel.OPERATE -> WeMadeColors.Warning
                AccessLevel.MANAGE  -> WeMadeColors.Success
            }
            val newRoleText = formatRoleNames(newRoleIds, allRoles)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.PrimaryContainer,
                        outline = WeMadeColors.Primary,
                        borderWidth = ClayBorder.Thick
                    )
                    .padding(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Diubah Menjadi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.PrimaryDark
                    )
                    ClayTag(text = "Baru", tint = WeMadeColors.Primary, fontSize = 9.sp)
                }

                HorizontalDivider(color = WeMadeColors.Primary.copy(alpha = 0.3f), thickness = 1.dp)

                // Divisi
                Text(
                    text = "Divisi:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.PrimaryDark
                )
                Text(
                    text = newDeptName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // Lingkup Jabatan
                Text(
                    text = "Lingkup Jabatan:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.PrimaryDark
                )
                Text(
                    text = if (!newIsSpecificRoles) "Semua Jabatan (Full Divisi)" else newRoleText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.PrimaryDark
                )

                // Level Akses
                Text(
                    text = "Level Akses:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.PrimaryDark
                )
                ClayTag(
                    text = newAccessLevel.displayName,
                    tint = newLevelTint,
                    fontSize = 10.sp
                )

                // Jangkauan Data
                Text(
                    text = "Jangkauan Data:",
                    fontSize = 10.5.sp,
                    color = WeMadeColors.PrimaryDark
                )
                Text(
                    text = newScope.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // Question Alert Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.WarningBg,
                    outline = WeMadeColors.Warning,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(ClaySpacing.Md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconWarning(modifier = Modifier.size(16.dp), color = WeMadeColors.Warning)
                Text(
                    text = "Apakah Anda yakin ingin mengubah hak akses ini?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = WeMadeColors.Border.copy(alpha = 0.6f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayButton(
                text = "Kembali Edit",
                onClick = onBackToEdit,
                style = ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            ClayButton(
                text = "Ya, Ubah Sekarang",
                onClick = onConfirmChanges,
                style = ClayButtonStyle.Primary,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
            )
        }
    }
}

