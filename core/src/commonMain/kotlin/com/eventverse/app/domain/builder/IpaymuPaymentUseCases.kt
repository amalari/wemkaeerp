package com.eventverse.app.domain.builder

/**
 * Hasil pembuatan checkout: invoice (mungkin sudah berubah `ipaymuTrxId`) dan URL halaman
 * pembayaran bila transaksi **baru** dibuat (`null` bila trx lama dipakai ulang — URL iPaymu
 * tidak disimpan di domain, cukup trxId untuk dicek ulang).
 */
data class IpaymuCheckoutResult(
    val invoice: SubscriptionInvoice,
    val paymentUrl: String?
)

/**
 * Membuat Hosted Checkout untuk satu invoice ISSUED. Idempoten: invoice yang sudah punya
 * `ipaymuTrxId` memakai transaksi yang sama (membuat trx baru tiap klik "Bayar" akan menulis
 * dua tagihan untuk satu invoice di dashboard iPaymu).
 */
class CreateInvoiceCheckoutUseCase(
    private val invoices: SubscriptionInvoiceRepository,
    private val gateway: PaymentGateway
) {
    suspend operator fun invoke(invoiceId: SubscriptionInvoiceId): Result<IpaymuCheckoutResult> = runCatching {
        val invoice = invoices.findAll().firstOrNull { it.id == invoiceId }
            ?: error("Invoice '${invoiceId.value}' tidak ditemukan")
        when (invoice.status) {
            SubscriptionInvoiceStatus.VOID -> error("Invoice ${invoice.number} sudah dibatalkan")
            SubscriptionInvoiceStatus.PAID -> error("Invoice ${invoice.number} sudah lunas")
            SubscriptionInvoiceStatus.DRAFT -> error("Invoice ${invoice.number} masih DRAFT")
            SubscriptionInvoiceStatus.ISSUED -> Unit
        }

        invoice.ipaymuTrxId?.let { return@runCatching IpaymuCheckoutResult(invoice, paymentUrl = null) }

        val checkout = gateway.createCheckout(invoice).getOrThrow()
        IpaymuCheckoutResult(invoices.save(invoice.copy(ipaymuTrxId = checkout.trxId)), checkout.paymentUrl)
    }
}

/**
 * Menangani callback `POST /api/payment/ipaymu/notify` (FR-PAY-3.3, dicocokkan dengan
 * dokumentasi Callback iPaymu 2026-10-01). Urutan yang menjamin kebenaran, tiap langkah
 * bisa menolak:
 *
 * 1. invoice dicari lewat **`sid`** (= SessionID saat create, tersimpan di `ipaymu_trx_id`) —
 *    callback dengan `sid` tak dikenal ditolak;
 * 2. invoice PAID → sukses tanpa perubahan (idempoten, sifat [ConfirmSubscriptionPaymentUseCase]);
 * 3. invoice VOID → ditolak;
 * 4. **nominal ≠ totalIdr → ditolak** (callback bisa dipalsukan; nominal tidak boleh dipercaya);
 * 5. **status dicek ulang ke API iPaymu** memakai `trx_id` numerik dari callback, dan
 *    `Data.SessionId` pada hasil cek **wajib sama** dengan `sid` tersimpan — menutup celah
 *    callback palsu yang mencuri `transactionId` transaksi lain yang sudah lunas.
 */
class HandleIpaymuNotificationUseCase(
    private val invoices: SubscriptionInvoiceRepository,
    private val gateway: PaymentGateway,
    private val confirm: ConfirmSubscriptionPaymentUseCase
) {
    suspend operator fun invoke(notification: IpaymuNotification): Result<SubscriptionInvoice> = runCatching {
        val invoice = invoices.findByIpaymuTrxId(notification.sid)
            ?: error("Callback untuk sid '${notification.sid}' tidak dikenal")

        // Rekam trx numerik iPaymu SEKARANG, sebelum verifikasi apa pun — angka ini satu-satunya
        // kunci untuk menanyakan ulang status lewat rekonsiliasi (FR-PAY-3.3 butir 7) bila
        // callback berikutnya hilang. Idempoten: nilai sama tidak menulis ulang.
        if (invoice.ipaymuTrxNumeric != notification.trxId) {
            invoices.save(invoice.copy(ipaymuTrxNumeric = notification.trxId))
        }

        when (invoice.status) {
            SubscriptionInvoiceStatus.PAID -> return@runCatching invoice
            SubscriptionInvoiceStatus.VOID -> error("Invoice ${invoice.number} sudah dibatalkan")
            else -> Unit
        }

        require(notification.amountIdr == invoice.totalIdr.amount) {
            "Nominal callback ${notification.amountIdr} != total invoice ${invoice.totalIdr.amount}"
        }

        // Sumber kebenaran adalah API iPaymu, bukan isi callback (FR-PAY-3.3 butir 2).
        val check = gateway.checkStatus(notification.trxId).getOrThrow()
        require(check.sessionId.equals(invoice.ipaymuTrxId, ignoreCase = true)) {
            "SessionId hasil cek '${check.sessionId}' != sid tersimpan '${invoice.ipaymuTrxId}'"
        }
        if (check.status != IpaymuTransactionStatus.PAID) {
            // PENDING/EXPIRED/FAILED: tidak ada perubahan status — callback dicatat & diabaikan.
            return@runCatching invoice
        }

        confirm(invoice.id, note = "iPaymu trx ${notification.trxId} (sid ${notification.sid})").getOrThrow()
    }
}
