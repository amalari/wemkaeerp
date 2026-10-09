package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.customfield.CustomAttributesCodec
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.crm.CrmLeadCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
/**
 * Client for the CRM Leads API. Uses [CrmLeadCodec] — the SAME codec the server encodes
 * with — so there is one wire format rather than two, exactly the reasoning
 * [PipelineApiClient] states.
 */
class CrmApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : CrmRemoteDataSource {
    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getSchema(tenantSlug: String): Result<List<LeadFieldDescriptor>> = runCatching {
        val response = httpClient.get(resolveUrl("$LEADS_PATH/schema")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        CrmLeadCodec.decodeSchema(response.requireBody("memuat skema lead"))
    }

    override suspend fun getLeads(tenantSlug: String): Result<List<CrmLead>> = runCatching {
        val response = httpClient.get(resolveUrl(LEADS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        CrmLeadCodec.decodeLeads(response.requireBody("memuat daftar lead"))
    }

    override suspend fun createLead(
        tenantSlug: String,
        request: CrmLeadCodec.CreateLeadRequest
    ): Result<CrmLead> = runCatching {
        val response = httpClient.post(resolveUrl(LEADS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(CrmLeadCodec.encodeCreateRequest(request))
        }
        decodeLeadOrThrow(response, "membuat lead")
    }

    override suspend fun patchLead(
        tenantSlug: String,
        leadId: LeadId,
        patch: CrmLeadCodec.PatchLeadRequest
    ): Result<CrmLead> = runCatching {
        val response = httpClient.patch(resolveUrl("$LEADS_PATH/${leadId.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(CrmLeadCodec.encodePatchRequest(patch))
        }
        decodeLeadOrThrow(response, "menyimpan lead")
    }

    override suspend fun updateStage(tenantSlug: String, leadId: LeadId, stage: LeadStage): Result<CrmLead> = runCatching {
        val response = httpClient.post(resolveUrl("$LEADS_PATH/${leadId.value}/stage")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(CrmLeadCodec.encodeStageRequest(stage))
        }
        decodeLeadOrThrow(response, "mengubah tahap lead")
    }

    override suspend fun updateStageWithDeal(
        tenantSlug: String,
        leadId: LeadId,
        stage: LeadStage
    ): Result<LeadStageTransition> = runCatching {
        val response = httpClient.post(resolveUrl("$LEADS_PATH/${leadId.value}/stage")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(CrmLeadCodec.encodeStageRequest(stage))
        }
        val body = response.requireBody("mengubah tahap lead")
        val obj = JsonParser.parseObject(body)
        val lead = CrmLeadCodec.decodeLead(obj) ?: error("Respons lead tidak valid")
        LeadStageTransition(
            lead = lead,
            dealId = obj.string("dealId"),
            dealAlreadyExisted = obj.boolean("dealAlreadyExisted") ?: false
        )
    }

    override suspend fun archiveLead(tenantSlug: String, leadId: LeadId): Result<Unit> = runCatching {
        val response = httpClient.delete(resolveUrl("$LEADS_PATH/${leadId.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal mengarsipkan lead (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    override suspend fun addCustomField(
        tenantSlug: String,
        label: String,
        type: FieldType,
        isRequired: Boolean
    ): Result<CustomFieldDefinition> = runCatching {
        val response = httpClient.post(resolveUrl("/api/tenant/crm/fields")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(CrmLeadCodec.encodeAddFieldRequest(label, type.code, CustomAttributesCodec.encodeConfig(type), isRequired))
        }
        val body = response.requireBody("menambah kolom kustom")
        // The server does not know the client's TenantId string form ahead of time; a
        // placeholder is fine here since the definition is immediately re-fetched via the
        // schema endpoint, which is tenant-scoped by the request itself.
        CustomAttributesCodec.decodeDefinition(TenantId(tenantSlug), JsonParser.parseObject(body))
            ?: error("Respons kolom kustom tidak valid")
    }

    override suspend fun deleteCustomField(tenantSlug: String, fieldId: String): Result<Unit> = runCatching {
        val response = httpClient.delete(resolveUrl("/api/tenant/crm/fields/$fieldId")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal menghapus kolom kustom (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    override suspend fun getActivities(
        tenantSlug: String,
        leadId: LeadId
    ): Result<List<com.eventverse.app.domain.crm.LeadActivity>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/crm/leads/${leadId.value}/activities")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val body = response.requireBody("memuat aktivitas lead")
        CrmLeadCodec.decodeActivities(body)
    }

    override suspend fun addActivity(
        tenantSlug: String,
        leadId: LeadId,
        content: String
    ): Result<com.eventverse.app.domain.crm.LeadActivity> = runCatching {
        val response = httpClient.post(resolveUrl("/api/tenant/crm/leads/${leadId.value}/activities")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(com.eventverse.app.shared.json.jsonObjectOf("content" to com.eventverse.app.shared.json.jsonOf(content)).encode())
        }
        val body = response.requireBody("menambah aktivitas lead")
        CrmLeadCodec.decodeActivity(JsonParser.parseObject(body)) ?: error("Respons aktivitas tidak valid")
    }

    override suspend fun uploadLeadFieldFile(
        tenantSlug: String,
        leadId: LeadId,
        fieldId: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        // Kontrak §4.4: metadata via query, byte raw di body — satu request tanpa multipart.
        val response = httpClient.post(resolveUrl("$LEADS_PATH/${leadId.value}/fields/$fieldId/upload")) {
            tenantRequest(tenantSlug, tokenProvider)
            parameter("fileName", fileName)
            parameter("contentType", contentType)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        val body = response.bodyOrFieldFileThrow("mengunggah berkas")
        JsonParser.parseObject(body).string("ref") ?: error("Server tidak mengembalikan referensi berkas")
    }

    override suspend fun leadFieldFileDownloadUrl(
        tenantSlug: String,
        leadId: LeadId,
        fieldId: String
    ): Result<String> = runCatching {
        val response = httpClient.get(resolveUrl("$LEADS_PATH/${leadId.value}/fields/$fieldId/download")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.bodyOrFieldFileThrow("membuat tautan unduhan")
        JsonParser.parseObject(body).string("url") ?: error("Respons tautan unduhan tidak valid")
    }

    /** Seperti [requireBody], tapi galat non-2xx membawa status untuk pemetaan 413/415/503 (FR-3). */
    private suspend fun HttpResponse.bodyOrFieldFileThrow(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            throw FieldFileHttpException(status.value, body.ifBlank { "Gagal $action (HTTP ${status.value})" })
        }
        return body
    }

    private suspend fun decodeLeadOrThrow(response: HttpResponse, action: String): CrmLead {
        val body = response.requireBody(action)
        return CrmLeadCodec.decodeLead(JsonParser.parseObject(body)) ?: error("Respons lead tidak valid")
    }

    /**
     * Reads the body, converting a non-2xx response into a failure that carries the
     * server's own message (e.g. a validation error naming which field failed) — see
     * [PipelineApiClient.requireBody] for the same reasoning.
     */
    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val LEADS_PATH = "/api/tenant/crm/leads"
    }
}
