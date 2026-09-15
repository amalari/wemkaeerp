package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconStar
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Tampilan Kanban Responsif untuk Smartphone (< 840dp):
 * - Segmented Tab Switcher antar kolom tanpa emoji
 * - Empty state terpusat secara vertikal tanpa kotak pembungkus
 * - Tombol Qualified berwarna hijau
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmMobileKanbanView(
    leads: List<CrmLead>,
    schema: List<LeadFieldDescriptor>,
    employees: List<OrgNode>,
    selectedLeadId: LeadId?,
    searchQuery: String,
    viewMode: CrmViewMode,
    activeStage: LeadStage,
    canWrite: Boolean,
    canManage: Boolean,
    onViewModeChange: (CrmViewMode) -> Unit,
    onSelectStage: (LeadStage) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val selectedLead = leads.firstOrNull { it.id == selectedLeadId }
    val currentStageLeads = leads.filter { it.stage == activeStage }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Lg)) {
        // Baris 1: Pencarian
        ClayTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Cari brand atau kontak…",
            modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Md)
        )

        // Baris 2: Tombol Tambah Lead (jika diizinkan)
        if (onAddLead != null && activeStage != LeadStage.UNQUALIFIED) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Lg),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = if (activeStage == LeadStage.QUALIFIED) "+ Tambah Qualified" else "+ Tambah Lead",
                    style = if (activeStage == LeadStage.QUALIFIED) ClayButtonStyle.Success else ClayButtonStyle.Primary,
                    onClick = { onAddLead(activeStage) },
                    fontSize = 12.sp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        // Segmented Tab Switcher Antar Kolom Kanban (tanpa emoji tofu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.SurfaceMuted,
                    outline = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            LeadStage.entries.forEach { stage ->
                val stageCount = leads.count { it.stage == stage }
                val isSelected = stage == activeStage

                val activeStyle = when (stage) {
                    LeadStage.QUALIFIED -> ClayButtonStyle.Success
                    LeadStage.UNQUALIFIED -> ClayButtonStyle.Danger
                    LeadStage.NEW_LEAD -> ClayButtonStyle.Primary
                }

                ClayButton(
                    text = "${stage.displayName} ($stageCount)",
                    style = if (isSelected) activeStyle else ClayButtonStyle.Ghost,
                    fontSize = 11.sp,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    onClick = { onSelectStage(stage) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // Konten Kartu untuk Stage yang Aktif / Empty State Vertikal Tanpa Box
        if (currentStageLeads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ClaySpacing.Md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    when (activeStage) {
                        LeadStage.NEW_LEAD -> IconInbox(modifier = Modifier.size(44.dp), color = WeMadeColors.Primary)
                        LeadStage.QUALIFIED -> IconStar(modifier = Modifier.size(44.dp), color = WeMadeColors.Success)
                        LeadStage.UNQUALIFIED -> IconBan(modifier = Modifier.size(44.dp), color = WeMadeColors.Error)
                    }

                    Text(
                        text = "Belum Ada Prospek ${activeStage.displayName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    Text(
                        text = when (activeStage) {
                            LeadStage.NEW_LEAD -> "Mulai catat inquiry atau kontak baru di tahap awal ini."
                            LeadStage.QUALIFIED -> "Prospek dengan kuantiti & estimasi nilai akan muncul di sini."
                            LeadStage.UNQUALIFIED -> "Lead yang batal atau diarsipkan akan terkumpul di sini."
                        },
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center
                    )

                    if (canWrite && onAddLead != null && activeStage != LeadStage.UNQUALIFIED) {
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        ClayButton(
                            text = if (activeStage == LeadStage.QUALIFIED) "+ Tambah Qualified" else "+ Tambah Inquiry",
                            style = if (activeStage == LeadStage.QUALIFIED) ClayButtonStyle.Success else ClayButtonStyle.Primary,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                            onClick = { onAddLead(activeStage) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                items(currentStageLeads, key = { it.id.value }) { lead ->
                    CrmKanbanCard(
                        lead = lead,
                        employees = employees,
                        selected = lead.id == selectedLeadId,
                        canWrite = canWrite,
                        onSelectLead = { onSelectLead(it) },
                        onUpdateStage = { targetStage -> onUpdateStage(lead.id, targetStage) },
                        onOpenActivities = onOpenActivities
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet untuk Detail Inspector di Mobile
    if (selectedLead != null) {
        ModalBottomSheet(
            onDismissRequest = { onSelectLead(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
