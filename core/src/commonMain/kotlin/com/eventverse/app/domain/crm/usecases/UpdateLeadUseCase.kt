package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadSource
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CustomFieldValidation
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Every field is optional: only fields present are patched. `null` inside an Optional clears it. */
data class LeadPatch(
    val brandName: BrandName? = null,
    val contactPerson: String? = null,
    val whatsappNumber: WhatsappNumber? = null,
    val email: String? = null,
    val source: LeadSource? = null,
    val estimatedPcs: Int? = null,
    val estimatedValue: MoneyIdr? = null,
    val ownerEmployeeId: Optional<OrgNodeId>? = null,
    val expectedCloseDate: Optional<LocalDate>? = null,
    val customValues: Map<CustomFieldId, JsonValue.Obj?> = emptyMap()
)

/** Distinguishes "not supplied" (null) from "explicitly cleared" (Optional wrapping null). */
data class Optional<T>(val value: T?)

/**
 * Applies a partial patch to an existing lead, including optimistic-concurrency checking
 * via [expectedUpdatedAt].
 */
class UpdateLeadUseCase(
    private val leadRepository: CrmLeadRepository,
    private val customFieldRepository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        leadId: LeadId,
        patch: LeadPatch,
        expectedUpdatedAt: Instant? = null
    ): Result<CrmLead> = runCatching {
        val existing = requireNotNull(leadRepository.findById(tenantId, leadId)) {
            "Lead not found: ${leadId.value}"
        }
        if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
            throw LeadConflictException(existing)
        }

        if (patch.customValues.isNotEmpty()) {
            val definitions = customFieldRepository.findActiveByResource(tenantId, OwnerResource.CRM_SALES)
            val errors = CustomFieldValidation.validateForPatch(tenantId, definitions, existing.createdAt, patch.customValues)
            if (errors.isNotEmpty()) throw LeadValidationException(errors)
        }

        val now = Clock.System.now()
        var attributes = existing.customAttributes
        patch.customValues.forEach { (fieldId, cell) -> attributes = attributes.with(fieldId, cell) }

        val updated = existing.copy(
            brandName = patch.brandName ?: existing.brandName,
            contactPerson = patch.contactPerson ?: existing.contactPerson,
            whatsappNumber = patch.whatsappNumber ?: existing.whatsappNumber,
            email = patch.email ?: existing.email,
            source = patch.source ?: existing.source,
            estimatedPcs = patch.estimatedPcs ?: existing.estimatedPcs,
            estimatedValue = patch.estimatedValue ?: existing.estimatedValue,
            ownerEmployeeId = patch.ownerEmployeeId?.value ?: existing.ownerEmployeeId,
            expectedCloseDate = patch.expectedCloseDate?.value ?: existing.expectedCloseDate,
            customAttributes = attributes,
            updatedAt = now
        )

        leadRepository.save(updated).getOrThrow()
    }
}

/** Thrown-as-Result when [expectedUpdatedAt] does not match — the caller must reconcile, not clobber. */
data class LeadConflictException(val current: CrmLead) : Exception("Lead was modified concurrently: ${current.id.value}")
