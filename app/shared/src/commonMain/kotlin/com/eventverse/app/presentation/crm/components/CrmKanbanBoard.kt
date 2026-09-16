package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.presentation.crm.CrmViewMode
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Papan Kanban multi-kolom untuk Desktop & Tablet Landscape (≥ 840dp).
 *
 * Menampilkan 3 kolom horizontal: New Lead, Qualified Lead, Unqualified.
 * Jika salah satu kartu dipilih, Lead Inspector Drawer meluncur dari sisi kanan
 * sehingga pengguna dapat memeriksa/mengedit custom field tanpa keluar dari papan Kanban.
 *
 * Mendukung Jira-like Drag & Drop antar kolom dengan floating card overlay di level papan
 * sehingga kartu tidak pernah terpotong (clipped) saat keluar dari kolom container.
 */
@Composable
fun CrmKanbanBoard(
    leads: List<CrmLead>,
    schema: List<LeadFieldDescriptor>,
    employees: List<OrgNode>,
    selectedLeadId: LeadId?,
    searchQuery: String,
    viewMode: CrmViewMode,
    canWrite: Boolean,
    canManage: Boolean,
    onViewModeChange: (CrmViewMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectLead: (LeadId?) -> Unit,
    onAddLead: ((LeadStage) -> Unit)?,
    onCommitField: (fieldId: String, value: JsonValue.Obj?) -> Unit,
    onUpdateStage: (LeadId, LeadStage) -> Unit,
    onArchive: (LeadId) -> Unit,
    onAddField: () -> Unit,
    onDeleteField: ((fieldId: String) -> Unit)? = null,
    onOpenActivities: (CrmLead) -> Unit = {},
    activities: List<com.eventverse.app.domain.crm.LeadActivity> = emptyList(),
    isLoadingActivities: Boolean = false,
    isSubmittingActivity: Boolean = false,
    onSubmitActivity: ((content: String) -> Unit)? = null,
    kpiMetrics: com.eventverse.app.domain.crm.CrmLeadKpiMetrics = com.eventverse.app.domain.crm.CrmLeadKpiMetrics(),
    selectedEmployeeId: OrgNodeId? = null,
    selectedSource: String? = null,
    onFilterEmployee: (OrgNodeId?) -> Unit = {},
    onFilterSource: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedLead = leads.firstOrNull { it.id == selectedLeadId }

    val newLeads = leads.filter { it.stage == LeadStage.NEW_LEAD }
    val qualifiedLeads = leads.filter { it.stage == LeadStage.QUALIFIED }
    val unqualifiedLeads = leads.filter { it.stage == LeadStage.UNQUALIFIED }

    val dragDropState = rememberCrmDragDropState()
    var rootWindowOffset by remember { mutableStateOf(Offset.Zero) }

    var employeeMenuExpanded by remember { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalCrmDragDropState provides dragDropState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        rootWindowOffset = coords.positionInWindow()
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
                // Baris Atas: Executive KPI Metric Strip (4 Kartu Clay)
                CrmKpiMetricsRow(metrics = kpiMetrics)

                Spacer(Modifier.height(ClaySpacing.Lg))

                // Toolbar Atas: Search Bar + Filter PIC + Filter Sumber + View Mode Toggle + Tambah Lead
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        ClayTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = "Cari brand, kontak, nomor WA, kategori…",
                            modifier = Modifier.width(280.dp)
                        )

                        // Dropdown Filter Sales PIC
                        Box {
                            val selectedEmpName = employees.firstOrNull { it.id == selectedEmployeeId }?.name
                            Row(
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Button,
                                        background = if (selectedEmployeeId != null) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                                        outline = if (selectedEmployeeId != null) WeMadeColors.Primary else WeMadeColors.Outline,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .clickable { employeeMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = selectedEmpName ?: "Semua Sales PIC",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedEmployeeId != null) WeMadeColors.Primary else WeMadeColors.OnSurface
                                )
                                IconChevronDown(Modifier.size(10.dp), color = if (selectedEmployeeId != null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted)
                            }
                            DropdownMenu(
                                expanded = employeeMenuExpanded,
                                onDismissRequest = { employeeMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Semua Sales PIC", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        employeeMenuExpanded = false
                                        onFilterEmployee(null)
                                    }
                                )
                                employees.forEach { emp ->
                                    DropdownMenuItem(
                                        text = { Text(emp.name, fontSize = 12.sp) },
                                        onClick = {
                                            employeeMenuExpanded = false
                                            onFilterEmployee(emp.id)
                                        }
                                    )
                                }
                            }
                        }

                        // Dropdown Filter Sumber Channel
                        Box {
                            Row(
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Button,
                                        background = if (selectedSource != null) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                                        outline = if (selectedSource != null) WeMadeColors.Primary else WeMadeColors.Outline,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .clickable { sourceMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = selectedSource ?: "Semua Sumber",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedSource != null) WeMadeColors.Primary else WeMadeColors.OnSurface
                                )
                                IconChevronDown(Modifier.size(10.dp), color = if (selectedSource != null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted)
                            }
                            DropdownMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false }
                            ) {
                                listOf(null, "WhatsApp", "Referral", "Pameran", "Walk-in", "Instagram").forEach { src ->
                                    DropdownMenuItem(
                                        text = { Text(src ?: "Semua Sumber", fontSize = 12.sp, fontWeight = if (src == null) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            sourceMenuExpanded = false
                                            onFilterSource(src)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        CrmViewToggle(
                            currentMode = viewMode,
                            onModeChange = onViewModeChange
                        )

                        if (onAddLead != null) {
                            ClayButton(
                                text = "+ Tambah Lead",
                                onClick = { onAddLead(LeadStage.NEW_LEAD) }
                            )
                        }
                    }
                }

                // Area 3 Kolom Kanban Utama (memenuhi seluruh lebar papan)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    // Kolom 1: New Lead
                    CrmKanbanColumn(
                        stage = LeadStage.NEW_LEAD,
                        leads = newLeads,
                        employees = employees,
                        selectedLeadId = selectedLeadId,
                        canWrite = canWrite,
                        onSelectLead = { onSelectLead(it) },
                        onUpdateStage = onUpdateStage,
                        onAddLead = onAddLead?.let { { it(LeadStage.NEW_LEAD) } },
                        onOpenActivities = onOpenActivities,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )

                    // Kolom 2: Qualified Lead (bisa langsung tambah lead)
                    CrmKanbanColumn(
                        stage = LeadStage.QUALIFIED,
                        leads = qualifiedLeads,
                        employees = employees,
                        selectedLeadId = selectedLeadId,
                        canWrite = canWrite,
                        onSelectLead = { onSelectLead(it) },
                        onUpdateStage = onUpdateStage,
                        onAddLead = onAddLead?.let { { it(LeadStage.QUALIFIED) } },
                        onOpenActivities = onOpenActivities,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )

                    // Kolom 3: Unqualified (tidak ada tombol tambah lead)
                    CrmKanbanColumn(
                        stage = LeadStage.UNQUALIFIED,
                        leads = unqualifiedLeads,
                        employees = employees,
                        selectedLeadId = selectedLeadId,
                        canWrite = canWrite,
                        onSelectLead = { onSelectLead(it) },
                        onUpdateStage = onUpdateStage,
                        onAddLead = null,
                        onOpenActivities = onOpenActivities,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Modal Dialog Detail Lead saat kartu lead diklik
            if (selectedLead != null) {
                Dialog(
                    onDismissRequest = { onSelectLead(null) },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    ClayCard(
                        modifier = Modifier
                            .widthIn(min = 480.dp, max = 640.dp)
                            .fillMaxHeight(0.88f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        LeadInspectorPane(
                            lead = selectedLead,
                            schema = schema,
                            employees = employees,
                            canWrite = canWrite,
                            canManage = canManage,
                            onCommitField = onCommitField,
                            onUpdateStage = { targetStage -> onUpdateStage(selectedLead.id, targetStage) },
                            onArchive = { onArchive(selectedLead.id) },
                            onAddField = onAddField,
                            onDeleteField = onDeleteField,
                            onClose = { onSelectLead(null) },
                            activities = activities,
                            isLoadingActivities = isLoadingActivities,
                            isSubmittingActivity = isSubmittingActivity,
                            onSubmitActivity = onSubmitActivity,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Floating Drag Overlay ala Jira: melayang bebas di atas seluruh board tanpa ter-clip
            if (dragDropState.isDragging && dragDropState.draggedLead != null) {
                val lead = dragDropState.draggedLead!!
                val floatingOffset = dragDropState.floatingCardOffset(rootWindowOffset)
                val cardWidth = with(LocalDensity.current) {
                    if (dragDropState.cardInitialSize.width > 0f) {
                        dragDropState.cardInitialSize.width.toDp()
                    } else {
                        280.dp
                    }
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(floatingOffset.x.toInt(), floatingOffset.y.toInt()) }
                        .width(cardWidth)
                        .zIndex(999f)
                        .graphicsLayer {
                            rotationZ = -2.5f
                            scaleX = 1.02f
                            scaleY = 1.02f
                            alpha = 0.95f
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    CrmKanbanCard(
                        lead = lead,
                        employees = employees,
                        selected = false,
                        canWrite = false,
                        onSelectLead = {},
                        onUpdateStage = {},
                        onOpenActivities = {}
                    )
                }
            }
        }
    }
}

