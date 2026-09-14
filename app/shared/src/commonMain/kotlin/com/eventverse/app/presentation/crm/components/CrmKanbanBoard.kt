package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.CrmViewMode
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
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
    modifier: Modifier = Modifier
) {
    val selectedLead = leads.firstOrNull { it.id == selectedLeadId }
    val totalPipelineValue = leads.sumOf { it.estimatedValue?.amount ?: 0L }

    val newLeads = leads.filter { it.stage == LeadStage.NEW_LEAD }
    val qualifiedLeads = leads.filter { it.stage == LeadStage.QUALIFIED }
    val unqualifiedLeads = leads.filter { it.stage == LeadStage.UNQUALIFIED }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
        // Toolbar Atas: Search Bar + View Mode Toggle + Metrics + Tambah Lead
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Cari brand, kontak, nomor WA…",
                modifier = Modifier.width(360.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                // Ringkasan Pipeline Value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = "Pipeline:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    ClayTag(
                        text = "${leads.size} Lead • ${formatRupiah(totalPipelineValue)}",
                        tint = WeMadeColors.Primary,
                        fontSize = 12.sp
                    )
                }

                if (onAddLead != null) {
                    ClayButton(
                        text = "+ Tambah Lead",
                        onClick = { onAddLead(LeadStage.NEW_LEAD) }
                    )
                }
            }
        }

        // Area Papan Kanban & Inspector Drawer
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
        ) {
            // Area 3 Kolom Kanban Utama
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            // Lead Inspector Side Drawer (jika ada lead terpilih)
            if (selectedLead != null) {
                Box(
                    modifier = Modifier
                        .width(ClayPaneWidth.List)
                        .fillMaxHeight()
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.Surface,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Medium
                        )
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
                        onClose = { onSelectLead(null) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
