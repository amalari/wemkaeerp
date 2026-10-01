package com.eventverse.app.domain.builder

import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Rekonsiliasi iPaymu (FR-PAY-3.3 butir 7) — sisi **pull** yang menutup kelemahan callback:
 * callback adalah push sekali jalan; bila tidak pernah sampai (tunnel mati, laptop sleep,
 * retry iPaymu habis), invoice yang **faktanya sudah dibayar** akan selamanya `ISSUED`.
 *
 * Cara kerja: ambil invoice `ISSUED` yang punya `ipaymuTrxNumeric` (angka yang hanya bisa
 * didapat dari callback pertama — V87) dan sudah cukup tua, tanyakan ulang statusnya ke API
 * iPaymu, lalu:
 *  - **PAID** dan `sessionId` cocok dengan `ipaymuTrxId` tersimpan → lunasi lewat
 *    [ConfirmSubscriptionPaymentUseCase] (reuse: idempoten, ter-audit, tetap cek nominal);
 *  - **PENDING / EXPIRED / FAILED** → dibiarkan `ISSUED` — keputusan void/refund adalah
 *    keputusan manusia, bukan otomatisasi;
 *  - `sessionId` hasil cek **tidak cocok** → dicatat sebagai failure, TIDAK dipercaya
 *    (anti-replay yang sama dengan callback).
 *
 * Invoice tanpa `ipaymuTrxNumeric` (belum pernah dikirimi callback apa pun) tidak bisa
 * dicek — endpoint cek status iPaymu butuh angka numerik. Batas ini tercatat di TRD-PAY-001 §6.
 */
class ReconcileSubscriptionInvoicesUseCase(
    private val invoices: SubscriptionInvoiceRepository,
    private val gateway: PaymentGateway,
    private val confirm: ConfirmSubscriptionPaymentUseCase,
    private val clock: Clock = Clock.System
) {
    /** Ringkasan satu putaran rekonsiliasi — cukup untuk log audit superadmin. */
    data class Summary(
        val checked: Int,
        /** Invoice yang berhasil dilunasi lewat rekonsiliasi (untuk audit per tenant). */
        val confirmedInvoices: List<SubscriptionInvoice>,
        val stillPending: Int,
        val expiredOrFailed: Int,
        val failures: List<String>
    )

    /**
     * @param minAge invoice lebih muda dari ini di-skip — callback resmi mungkin masih
     *   di dalam antrian/retry iPaymu; jangan merebut tugasnya sebelum waktu yang wajar.
     */
    suspend operator fun invoke(minAge: Duration = 1.hours): Result<Summary> = runCatching {
        val now = clock.now()
        val candidates = invoices.findAll().filter {
            it.status == SubscriptionInvoiceStatus.ISSUED &&
                !it.ipaymuTrxNumeric.isNullOrBlank() &&
                it.issuedAt != null && (now - it.issuedAt) >= minAge
        }

        val confirmedInvoices = mutableListOf<SubscriptionInvoice>()
        var stillPending = 0
        var expiredOrFailed = 0
        val failures = mutableListOf<String>()

        for (invoice in candidates) {
            val numeric = invoice.ipaymuTrxNumeric ?: continue
            val check = runCatching { gateway.checkStatus(numeric).getOrThrow() }.fold(
                onSuccess = { it },
                onFailure = { e ->
                    failures += "${invoice.number}: cek status gagal — ${e.message}"
                    null
                }
            ) ?: continue

            if (!check.sessionId.equals(invoice.ipaymuTrxId, ignoreCase = true)) {
                // Status milik transaksi lain — jangan pernah dipakai melunasi invoice ini.
                failures += "${invoice.number}: sessionId hasil cek '${check.sessionId}' ≠ sid tersimpan"
                continue
            }

            when (check.status) {
                IpaymuTransactionStatus.PAID -> confirm(
                    invoice.id,
                    note = "rekonsiliasi iPaymu: trx numerik $numeric (callback tidak diterima)"
                ).fold(
                    onSuccess = { confirmedInvoices += it },
                    onFailure = { e -> failures += "${invoice.number}: konfirmasi gagal — ${e.message}" }
                )
                IpaymuTransactionStatus.PENDING -> stillPending++
                IpaymuTransactionStatus.EXPIRED, IpaymuTransactionStatus.FAILED -> expiredOrFailed++
            }
        }

        Summary(
            checked = candidates.size,
            confirmedInvoices = confirmedInvoices,
            stillPending = stillPending,
            expiredOrFailed = expiredOrFailed,
            failures = failures
        )
    }
}
