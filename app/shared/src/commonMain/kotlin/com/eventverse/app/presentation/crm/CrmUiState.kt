package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.shared.json.JsonValue

enum class CrmViewMode {
    KANBAN,
    LIST
}

data class CrmUiState(
    val access: ModuleAccessConfig = ModuleAccessConfig(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,

    val schema: List<LeadFieldDescriptor> = emptyList(),
    val leads: List<CrmLead> = emptyList(),
    val employees: List<OrgNode> = emptyList(),

    val selectedLeadId: LeadId? = null,
    val searchQuery: String = "",

    val viewMode: CrmViewMode = CrmViewMode.KANBAN,
    val activeMobileStage: LeadStage = LeadStage.NEW_LEAD,

    val isCreateDialogOpen: Boolean = false,
    val createDialogInitialStage: LeadStage = LeadStage.NEW_LEAD,
    val isAddFieldDialogOpen: Boolean = false,

    val activeLeadForActivities: CrmLead? = null,
    val leadActivities: List<LeadActivity> = emptyList(),
    val isLoadingActivities: Boolean = false,
    val isSubmittingActivity: Boolean = false,

    /** Cells being edited in the inspector but not yet committed to the server. */
    val pendingEdits: Map<String, JsonValue.Obj?> = emptyMap()
) {
    val canWrite: Boolean get() = access.canWrite
    val canManage: Boolean get() = access.canManage

    val visibleLeads: List<CrmLead>
        get() = leads.filter { lead ->
            searchQuery.isBlank() ||
                lead.brandName.value.contains(searchQuery, ignoreCase = true) ||
                lead.contactPerson.contains(searchQuery, ignoreCase = true) ||
                lead.email.contains(searchQuery, ignoreCase = true) ||
                (lead.whatsappNumber?.normalizedNumber ?: "").contains(searchQuery)
        }.sortedByDescending { it.updatedAt.toEpochMilliseconds() }

    val selectedLead: CrmLead? get() = selectedLeadId?.let { id -> leads.firstOrNull { it.id == id } }

    val leadsByStage: Map<LeadStage, List<CrmLead>>
        get() = LeadStage.entries.associateWith { stage ->
            visibleLeads.filter { it.stage == stage }
        }

    fun stageTotalEstimatedValue(stage: LeadStage): Long =
        visibleLeads.filter { it.stage == stage }
            .sumOf { it.estimatedValue?.amount ?: 0L }

    val totalPipelineValue: Long
        get() = visibleLeads.sumOf { it.estimatedValue?.amount ?: 0L }

    val customFieldSchema: List<LeadFieldDescriptor> get() = schema.filter { !it.isCore }
    val coreFieldSchema: List<LeadFieldDescriptor> get() = schema.filter { it.isCore }
}

sealed interface CrmUiEvent {
    data object Load : CrmUiEvent
    data object Retry : CrmUiEvent

    data class SetViewMode(val mode: CrmViewMode) : CrmUiEvent
    data class SetMobileStage(val stage: LeadStage) : CrmUiEvent

    data class SelectLead(val leadId: LeadId?) : CrmUiEvent
    data class UpdateSearchQuery(val query: String) : CrmUiEvent

    data class OpenCreateDialog(val stage: LeadStage = LeadStage.NEW_LEAD) : CrmUiEvent
    data object CloseCreateDialog : CrmUiEvent
    data class CreateLead(
        val brandName: String,
        val contactPerson: String,
        val phoneNumber: String,
        val email: String = "",
        val stage: LeadStage = LeadStage.NEW_LEAD
    ) : CrmUiEvent

    data class UpdateStage(val leadId: LeadId, val newStage: LeadStage) : CrmUiEvent
    data class ArchiveLead(val leadId: LeadId) : CrmUiEvent

    /** Commits ONE field (core or custom) of the currently selected lead. */
    data class CommitField(val fieldId: String, val value: JsonValue.Obj?) : CrmUiEvent

    data object OpenAddFieldDialog : CrmUiEvent
    data object CloseAddFieldDialog : CrmUiEvent
    data class AddCustomField(val label: String, val type: FieldType, val isRequired: Boolean) : CrmUiEvent
    data class DeleteCustomField(val fieldId: String) : CrmUiEvent

    data class OpenActivities(val lead: CrmLead) : CrmUiEvent
    data object CloseActivities : CrmUiEvent
    data class SubmitActivity(val leadId: LeadId, val content: String) : CrmUiEvent

    data object DismissStatusMessage : CrmUiEvent
    data object DismissError : CrmUiEvent
}
