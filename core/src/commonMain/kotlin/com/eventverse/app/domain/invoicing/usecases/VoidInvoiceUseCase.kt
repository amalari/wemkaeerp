package com.eventverse.app.domain.invoicing.usecases

import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.InvoiceId
import com.eventverse.app.domain.invoicing.InvoicePaymentRepository
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class VoidInvoiceCommand(
    val tenantId: TenantId,
    val invoiceId: InvoiceId,
    val reason: String,
    val now: Instant = Clock.System.now()
)

class VoidInvoiceUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: InvoicePaymentRepository
) {
    suspend operator fun invoke(command: VoidInvoiceCommand): Result<Invoice> = runCatching {
        val invoice = invoiceRepository.findById(command.tenantId, command.invoiceId)
            ?: error("Invoice dengan ID '${command.invoiceId.value}' tidak ditemukan.")

        val paidAmount = paymentRepository.totalPaidFor(command.tenantId, command.invoiceId)
        require(paidAmount.isZero) {
            "Invoice ${invoice.number.value} sudah memiliki riwayat pembayaran (${paidAmount.minorUnits}). Batalkan atau kembalikan pembayaran terlebih dahulu."
        }

        val voided = invoice.void(reason = command.reason, at = command.now)
        invoiceRepository.save(voided)
        voided
    }
}
