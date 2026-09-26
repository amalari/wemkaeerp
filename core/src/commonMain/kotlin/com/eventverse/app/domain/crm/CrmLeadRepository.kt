package com.eventverse.app.domain.crm

import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Persists CRM leads. Deliberately does NOT also hold custom field definitions — see
 * [com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository], which is a
 * separate aggregate shared by every module.
 */
interface CrmLeadRepository {

    suspend fun findById(tenantId: TenantId, id: LeadId): CrmLead?

    /**
     * Active (non-archived) leads, already restricted to [ownerReachIds] when non-null.
     * `null` means unrestricted (`DataScope.ALL_TENANT_DATA`) — the caller (a use case) is
     * required to pass an explicit value derived from [com.eventverse.app.domain.crm.LeadScope.reachableOwnerIds],
     * never to default it, since a defaulted scope is exactly how "forgot to filter" becomes
     * a data leak.
     */
    suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<CrmLead>

    suspend fun save(lead: CrmLead): Result<CrmLead>
}
