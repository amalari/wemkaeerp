package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.rbac.DynamicRbacViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

data class RoleAccessibleModule(
    val module: BusinessModule,
    val accessLevel: AccessLevel,
    val scope: DataScope,
    val isSpecificToRole: Boolean,
    val departmentName: String,
    val assignment: DepartmentModuleAssignment?
)

/**
 * Menghitung daftar modul yang dapat diakses oleh sebuah jabatan, menggabungkan:
 * 1. Penugasan khusus jabatan (DepartmentModuleAssignment spesifik roleId)
 * 2. Penugasan divisi penuh (DepartmentModuleAssignment appliesToAllRoles)
 * 3. Wewenang bawaan jabatan (CustomRole.modulePermissions)
 *
 * Menggunakan prinsip Highest Privilege Union yang identik dengan AccessDecisionEngine.
 */
fun resolveAccessibleModulesForRole(
    role: CustomRole,
    dept: Department?,
    assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>
): List<RoleAccessibleModule> {
    val list = mutableListOf<RoleAccessibleModule>()
    val roleIdStr = role.id.value
    val deptIdStr = dept?.id?.value

    BusinessModule.entries.forEach { mod ->
        val assignList = assignments[mod].orEmpty()
        val specific = assignList.find { it.specificRoleIds.contains(roleIdStr) }
        val deptWide = if (deptIdStr != null) {
            assignList.find { it.departmentId == deptIdStr && it.appliesToAllRoles }
        } else null

        val roleCfg = role.getAccess(mod)
        val hasRoleAccess = roleCfg.isAccessible

        when {
            // 1. Penugasan khusus untuk jabatan ini pada modul ini
            specific != null -> {
                val roleWins = hasRoleAccess && roleCfg.level.weight > specific.accessLevel.weight
                list.add(
                    RoleAccessibleModule(
                        module = mod,
                        accessLevel = if (roleWins) roleCfg.level else specific.accessLevel,
                        scope = if (roleWins) roleCfg.scope else specific.scope,
                        isSpecificToRole = true,
                        departmentName = specific.departmentName,
                        assignment = specific
                    )
                )
            }

            // 2. Penugasan tingkat divisi (seluruh anggota divisi)
            deptWide != null -> {
                if (hasRoleAccess && roleCfg.level.weight > deptWide.accessLevel.weight) {
                    // Wewenang bawaan jabatan lebih tinggi dari penugasan divisi
                    list.add(
                        RoleAccessibleModule(
                            module = mod,
                            accessLevel = roleCfg.level,
                            scope = roleCfg.scope,
                            isSpecificToRole = true,
                            departmentName = dept?.displayName ?: "Bawaan Jabatan",
                            assignment = null
                        )
                    )
                } else {
                    list.add(
                        RoleAccessibleModule(
                            module = mod,
                            accessLevel = deptWide.accessLevel,
                            scope = deptWide.scope,
                            isSpecificToRole = false,
                            departmentName = deptWide.departmentName,
                            assignment = deptWide
                        )
                    )
                }
            }

            // 3. Hak akses berasal langsung dari wewenang bawaan jabatan
            hasRoleAccess -> {
                list.add(
                    RoleAccessibleModule(
                        module = mod,
                        accessLevel = roleCfg.level,
                        scope = roleCfg.scope,
                        isSpecificToRole = true,
                        departmentName = dept?.displayName ?: "Bawaan Jabatan",
                        assignment = null
                    )
                )
            }
        }
    }

    return list
}

