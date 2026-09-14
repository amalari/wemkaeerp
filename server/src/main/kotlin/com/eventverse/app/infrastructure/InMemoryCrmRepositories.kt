package com.eventverse.app.infrastructure

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe in-memory test double for [CrmLeadRepository]. Not used in production. */
class InMemoryCrmLeadRepository : CrmLeadRepository {
    private val leads = ConcurrentHashMap<String, CrmLead>()

    private fun key(tenantId: TenantId, id: LeadId) = "${tenantId.value}::${id.value}"

    override suspend fun findById(tenantId: TenantId, id: LeadId): CrmLead? = leads[key(tenantId, id)]

    override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<CrmLead> =
        leads.values
            .filter { it.tenantId == tenantId && !it.isArchived }
            .filter { lead ->
                when {
                    ownerReachIds == null -> true
                    ownerReachIds.isEmpty() -> false
                    else -> lead.ownerEmployeeId != null && lead.ownerEmployeeId in ownerReachIds
                }
            }
            .sortedByDescending { it.updatedAt }

    override suspend fun save(lead: CrmLead): Result<CrmLead> {
        leads[key(lead.tenantId, lead.id)] = lead
        return Result.success(lead)
    }
}

/** Thread-safe in-memory test double for [CustomFieldDefinitionRepository]. Not used in production. */
class InMemoryCustomFieldDefinitionRepository : CustomFieldDefinitionRepository {
    private val definitions = ConcurrentHashMap<String, CustomFieldDefinition>()

    private fun key(tenantId: TenantId, id: CustomFieldId) = "${tenantId.value}::${id.value}"

    override suspend fun findActiveByResource(
        tenantId: TenantId,
        ownerResource: OwnerResource
    ): List<CustomFieldDefinition> = definitions.values
        .filter { it.tenantId == tenantId && it.ownerResource == ownerResource && !it.isArchived }
        .sortedBy { it.position }

    override suspend fun findById(tenantId: TenantId, id: CustomFieldId): CustomFieldDefinition? =
        definitions[key(tenantId, id)]

    override suspend fun save(definition: CustomFieldDefinition): Result<CustomFieldDefinition> {
        definitions[key(definition.tenantId, definition.id)] = definition
        return Result.success(definition)
    }

    override suspend fun existingKeys(tenantId: TenantId, ownerResource: OwnerResource): Set<String> =
        definitions.values
            .filter { it.tenantId == tenantId && it.ownerResource == ownerResource }
            .map { it.key.value }
            .toSet()
}
