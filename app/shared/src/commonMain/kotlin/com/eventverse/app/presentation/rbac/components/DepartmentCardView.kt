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
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun DepartmentCardList(
    departments: List<Department>,
    assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>,
    roles: List<CustomRole>,
    onOpenAssignModal: (Department, DepartmentModuleAssignment?, BusinessModule?) -> Unit,
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

        val rows = remember(departments, columns) {
            departments.chunked(columns)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            contentPadding = PaddingValues(bottom = ClaySpacing.Xxl)
        ) {
            items(rows, key = { row -> row.joinToString("-") { it.id.value } }) { rowDepts ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    rowDepts.forEach { dept ->
                        val deptAssignments = remember(assignments, dept) {
                            assignments.flatMap { (module, assignList) ->
                                assignList.filter { it.departmentId == dept.id.value }
                                    .map { assignment -> module to assignment }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SingleDepartmentCard(
                                department = dept,
                                assignedModules = deptAssignments,
                                roles = roles,
                                onAddModuleClick = { onOpenAssignModal(dept, null, null) },
                                onEditAssignmentClick = { module, assignment -> onOpenAssignModal(dept, assignment, module) },
                                onRemoveAssignmentClick = { module, assignKey -> onRemoveAssignment(module, assignKey) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Isi sisa kolom jika baris terakhir tidak genap penuh
                    val remaining = columns - rowDepts.size
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
fun SingleDepartmentCard(
    department: Department,
    assignedModules: List<Pair<BusinessModule, DepartmentModuleAssignment>>,
    roles: List<CustomRole>,
    onAddModuleClick: () -> Unit,
    onEditAssignmentClick: (BusinessModule, DepartmentModuleAssignment) -> Unit,
    onRemoveAssignmentClick: (BusinessModule, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deptColor = Color(department.colorHex)

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
        // Header: Department info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
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
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(deptColor)
                    )
                }

                Column {
                    Text(
                        text = department.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Kode: ${department.code.uppercase()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            ClayTag(
                text = "${assignedModules.size} Modul",
                tint = if (assignedModules.isNotEmpty()) deptColor else WeMadeColors.OnSurfaceMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

        // List of assigned modules
        Text(
            text = "Akses Modul Terhubung (${assignedModules.size}):",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (assignedModules.isEmpty()) {
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
                    text = "Belum ada modul yang ditugaskan ke divisi ini.",
                    fontSize = 11.5.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                assignedModules.forEach { (module, assignment) ->
                    DepartmentModuleItemRow(
                        module = module,
                        assignment = assignment,
                        roles = roles,
                        onEdit = { onEditAssignmentClick(module, assignment) },
                        onRemove = { onRemoveAssignmentClick(module, assignment.assignmentKey) }
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
private fun DepartmentModuleItemRow(
    module: BusinessModule,
    assignment: DepartmentModuleAssignment,
    roles: List<CustomRole>,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val levelColor = when (assignment.accessLevel) {
        AccessLevel.NONE    -> WeMadeColors.OnSurfaceMuted
        AccessLevel.VIEW    -> WeMadeColors.Info
        AccessLevel.OPERATE -> WeMadeColors.Warning
        AccessLevel.MANAGE  -> WeMadeColors.Success
    }

    val roleScopeText = if (assignment.appliesToAllRoles) {
        "Semua Jabatan (Full Divisi)"
    } else {
        val names = assignment.specificRoleIds.mapNotNull { id -> roles.find { it.id.value == id }?.name }
        if (names.isEmpty()) "Spesifik Jabatan" else names.joinToString(", ")
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
                text = module.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 2: Scope text (e.g. Semua Jabatan)
            Text(
                text = roleScopeText,
                fontSize = 10.5.sp,
                color = WeMadeColors.PrimaryDark,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Line 3: Bottom Bar (Left: Tags, Right: Actions)
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
                        text = assignment.accessLevel.displayName,
                        tint = levelColor,
                        fontSize = 9.sp
                    )

                    ClayTag(
                        text = if (module.isGlobalOnly) "Shared" else assignment.scope.shortLabel,
                        tint = WeMadeColors.Primary,
                        fontSize = 9.sp
                    )
                }

                // Right: Edit & Remove Action Buttons (separated to right side)
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
