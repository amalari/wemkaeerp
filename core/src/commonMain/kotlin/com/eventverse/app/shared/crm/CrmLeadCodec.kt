package com.eventverse.app.shared.crm

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadSource
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.ProductCategory
import com.eventverse.app.domain.crm.CrmLeadKpiMetrics
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomAttributesCodec
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadActivityId
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.common.DateTimeCodec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Wire format for CRM Leads, shared by the Ktor server that produces/consumes it and the
 * Compose Multiplatform client — the same reason [com.eventverse.app.shared.pipeline.PipelineGraphCodec]
 * and [com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec] live here rather than in
 * either the server or the client module: one wire format, so the two can never drift the
 * way two hand-written parsers would.
 *
 * Built entirely on [JsonValue]/[com.eventverse.app.shared.json.JsonWriter] — never on
 * hand-rolled string escaping. `JsonValue.Str` already routes through the RFC-8259-correct
 * `JsonWriter.appendQuoted`; lead fields hold user-pasted free text, so a hand-rolled
 * escaper (like `EmployeeDto.escape`, which drops `\r` and leaves control characters
 * unescaped) would be hit immediately.
 */
object CrmLeadCodec {

    // -----------------------------------------------------------------------
    // Lead <-> JSON
    // -----------------------------------------------------------------------

