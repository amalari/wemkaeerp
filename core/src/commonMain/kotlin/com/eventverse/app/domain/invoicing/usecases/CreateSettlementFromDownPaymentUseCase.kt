package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class CreateSettlementFromDownPaymentCommand(
    val downPaymentInvoiceId: InvoiceId,
    val issueDate: LocalDate,
    val dueDate: LocalDate? = null,
    val createdBy: String,
    val now: Instant = Clock.System.now()
)

class CreateSettlementFromDownPaymentUseCase(
    private val invoiceRepository: InvoiceRepository
) {
    suspend operator fun invoke(command: CreateSettlementFromDownPaymentCommand): Result<Invoice> = runCatching {
        val dpInvoice = invoiceRepository.findById(command.downPaymentInvoiceId)
            ?: error("Invoice DP dengan ID '${command.downPaymentInvoiceId.value}' tidak ditemukan.")

        require(dpInvoice.kind == InvoiceKind.DOWN_PAYMENT) {
            "Invoice ${dpInvoice.number.value} bukan jenis DOWN_PAYMENT (${dpInvoice.kind.displayName})."
        }
        require(dpInvoice.status != InvoiceStatus.VOID) {
            "Invoice DP ${dpInvoice.number.value} sudah dibatalkan (VOID)."
        }

        val contractValue = dpInvoice.contractValue ?: dpInvoice.total
        val settlementAmount = contractValue - dpInvoice.total
        require(settlementAmount.isPositive) {
            "Nilai pelunasan harus lebih besar dari nol. DP (${dpInvoice.total.minorUnits}) >= Kontrak (${contractValue.minorUnits})."
        }

        val yearStr = command.issueDate.year.toString()
        val monthStr = command.issueDate.monthNumber.toString().padStart(2, '0')
        val period = "$yearStr$monthStr"

        val reservedNumber = invoiceRepository.reserveNextNumber(
            tenantId = dpInvoice.tenantId,
            kind = InvoiceKind.SETTLEMENT,
            period = period
        )

        val settlementLineId = InvoiceLineId("line-settle-${command.now.toEpochMilliseconds()}")
        val line = InvoiceLine(
            id = settlementLineId,
            description = "Pelunasan Tagihan atas DP Invoice #${dpInvoice.number.value}",
            quantity = Quantity.of(1.0, UnitOfMeasure.PIECE),
            unitPrice = settlementAmount,
            discount = Ratio.ZERO,
            sortOrder = 0
        )

        val settlementInvoiceId = InvoiceId("inv-${dpInvoice.tenantId.value}-${command.now.toEpochMilliseconds()}")

        val settlementInvoice = Invoice(
            id = settlementInvoiceId,
            tenantId = dpInvoice.tenantId,
            number = reservedNumber,
            kind = InvoiceKind.SETTLEMENT,
            status = InvoiceStatus.DRAFT,
            billTo = dpInvoice.billTo,
            issuer = dpInvoice.issuer,
            lines = listOf(line),
            taxRatio = dpInvoice.taxRatio,
            globalDiscount = Ratio.ZERO,
            currency = dpInvoice.currency,
            issueDate = command.issueDate,
            dueDate = command.dueDate,
            templateId = dpInvoice.templateId,
            renderedTemplate = null,
            sourceKind = dpInvoice.sourceKind,
            sourceRef = dpInvoice.number.value,
            parentInvoiceId = dpInvoice.id,
            contractValue = contractValue,
            notes = "Pelunasan kontrak setelah pembayaran Uang Muka (DP).",
            terms = dpInvoice.terms,
            createdBy = command.createdBy,
            createdAt = command.now,
            updatedAt = command.now
        )

        invoiceRepository.save(settlementInvoice)
        settlementInvoice
    }
}
