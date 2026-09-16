package com.eventverse.app.presentation.invoicing.template

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.infrastructure.api.CrmRemoteDataSource
import com.eventverse.app.infrastructure.api.LeadStageTransition
import com.eventverse.app.shared.crm.CrmLeadCodec

/**
 * Fake [CrmRemoteDataSource] untuk test presentasi desainer template.
 *
 * Hanya [getLeads] yang punya perilaku nyata — desainer template tidak pernah menulis ke CRM,
 * sehingga seluruh operasi tulis sengaja gagal dengan pesan eksplisit: kalau suatu hari test
 * memanggilnya, pesan itu langsung menunjuk ke test yang salah asumsi.
 */
class FakeCrmRemoteDataSource(
    var leads: List<CrmLead> = emptyList(),
    private val failing: Boolean = false
) : CrmRemoteDataSource {

    override suspend fun getSchema(tenantSlug: String): Result<List<LeadFieldDescriptor>> =
        Result.success(emptyList())

    override suspend fun getLeads(tenantSlug: String): Result<List<CrmLead>> =
        if (failing) {
            Result.failure(IllegalStateException("CRM tidak terjangkau (simulasi)"))
        } else {
            Result.success(leads)
        }

    override suspend fun createLead(
        tenantSlug: String,
        request: CrmLeadCodec.CreateLeadRequest
    ): Result<CrmLead> = unexpectedWrite()

    override suspend fun patchLead(
        tenantSlug: String,
        leadId: LeadId,
        patch: CrmLeadCodec.PatchLeadRequest
    ): Result<CrmLead> = unexpectedWrite()

    override suspend fun updateStage(tenantSlug: String, leadId: LeadId, stage: LeadStage): Result<CrmLead> =
        unexpectedWrite()

    override suspend fun updateStageWithDeal(
        tenantSlug: String,
        leadId: LeadId,
        stage: LeadStage
    ): Result<LeadStageTransition> = unexpectedWrite()

    override suspend fun archiveLead(tenantSlug: String, leadId: LeadId): Result<Unit> = unexpectedWrite()

    override suspend fun addCustomField(
        tenantSlug: String,
        label: String,
        type: FieldType,
        isRequired: Boolean
    ): Result<CustomFieldDefinition> = unexpectedWrite()

    override suspend fun deleteCustomField(tenantSlug: String, fieldId: String): Result<Unit> = unexpectedWrite()

    override suspend fun getActivities(tenantSlug: String, leadId: LeadId): Result<List<LeadActivity>> =
        Result.success(emptyList())

    override suspend fun addActivity(
        tenantSlug: String,
        leadId: LeadId,
        content: String
    ): Result<LeadActivity> = unexpectedWrite()

    private fun <T> unexpectedWrite(): Result<T> =
        Result.failure(IllegalStateException("Desainer template tidak seharusnya menulis ke CRM"))
}
