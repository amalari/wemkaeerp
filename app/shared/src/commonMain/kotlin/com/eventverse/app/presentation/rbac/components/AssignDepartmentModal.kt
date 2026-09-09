package com.eventverse.app.presentation.rbac.components

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
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.presentation.theme.WeMadeColors

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(520.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
            border = BorderStroke(1.dp, WeMadeColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header Dialog
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = WeMadeColors.Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Pilih Divisi
                Text(
                    text = "1. Pilih Divisi Pabrik",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                .clickable { selectedDeptId = dept.id.value }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    fontSize = 13.sp,
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

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Lingkup Jabatan di Divisi Ini
                Text(
                    text = "2. Lingkup Jabatan di Divisi Ini",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                            .padding(10.dp),
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
                            .padding(10.dp),
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

                // If specific roles mode, show checklist of roles
                if (isSpecificRolesMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        roles.forEach { role ->
                            val isChecked = selectedRoleIds.contains(role.id.value)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        selectedRoleIds = if (isChecked) {
                                            selectedRoleIds - role.id.value
                                        } else {
                                            selectedRoleIds + role.id.value
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = role.name,
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Level Akses & Jangkauan Data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Level Akses
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Level Akses",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

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
                        Spacer(modifier = Modifier.height(6.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Batal & Konfirmasi
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
