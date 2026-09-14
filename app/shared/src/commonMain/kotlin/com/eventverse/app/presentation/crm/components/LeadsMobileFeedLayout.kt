package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.shared.json.JsonValue

/**
 * Mobile layout (< [com.eventverse.app.presentation.designsystem.ClayBreakpoints.MasterDetail]):
 * a single-column card feed; tapping a card opens the inspector as a [ModalBottomSheet]
 * rather than splitting the narrow screen into two unusable columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadsMobileFeedLayout(
    leads: List<CrmLead>,
    schema: List<LeadFieldDescriptor>,
    employees: List<OrgNode>,
    selectedLeadId: LeadId?,
    searchQuery: String,
    canWrite: Boolean,
    canManage: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectLead: (LeadId?) -> Unit,
    onAddLead: (() -> Unit)?,
    onCommitField: (fieldId: String, value: JsonValue.Obj?) -> Unit,
    onUpdateStage: (LeadStage) -> Unit,
    onArchive: (LeadId) -> Unit,
    onAddField: () -> Unit,
    viewMode: com.eventverse.app.presentation.crm.CrmViewMode = com.eventverse.app.presentation.crm.CrmViewMode.LIST,
    onViewModeChange: ((com.eventverse.app.presentation.crm.CrmViewMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedLead = leads.firstOrNull { it.id == selectedLeadId }

    LeadListPane(
        leads = leads,
        selectedLeadId = null, // no persistent "selected" highlight on the feed itself
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSelectLead = { onSelectLead(it) },
        onAddLead = onAddLead,
        viewMode = viewMode,
        onViewModeChange = onViewModeChange,
        modifier = modifier.fillMaxSize().padding(ClaySpacing.Xl)
    )

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
                onUpdateStage = onUpdateStage,
                onArchive = { onArchive(selectedLead.id) },
                onAddField = onAddField,
                onClose = { onSelectLead(null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
