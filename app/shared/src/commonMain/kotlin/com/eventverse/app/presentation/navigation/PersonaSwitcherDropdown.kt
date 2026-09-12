package com.eventverse.app.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.rbac.DynamicRbacViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

private enum class PersonaTab(val label: String) {
    ROLES("Berdasarkan Jabatan (Role)"),
    EMPLOYEES("Karyawan Riil (Org Chart)")
}

/**
 * Capsule di top bar untuk berpindah persona pengujian RBAC via Modal Dialog luas.
 *
 * Menggantikan popover dropdown kecil agar pengguna leluasa melihat peran & divisi
 * secara terintegrasi (format: `{Jabatan} - {Divisi}`) serta mendukung pencarian real-time.
 */
@Composable
fun PersonaSwitcherDropdown(
    activePersona: TestingPersona?,
    employees: List<OrgNode>,
    departments: List<Department>,
    roles: List<CustomRole>,
    tenantId: TenantId,
    tenantSlug: String,
    isAuditViewEnabled: Boolean,
    onAuditViewChange: (Boolean) -> Unit,
    onApplyPersona: (TestingPersona) -> Unit,
    onResetSuperadmin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isModalOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ClayActionSurface(onClick = { isModalOpen = true }) {
            Column {
                Text(
                    text = activePersona?.displayLabel ?: "Pilih Persona Pengujian",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activePersona?.let { "${it.roleTitle} · ${it.departmentName}" } ?: "Belum ada persona aktif",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconChevronDown(
                modifier = Modifier.size(10.dp),
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        if (isModalOpen) {
            PersonaTestingModal(
                activePersona = activePersona,
                employees = employees,
                departments = departments,
                roles = roles,
                tenantId = tenantId,
                tenantSlug = tenantSlug,
                isAuditViewEnabled = isAuditViewEnabled,
                onAuditViewChange = onAuditViewChange,
                onApplyPersona = { persona ->
                    onApplyPersona(persona)
                    isModalOpen = false
                },
                onResetSuperadmin = onResetSuperadmin?.let { action ->
                    {
                        action()
                        isModalOpen = false
                    }
                },
                onDismiss = { isModalOpen = false }
            )
        }
    }
}

@Composable
fun PersonaTestingModal(
    activePersona: TestingPersona?,
    employees: List<OrgNode>,
    departments: List<Department>,
    roles: List<CustomRole>,
    tenantId: TenantId,
    tenantSlug: String,
    isAuditViewEnabled: Boolean,
    onAuditViewChange: (Boolean) -> Unit,
    onApplyPersona: (TestingPersona) -> Unit,
    onResetSuperadmin: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(PersonaTab.ROLES) }
    var searchQuery by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    val effectiveRoles = remember(roles, tenantId) {
        if (roles.isEmpty()) CustomRole.createFactoryPresets(tenantId) else roles
    }

    val personas = remember(employees, roles, tenantId, tenantSlug) {
        if (employees.isEmpty()) {
            TestingPersona.factoryPresets(tenantId, tenantSlug)
        } else {
            TestingPersona.fromDirectory(employees, roles, tenantId, tenantSlug)
        }
    }

    val filteredRoles = remember(effectiveRoles, departments, searchQuery) {
        if (searchQuery.isBlank()) {
            effectiveRoles
        } else {
            val q = searchQuery.trim().lowercase()
            effectiveRoles.filter { role ->
                val dept = DynamicRbacViewModel.resolveDepartmentForRole(role, departments)
                val deptName = dept?.displayName?.lowercase().orEmpty()
                val deptCode = dept?.code?.lowercase().orEmpty()
                role.name.lowercase().contains(q) ||
                    deptName.contains(q) ||
                    deptCode.contains(q) ||
                    role.description.lowercase().contains(q)
            }
        }
    }

    val filteredPersonas = remember(personas, searchQuery) {
        if (searchQuery.isBlank()) {
            personas
        } else {
            val q = searchQuery.trim().lowercase()
            personas.filter { persona ->
                persona.name.lowercase().contains(q) ||
                    persona.roleTitle.lowercase().contains(q) ||
                    persona.departmentName.lowercase().contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .widthIn(min = 900.dp, max = 1200.dp)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.88f),
            shape = ClayShapes.Panel,
            containerColor = WeMadeColors.Surface,
            outlineColor = WeMadeColors.Outline,
            shadowColor = WeMadeColors.Outline,
            offset = ClayOffset.Rest,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                // Header Dialog: Judul & Penjelasan (Tanpa Icon & Tanpa Tutup Button)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Persona Pengujian RBAC",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Simulasikan hak akses & navigasi menu sesuai peran atau karyawan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Active Persona Banner (jika ada simulasi aktif) - tanpa icon
                if (activePersona != null) {
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = WeMadeColors.PrimaryContainer,
                        outlineColor = WeMadeColors.Primary,
                        contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
                        offset = ClayOffset.Flat
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Persona Aktif: ${activePersona.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = "${activePersona.roleTitle} - ${activePersona.departmentName}",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClayTag(text = "Simulasi Berjalan", tint = WeMadeColors.Primary)
                                if (onResetSuperadmin != null) {
                                    ClayButton(
                                        text = "Kembali ke Superadmin",
                                        onClick = onResetSuperadmin,
                                        style = ClayButtonStyle.Primary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar & Tab Navigation (Tanpa Icon)
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = if (tab == PersonaTab.ROLES) {
                            "Cari jabatan atau divisi... (mis. Sales, PPIC, Gudang)"
                        } else {
                            "Cari nama karyawan atau jabatan... (mis. Budi, Rian)"
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PersonaTab.entries.forEach { entry ->
                            ClayButton(
                                text = entry.label,
                                onClick = { tab = entry },
                                style = if (tab == entry) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Scrollable List Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (tab) {
                        PersonaTab.ROLES -> {
                            RolesListSection(
                                roles = filteredRoles,
                                departments = departments,
                                tenantId = tenantId,
                                tenantSlug = tenantSlug,
                                activePersona = activePersona,
                                customName = customName,
                                onCustomNameChange = { customName = it },
                                searchQuery = searchQuery,
                                onClearSearch = { searchQuery = "" },
                                onSelect = onApplyPersona,
                                onResetSuperadmin = onResetSuperadmin
                            )
                        }
                        PersonaTab.EMPLOYEES -> {
                            EmployeesListSection(
                                personas = filteredPersonas,
                                activePersona = activePersona,
                                searchQuery = searchQuery,
                                onClearSearch = { searchQuery = "" },
                                onSelect = onApplyPersona
                            )
                        }
                    }
                }

                // Footer Section: Mode Audit Saja (Tanpa Tombol Tutup)
                HorizontalDivider(
                    thickness = ClayBorder.Medium,
                    color = WeMadeColors.Border
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mode Audit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Tampilkan modul terkunci dengan status gembok",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayButton(
                        text = if (isAuditViewEnabled) "Aktif" else "Nonaktif",
                        onClick = { onAuditViewChange(!isAuditViewEnabled) },
                        style = if (isAuditViewEnabled) ClayButtonStyle.Accent else ClayButtonStyle.Secondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RolesListSection(
    roles: List<CustomRole>,
    departments: List<Department>,
    tenantId: TenantId,
    tenantSlug: String,
    activePersona: TestingPersona?,
    customName: String,
    onCustomNameChange: (String) -> Unit,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onSelect: (TestingPersona) -> Unit,
    onResetSuperadmin: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayTextField(
            value = customName,
            onValueChange = onCustomNameChange,
            label = "Nama Persona (Opsional)",
            placeholder = "Kosongkan untuk memakai nama jabatan (mis. Achmad)",
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val q = searchQuery.trim().lowercase()
        val showSuperAdminCard = onResetSuperadmin != null && (
            q.isEmpty() ||
            "superadmin".contains(q) ||
            "platform".contains(q) ||
            "admin".contains(q) ||
            "bypass".contains(q)
        )

        if (roles.isEmpty() && !showSuperAdminCard) {
            EmptyStateView(
                message = if (searchQuery.isNotBlank()) {
                    "Tidak ada jabatan atau divisi yang cocok dengan \"$searchQuery\"."
                } else {
                    "Tidak ada jabatan yang tersedia."
                },
                showClear = searchQuery.isNotBlank(),
                onClear = onClearSearch
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                // Platform Superadmin bypass card
                if (showSuperAdminCard && onResetSuperadmin != null) {
                    val isSuperAdminActive = activePersona?.isOwnerOrSuperAdmin == true

                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (isSuperAdminActive) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                        outlineColor = if (isSuperAdminActive) WeMadeColors.Primary else WeMadeColors.Outline,
                        contentPadding = PaddingValues(ClaySpacing.Md),
                        offset = ClayOffset.Small,
                        onClick = onResetSuperadmin
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                                ) {
                                    Text(
                                        text = "Platform Superadmin",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "-",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                    ClayTag(
                                        text = "Akses Penuh / Bypass",
                                        tint = WeMadeColors.Primary
                                    )
                                }
                                Text(
                                    text = "Kembali ke akun Platform Superadmin tanpa batasan wewenang atau divisi.",
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                modifier = Modifier.padding(start = ClaySpacing.Md)
                            ) {
                                if (isSuperAdminActive) {
                                    ClayTag(text = "Aktif", tint = WeMadeColors.Primary)
                                }
                                ClayTag(
                                    text = if (isSuperAdminActive) "Terpilih" else "Pilih",
                                    tint = if (isSuperAdminActive) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }

                roles.forEach { role ->
                    val dept = DynamicRbacViewModel.resolveDepartmentForRole(role, departments)
                    val deptName = dept?.displayName ?: "Lintas Divisi"
                    val isCurrentRole = activePersona?.roleId == role.id

                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (isCurrentRole) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                        outlineColor = if (isCurrentRole) WeMadeColors.Primary else WeMadeColors.Outline,
                        contentPadding = PaddingValues(ClaySpacing.Md),
                        offset = ClayOffset.Small,
                        onClick = {
                            val finalName = customName.trim().ifBlank { role.name }
                            val persona = TestingPersona.custom(
                                name = finalName,
                                tenantId = tenantId,
                                tenantSlug = tenantSlug,
                                department = dept,
                                role = role
                            )
                            onSelect(persona)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                                ) {
                                    Text(
                                        text = role.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "-",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                    ClayTag(
                                        text = deptName,
                                        tint = if (dept != null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                                    )
                                }
                                if (role.description.isNotBlank()) {
                                    Text(
                                        text = role.description,
                                        fontSize = 12.sp,
                                        color = WeMadeColors.OnSurfaceMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                modifier = Modifier.padding(start = ClaySpacing.Md)
                            ) {
                                if (isCurrentRole) {
                                    ClayTag(text = "Aktif", tint = WeMadeColors.Primary)
                                }
                                ClayTag(
                                    text = if (isCurrentRole) "Terpilih" else "Pilih",
                                    tint = if (isCurrentRole) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeesListSection(
    personas: List<TestingPersona>,
    activePersona: TestingPersona?,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onSelect: (TestingPersona) -> Unit
) {
    if (personas.isEmpty()) {
        EmptyStateView(
            message = if (searchQuery.isNotBlank()) {
                "Tidak ada karyawan yang cocok dengan \"$searchQuery\"."
            } else {
                "Tidak ada data karyawan."
            },
            showClear = searchQuery.isNotBlank(),
            onClear = onClearSearch
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            personas.forEach { persona ->
                val isActive = persona.userId == activePersona?.userId

                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isActive) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                    outlineColor = if (isActive) WeMadeColors.Primary else WeMadeColors.Outline,
                    contentPadding = PaddingValues(ClaySpacing.Md),
                    offset = ClayOffset.Small,
                    onClick = { onSelect(persona) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = persona.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = persona.roleTitle,
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "-",
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                                ClayTag(
                                    text = persona.departmentName,
                                    tint = if (persona.departmentName != "Tanpa Divisi") WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                            modifier = Modifier.padding(start = ClaySpacing.Md)
                        ) {
                            if (isActive) {
                                ClayTag(text = "Aktif", tint = WeMadeColors.Primary)
                            }
                            ClayTag(
                                text = if (isActive) "Terpilih" else "Pilih",
                                tint = if (isActive) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    message: String,
    showClear: Boolean,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ClaySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = WeMadeColors.OnSurfaceMuted,
            fontWeight = FontWeight.Medium
        )
        if (showClear) {
            ClayButton(
                text = "Hapus Pencarian",
                onClick = onClear,
                style = ClayButtonStyle.Secondary,
                fontSize = 11.sp
            )
        }
    }
}
