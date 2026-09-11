package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.rbac.DynamicRbacViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun AssignModuleModal(
    isOpen: Boolean,
    department: Department?,
    role: CustomRole?,
    departments: List<Department>,
    roles: List<CustomRole>,
    initialAssignment: DepartmentModuleAssignment? = null,
    initialModule: BusinessModule? = null,
    onConfirm: (BusinessModule, DepartmentModuleAssignment) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val targetDept = remember(department, role, departments) {
        department ?: role?.let { DynamicRbacViewModel.resolveDepartmentForRole(it, departments) } ?: departments.firstOrNull()
    }

    var selectedModule by remember(initialModule, initialAssignment) {
        mutableStateOf(initialModule ?: BusinessModule.CRM_SALES)
    }

    val deptRoles = remember(targetDept, roles) {
        if (targetDept == null) roles
        else {
            val code = targetDept.code.lowercase()
            val name = targetDept.displayName.lowercase()
            roles.filter { r ->
                val rName = r.name.lowercase()
                r.departmentId == targetDept.id.value ||
                rName.contains(code) ||
                (code.contains("sales") && rName.contains("sales")) ||
                (code.contains("ppic") && (rName.contains("ppic") || rName.contains("jahit") || rName.contains("operator"))) ||
                (code.contains("warehouse") && rName.contains("gudang")) ||
                (code.contains("qc") && rName.contains("qc")) ||
                (code.contains("finance") && rName.contains("keuangan"))
            }
        }
    }

    var isSpecificRolesMode by remember(initialAssignment, role) {
        mutableStateOf(role != null || (initialAssignment?.specificRoleIds?.isNotEmpty() == true))
    }

    var selectedRoleIds by remember(initialAssignment, role) {
        mutableStateOf(
            if (role != null) setOf(role.id.value)
            else initialAssignment?.specificRoleIds ?: emptySet()
        )
    }

    var selectedAccessLevel by remember(initialAssignment) {
        mutableStateOf(initialAssignment?.accessLevel ?: AccessLevel.OPERATE)
    }

    var selectedScope by remember(initialAssignment, selectedModule) {
        mutableStateOf(
            if (selectedModule.isGlobalOnly) DataScope.ALL_TENANT_DATA
            else initialAssignment?.scope ?: DataScope.ALL_TENANT_DATA
        )
    }

    val rolesScrollState = rememberScrollState()
    val modulesScrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier
                .widthIn(max = 580.dp)
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
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (initialAssignment != null) "Edit Penugasan Modul" else "Tugaskan Modul",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        if (role != null) {
                            Text(
                                text = "Untuk Jabatan: ${role.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeMadeColors.PrimaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (targetDept != null) {
                            Text(
                                text = "Untuk Divisi: ${targetDept.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeMadeColors.PrimaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (targetDept != null) {
                        ClayTag(
                            text = targetDept.shortName,
                            tint = Color(targetDept.colorHex),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Md))
                HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                // 1. Pilih Modul SaaS
                Text(
                    text = "1. Pilih Modul SaaS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .verticalScroll(modulesScrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BusinessModule.entries.forEach { mod ->
                        val isSelected = mod == selectedModule
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
                                    selectedModule = mod
                                    if (mod.isGlobalOnly) {
                                        selectedScope = DataScope.ALL_TENANT_DATA
                                    }
                                }
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = mod.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = WeMadeColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                ClayTag(
                                    text = mod.category.displayName,
                                    tint = WeMadeColors.Primary,
                                    fontSize = 9.sp
                                )
                            }

                            if (isSelected) {
                                ClayTag(text = "Terpilih", tint = WeMadeColors.Primary, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 2. Lingkup Jabatan (Hanya jika dibuka dari Divisi)
                if (role == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "2. Lingkup Jabatan di Divisi Ini",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Semua Jabatan (Full Divisi)",
                                fontSize = 11.5.sp,
                                fontWeight = if (!isSpecificRolesMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isSpecificRolesMode) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = if (isSpecificRolesMode) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                                    outline = if (isSpecificRolesMode) WeMadeColors.Primary else WeMadeColors.Border
                                )
                                .clickable { isSpecificRolesMode = true }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pilih Jabatan Spesifik",
                                fontSize = 11.5.sp,
                                fontWeight = if (isSpecificRolesMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSpecificRolesMode) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }

                    if (isSpecificRolesMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp)
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.Surface,
                                    outline = WeMadeColors.Border
                                )
                                .padding(6.dp)
                                .verticalScroll(rolesScrollState),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            deptRoles.forEach { r ->
                                val isChecked = selectedRoleIds.contains(r.id.value)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRoleIds = if (isChecked) selectedRoleIds - r.id.value else selectedRoleIds + r.id.value
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clayFlat(
                                                shape = RoundedCornerShape(4.dp),
                                                background = if (isChecked) WeMadeColors.Primary else WeMadeColors.Surface,
                                                outline = if (isChecked) WeMadeColors.PrimaryDark else WeMadeColors.Border
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isChecked) {
                                            IconCheck(modifier = Modifier.size(11.dp), color = WeMadeColors.Surface)
                                        }
                                    }
                                    Text(
                                        text = r.name,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                        color = WeMadeColors.OnSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Level Akses (Full 1 Row)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (role != null) "2. Level Akses" else "3. Level Akses",
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
                        text = if (role != null) "3. Jangkauan Data" else "4. Jangkauan Data",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    if (selectedModule.isGlobalOnly) {
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
                            selectedModule.supportedScopes.forEach { scope ->
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = WeMadeColors.Border.copy(alpha = 0.6f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Actions Footer
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
                        text = if (initialAssignment != null) "Simpan Perubahan" else "Tugaskan Modul",
                        onClick = {
                            val deptId = targetDept?.id?.value ?: (role?.departmentId ?: "dept-general")
                            val deptName = targetDept?.displayName ?: "Divisi Umum"
                            val finalRoleIds = if (role != null) setOf(role.id.value)
                                              else if (isSpecificRolesMode) selectedRoleIds
                                              else emptySet()

                            val assignment = DepartmentModuleAssignment(
                                departmentId = deptId,
                                departmentName = deptName,
                                accessLevel = selectedAccessLevel,
                                specificRoleIds = finalRoleIds,
                                scope = selectedScope,
                                id = initialAssignment?.id ?: ""
                            )
                            onConfirm(selectedModule, assignment)
                        },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
