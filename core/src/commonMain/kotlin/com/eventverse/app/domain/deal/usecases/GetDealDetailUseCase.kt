package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.tenant.TenantId

data class DealDetail(
    val deal: Deal,
    val contact: Contact?,
    val purchaseOrders: List<PurchaseOrder>
)

/** Loads one deal together with its contact and attached purchase orders, all tenant-scoped. */
class GetDealDetailUseCase(
    private val dealRepository: DealRepository,
    private val contactRepository: ContactRepository,
) {
    suspend operator fun invoke(tenantId: TenantId, dealId: DealId): Result<DealDetail> = runCatching {
        val deal = requireNotNull(dealRepository.findById(tenantId, dealId)) {
            "Deal tidak ditemukan: ${dealId.value}"
        }
        val contact = contactRepository.findById(tenantId, deal.contactId)
        val purchaseOrders = dealRepository.findPurchaseOrders(tenantId, dealId)
        DealDetail(deal, contact, purchaseOrders)
    }
}
