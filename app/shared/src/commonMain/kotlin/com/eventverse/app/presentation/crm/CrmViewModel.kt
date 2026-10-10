package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadSource
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.api.CrmApiClient
import com.eventverse.app.infrastructure.api.CrmRemoteDataSource
import com.eventverse.app.shared.crm.CrmLeadCodec
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the CRM Leads screen — the first module to move off
 * [com.eventverse.app.presentation.workspace.ModuleWorkspaceScreen]'s hardcoded placeholder.
 *
 * `AccessLevel.VIEW` makes the whole screen read-only in three layers, matching the
 * project's RBAC precedent (`OrgChartAccessGuard`): (1) [CrmUiState.canWrite]/[CrmUiState.canManage]
 * gate every affordance in the UI, (2) events that mutate are simply dropped here when the
 * held [access] disallows them rather than sent to the server, and (3) the server enforces
 * the real authority regardless — a UI-only guard is not a guard.
 */
class CrmViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: CrmRemoteDataSource = CrmApiClient(),
    access: ModuleAccessConfig = ModuleAccessConfig(),
    employees: List<OrgNode> = emptyList(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(CrmUiState(access = access, employees = employees))
    val uiState: StateFlow<CrmUiState> = _uiState.asStateFlow()

    fun onEvent(event: CrmUiEvent) {
        when (event) {
            is CrmUiEvent.Load, is CrmUiEvent.Retry -> load()
            is CrmUiEvent.SetMobileStage -> _uiState.update { it.copy(activeMobileStage = event.stage) }
            is CrmUiEvent.SelectLead -> {
                _uiState.update { it.copy(selectedLeadId = event.leadId) }
                event.leadId?.let { id ->
                    loadActivitiesForLead(id)
                }
            }
            is CrmUiEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is CrmUiEvent.FilterByEmployee -> _uiState.update { it.copy(selectedEmployeeId = event.employeeId) }
            is CrmUiEvent.FilterBySource -> _uiState.update { it.copy(selectedSource = event.source) }
            is CrmUiEvent.OpenCreateDialog -> _uiState.update {
                it.copy(isCreateDialogOpen = true, createDialogInitialStage = event.stage)
            }
            is CrmUiEvent.CloseCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = false) }
            is CrmUiEvent.CreateLead -> createLead(event)
            is CrmUiEvent.UpdateStage -> updateStage(event.leadId, event.newStage)
            is CrmUiEvent.ArchiveLead -> archiveLead(event.leadId)
            is CrmUiEvent.CommitField -> commitField(event.fieldId, event.value)
            is CrmUiEvent.OpenAddFieldDialog -> _uiState.update { it.copy(isAddFieldDialogOpen = true) }
            is CrmUiEvent.CloseAddFieldDialog -> _uiState.update { it.copy(isAddFieldDialogOpen = false) }
            is CrmUiEvent.AddCustomField -> addCustomField(event)
            is CrmUiEvent.DeleteCustomField -> deleteCustomField(event)
            is CrmUiEvent.OpenActivities -> openActivities(event.lead)
            is CrmUiEvent.CloseActivities -> closeActivities()
            is CrmUiEvent.SubmitActivity -> submitActivity(event.leadId, event.content)
            is CrmUiEvent.UploadFieldFile -> uploadFieldFile(event)
            is CrmUiEvent.OpenFieldFile -> openFieldFile(event)
            is CrmUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null, error = null) }
            is CrmUiEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            val schemaResult = remoteDataSource.getSchema(tenantSlug)
            val leadsResult = remoteDataSource.getLeads(tenantSlug)

            _uiState.update { state ->
                when {
                    schemaResult.isFailure -> state.copy(isLoading = false, error = schemaResult.exceptionOrNull()?.message)
                    leadsResult.isFailure -> state.copy(isLoading = false, error = leadsResult.exceptionOrNull()?.message)
                    else -> state.copy(
                        isLoading = false,
                        schema = schemaResult.getOrThrow(),
                        leads = leadsResult.getOrThrow()
                    )
                }
            }
        }
    }

    private fun createLead(event: CrmUiEvent.CreateLead) {
        if (!_uiState.value.canWrite) return
        _uiState.update { it.copy(isSaving = true) }

        scope.launch {
            val request = CrmLeadCodec.CreateLeadRequest(
                brandName = com.eventverse.app.domain.crm.BrandName(event.brandName),
                contactPerson = event.contactPerson,
                whatsappNumber = WhatsappNumber.parse(event.phoneNumber),
                email = event.email.trim(),
                stage = event.stage,
                source = LeadSource.UNSPECIFIED,
                estimatedPcs = event.estimatedPcs,
                estimatedValue = null,
                ownerEmployeeId = null,
                expectedCloseDate = null,
                productCategory = com.eventverse.app.domain.crm.ProductCategory(event.productCategory),
                customValues = event.customValues,
                createdVia = event.createdVia
            )

            remoteDataSource.createLead(tenantSlug, request)
                .onSuccess { lead ->
                    val displayName = lead.title
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            leads = it.leads + lead,
                            isCreateDialogOpen = false,
                            selectedLeadId = lead.id,
                            statusMessage = "Lead \"$displayName\" ditambahkan."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    private fun updateStage(leadId: LeadId, newStage: com.eventverse.app.domain.crm.LeadStage) {
        if (!_uiState.value.canWrite) return
        scope.launch {
            remoteDataSource.updateStageWithDeal(tenantSlug, leadId, newStage)
                .onSuccess { transition ->
                    replaceLead(transition.lead)
                    if (transition.dealId != null) {
                        val verb = if (transition.dealAlreadyExisted) "dibuka kembali" else "dibuat"
                        _uiState.update {
                            it.copy(
                                statusMessage = "Lead berkualifikasi - Deal $verb (ID: ${transition.dealId}).",
                                lastQualifiedDealId = transition.dealId
                            )
                        }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    private fun archiveLead(leadId: LeadId) {
        if (!_uiState.value.canWrite) return
        scope.launch {
            remoteDataSource.archiveLead(tenantSlug, leadId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            leads = state.leads.filterNot { it.id == leadId },
                            selectedLeadId = state.selectedLeadId?.takeIf { it != leadId },
                            statusMessage = "Lead diarsipkan."
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    /**
     * Commits one field of the selected lead — core or custom, dispatched by
     * [LeadFieldProjection]'s prefix rule. Optimistic: the UI's own [CrmUiState.pendingEdits]
     * is cleared as soon as the request is sent; on failure the reloaded lead simply keeps
     * its last-known-good server value, which is the honest signal that the edit did not stick.
     */
    private fun commitField(fieldId: String, value: JsonValue.Obj?) {
        val state = _uiState.value
        if (!state.canWrite) return
        val lead = state.selectedLead ?: return

        scope.launch {
            val patch = if (LeadFieldProjection.isCoreField(fieldId)) {
                coreFieldPatchOf(fieldId, value)
            } else {
                CrmLeadCodec.PatchLeadRequest(customValues = mapOf(CustomFieldId(fieldId) to value))
            }

            val currentLead = _uiState.value.selectedLead ?: lead
            remoteDataSource.patchLead(tenantSlug, currentLead.id, patch.copy(expectedUpdatedAt = currentLead.updatedAt))
                .onSuccess { updated -> replaceLead(updated) }
                .onFailure { error ->
                    val errorMsg = error.message ?: "Gagal menyimpan field"
                    if (errorMsg.contains("HTTP 409")) {
                        val jsonStart = errorMsg.indexOf('{')
                        if (jsonStart != -1) {
                            runCatching {
                                val obj = com.eventverse.app.shared.json.JsonParser.parseObject(errorMsg.substring(jsonStart))
                                val serverLead = obj?.let { CrmLeadCodec.decodeLead(it) }
                                if (serverLead != null) replaceLead(serverLead)
                            }
                        }
                    }
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    private fun addCustomField(event: CrmUiEvent.AddCustomField) {
        if (!_uiState.value.canManage) return
        _uiState.update { it.copy(isSaving = true) }

        scope.launch {
            remoteDataSource.addCustomField(tenantSlug, event.label, event.type, event.isRequired)
                .onSuccess {
                    // Re-fetch the whole schema rather than append locally: position ordering
                    // and any server-side normalisation (e.g. slugified key) come from one
                    // source of truth instead of being guessed on the client.
                    remoteDataSource.getSchema(tenantSlug).onSuccess { schema ->
                        _uiState.update {
                            it.copy(isSaving = false, schema = schema, isAddFieldDialogOpen = false, statusMessage = "Kolom \"${event.label}\" ditambahkan.")
                        }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, error = error.message) } }
        }
    }

    private fun deleteCustomField(event: CrmUiEvent.DeleteCustomField) {
        if (!_uiState.value.canManage) return
        _uiState.update { it.copy(isSaving = true) }

        scope.launch {
            remoteDataSource.deleteCustomField(tenantSlug, event.fieldId)
                .onSuccess {
                    remoteDataSource.getSchema(tenantSlug).onSuccess { schema ->
                        _uiState.update {
                            it.copy(isSaving = false, schema = schema, statusMessage = "Kolom berhasil dihapus.")
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isSaving = false) }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, error = error.message) } }
        }
    }

    private fun replaceLead(updated: com.eventverse.app.domain.crm.CrmLead) {
        _uiState.update { state ->
            state.copy(leads = state.leads.map { if (it.id == updated.id) updated else it })
        }
    }

    private fun loadActivitiesForLead(leadId: LeadId) {
        _uiState.update {
            it.copy(
                leadActivities = emptyList(),
                isLoadingActivities = true
            )
        }
        scope.launch {
            remoteDataSource.getActivities(tenantSlug, leadId)
                .onSuccess { activities ->
                    _uiState.update {
                        it.copy(
                            leadActivities = activities,
                            isLoadingActivities = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoadingActivities = false)
                    }
                }
        }
    }

    private fun openActivities(lead: com.eventverse.app.domain.crm.CrmLead) {
        _uiState.update {
            it.copy(
                activeLeadForActivities = lead,
                leadActivities = emptyList(),
                isLoadingActivities = true
            )
        }
        scope.launch {
            remoteDataSource.getActivities(tenantSlug, lead.id)
                .onSuccess { activities ->
                    _uiState.update {
                        it.copy(
                            leadActivities = activities,
                            isLoadingActivities = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingActivities = false,
                            error = "Gagal memuat aktivitas: ${error.message}"
                        )
                    }
                }
        }
    }

    private fun closeActivities() {
        _uiState.update {
            it.copy(
                activeLeadForActivities = null,
                leadActivities = emptyList(),
                isLoadingActivities = false,
                isSubmittingActivity = false
            )
        }
    }

    private fun submitActivity(leadId: LeadId, content: String) {
        _uiState.update { it.copy(isSubmittingActivity = true) }
        scope.launch {
            remoteDataSource.addActivity(tenantSlug, leadId, content)
                .onSuccess { newActivity ->
                    _uiState.update { state ->
                        val updatedActivities = listOf(newActivity) + state.leadActivities
                        val updatedLeads = state.leads.map { lead ->
                            if (lead.id == leadId) lead.copy(activityCount = lead.activityCount + 1) else lead
                        }
                        val updatedActiveLead = state.activeLeadForActivities?.let {
                            if (it.id == leadId) it.copy(activityCount = it.activityCount + 1) else it
                        }
                        state.copy(
                            isSubmittingActivity = false,
                            leadActivities = updatedActivities,
                            leads = updatedLeads,
                            activeLeadForActivities = updatedActiveLead
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmittingActivity = false,
                            error = "Gagal menambahkan aktivitas: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * C8 (TRD-FIELD-002): unggah berkas field FILE. Gerbang tulis di UI mengikuti aturan layar
     * ini (`canWrite`), gerbang sahnya tetap di server (OPERATE modul CRM, fail-closed). Ref yang
     * kembang di-commit pemanggil ke sel lewat [CrmUiEvent.CommitField] — byte tak pernah ke sel.
     */
    private fun uploadFieldFile(event: CrmUiEvent.UploadFieldFile) {
        if (!_uiState.value.canWrite) {
            event.onDone(Result.failure(IllegalStateException("Anda tidak berwenang mengubah lead ini.")))
            return
        }
        scope.launch {
            val result = remoteDataSource.uploadLeadFieldFile(
                tenantSlug, event.leadId, event.fieldId, event.fileName, event.contentType, event.bytes
            )
            event.onDone(result)
        }
    }

    private fun openFieldFile(event: CrmUiEvent.OpenFieldFile) {
        scope.launch {
            event.onDone(remoteDataSource.leadFieldFileDownloadUrl(tenantSlug, event.leadId, event.fieldId))
        }
    }
}

/**
 * Patch untuk edit field core dari sel editor. Teks sel dibaca **agnostik-tipe** (`Str` atau `Num`):
 * cabang builder Number menghasilkan sel `Num` (syarat validasi tulis `CustomFieldValidation`),
 * sementara cabang teks menghasilkan `Str` — keduanya wajib terbaca di sini. Murni, tanpa state.
 */
internal fun coreFieldPatchOf(fieldId: String, value: JsonValue.Obj?): CrmLeadCodec.PatchLeadRequest {
    val text = value?.let { cell ->
        when (val v = cell.entries["v"]) {
            is JsonValue.Str -> v.value
            is JsonValue.Num -> v.raw
            else -> null
        }
    }
    return when (fieldId) {
        LeadFieldDescriptorCoreIds.BRAND_NAME ->
            CrmLeadCodec.PatchLeadRequest(brandName = text?.let { com.eventverse.app.domain.crm.BrandName(it) })
        LeadFieldDescriptorCoreIds.CONTACT_PERSON -> CrmLeadCodec.PatchLeadRequest(contactPerson = text ?: "")
        LeadFieldDescriptorCoreIds.WHATSAPP_NUMBER ->
            CrmLeadCodec.PatchLeadRequest(whatsappNumber = text?.let { WhatsappNumber.parse(it) })
        LeadFieldDescriptorCoreIds.EMAIL ->
            CrmLeadCodec.PatchLeadRequest(email = text ?: "")
        LeadFieldDescriptorCoreIds.SOURCE -> CrmLeadCodec.PatchLeadRequest(source = LeadSource(text ?: ""))
        LeadFieldDescriptorCoreIds.ESTIMATED_PCS ->
            CrmLeadCodec.PatchLeadRequest(estimatedPcs = text?.toIntOrNull())
        LeadFieldDescriptorCoreIds.ESTIMATED_VALUE ->
            CrmLeadCodec.PatchLeadRequest(estimatedValue = text?.toLongOrNull()?.let { com.eventverse.app.domain.moduledev.MoneyIdr(it) })
        LeadFieldDescriptorCoreIds.OWNER_EMPLOYEE_ID -> CrmLeadCodec.PatchLeadRequest(
            ownerEmployeeIdSet = true,
            ownerEmployeeId = text?.let { com.eventverse.app.domain.orgchart.OrgNodeId(it) }
        )
        LeadFieldDescriptorCoreIds.EXPECTED_CLOSE_DATE -> CrmLeadCodec.PatchLeadRequest(
            expectedCloseDateSet = true,
            expectedCloseDate = text?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
        )
        else -> CrmLeadCodec.PatchLeadRequest()
    }
}

/** Core field id constants — mirrors [com.eventverse.app.domain.crm.LeadFieldDescriptor.coreFieldId]. */
private object LeadFieldDescriptorCoreIds {
    const val BRAND_NAME = "core:brand_name"
    const val CONTACT_PERSON = "core:contact_person"
    const val WHATSAPP_NUMBER = "core:whatsapp_number"
    const val EMAIL = "core:email"
    const val SOURCE = "core:source"
    const val ESTIMATED_PCS = "core:estimated_pcs"
    const val ESTIMATED_VALUE = "core:estimated_value_idr"
    const val OWNER_EMPLOYEE_ID = "core:owner_employee_id"
    const val EXPECTED_CLOSE_DATE = "core:expected_close_date"
}
