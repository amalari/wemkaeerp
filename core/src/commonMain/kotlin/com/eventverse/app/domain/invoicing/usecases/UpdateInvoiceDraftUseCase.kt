package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class UpdateInvoiceDraftCommand(
    val tenantId: TenantId,
    val invoiceId: InvoiceId,
    val billTo: BillToParty,
    val kind: InvoiceKind,
    val lines: List<InvoiceLine>,
    val taxRatio: Ratio,
    val globalDiscount: Ratio,
    val issueDate: LocalDate,
    val dueDate: LocalDate?,
    val templateId: InvoiceTemplateId,
    val notes: String,
    val terms: String,
    val now: Instant = Clock.System.now()
)

class UpdateInvoiceDraftUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val templateRepository: InvoiceTemplateRepository
) {
    suspend operator fun invoke(command: UpdateInvoiceDraftCommand): Result<Invoice> = runCatching {
        val invoice = invoiceRepository.findById(command.tenantId, command.invoiceId)
            ?: error("Invoice dengan ID '${command.invoiceId.value}' tidak ditemukan.")

        require(invoice.status.isMutable) {
            "Invoice ${invoice.number.value} sudah berstatus '${invoice.status.displayName}' dan tidak dapat diubah."
        }

        val template = templateRepository.findById(command.tenantId, command.templateId)
            ?: error("Template invoice dengan ID '${command.templateId.value}' tidak ditemukan.")
        require(!template.isArchived) { "Template invoice '${template.name}' sudah diarsipkan." }

        val updated = invoice.copy(
            billTo = command.billTo,
            kind = command.kind,
            lines = command.lines,
            taxRatio = command.taxRatio,
            globalDiscount = command.globalDiscount,
            issueDate = command.issueDate,
            dueDate = command.dueDate,
            templateId = command.templateId,
            notes = command.notes,
            terms = command.terms,
            updatedAt = command.now
        )

        invoiceRepository.save(updated)
        updated
    }
}
