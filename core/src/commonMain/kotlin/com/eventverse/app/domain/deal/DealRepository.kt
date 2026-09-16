package com.eventverse.app.domain.deal

import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Persists [Deal] and its child [PurchaseOrder] documents. Purchase orders live in the same
 * repository because they have no meaning outside the deal that owns them (aggregate-child
 * lifetime), but each has its own accessors so listing deals never over-fetches POs.
 */
interface DealRepository {

    suspend fun findById(tenantId: TenantId, id: DealId): Deal?

    /**
     * The idempotency anchor of qualification: exactly one deal may exist per source lead,
     * so re-qualifying a lead returns the existing deal instead of forking a duplicate.
     */
    suspend fun findBySourceLeadId(tenantId: TenantId, leadId: com.eventverse.app.domain.crm.LeadId): Deal?

    /**
     * Active (non-archived) deals, already restricted to [ownerReachIds] when non-null —
     * the same explicit-scope contract as [com.eventverse.app.domain.crm.CrmLeadRepository.findActive].
     */
    suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<Deal>

    suspend fun save(deal: Deal): Result<Deal>

    /**
     * True when any OTHER deal (archived included — its FK row still pins the contact)
     * references [contactId]. [excludeDealId] is the deal being demoted/archived.
     */
    suspend fun existsForContact(
        tenantId: TenantId,
        contactId: com.eventverse.app.domain.crm.ContactId,
        excludeDealId: DealId?
    ): Boolean

    suspend fun findPurchaseOrders(tenantId: TenantId, dealId: DealId): List<PurchaseOrder>

    suspend fun findPurchaseOrderById(tenantId: TenantId, poId: PurchaseOrderId): PurchaseOrder?

    suspend fun savePurchaseOrder(po: PurchaseOrder): Result<PurchaseOrder>
}
