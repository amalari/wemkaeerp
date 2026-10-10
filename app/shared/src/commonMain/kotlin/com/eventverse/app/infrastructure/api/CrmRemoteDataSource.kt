package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.shared.crm.CrmLeadCodec

/**
 * Remote operations the CRM Leads screen needs.
 *
 * Declared as an interface so [com.eventverse.app.presentation.crm.CrmViewModel] can be unit
 * tested against a fake instead of a live HTTP client, per this project's presentation-layer
 * testing rule (the same reason [PipelineRemoteDataSource] exists).
 */
interface CrmRemoteDataSource {

    suspend fun getSchema(tenantSlug: String): Result<List<LeadFieldDescriptor>>

    suspend fun getLeads(tenantSlug: String): Result<List<CrmLead>>

    suspend fun createLead(tenantSlug: String, request: CrmLeadCodec.CreateLeadRequest): Result<CrmLead>

    suspend fun patchLead(
        tenantSlug: String,
        leadId: LeadId,
        patch: CrmLeadCodec.PatchLeadRequest
    ): Result<CrmLead>

    suspend fun updateStage(tenantSlug: String, leadId: LeadId, stage: LeadStage): Result<CrmLead>

    /**
     * Stage transition with the qualification handshake: when [stage] is QUALIFIED the server
     * atomically creates (or idempotently returns) the Contact + Deal pair, and the response
     * carries `dealId` so the UI can navigate straight to the deal.
     */
    suspend fun updateStageWithDeal(
        tenantSlug: String,
        leadId: LeadId,
        stage: LeadStage
    ): Result<LeadStageTransition>

    suspend fun archiveLead(tenantSlug: String, leadId: LeadId): Result<Unit>

    suspend fun addCustomField(
        tenantSlug: String,
        label: String,
        type: CrmFieldType,
        isRequired: Boolean
    ): Result<CustomFieldDefinition>

    suspend fun deleteCustomField(tenantSlug: String, fieldId: String): Result<Unit>

    suspend fun getActivities(tenantSlug: String, leadId: LeadId): Result<List<com.eventverse.app.domain.crm.LeadActivity>>

    suspend fun addActivity(tenantSlug: String, leadId: LeadId, content: String): Result<com.eventverse.app.domain.crm.LeadActivity>

    /**
     * Mengunggah berkas untuk field `FILE` (C8, TRD-FIELD-002 §4.4): byte mentah di body,
     * metadata di query; sukses = referensi `fields/...` yang disimpan ke sel lewat patch biasa.
     */
    suspend fun uploadLeadFieldFile(
        tenantSlug: String,
        leadId: LeadId,
        fieldId: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String>

    /** URL presigned (15 menit) untuk mengunduh berkas field `FILE` lead. */
    suspend fun leadFieldFileDownloadUrl(tenantSlug: String, leadId: LeadId, fieldId: String): Result<String>
}

data class LeadStageTransition(
    val lead: CrmLead,
    /** Non-null when the transition created/confirmed a Deal (QUALIFIED). */
    val dealId: String?,
    val dealAlreadyExisted: Boolean
)
