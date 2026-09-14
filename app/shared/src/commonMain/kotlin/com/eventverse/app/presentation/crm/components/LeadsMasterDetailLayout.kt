package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.shared.json.JsonValue

/**
 * Desktop/tablet-landscape layout (≥ [com.eventverse.app.presentation.designsystem.ClayBreakpoints.MasterDetail]):
 * a fixed-width list on the left, a flexible detail inspector on the right — the pattern
 * that replaces the ClayDataGrid this codebase's earlier design considered and rejected for
 * being unusable on a phone.
 */
@Composable
fun LeadsMasterDetailLayout(
    leads: List<CrmLead>,
    schema: List<LeadFieldDescriptor>,
    employees: List<OrgNode>,
    selectedLeadId: LeadId?,
    searchQuery: String,
    canWrite: Boolean,
    canManage: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectLead: (LeadId) -> Unit,
    onAddLead: (() -> Unit)?,
    onCommitField: (fieldId: String, value: JsonValue.Obj?) -> Unit,
    onUpdateStage: (LeadStage) -> Unit,
    onArchive: (LeadId) -> Unit,
    onAddField: () -> Unit,
    onDeleteField: ((fieldId: String) -> Unit)? = null,
    viewMode: com.eventverse.app.presentation.crm.CrmViewMode = com.eventverse.app.presentation.crm.CrmViewMode.LIST,
    onViewModeChange: ((com.eventverse.app.presentation.crm.CrmViewMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedLead = leads.firstOrNull { it.id == selectedLeadId }

    Row(modifier = modifier.fillMaxSize()) {
        LeadListPane(
            leads = leads,
            selectedLeadId = selectedLeadId,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSelectLead = onSelectLead,
            onAddLead = onAddLead,
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
            modifier = Modifier.width(ClayPaneWidth.List).padding(ClaySpacing.Xxl)
        )

        LeadInspectorPane(
            lead = selectedLead,
            schema = schema,
            employees = employees,
            canWrite = canWrite,
            canManage = canManage,
            onCommitField = onCommitField,
            onUpdateStage = onUpdateStage,
            onArchive = { selectedLead?.let { onArchive(it.id) } },
            onAddField = onAddField,
            onDeleteField = onDeleteField,
            modifier = Modifier.fillMaxSize()
        )
    }
}
