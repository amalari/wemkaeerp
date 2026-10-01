package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceRepository
import com.eventverse.app.domain.tenant.TenantId

/** Pasangan [SubscriptionInvoiceRepository] untuk test ktor. */
class InMemorySubscriptionInvoiceRepository : SubscriptionInvoiceRepository {
    val rows = mutableListOf<SubscriptionInvoice>()

    override suspend fun findByTenant(tenantId: TenantId) = rows.filter { it.tenantId == tenantId }
    override suspend fun findAll() = rows.toList()
    override suspend fun findByIpaymuTrxId(trxId: String) = rows.firstOrNull { it.ipaymuTrxId == trxId }
    override suspend fun save(invoice: SubscriptionInvoice): SubscriptionInvoice {
        rows.removeAll { it.id == invoice.id }
        rows.add(invoice)
        return invoice
    }
}
