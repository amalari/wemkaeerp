package com.eventverse.app.infrastructure

import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.crm.ContactId
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.deal.PurchaseOrderId
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe in-memory test double for [ContactRepository]. Not used in production. */
class InMemoryContactRepository : ContactRepository {
    private val contacts = ConcurrentHashMap<String, Contact>()

    private fun key(tenantId: TenantId, id: ContactId) = "${tenantId.value}::${id.value}"

    override suspend fun findById(tenantId: TenantId, id: ContactId): Contact? = contacts[key(tenantId, id)]

    override suspend fun findByPhone(tenantId: TenantId, phone: WhatsappNumber): Contact? =
        contacts.values.singleOrNull { it.tenantId == tenantId && it.phone?.value == phone.value }

    override suspend fun findActive(tenantId: TenantId): List<Contact> =
        contacts.values.filter { it.tenantId == tenantId }.sortedByDescending { it.updatedAt }

    override suspend fun save(contact: Contact): Result<Contact> {
        contacts[key(contact.tenantId, contact.id)] = contact
        return Result.success(contact)
    }

    override suspend fun delete(tenantId: TenantId, id: ContactId): Boolean =
        contacts.remove(key(tenantId, id)) != null
}

/** Thread-safe in-memory test double for [DealRepository]. Not used in production. */
class InMemoryDealRepository : DealRepository {
    private val deals = ConcurrentHashMap<String, Deal>()
    private val purchaseOrders = ConcurrentHashMap<String, PurchaseOrder>()

    private fun key(tenantId: TenantId, id: DealId) = "${tenantId.value}::${id.value}"

    override suspend fun findById(tenantId: TenantId, id: DealId): Deal? = deals[key(tenantId, id)]

    override suspend fun findBySourceLeadId(tenantId: TenantId, leadId: LeadId): Deal? =
        deals.values.singleOrNull { it.tenantId == tenantId && it.sourceLeadId == leadId }

    override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<Deal> =
        deals.values
            .filter { it.tenantId == tenantId && !it.isArchived }
            .filter { deal ->
                when {
                    ownerReachIds == null -> true
                    ownerReachIds.isEmpty() -> false
                    else -> deal.ownerEmployeeId != null && deal.ownerEmployeeId in ownerReachIds
                }
            }
            .sortedByDescending { it.updatedAt }

    override suspend fun save(deal: Deal): Result<Deal> {
        deals[key(deal.tenantId, deal.id)] = deal
        return Result.success(deal)
    }

    override suspend fun existsForContact(
        tenantId: TenantId,
        contactId: com.eventverse.app.domain.crm.ContactId,
        excludeDealId: DealId?
    ): Boolean = deals.values.any {
        it.tenantId == tenantId && it.contactId == contactId && it.id != excludeDealId
    }

    override suspend fun findPurchaseOrders(tenantId: TenantId, dealId: DealId): List<PurchaseOrder> =
        purchaseOrders.values
            .filter { it.tenantId == tenantId && it.dealId == dealId }
            .sortedByDescending { it.createdAt }

    override suspend fun findPurchaseOrderById(tenantId: TenantId, poId: PurchaseOrderId): PurchaseOrder? =
        purchaseOrders["${tenantId.value}::${poId.value}"]

    override suspend fun savePurchaseOrder(po: PurchaseOrder): Result<PurchaseOrder> {
        purchaseOrders["${po.tenantId.value}::${po.id.value}"] = po
        return Result.success(po)
    }
}
