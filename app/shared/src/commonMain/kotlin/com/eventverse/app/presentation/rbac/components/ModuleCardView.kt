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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.presentation.theme.WeMadeColors

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(modules, key = { it.code }) { module ->
            val moduleAssignments = assignments[module] ?: emptyList()

            SingleModuleCard(
                module = module,
                assignments = moduleAssignments,
                departments = departments,
                roles = roles,
                onAddDepartmentClick = { onOpenAssignModal(module, null) },
                onEditAssignmentClick = { assignment -> onOpenAssignModal(module, assignment) },
                onRemoveAssignmentClick = { deptId -> onRemoveAssignment(module, deptId) }
            )
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
        border = BorderStroke(1.dp, WeMadeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Row: Category Badge + Module Name + Dynamic Scope Capability Chip
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(WeMadeColors.PrimaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = module.category.displayName.take(3).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.PrimaryDark
                        )
                    }

                    Column {
                        Text(
                            text = module.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Dynamic Scope Capability Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (module.isGlobalOnly) Color(0xFFEFF6FF) else Color(0xFFF0FDF4))
                        .border(
                            1.dp,
                            if (module.isGlobalOnly) Color(0xFFBFDBFE) else Color(0xFFBBF7D0),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (module.isGlobalOnly) "🌐 Seluruh Pabrik" else "👥 Multi-Jangkauan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (module.isGlobalOnly) Color(0xFF1D4ED8) else Color(0xFF15803D)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Middle Section: Assigned Departments
            Text(
                text = "Divisi Yang Memiliki Akses (${assignments.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (assignments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Belum ada divisi yang ditugaskan ke modul ini. Klik tombol di bawah untuk menambahkan hak akses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    assignments.forEach { assign ->
                        val dept = departments.find { it.id.value == assign.departmentId }
                        val deptColor = Color(dept?.colorHex ?: 0xFF475569)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, WeMadeColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Dept info & role scope
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(deptColor)
                                )

                                Text(
                                    text = assign.departmentName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )

                                // Access Level Pill
                                val (bgLevel, textLevel) = when (assign.accessLevel) {
                                    AccessLevel.NONE -> Color(0xFFE2E8F0) to Color(0xFF475569)
                                    AccessLevel.VIEW -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
                                    AccessLevel.OPERATE -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                                    AccessLevel.MANAGE -> Color(0xFFD1FAE5) to Color(0xFF047857)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(bgLevel)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = assign.accessLevel.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textLevel
                                    )
                                }

                                // Role Specificity info
                                val roleText = if (assign.appliesToAllRoles) {
                                    "Semua Jabatan"
                                } else {
                                    val count = assign.specificRoleIds.size
                                    "$count Jabatan Spesifik"
                                }

                                Text(
                                    text = "• $roleText",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )

                                // Scope info
                                val scopeText = if (module.isGlobalOnly) "Semua Data Pabrik" else assign.scope.shortLabel
                                Text(
                                    text = "• Jangkauan: $scopeText",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Right: Edit & Remove actions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(
                                    onClick = { onEditAssignmentClick(assign) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Edit", fontSize = 11.sp, color = WeMadeColors.Primary)
                                }

                                TextButton(
                                    onClick = { onRemoveAssignmentClick(assign.departmentId) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Hapus", fontSize = 11.sp, color = WeMadeColors.Error)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button to Add Division
            OutlinedButton(
                onClick = onAddDepartmentClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, WeMadeColors.Primary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WeMadeColors.Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+ Tambah Divisi ke Modul Ini",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
