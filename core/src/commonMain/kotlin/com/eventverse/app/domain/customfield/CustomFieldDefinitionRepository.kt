package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.tenant.TenantId

/**
 * Persists custom field definitions. Deliberately separate from any entity repository
 * (e.g. `CrmLeadRepository`) — mixing "the schema of a resource" and "the rows of a
 * resource" into one repository is exactly the "repository as generic DAO" anti-pattern
 * the project rules forbid, and this repository is meant to be shared by every module,
 * not owned by CRM.
 */
interface CustomFieldDefinitionRepository {

    /** Active (non-archived) definitions for one owner resource, ordered by position. */
    suspend fun findActiveByResource(tenantId: TenantId, ownerResource: OwnerResource): List<CustomFieldDefinition>

    suspend fun findById(tenantId: TenantId, id: CustomFieldId): CustomFieldDefinition?

    suspend fun save(definition: CustomFieldDefinition): Result<CustomFieldDefinition>

    /** All field keys currently in use (active or archived) for this resource, for uniqueness checks. */
    suspend fun existingKeys(tenantId: TenantId, ownerResource: OwnerResource): Set<String>
}
