package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.tenant.TenantId

data class InvoiceQuery(
    val tenantId: TenantId,
    val status: InvoiceStatus? = null,
    val kind: InvoiceKind? = null,
    val searchQuery: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20
) {
    init {
        require(page >= 1) { "Halaman harus >= 1." }
        require(pageSize in 1..100) { "Ukuran halaman harus antara 1 s/d 100." }
    }
}

data class InvoicePage(
    val items: List<Invoice>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
) {
    val totalPages: Int
        get() = if (totalCount == 0L) 1 else ((totalCount + pageSize - 1) / pageSize).toInt()
}

interface InvoiceRepository {
    suspend fun findById(id: InvoiceId): Invoice?
    suspend fun findByNumber(tenantId: TenantId, number: InvoiceNumber): Invoice?
    suspend fun search(query: InvoiceQuery): InvoicePage
    suspend fun save(invoice: Invoice)
    suspend fun reserveNextNumber(tenantId: TenantId, kind: InvoiceKind, period: String): InvoiceNumber
    suspend fun deleteDraft(id: InvoiceId)
}

interface InvoiceTemplateRepository {
    suspend fun findById(id: InvoiceTemplateId): InvoiceTemplate?
    suspend fun findAllByTenant(tenantId: TenantId, includeArchived: Boolean = false): List<InvoiceTemplate>
    suspend fun findDefault(tenantId: TenantId): InvoiceTemplate?
    suspend fun save(template: InvoiceTemplate)
    suspend fun setDefault(tenantId: TenantId, id: InvoiceTemplateId)
    suspend fun archive(id: InvoiceTemplateId)
}

interface InvoicePaymentRepository {
    suspend fun historyFor(invoiceId: InvoiceId): List<InvoicePayment>
    suspend fun append(payment: InvoicePayment)
    suspend fun totalPaidFor(invoiceId: InvoiceId): Money
}

interface InvoiceIssuerProfileRepository {
    suspend fun findByTenantId(tenantId: TenantId): IssuerProfile?
    suspend fun save(tenantId: TenantId, profile: IssuerProfile)
}
