package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadSource
import com.eventverse.app.domain.crm.ProductCategory
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CustomFieldValidation
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/** Thrown-as-Result when core and/or custom field validation fails on create. */
data class LeadValidationException(
    val customFieldErrors: List<com.eventverse.app.domain.customfield.CustomFieldValidationError>
) : Exception("Validasi lead gagal: ${customFieldErrors.size} kolom kustom tidak valid")

class CreateLeadUseCase(
    private val leadRepository: CrmLeadRepository,
    private val customFieldRepository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        brandName: BrandName = BrandName(""),
        contactPerson: String = "",
        whatsappNumber: WhatsappNumber? = null,
        email: String = "",
        stage: com.eventverse.app.domain.crm.LeadStage = com.eventverse.app.domain.crm.LeadStage.NEW_LEAD,
        source: LeadSource = LeadSource.UNSPECIFIED,
        estimatedPcs: Int? = null,
        estimatedValue: MoneyIdr? = null,
        ownerEmployeeId: OrgNodeId? = null,
        expectedCloseDate: LocalDate? = null,
        productCategory: ProductCategory = ProductCategory.EMPTY,
        customValues: Map<CustomFieldId, JsonValue.Obj?> = emptyMap(),
        createdByUserId: String? = null,
        createdVia: com.eventverse.app.domain.crm.LeadCreationChannel = com.eventverse.app.domain.crm.LeadCreationChannel.MANUAL,
        newId: () -> String
    ): Result<CrmLead> = runCatching {
        val definitions = customFieldRepository.findActiveByResource(tenantId, OwnerResource.CRM_SALES)
        val errors = CustomFieldValidation.validateForCreate(tenantId, definitions, customValues)
        if (errors.isNotEmpty()) throw LeadValidationException(errors)

        val now = Clock.System.now()
        var attributes = CustomAttributes.EMPTY
        customValues.forEach { (fieldId, cell) -> attributes = attributes.with(fieldId, cell) }

        val lead = CrmLead(
            id = LeadId(newId()),
            tenantId = tenantId,
            brandName = brandName,
            contactPerson = contactPerson,
            whatsappNumber = whatsappNumber,
            email = email,
            stage = stage,
            source = source,
            estimatedPcs = estimatedPcs,
            estimatedValue = estimatedValue,
            ownerEmployeeId = ownerEmployeeId,
            expectedCloseDate = expectedCloseDate,
            productCategory = productCategory,
            lastContactedAt = now,
            customAttributes = attributes,
            createdByUserId = createdByUserId,
            createdVia = createdVia,
            createdAt = now,
            updatedAt = now
        )

        leadRepository.save(lead).getOrThrow()
    }
}
