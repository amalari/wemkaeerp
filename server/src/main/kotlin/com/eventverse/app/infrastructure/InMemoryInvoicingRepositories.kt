package com.eventverse.app.infrastructure

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

class InMemoryInvoiceIssuerProfileRepository : InvoiceIssuerProfileRepository {
    private val profiles = mutableMapOf<TenantId, IssuerProfile>()

    override suspend fun findByTenantId(tenantId: TenantId): IssuerProfile? = profiles[tenantId]

    override suspend fun save(tenantId: TenantId, profile: IssuerProfile) {
        profiles[tenantId] = profile
    }
}

class InMemoryInvoiceTemplateRepository : InvoiceTemplateRepository {
    private val templates = mutableMapOf<InvoiceTemplateId, InvoiceTemplate>()

    override suspend fun findById(id: InvoiceTemplateId): InvoiceTemplate? =
        templates[id]?.takeIf { !it.isArchived }

    override suspend fun findAllByTenant(tenantId: TenantId, includeArchived: Boolean): List<InvoiceTemplate> =
        templates.values.filter { it.tenantId == tenantId && (includeArchived || !it.isArchived) }

    override suspend fun findDefault(tenantId: TenantId): InvoiceTemplate? =
        templates.values.firstOrNull { it.tenantId == tenantId && it.isDefault && !it.isArchived }

    override suspend fun save(template: InvoiceTemplate) {
        if (template.isDefault) {
            templates.values.filter { it.tenantId == template.tenantId && it.id != template.id }
                .forEach { templates[it.id] = it.copy(isDefault = false) }
        }
        templates[template.id] = template
    }

    override suspend fun setDefault(tenantId: TenantId, id: InvoiceTemplateId) {
        templates.values.filter { it.tenantId == tenantId }
            .forEach { templates[it.id] = it.copy(isDefault = (it.id == id)) }
    }

    override suspend fun archive(id: InvoiceTemplateId) {
        templates[id]?.let {
            templates[id] = it.copy(archivedAt = Clock.System.now(), isDefault = false)
        }
    }
}

class InMemoryInvoicePaymentRepository : InvoicePaymentRepository {
    private val payments = mutableListOf<InvoicePayment>()

    override suspend fun historyFor(invoiceId: InvoiceId): List<InvoicePayment> =
        payments.filter { it.invoiceId == invoiceId }.sortedBy { it.paidAt }

    override suspend fun append(payment: InvoicePayment) {
        payments.add(payment)
    }

    override suspend fun totalPaidFor(invoiceId: InvoiceId): Money {
        val matching = payments.filter { it.invoiceId == invoiceId }
        val sumMinor = matching.sumOf { it.amount.minorUnits }
        val currency = matching.firstOrNull()?.amount?.currency ?: CurrencyCode.IDR
        return Money(sumMinor, currency)
    }
}

class InMemoryInvoiceRepository : InvoiceRepository {
    private val invoices = mutableMapOf<InvoiceId, Invoice>()
    private val seqMap = mutableMapOf<String, Long>()

    override suspend fun findById(id: InvoiceId): Invoice? = invoices[id]

    override suspend fun findByNumber(tenantId: TenantId, number: InvoiceNumber): Invoice? =
        invoices.values.firstOrNull { it.tenantId == tenantId && it.number == number }

    override suspend fun search(query: InvoiceQuery): InvoicePage {
        val queryText = query.searchQuery
        val filtered = invoices.values.filter { inv ->
            inv.tenantId == query.tenantId &&
            (query.status == null || inv.status == query.status) &&
            (query.kind == null || inv.kind == query.kind) &&
            (queryText == null || inv.number.value.contains(queryText, ignoreCase = true) || inv.billTo.name.contains(queryText, ignoreCase = true))
        }.sortedByDescending { it.updatedAt }

        val offset = ((query.page - 1) * query.pageSize).coerceAtLeast(0)
        val items = filtered.drop(offset).take(query.pageSize)
        return InvoicePage(items, filtered.size.toLong(), query.page, query.pageSize)
    }

    override suspend fun reserveNextNumber(tenantId: TenantId, kind: InvoiceKind, period: String): InvoiceNumber {
        val key = "${tenantId.value}-$period"
        val current = seqMap.getOrPut(key) { 0L } + 1L
        seqMap[key] = current
        val (y, m) = if (period.contains('/') || period.contains('-')) {
            val parts = period.replace("-", "/").split("/")
            (parts.getOrNull(0) ?: "2026") to (parts.getOrNull(1) ?: "01")
        } else if (period.length >= 6) {
            period.substring(0, 4) to period.substring(4, 6)
        } else {
            "2026" to "01"
        }
        return InvoiceNumber("INV/$y/$m/${current.toString().padStart(4, '0')}")
    }

    override suspend fun deleteDraft(id: InvoiceId) {
        val inv = invoices[id]
        if (inv != null && inv.status == InvoiceStatus.DRAFT) {
            invoices.remove(id)
        }
    }

    override suspend fun save(invoice: Invoice) {
        invoices[invoice.id] = invoice
    }
}
