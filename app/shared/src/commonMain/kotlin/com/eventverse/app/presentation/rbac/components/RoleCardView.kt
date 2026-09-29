package com.eventverse.app.presentation.rbac.components

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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    ActiveTenantPack.current.moduleIds.forEach { mod ->
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
    if (roles.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum ada jabatan yang terdaftar atau sesuai pencarian.",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            contentPadding = PaddingValues(bottom = ClaySpacing.Xxl)
        ) {
            items(roles, key = { it.id.value }) { role ->
                val dept = remember(role, departments) {
                    DynamicRbacViewModel.resolveDepartmentForRole(role, departments)
                }

                val accessibleModules = remember(role, dept, assignments) {
                    resolveAccessibleModulesForRole(role, dept, assignments)
                }

                RoleRowCard(
                    role = role,
                    department = dept,
                    accessibleModules = accessibleModules,
                    onAddModuleClick = { onOpenAssignModal(role, dept, null, null) },
                    onEditAssignmentClick = { mod, assignment -> onOpenAssignModal(role, dept, assignment, mod) },
                    onRemoveAssignmentClick = { mod, assignKey -> onRemoveAssignment(mod, assignKey) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RoleRowCard(
    role: CustomRole,
    department: Department?,
    accessibleModules: List<RoleAccessibleModule>,
    onAddModuleClick: () -> Unit,
    onEditAssignmentClick: (BusinessModule, DepartmentModuleAssignment) -> Unit,
    onRemoveAssignmentClick: (BusinessModule, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    val deptColor = department?.let { Color(it.colorHex) } ?: WeMadeColors.Primary

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        shadowColor = WeMadeColors.Outline,
        offset = ClayOffset.Small,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Header Row: Role identity, Department badge, module counter, action buttons & collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    // Avatar / Dept color badge
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clayFlat(
                                shape = CircleShape,
                                background = deptColor.copy(alpha = 0.15f),
                                outline = deptColor,
                                borderWidth = 1.5.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(deptColor)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Text(
                                text = role.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            ClayTag(
                                text = if (department != null) "Divisi: ${department.displayName}" else "Direksi / Lintas Divisi",
                                tint = deptColor,
                                fontSize = 10.5.sp
                            )

                            ClayTag(
                                text = "${accessibleModules.size} Modul",
                                tint = if (accessibleModules.isNotEmpty()) deptColor else WeMadeColors.OnSurfaceMuted,
                                fontSize = 10.5.sp
                            )
                        }

                        if (role.description.isNotBlank()) {
                            Text(
                                text = role.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = WeMadeColors.OnSurfaceMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Action buttons on the right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayButton(
                        text = "+ Tambah Akses Modul",
                        leading = { IconPlus(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurface) },
                        onClick = onAddModuleClick,
                        style = ClayButtonStyle.Secondary,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    )

                    ClayActionSurface(
                        onClick = { isExpanded = !isExpanded },
                        containerColor = WeMadeColors.SurfaceMuted,
                        outlineColor = WeMadeColors.Border,
                        offset = ClayOffset.Flat,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Sembunyikan" else "Buka Detail (${accessibleModules.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WeMadeColors.OnSurface
                            )
                            if (isExpanded) {
                                IconChevronUp(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurface)
                            } else {
                                IconChevronDown(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurface)
                            }
                        }
                    }
                }
            }

            // Expanded Module Grid Section
            if (isExpanded) {
                HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)

                if (accessibleModules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Border
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Jabatan ini belum memiliki hak akses ke modul apa pun.",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val moduleColumns = when {
                            maxWidth >= 1200.dp -> 4
                            maxWidth >= 850.dp -> 3
                            maxWidth >= 550.dp -> 2
                            else -> 1
                        }

                        val moduleRows = remember(accessibleModules, moduleColumns) {
                            accessibleModules.chunked(moduleColumns)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            moduleRows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            RoleModuleItemCard(
                                                item = item,
                                                onEdit = { item.assignment?.let { onEditAssignmentClick(item.module, it) } },
                                                onRemove = { item.assignment?.let { onRemoveAssignmentClick(item.module, it.assignmentKey) } }
                                            )
                                        }
                                    }

                                    val remaining = moduleColumns - rowItems.size
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
            }
        }
    }
}

@Composable
private fun RoleModuleItemCard(
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
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Line 1: Module Name & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.module.displayName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item.isSpecificToRole && item.assignment != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ClayActionSurface(
                            onClick = onEdit,
                            containerColor = WeMadeColors.PrimaryContainer,
                            outlineColor = WeMadeColors.Primary,
                            offset = ClayOffset.Flat,
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            IconEdit(modifier = Modifier.size(9.dp), color = WeMadeColors.Primary)
                            Text("Edit", fontSize = 9.5.sp, color = WeMadeColors.Primary, fontWeight = FontWeight.Bold)
                        }

                        ClayActionSurface(
                            onClick = onRemove,
                            containerColor = WeMadeColors.ErrorBg,
                            outlineColor = WeMadeColors.Error,
                            offset = ClayOffset.Flat,
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            IconTrash(modifier = Modifier.size(9.dp), color = WeMadeColors.Error)
                            Text("Hapus", fontSize = 9.5.sp, color = WeMadeColors.Error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Line 2: Origin text & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.isSpecificToRole) "Khusus Jabatan" else "Dari ${item.departmentName}",
                    fontSize = 10.sp,
                    color = if (item.isSpecificToRole) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted,
                    fontWeight = if (item.isSpecificToRole) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ClayTag(
                        text = item.accessLevel.displayName,
                        tint = levelColor,
                        fontSize = 8.5.sp
                    )

                    ClayTag(
                        text = if (item.module.isGlobalOnly) "Shared" else item.scope.shortLabel,
                        tint = WeMadeColors.Primary,
                        fontSize = 8.5.sp
                    )
                }
            }
        }
    }
}
