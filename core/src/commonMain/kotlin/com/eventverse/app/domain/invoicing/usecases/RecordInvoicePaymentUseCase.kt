package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class RecordInvoicePaymentCommand(
    val tenantId: TenantId,
    val invoiceId: InvoiceId,
    val amount: Money,
    val method: String,
    val reference: String = "",
    val note: String = "",
    val recordedBy: String,
    val paidAt: Instant = Clock.System.now()
)

class RecordInvoicePaymentUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: InvoicePaymentRepository
) {
    suspend operator fun invoke(command: RecordInvoicePaymentCommand): Result<InvoicePayment> = runCatching {
        val invoice = invoiceRepository.findById(command.tenantId, command.invoiceId)
            ?: error("Invoice dengan ID '${command.invoiceId.value}' tidak ditemukan.")

        require(invoice.status != InvoiceStatus.DRAFT) {
            "Invoice ${invoice.number.value} masih berstatus draf. Terbitkan invoice sebelum mencatat pembayaran."
        }
        require(invoice.status != InvoiceStatus.VOID) {
            "Invoice ${invoice.number.value} sudah dibatalkan (VOID) dan tidak dapat menerima pembayaran."
        }
        require(command.amount.currency == invoice.currency) {
            "Mata uang pembayaran (${command.amount.currency.code}) berbeda dari mata uang invoice (${invoice.currency.code})."
        }

        val currentTotalPaid = paymentRepository.totalPaidFor(command.tenantId, invoice.id)
        val newTotalPaid = currentTotalPaid + command.amount
        require(newTotalPaid <= invoice.total) {
            "Total pembayaran (${newTotalPaid.minorUnits}) melebihi nilai tagihan (${invoice.total.minorUnits})."
        }

        val paymentId = InvoicePaymentId("pay-${command.invoiceId.value}-${command.paidAt.toEpochMilliseconds()}")
        val payment = InvoicePayment(
            id = paymentId,
            invoiceId = command.invoiceId,
            amount = command.amount,
            paidAt = command.paidAt,
            method = command.method,
            reference = command.reference,
            note = command.note,
            recordedBy = command.recordedBy
        )

        paymentRepository.append(command.tenantId, payment)

        // Perbarui status invoice sesuai akumulasi pembayaran
        val updatedInvoice = invoice.evaluatePaymentStatus(paidAmount = newTotalPaid, at = command.paidAt)
        invoiceRepository.save(updatedInvoice)

        payment
    }
}