    fun encodeLead(lead: CrmLead): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(lead.id.value),
        "brandName" to jsonOf(lead.brandName.value),
        "contactPerson" to jsonOf(lead.contactPerson),
        "whatsappNumber" to jsonOf(lead.whatsappNumber?.value),
        "waLink" to jsonOf(lead.whatsappNumber?.waLink),
        "email" to jsonOf(lead.email),
        "stage" to jsonOf(lead.stage.name),
        "source" to jsonOf(lead.source.value),
        "estimatedPcs" to (lead.estimatedPcs?.let { jsonOf(it) } ?: JsonValue.Null),
        "estimatedValueIdr" to (lead.estimatedValue?.let { jsonOf(it.amount) } ?: JsonValue.Null),
        "ownerEmployeeId" to jsonOf(lead.ownerEmployeeId?.value),
        "expectedCloseDate" to jsonOf(lead.expectedCloseDate?.toString()),
        "customAttributes" to lead.customAttributes.toJsonValue(),
        "productCategory" to jsonOf(lead.productCategory.value),
        "lastContactedAt" to jsonOf(lead.lastContactedAt?.toString()),
        "createdAt" to jsonOf(lead.createdAt.toString()),
        "updatedAt" to jsonOf(lead.updatedAt.toString()),
        "archivedAt" to jsonOf(lead.archivedAt?.toString()),
        "activityCount" to jsonOf(lead.activityCount)
    )

    fun encodeLeads(leads: List<CrmLead>): String = jsonArrayOf(leads.map(::encodeLead)).encode()

    /**
     * Decodes ONE lead from an API response, for client use. Server-side code never needs
     * this direction — it builds [CrmLead] straight from its own repository rows.
     */
    fun decodeLead(obj: JsonValue.Obj): CrmLead? {
        val id = obj.string("id") ?: return null
        val tenantIdRaw = obj.string("tenantId") // optional: leads endpoint responses may omit it (tenant is ambient)
        val brandName = obj.string("brandName") ?: ""
        val email = obj.string("email") ?: ""
        val stage = obj.string("stage")?.let { LeadStage.fromCode(it) } ?: LeadStage.NEW_LEAD
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0))
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt)

        return CrmLead(
            id = com.eventverse.app.domain.crm.LeadId(id),
            tenantId = tenantIdRaw?.let { com.eventverse.app.domain.tenant.TenantId(it) }
                ?: com.eventverse.app.domain.tenant.TenantId("unknown"),
            brandName = BrandName(brandName),
            contactPerson = obj.string("contactPerson") ?: "",
            whatsappNumber = obj.string("whatsappNumber")?.let { WhatsappNumber.parse(it) },
            email = email,
            stage = stage,
            source = LeadSource(obj.string("source") ?: ""),
            estimatedPcs = obj.int("estimatedPcs"),
            estimatedValue = obj.long("estimatedValueIdr")?.let { MoneyIdr(it) },
            ownerEmployeeId = obj.string("ownerEmployeeId")?.let { OrgNodeId(it) },
            expectedCloseDate = DateTimeCodec.parseLocalDateOrNull(obj.string("expectedCloseDate")),
            customAttributes = com.eventverse.app.domain.customfield.CustomAttributes.fromJsonValue(
                obj.obj("customAttributes") ?: JsonValue.Obj(emptyMap())
            ),
            productCategory = com.eventverse.app.domain.crm.ProductCategory(obj.string("productCategory") ?: ""),
            lastContactedAt = DateTimeCodec.parseInstantOrNull(obj.string("lastContactedAt")),
            createdAt = createdAt,
            updatedAt = updatedAt,
            archivedAt = DateTimeCodec.parseInstantOrNull(obj.string("archivedAt")),
            activityCount = obj.int("activityCount") ?: 0
        )
    }

    fun decodeLeads(rawJson: String): List<CrmLead> =
        JsonParser.parseArray(rawJson).filterIsInstance<JsonValue.Obj>().mapNotNull(::decodeLead)

    // -----------------------------------------------------------------------
    // Activity <-> JSON
    // -----------------------------------------------------------------------

    fun encodeActivity(activity: LeadActivity): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(activity.id.value),
        "tenantId" to jsonOf(activity.tenantId.value),
        "leadId" to jsonOf(activity.leadId.value),
        "authorEmployeeId" to jsonOf(activity.authorEmployeeId?.value),
        "authorName" to jsonOf(activity.authorName),
        "content" to jsonOf(activity.content),
        "createdAt" to jsonOf(activity.createdAt.toString())
    )

    fun encodeActivities(activities: List<LeadActivity>): String =
        jsonArrayOf(activities.map(::encodeActivity)).encode()

    fun decodeActivity(obj: JsonValue.Obj): LeadActivity? {
        val id = obj.string("id") ?: return null
        val tenantId = obj.string("tenantId") ?: return null
        val leadId = obj.string("leadId") ?: return null
        val content = obj.string("content") ?: return null
        val authorName = obj.string("authorName") ?: "Sales"
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0))

        return LeadActivity(
            id = LeadActivityId(id),
            tenantId = TenantId(tenantId),
            leadId = LeadId(leadId),
            authorEmployeeId = obj.string("authorEmployeeId")?.let { OrgNodeId(it) },
            authorName = authorName,
            content = content,
            createdAt = createdAt
        )
    }

    fun decodeActivities(rawJson: String): List<LeadActivity> =
        JsonParser.parseArray(rawJson).filterIsInstance<JsonValue.Obj>().mapNotNull(::decodeActivity)

    // -----------------------------------------------------------------------
    // Form schema <-> JSON
    // -----------------------------------------------------------------------

    fun encodeSchema(fields: List<LeadFieldDescriptor>): String = jsonArrayOf(
        fields.map { field ->
            jsonObjectOf(
                "fieldId" to jsonOf(field.fieldId),
                "label" to jsonOf(field.label),
                "type" to jsonOf(field.type.code),
                "config" to CustomAttributesCodec.encodeConfig(field.type),
                "isRequired" to jsonOf(field.isRequired),
                "isEditable" to jsonOf(field.isEditable),
                "isDeletable" to jsonOf(field.isDeletable),
                "isCore" to jsonOf(field.isCore)
            )
        }
    ).encode()

    fun decodeSchema(rawJson: String): List<LeadFieldDescriptor> =
        JsonParser.parseArray(rawJson).filterIsInstance<JsonValue.Obj>().mapNotNull { obj ->
            val fieldId = obj.string("fieldId") ?: return@mapNotNull null
            val label = obj.string("label") ?: return@mapNotNull null
            val typeCode = obj.string("type") ?: return@mapNotNull null
            val config = obj.obj("config") ?: JsonValue.Obj(emptyMap())
            val type = CustomAttributesCodec.decodeFieldType(typeCode, config) ?: return@mapNotNull null

            LeadFieldDescriptor(
                fieldId = fieldId,
                label = label,
                type = type,
                isRequired = obj.boolean("isRequired") ?: false,
                isEditable = obj.boolean("isEditable") ?: true,
                isDeletable = obj.boolean("isDeletable") ?: true,
                isCore = obj.boolean("isCore") ?: false
            )
        }

    // -----------------------------------------------------------------------
    // Create / patch requests — built by the client, decoded by the server
    // -----------------------------------------------------------------------

    data class CreateLeadRequest(
        val brandName: BrandName = BrandName(""),
        val contactPerson: String = "",
        val whatsappNumber: WhatsappNumber? = null,
        val email: String = "",
        val stage: LeadStage = LeadStage.NEW_LEAD,
        val source: LeadSource = LeadSource.UNSPECIFIED,
        val estimatedPcs: Int? = null,
        val estimatedValue: MoneyIdr? = null,
        val ownerEmployeeId: OrgNodeId? = null,
        val expectedCloseDate: LocalDate? = null,
        val productCategory: ProductCategory = ProductCategory.EMPTY,
        val customValues: Map<CustomFieldId, JsonValue.Obj?> = emptyMap()
    )

    fun encodeCreateRequest(request: CreateLeadRequest): String = jsonObjectOf(
        "brandName" to jsonOf(request.brandName.value),
        "contactPerson" to jsonOf(request.contactPerson),
        "whatsappNumber" to jsonOf(request.whatsappNumber?.value),
        "email" to jsonOf(request.email),
        "stage" to jsonOf(request.stage.name),
        "source" to jsonOf(request.source.value),
        "estimatedPcs" to (request.estimatedPcs?.let { jsonOf(it) } ?: JsonValue.Null),
        "estimatedValueIdr" to (request.estimatedValue?.let { jsonOf(it.amount) } ?: JsonValue.Null),
        "ownerEmployeeId" to jsonOf(request.ownerEmployeeId?.value),
        "expectedCloseDate" to jsonOf(request.expectedCloseDate?.toString()),
        "productCategory" to jsonOf(request.productCategory.value),
        "customAttributes" to encodeCustomValues(request.customValues)
    ).encode()

    fun decodeCreateRequest(rawJson: String): CreateLeadRequest {
        val root = JsonParser.parseObject(rawJson)
        return CreateLeadRequest(
            brandName = BrandName(root.string("brandName") ?: ""),
            contactPerson = root.string("contactPerson") ?: "",
            whatsappNumber = root.string("whatsappNumber")?.let { WhatsappNumber.parse(it) },
            email = root.string("email") ?: "",
            stage = root.string("stage")?.let { LeadStage.fromCode(it) } ?: LeadStage.NEW_LEAD,
            source = LeadSource(root.string("source") ?: ""),
            estimatedPcs = root.int("estimatedPcs"),
            estimatedValue = root.long("estimatedValueIdr")?.let { MoneyIdr(it) },
            ownerEmployeeId = root.string("ownerEmployeeId")?.let { OrgNodeId(it) },
            expectedCloseDate = DateTimeCodec.parseLocalDateOrNull(root.string("expectedCloseDate")),
            productCategory = ProductCategory(root.string("productCategory") ?: ""),
            customValues = decodeCustomValues(root)
        )
    }

    // -----------------------------------------------------------------------
    // KPI Metrics <-> JSON
    // -----------------------------------------------------------------------

    fun encodeKpiMetrics(metrics: CrmLeadKpiMetrics): JsonValue.Obj = jsonObjectOf(
        "totalPipelineValue" to jsonOf(metrics.totalPipelineValue),
        "activeLeadsCount" to jsonOf(metrics.activeLeadsCount),
        "qualifiedConversionRate" to jsonOf(metrics.qualifiedConversionRate),
        "followUpNeededCount" to jsonOf(metrics.followUpNeededCount)
    )

    fun decodeKpiMetrics(rawJson: String): CrmLeadKpiMetrics {
        val root = JsonParser.parseObjectOrNull(rawJson) ?: return CrmLeadKpiMetrics()
        return CrmLeadKpiMetrics(
            totalPipelineValue = root.long("totalPipelineValue") ?: 0L,
            activeLeadsCount = root.int("activeLeadsCount") ?: 0,
            qualifiedConversionRate = root.double("qualifiedConversionRate") ?: 0.0,
            followUpNeededCount = root.int("followUpNeededCount") ?: 0
        )
    }

    data class PatchLeadRequest(
        val brandName: BrandName? = null,
        val contactPerson: String? = null,
        val whatsappNumber: WhatsappNumber? = null,
        val email: String? = null,
        val stage: LeadStage? = null,
        val source: LeadSource? = null,
        val estimatedPcs: Int? = null,
        val estimatedValue: MoneyIdr? = null,
        val ownerEmployeeIdSet: Boolean = false,
        val ownerEmployeeId: OrgNodeId? = null,
        val expectedCloseDateSet: Boolean = false,
        val expectedCloseDate: LocalDate? = null,
        val customValues: Map<CustomFieldId, JsonValue.Obj?> = emptyMap(),
        val expectedUpdatedAt: Instant? = null
    )

    fun encodePatchRequest(request: PatchLeadRequest): String {
        val entries = mutableListOf<Pair<String, JsonValue>>()
        request.brandName?.let { entries += "brandName" to jsonOf(it.value) }
        request.contactPerson?.let { entries += "contactPerson" to jsonOf(it) }
        request.whatsappNumber?.let { entries += "whatsappNumber" to jsonOf(it.value) }
        request.email?.let { entries += "email" to jsonOf(it) }
        request.stage?.let { entries += "stage" to jsonOf(it.name) }
        request.source?.let { entries += "source" to jsonOf(it.value) }
        request.estimatedPcs?.let { entries += "estimatedPcs" to jsonOf(it) }
        request.estimatedValue?.let { entries += "estimatedValueIdr" to jsonOf(it.amount) }
        if (request.ownerEmployeeIdSet) entries += "ownerEmployeeId" to jsonOf(request.ownerEmployeeId?.value)
        if (request.expectedCloseDateSet) entries += "expectedCloseDate" to jsonOf(request.expectedCloseDate?.toString())
        if (request.customValues.isNotEmpty()) entries += "customAttributes" to encodeCustomValues(request.customValues)
        request.expectedUpdatedAt?.let { entries += "expectedUpdatedAt" to jsonOf(it.toString()) }
        return jsonObjectOf(*entries.toTypedArray()).encode()
    }

    fun decodePatchRequest(rawJson: String): PatchLeadRequest {
        val root = JsonParser.parseObject(rawJson)
        return PatchLeadRequest(
            brandName = root.string("brandName")?.let { BrandName(it) },
            contactPerson = root.string("contactPerson"),
            whatsappNumber = root.string("whatsappNumber")?.let { WhatsappNumber.parse(it) },
            email = root.string("email"),
            stage = root.string("stage")?.let { LeadStage.fromCode(it) },
            source = root.string("source")?.let { LeadSource(it) },
            estimatedPcs = root.int("estimatedPcs"),
            estimatedValue = root.long("estimatedValueIdr")?.let { MoneyIdr(it) },
            ownerEmployeeIdSet = root.has("ownerEmployeeId"),
            ownerEmployeeId = root.string("ownerEmployeeId")?.let { OrgNodeId(it) },
            expectedCloseDateSet = root.has("expectedCloseDate"),
            expectedCloseDate = DateTimeCodec.parseLocalDateOrNull(root.string("expectedCloseDate")),
            customValues = decodeCustomValues(root),
            expectedUpdatedAt = DateTimeCodec.parseInstantOrNull(root.string("expectedUpdatedAt"))
        )
    }

    private fun encodeCustomValues(values: Map<CustomFieldId, JsonValue.Obj?>): JsonValue.Obj =
        JsonValue.Obj(values.entries.associateTo(LinkedHashMap()) { (id, cell) -> id.value to (cell ?: JsonValue.Null) })

    private fun decodeCustomValues(root: JsonValue.Obj): Map<CustomFieldId, JsonValue.Obj?> {
        val customAttrs = root.obj("customAttributes") ?: return emptyMap()
        return customAttrs.entries.entries.associate { (key, value) ->
            CustomFieldId(key) to (value as? JsonValue.Obj)
        }
    }

    // -----------------------------------------------------------------------
    // Add custom field request
    // -----------------------------------------------------------------------

    data class AddCustomFieldRequest(val label: String, val typeCode: String, val config: JsonValue.Obj, val isRequired: Boolean)

    fun encodeAddFieldRequest(label: String, typeCode: String, config: JsonValue.Obj, isRequired: Boolean): String = jsonObjectOf(
        "label" to jsonOf(label),
        "type" to jsonOf(typeCode),
        "config" to config,
        "isRequired" to jsonOf(isRequired)
    ).encode()

    fun decodeAddFieldRequest(rawJson: String): AddCustomFieldRequest {
        val root = JsonParser.parseObject(rawJson)
        return AddCustomFieldRequest(
            label = requireNotNull(root.string("label")) { "label is required" },
            typeCode = requireNotNull(root.string("type")) { "type is required" },
            config = root.obj("config") ?: JsonValue.Obj(emptyMap()),
            isRequired = root.boolean("isRequired") ?: false
        )
    }

    // -----------------------------------------------------------------------
    // Stage transition request
    // -----------------------------------------------------------------------

    fun encodeStageRequest(stage: LeadStage): String = jsonObjectOf("stage" to jsonOf(stage.name)).encode()

    fun decodeStageRequest(rawJson: String): LeadStage? =
        JsonParser.parseObject(rawJson).string("stage")?.let { LeadStage.fromCode(it) }
}