@Composable
fun RoleCardList(
    roles: List<CustomRole>,
    departments: List<Department>,
    assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>,
    onOpenAssignModal: (CustomRole, Department?, DepartmentModuleAssignment?, BusinessModule?) -> Unit,
    onRemoveAssignment: (BusinessModule, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1400.dp -> 4
            maxWidth >= 1000.dp -> 3
            maxWidth >= 650.dp -> 2
            else -> 1
        }

        val rows = remember(roles, columns) {
            roles.chunked(columns)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            contentPadding = PaddingValues(bottom = ClaySpacing.Xxl)
        ) {
            items(rows, key = { row -> row.joinToString("-") { it.id.value } }) { rowRoles ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    rowRoles.forEach { role ->
                        val dept = remember(role, departments) {
                            DynamicRbacViewModel.resolveDepartmentForRole(role, departments)
                        }

                        val accessibleModules = remember(role, dept, assignments) {
                            resolveAccessibleModulesForRole(role, dept, assignments)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SingleRoleCard(
                                role = role,
                                department = dept,
                                accessibleModules = accessibleModules,
                                onAddModuleClick = { onOpenAssignModal(role, dept, null, null) },
                                onEditAssignmentClick = { mod, assignment -> onOpenAssignModal(role, dept, assignment, mod) },
                                onRemoveAssignmentClick = { mod, assignKey -> onRemoveAssignment(mod, assignKey) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    val remaining = columns - rowRoles.size
                    if (remaining > 0) {
                        repeat(remaining) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SingleRoleCard(
    role: CustomRole,
    department: Department?,
    accessibleModules: List<RoleAccessibleModule>,
    onAddModuleClick: () -> Unit,
    onEditAssignmentClick: (BusinessModule, DepartmentModuleAssignment) -> Unit,
    onRemoveAssignmentClick: (BusinessModule, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deptColor = department?.let { Color(it.colorHex) } ?: WeMadeColors.Primary

    ClayCard(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        shadowColor = WeMadeColors.Outline,
        offset = ClayOffset.Small,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // Header: Role Name & Department Data
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = role.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Data Divisi info jelas
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(deptColor)
                    )
                    Text(
                        text = if (department != null) "Divisi: ${department.displayName}" else "Direksi / Lintas Divisi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = deptColor
                    )
                }
            }

            ClayTag(
                text = "${accessibleModules.size} Modul",
                tint = if (accessibleModules.isNotEmpty()) deptColor else WeMadeColors.OnSurfaceMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = role.description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

        // List of accessible modules
        Text(
            text = "Modul yang Dapat Diakses (${accessibleModules.size}):",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (accessibleModules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border
                    )
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Jabatan ini belum memiliki hak akses ke modul apa pun.",
                    fontSize = 11.5.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                accessibleModules.forEach { item ->
                    RoleModuleItemRow(
                        item = item,
                        onEdit = { item.assignment?.let { onEditAssignmentClick(item.module, it) } },
                        onRemove = { item.assignment?.let { onRemoveAssignmentClick(item.module, it.assignmentKey) } }
                    )
                }
            }
        }

        // Spacer weight(1f) mendorong tombol ke posisi paling bawah kartu persis seperti Per Modul
        Spacer(modifier = Modifier.weight(1f))

        // Sticky Bottom Footer Button
        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

        ClayButton(
            text = "+ Tambahkan Akses Modul",
            onClick = onAddModuleClick,
            style = ClayButtonStyle.Secondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = ClaySpacing.Sm)
        )
    }
}

@Composable
private fun RoleModuleItemRow(
    item: RoleAccessibleModule,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val levelColor = when (item.accessLevel) {
        AccessLevel.NONE    -> WeMadeColors.OnSurfaceMuted
        AccessLevel.VIEW    -> WeMadeColors.Info
        AccessLevel.OPERATE -> WeMadeColors.Warning
        AccessLevel.MANAGE  -> WeMadeColors.Success
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Line 1: Module Name
            Text(
                text = item.module.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 2: Origin text
            Text(
                text = if (item.isSpecificToRole) "Khusus Jabatan Ini" else "Dari ${item.departmentName} (Full Divisi)",
                fontSize = 10.5.sp,
                color = if (item.isSpecificToRole) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted,
                fontWeight = if (item.isSpecificToRole) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Line 3: Bottom Bar (Left: Tags, Right: Actions if specific)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Access Level & Scope Tags
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ClayTag(
                        text = item.accessLevel.displayName,
                        tint = levelColor,
                        fontSize = 9.sp
                    )

                    ClayTag(
                        text = if (item.module.isGlobalOnly) "Shared" else item.scope.shortLabel,
                        tint = WeMadeColors.Primary,
                        fontSize = 9.sp
                    )
                }

                // Right: Actions if editable (separated to right side)
                if (item.isSpecificToRole && item.assignment != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ClayActionSurface(
                            onClick = onEdit,
                            containerColor = WeMadeColors.PrimaryContainer,
                            outlineColor = WeMadeColors.Primary,
                            offset = ClayOffset.Flat,
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            IconEdit(modifier = Modifier.size(10.dp), color = WeMadeColors.Primary)
                            Text("Edit", fontSize = 10.sp, color = WeMadeColors.Primary, fontWeight = FontWeight.Bold)
                        }

                        ClayActionSurface(
                            onClick = onRemove,
                            containerColor = WeMadeColors.ErrorBg,
                            outlineColor = WeMadeColors.Error,
                            offset = ClayOffset.Flat,
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            IconTrash(modifier = Modifier.size(10.dp), color = WeMadeColors.Error)
                            Text("Hapus", fontSize = 10.sp, color = WeMadeColors.Error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
