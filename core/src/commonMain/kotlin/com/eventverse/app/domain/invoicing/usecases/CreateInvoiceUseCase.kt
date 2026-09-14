package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class CreateInvoiceCommand(
    val tenantId: TenantId,
    val kind: InvoiceKind,
    val billTo: BillToParty,
    val lines: List<InvoiceLine> = emptyList(),
    val taxRatio: Ratio = Ratio.percent(11.0),
    val globalDiscount: Ratio = Ratio.ZERO,
    val currency: CurrencyCode = CurrencyCode.IDR,
    val issueDate: LocalDate,
    val dueDate: LocalDate? = null,
    val templateId: InvoiceTemplateId,
    val sourceKind: InvoiceSourceKind = InvoiceSourceKind.MANUAL,
    val sourceRef: String? = null,
    val parentInvoiceId: InvoiceId? = null,
    val contractValue: Money? = null,
    val notes: String = "",
    val terms: String = "",
    val createdBy: String,
    val now: Instant = Clock.System.now()
)

class CreateInvoiceUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val templateRepository: InvoiceTemplateRepository,
    private val issuerProfileRepository: InvoiceIssuerProfileRepository
) {
    suspend operator fun invoke(command: CreateInvoiceCommand): Result<Invoice> = runCatching {
        // Validasi template aktif
        val template = templateRepository.findById(command.templateId)
            ?: error("Template invoice dengan ID '${command.templateId.value}' tidak ditemukan.")
        require(!template.isArchived) { "Template invoice '${template.name}' sudah diarsipkan." }

        // Ambil profil penerbit tenant atau gunakan default
        val issuer = issuerProfileRepository.findByTenantId(command.tenantId)
            ?: IssuerProfile(companyName = "WeMade Garment Factory")

        // Format periode YYYY/MM untuk sequence atomik
        val yearStr = command.issueDate.year.toString()
        val monthStr = command.issueDate.monthNumber.toString().padStart(2, '0')
        val period = "$yearStr/$monthStr"

        val reservedNumber = invoiceRepository.reserveNextNumber(
            tenantId = command.tenantId,
            kind = command.kind,
            period = period
        )

        val invoiceId = InvoiceId("inv-${command.tenantId.value}-${command.now.toEpochMilliseconds()}")

        val invoice = Invoice(
            id = invoiceId,
            tenantId = command.tenantId,
            number = reservedNumber,
            kind = command.kind,
            status = InvoiceStatus.DRAFT,
            billTo = command.billTo,
            issuer = issuer,
            lines = command.lines,
            taxRatio = command.taxRatio,
            globalDiscount = command.globalDiscount,
            currency = command.currency,
            issueDate = command.issueDate,
            dueDate = command.dueDate,
            templateId = command.templateId,
            renderedTemplate = null, // DRAFT: belum dibekukan
            sourceKind = command.sourceKind,
            sourceRef = command.sourceRef,
            parentInvoiceId = command.parentInvoiceId,
            contractValue = command.contractValue,
            notes = command.notes,
            terms = command.terms,
            createdBy = command.createdBy,
            createdAt = command.now,
            updatedAt = command.now
        )

        invoiceRepository.save(invoice)
        invoice
    }
}
