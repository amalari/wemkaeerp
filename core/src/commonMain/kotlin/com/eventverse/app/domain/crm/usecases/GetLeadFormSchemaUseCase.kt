package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.tenant.TenantId

/**
 * The full form schema a lead is edited with: fixed core fields, followed by this tenant's
 * active custom fields, sorted by position. See [LeadFieldDescriptor] for why both are
 * projected to one shape.
 */
class GetLeadFormSchemaUseCase(
    private val customFieldRepository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<List<LeadFieldDescriptor>> = runCatching {
        val customDefs = customFieldRepository.findActiveByResource(tenantId, OwnerResource.CRM_SALES)
            .sortedBy { it.position }
            .map(LeadFieldDescriptor::fromCustomField)

        LeadFieldDescriptor.coreFields() + customDefs
    }
}
