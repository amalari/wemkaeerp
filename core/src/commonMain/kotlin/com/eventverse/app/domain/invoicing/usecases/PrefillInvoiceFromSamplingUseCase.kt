package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class PrefillSamplingInvoiceCommand(
    val tenantId: TenantId,
    val spkNumber: String,
    val styleName: String,
    val clientName: String,
    val samplePrice: Money = Money.idr(150_000),
    val templateId: InvoiceTemplateId,
    val issueDate: LocalDate,
    val dueDate: LocalDate? = null,
    val createdBy: String,
    val now: Instant = Clock.System.now()
)

class PrefillInvoiceFromSamplingUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val templateRepository: InvoiceTemplateRepository,
    private val issuerProfileRepository: InvoiceIssuerProfileRepository
) {
    suspend operator fun invoke(command: PrefillSamplingInvoiceCommand): Result<Invoice> = runCatching {
        val template = templateRepository.findById(command.tenantId, command.templateId)
            ?: error("Template invoice dengan ID '${command.templateId.value}' tidak ditemukan.")
        require(!template.isArchived) { "Template invoice '${template.name}' sudah diarsipkan." }

        val issuer = issuerProfileRepository.findByTenantId(command.tenantId)
            ?: IssuerProfile(companyName = "WeMade Garment Factory")

        val yearStr = command.issueDate.year.toString()
        val monthStr = command.issueDate.monthNumber.toString().padStart(2, '0')
        val period = "$yearStr$monthStr"

        val reservedNumber = invoiceRepository.reserveNextNumber(
            tenantId = command.tenantId,
            kind = InvoiceKind.SAMPLE,
            period = period
        )

        val line = InvoiceLine(
            id = InvoiceLineId("line-sample-${command.now.toEpochMilliseconds()}"),
            description = "Biaya Pembuatan Prototype Sample Baju: ${command.styleName} (SPK #${command.spkNumber})",
            quantity = Quantity.of(1.0, UnitOfMeasure.PIECE),
            unitPrice = command.samplePrice,
            discount = Ratio.ZERO,
            sortOrder = 0
        )

        val invoiceId = InvoiceId("inv-${command.tenantId.value}-${command.now.toEpochMilliseconds()}")

        val invoice = Invoice(
            id = invoiceId,
            tenantId = command.tenantId,
            number = reservedNumber,
            kind = InvoiceKind.SAMPLE,
            status = InvoiceStatus.DRAFT,
            billTo = BillToParty(name = command.clientName),
            issuer = issuer,
            lines = listOf(line),
            taxRatio = Ratio.percent(11.0),
            globalDiscount = Ratio.ZERO,
            currency = command.samplePrice.currency,
            issueDate = command.issueDate,
            dueDate = command.dueDate,
            templateId = command.templateId,
            renderedTemplate = null,
            sourceKind = InvoiceSourceKind.SAMPLING,
            sourceRef = command.spkNumber,
            parentInvoiceId = null,
            contractValue = null,
            notes = "Faktur tagihan jasa sampling baju.",
            terms = "Pembayaran tunai / transfer lunas sebelum sampel dikirim.",
            createdBy = command.createdBy,
            createdAt = command.now,
            updatedAt = command.now
        )

        invoiceRepository.save(invoice)
        invoice
    }
}
