package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.Money
import kotlinx.datetime.Instant

/**
 * Entitas pembayaran invoice (append-only ledger).
 * Setiap mutasi pembayaran dicatat permanen dan tidak dapat diedit secara serampangan.
 */
data class InvoicePayment(
    val id: InvoicePaymentId,
    val invoiceId: InvoiceId,
    val amount: Money,
    val paidAt: Instant,
    val method: String,
    val reference: String = "",
    val note: String = "",
    val recordedBy: String
) {
    init {
        require(amount.isPositive) { "Nominal pembayaran harus lebih besar dari nol: ${amount.minorUnits}." }
        require(method.isNotBlank()) { "Metode pembayaran tidak boleh kosong." }
        require(recordedBy.isNotBlank()) { "Petugas pencatat pembayaran tidak boleh kosong." }
    }
}
