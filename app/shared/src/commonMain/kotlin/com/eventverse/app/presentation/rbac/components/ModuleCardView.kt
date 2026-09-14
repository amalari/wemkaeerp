package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment
import com.eventverse.app.domain.rbac.ModuleCategory
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.module.ModuleIcon
import com.eventverse.app.presentation.theme.WeMadeColors

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember

@Composable
fun ModuleCardList(
    modules: List<BusinessModule>,
    assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>,
    departments: List<Department>,
    roles: List<CustomRole>,
    onOpenAssignModal: (BusinessModule, DepartmentModuleAssignment?) -> Unit,
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

        val rows = remember(modules, columns) {
            modules.chunked(columns)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            contentPadding = PaddingValues(bottom = ClaySpacing.Xxl)
        ) {
            items(rows, key = { row -> row.joinToString("-") { it.code } }) { rowModules ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    rowModules.forEach { module ->
                        val moduleAssignments = assignments[module] ?: emptyList()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SingleModuleCard(
                                module = module,
                                assignments = moduleAssignments,
                                departments = departments,
                                roles = roles,
                                onAddDepartmentClick = { onOpenAssignModal(module, null) },
                                onEditAssignmentClick = { assignment -> onOpenAssignModal(module, assignment) },
                                onRemoveAssignmentClick = { assignKey -> onRemoveAssignment(module, assignKey) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Isi sisa kolom jika baris terakhir tidak genap penuh
                    val remaining = columns - rowModules.size
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
fun SingleModuleCard(
    module: BusinessModule,
    assignments: List<DepartmentModuleAssignment>,
    departments: List<Department>,
    roles: List<CustomRole>,
    onAddDepartmentClick: () -> Unit,
    onEditAssignmentClick: (DepartmentModuleAssignment) -> Unit,
    onRemoveAssignmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
        // Top Row: Icon Tile + Module Name/Desc + Scope Tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.Top
        ) {
            // Icon Tile (category-tinted clay tile)
            val categoryColor = when (module.category) {
                ModuleCategory.GOVERNANCE -> WeMadeColors.Purple
                ModuleCategory.FOUNDATION -> WeMadeColors.Teal
                ModuleCategory.SALES      -> WeMadeColors.Primary
                ModuleCategory.LOGISTICS  -> WeMadeColors.Warning
                ModuleCategory.TECHNICAL  -> WeMadeColors.Info
                ModuleCategory.PRODUCTION -> WeMadeColors.Accent
                ModuleCategory.QUALITY    -> WeMadeColors.Success
            }
            val categoryBg = when (module.category) {
                ModuleCategory.GOVERNANCE -> WeMadeColors.PurpleBg
                ModuleCategory.FOUNDATION -> WeMadeColors.TealBg
                ModuleCategory.SALES      -> WeMadeColors.PrimaryContainer
                ModuleCategory.LOGISTICS  -> WeMadeColors.WarningBg
                ModuleCategory.TECHNICAL  -> WeMadeColors.TealBg
                ModuleCategory.PRODUCTION -> WeMadeColors.AccentLight
                ModuleCategory.QUALITY    -> WeMadeColors.SuccessBg
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clayFlat(
                        shape = ClayShapes.Tile,
                        background = categoryBg,
                        outline = categoryColor,
                        borderWidth = ClayBorder.Medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                ModuleIcon(
                    iconKey = module.iconKey,
                    modifier = Modifier.size(22.dp),
                    color = categoryColor
                )
            }

            // Name & description
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = module.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Scope Tag (top-right)
                    if (module.isGlobalOnly) {
                        ClayTag(text = "Seluruh Pabrik", tint = WeMadeColors.Primary, fontSize = 9.sp)
                    } else {
                        ClayTag(text = "Multi-Jangkauan", tint = WeMadeColors.Success, fontSize = 9.sp)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = WeMadeColors.OnSurfaceMuted,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // Middle Section: 2 Separate Assignment Lists (Divisi vs Jabatan Spesifik)
        val divisiAssignments = assignments.filter { it.appliesToAllRoles }
        val jabatanAssignments = assignments.filter { !it.appliesToAllRoles }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // ── Section 1: Hak Akses Divisi (Semua Jabatan) ───────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hak Akses Divisi (${divisiAssignments.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTag(text = "Full Divisi", tint = WeMadeColors.Primary, fontSize = 8.5.sp)
                }

                if (divisiAssignments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Border,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(horizontal = ClaySpacing.Md, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Belum ada divisi penuh yang ditugaskan.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    divisiAssignments.forEach { assign ->
                        AssignmentRowItem(
                            assign = assign,
                            departments = departments,
                            subtitle = "Semua Jabatan (Full Divisi)",
                            onEdit = { onEditAssignmentClick(assign) },
                            onRemove = { onRemoveAssignmentClick(assign.assignmentKey) }
                        )
                    }
                }
            }

            // ── Section 2: Hak Akses Jabatan Spesifik ─────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hak Akses Jabatan Spesifik (${jabatanAssignments.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTag(text = "Per Jabatan", tint = WeMadeColors.Accent, fontSize = 8.5.sp)
                }

                if (jabatanAssignments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Border,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(horizontal = ClaySpacing.Md, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Belum ada jabatan khusus yang ditugaskan.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    jabatanAssignments.forEach { assign ->
                        val roleTitle = resolveSpecificRoleTitle(assign.specificRoleIds, roles)
                        AssignmentRowItem(
                            assign = assign,
                            departments = departments,
                            subtitle = roleTitle,
                            onEdit = { onEditAssignmentClick(assign) },
                            onRemove = { onRemoveAssignmentClick(assign.assignmentKey) }
                        )
                    }
                }
            }
        }

        // Spacer weight(1f) mendorong footer selalu ke bawah card
        Spacer(modifier = Modifier.weight(1f))

        // ── Sticky Bottom Footer ─────────────────────────────────────────────
        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

        ClayButton(
            text = "+ Tambahkan Akses",
            onClick = onAddDepartmentClick,
            style = ClayButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            contentPadding = PaddingValues(vertical = ClaySpacing.Sm),
        )
    }
}

private fun resolveSpecificRoleTitle(roleIds: Set<String>, roles: List<CustomRole>): String {
    if (roleIds.isEmpty()) return "Semua Jabatan"
    return roleIds.mapNotNull { roleId ->
        roles.find { it.id.value == roleId }?.name ?: when (roleId) {
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
private fun AssignmentRowItem(
    assign: DepartmentModuleAssignment,
    departments: List<Department>,
    subtitle: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val dept = departments.find { it.id.value == assign.departmentId }
    val deptColor = dept?.let { Color(it.colorHex) } ?: WeMadeColors.OnSurfaceMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.BackgroundWarm,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Dept info & role scope
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(deptColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = assign.departmentName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Access Level Tag
                    val levelTint = when (assign.accessLevel) {
                        AccessLevel.NONE    -> WeMadeColors.OnSurfaceMuted
                        AccessLevel.VIEW    -> WeMadeColors.Info
                        AccessLevel.OPERATE -> WeMadeColors.Warning
                        AccessLevel.MANAGE  -> WeMadeColors.Success
                    }

                    ClayTag(
                        text = assign.accessLevel.displayName,
                        tint = levelTint,
                        fontSize = 8.5.sp
                    )
                }

                // Subtitle: nama jabatan atau "Semua Jabatan"
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = WeMadeColors.PrimaryDark,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Edit & Remove actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ClayActionSurface(
                onClick = onEdit,
                containerColor = WeMadeColors.PrimaryContainer,
                outlineColor = WeMadeColors.Primary,
                offset = ClayOffset.Flat,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
            ) {
                IconEdit(modifier = Modifier.size(10.dp), color = WeMadeColors.Primary)
                Text("Edit", fontSize = 10.5.sp, color = WeMadeColors.Primary, fontWeight = FontWeight.Bold)
            }

            ClayActionSurface(
                onClick = onRemove,
                containerColor = WeMadeColors.ErrorBg,
                outlineColor = WeMadeColors.Error,
                offset = ClayOffset.Flat,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
            ) {
                IconTrash(modifier = Modifier.size(10.dp), color = WeMadeColors.Error)
                Text("Hapus", fontSize = 10.5.sp, color = WeMadeColors.Error, fontWeight = FontWeight.Bold)
            }
        }
    }
}
