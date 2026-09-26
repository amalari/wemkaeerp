package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.InvoiceId
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.invoicing.InvoiceTemplateRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class IssueInvoiceCommand(
    val tenantId: TenantId,
    val invoiceId: InvoiceId,
    val now: Instant = Clock.System.now()
)

class IssueInvoiceUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val templateRepository: InvoiceTemplateRepository
) {
    suspend operator fun invoke(command: IssueInvoiceCommand): Result<Invoice> = runCatching {
        val invoice = invoiceRepository.findById(command.tenantId, command.invoiceId)
            ?: error("Invoice dengan ID '${command.invoiceId.value}' tidak ditemukan.")

        require(invoice.status.isMutable) {
            "Invoice ${invoice.number.value} sudah berstatus '${invoice.status.displayName}' dan tidak dapat diterbitkan kembali."
        }

        // Ambil template yang dipilih untuk dibekukan sebagai snapshot permanen
        val template = templateRepository.findById(command.tenantId, invoice.templateId)
            ?: error("Template invoice dengan ID '${invoice.templateId.value}' tidak ditemukan.")
        require(!template.isArchived) { "Template invoice '${template.name}' sudah diarsipkan." }

        val issuedInvoice = invoice.issue(template = template, at = command.now)
        invoiceRepository.save(issuedInvoice)
        issuedInvoice
    }
}
