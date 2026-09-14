package com.eventverse.app.presentation.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.presentation.crm.components.AddCustomFieldDialog
import com.eventverse.app.presentation.crm.components.CreateLeadDialog
import com.eventverse.app.presentation.crm.components.CrmKanbanBoard
import com.eventverse.app.presentation.crm.components.CrmMobileKanbanView
import com.eventverse.app.presentation.crm.components.LeadActivitiesDialog
import com.eventverse.app.presentation.crm.components.LeadsMasterDetailLayout
import com.eventverse.app.presentation.crm.components.LeadsMobileFeedLayout
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * The CRM Leads workspace — the first operational module to replace the generic
 * `ModuleWorkspaceScreen` placeholder with a real screen. Adaptive: a two-pane
 * master-detail layout at [ClayBreakpoints.MasterDetail] and above, a single-column card
 * feed with a bottom-sheet inspector below it. No spreadsheet grid — see the plan's
 * rationale for rejecting a dense `ClayDataGrid` in favour of this pattern.
 */
@Composable
fun CrmWorkspaceScreen(
    tenantSlug: String,
    access: ModuleAccessConfig,
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug) { CrmViewModel(tenantSlug = tenantSlug, access = access) }
    val state by viewModel.uiState.collectAsState()
    val employees by RbacAccessPolicyRepository.shared.employees.collectAsState()

    LaunchedEffect(tenantSlug) { viewModel.onEvent(CrmUiEvent.Load) }

    if (state.isLoading) {
        Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
            Text(text = "Memuat data CRM…", color = WeMadeColors.OnSurfaceMuted)
        }
        return
    }

    if (state.error != null && state.leads.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
            Text(text = "Gagal memuat: ${state.error}", color = WeMadeColors.Error)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Xxl, vertical = ClaySpacing.Sm)
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.ErrorBg,
                        outline = WeMadeColors.Error,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = err,
                    color = WeMadeColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "Tutup",
                    onClick = { viewModel.onEvent(CrmUiEvent.DismissError) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val isDesktop = maxWidth >= ClayBreakpoints.MasterDetail

        if (state.viewMode == CrmViewMode.KANBAN) {
            if (isDesktop) {
                CrmKanbanBoard(
                    leads = state.visibleLeads,
                    schema = state.schema,
                    employees = employees,
                    selectedLeadId = state.selectedLeadId,
                    searchQuery = state.searchQuery,
                    viewMode = state.viewMode,
                    canWrite = state.canWrite,
                    canManage = state.canManage,
                    onViewModeChange = { viewModel.onEvent(CrmUiEvent.SetViewMode(it)) },
                    onSearchQueryChange = { viewModel.onEvent(CrmUiEvent.UpdateSearchQuery(it)) },
                    onSelectLead = { viewModel.onEvent(CrmUiEvent.SelectLead(it)) },
                    onAddLead = if (state.canWrite) ({ stage -> viewModel.onEvent(CrmUiEvent.OpenCreateDialog(stage)) }) else null,
                    onCommitField = { fieldId, value -> viewModel.onEvent(CrmUiEvent.CommitField(fieldId, value)) },
                    onUpdateStage = { leadId, stage -> viewModel.onEvent(CrmUiEvent.UpdateStage(leadId, stage)) },
                    onArchive = { viewModel.onEvent(CrmUiEvent.ArchiveLead(it)) },
                    onAddField = { viewModel.onEvent(CrmUiEvent.OpenAddFieldDialog) },
                    onDeleteField = { viewModel.onEvent(CrmUiEvent.DeleteCustomField(it)) },
                    onOpenActivities = { viewModel.onEvent(CrmUiEvent.OpenActivities(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CrmMobileKanbanView(
                    leads = state.visibleLeads,
                    schema = state.schema,
                    employees = employees,
                    selectedLeadId = state.selectedLeadId,
                    searchQuery = state.searchQuery,
                    viewMode = state.viewMode,
                    activeStage = state.activeMobileStage,
                    canWrite = state.canWrite,
                    canManage = state.canManage,
                    onViewModeChange = { viewModel.onEvent(CrmUiEvent.SetViewMode(it)) },
                    onSelectStage = { viewModel.onEvent(CrmUiEvent.SetMobileStage(it)) },
                    onSearchQueryChange = { viewModel.onEvent(CrmUiEvent.UpdateSearchQuery(it)) },
                    onSelectLead = { viewModel.onEvent(CrmUiEvent.SelectLead(it)) },
                    onAddLead = if (state.canWrite) ({ stage -> viewModel.onEvent(CrmUiEvent.OpenCreateDialog(stage)) }) else null,
                    onCommitField = { fieldId, value -> viewModel.onEvent(CrmUiEvent.CommitField(fieldId, value)) },
                    onUpdateStage = { leadId, stage -> viewModel.onEvent(CrmUiEvent.UpdateStage(leadId, stage)) },
                    onArchive = { viewModel.onEvent(CrmUiEvent.ArchiveLead(it)) },
                    onAddField = { viewModel.onEvent(CrmUiEvent.OpenAddFieldDialog) },
                    onDeleteField = { viewModel.onEvent(CrmUiEvent.DeleteCustomField(it)) },
                    onOpenActivities = { viewModel.onEvent(CrmUiEvent.OpenActivities(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            if (isDesktop) {
                LeadsMasterDetailLayout(
                    leads = state.visibleLeads,
                    schema = state.schema,
                    employees = employees,
                    selectedLeadId = state.selectedLeadId,
                    searchQuery = state.searchQuery,
                    canWrite = state.canWrite,
                    canManage = state.canManage,
                    viewMode = state.viewMode,
                    onViewModeChange = { viewModel.onEvent(CrmUiEvent.SetViewMode(it)) },
                    onSearchQueryChange = { viewModel.onEvent(CrmUiEvent.UpdateSearchQuery(it)) },
                    onSelectLead = { viewModel.onEvent(CrmUiEvent.SelectLead(it)) },
                    onAddLead = if (state.canWrite) ({ viewModel.onEvent(CrmUiEvent.OpenCreateDialog()) }) else null,
                    onCommitField = { fieldId, value -> viewModel.onEvent(CrmUiEvent.CommitField(fieldId, value)) },
                    onUpdateStage = { stage ->
                        state.selectedLeadId?.let { viewModel.onEvent(CrmUiEvent.UpdateStage(it, stage)) }
                    },
                    onArchive = { viewModel.onEvent(CrmUiEvent.ArchiveLead(it)) },
                    onAddField = { viewModel.onEvent(CrmUiEvent.OpenAddFieldDialog) },
                    onDeleteField = { viewModel.onEvent(CrmUiEvent.DeleteCustomField(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LeadsMobileFeedLayout(
                    leads = state.visibleLeads,
                    schema = state.schema,
                    employees = employees,
                    selectedLeadId = state.selectedLeadId,
                    searchQuery = state.searchQuery,
                    canWrite = state.canWrite,
                    canManage = state.canManage,
                    viewMode = state.viewMode,
                    onViewModeChange = { viewModel.onEvent(CrmUiEvent.SetViewMode(it)) },
                    onSearchQueryChange = { viewModel.onEvent(CrmUiEvent.UpdateSearchQuery(it)) },
                    onSelectLead = { viewModel.onEvent(CrmUiEvent.SelectLead(it)) },
                    onAddLead = if (state.canWrite) ({ viewModel.onEvent(CrmUiEvent.OpenCreateDialog()) }) else null,
                    onCommitField = { fieldId, value -> viewModel.onEvent(CrmUiEvent.CommitField(fieldId, value)) },
                    onUpdateStage = { stage ->
                        state.selectedLeadId?.let { viewModel.onEvent(CrmUiEvent.UpdateStage(it, stage)) }
                    },
                    onArchive = { viewModel.onEvent(CrmUiEvent.ArchiveLead(it)) },
                    onAddField = { viewModel.onEvent(CrmUiEvent.OpenAddFieldDialog) },
                    onDeleteField = { viewModel.onEvent(CrmUiEvent.DeleteCustomField(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

    if (state.isCreateDialogOpen) {
        CreateLeadDialog(
            initialStage = state.createDialogInitialStage,
            onDismiss = { viewModel.onEvent(CrmUiEvent.CloseCreateDialog) },
            onCreate = { brandName, contactPerson, phoneNumber, email, stage ->
                viewModel.onEvent(CrmUiEvent.CreateLead(brandName, contactPerson, phoneNumber, email, stage))
            }
        )
    }

    if (state.isAddFieldDialogOpen) {
        AddCustomFieldDialog(
            onDismiss = { viewModel.onEvent(CrmUiEvent.CloseAddFieldDialog) },
            onAdd = { label, type, isRequired ->
                viewModel.onEvent(CrmUiEvent.AddCustomField(label, type, isRequired))
            }
        )
    }

    state.activeLeadForActivities?.let { lead ->
        LeadActivitiesDialog(
            lead = lead,
            activities = state.leadActivities,
            isLoading = state.isLoadingActivities,
            isSubmitting = state.isSubmittingActivity,
            onDismiss = { viewModel.onEvent(CrmUiEvent.CloseActivities) },
            onSubmit = { content ->
                viewModel.onEvent(CrmUiEvent.SubmitActivity(lead.id, content))
            }
        )
    }
}
