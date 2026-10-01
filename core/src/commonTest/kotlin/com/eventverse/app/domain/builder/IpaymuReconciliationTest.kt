package com.eventverse.app.domain.builder

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rekonsiliasi (FR-PAY-3.3 butir 7): invoice ISSUED yang faktanya sudah dibayar tapi callback-nya
 * hilang dilunasi lewat cek ulang ke gateway — dengan anti-replay yang sama (sessionId wajib cocok).
 * Fixture tenant non-rajut (Kontrak 6).
 */
class IpaymuReconciliationTest {

    private class FakeGateway(var status: IpaymuTransactionStatus) : PaymentGateway {
        var checkedSessionId = "session-1"
        var fail = false
        /** Status per trx numerik — meniru dua transaksi iPaymu yang berbeda nasib. */
        var statusByTrx: Map<String, IpaymuTransactionStatus> = emptyMap()

        override suspend fun createCheckout(invoice: SubscriptionInvoice): Result<IpaymuCheckout> =
            Result.failure(IllegalStateException("tidak dipakai"))

        override suspend fun checkStatus(transactionId: String): Result<IpaymuTransactionCheck> =
            if (fail) Result.failure(IllegalStateException("iPaymu down"))
            else Result.success(
                IpaymuTransactionCheck(statusByTrx[transactionId] ?: status, checkedSessionId)
            )
    }

    private class FakeInvoiceRepo : SubscriptionInvoiceRepository {
        val rows = mutableListOf<SubscriptionInvoice>()
        override suspend fun findByTenant(tenantId: TenantId) = rows.filter { it.tenantId == tenantId }
        override suspend fun findAll() = rows.toList()
        override suspend fun findByIpaymuTrxId(trxId: String) = rows.firstOrNull { it.ipaymuTrxId == trxId }
        override suspend fun save(invoice: SubscriptionInvoice): SubscriptionInvoice {
            rows.removeAll { it.id == invoice.id }
            rows.add(invoice)
            return invoice
        }
    }

    private fun staleIssued(
        id: String,
        numeric: String? = "12345678",
        issuedAt: Instant = Clock.System.now() - 5.hours
    ) = SubscriptionInvoice(
        id = SubscriptionInvoiceId(id),
        tenantId = TenantId("ten-bordir-uji"),
        number = "INV-$id",
        period = "2026-09",
        lines = listOf(SubscriptionInvoiceLine("sampling_order", "Order Sampling", MoneyIdr(150_000), "SUBSCRIPTION")),
        totalIdr = MoneyIdr(150_000),
        status = SubscriptionInvoiceStatus.ISSUED,
        issuedAt = issuedAt,
        ipaymuTrxId = "session-1",
        ipaymuTrxNumeric = numeric
    )

    @Test
    fun `stale issued invoice proven paid by gateway is confirmed`() = runTest {
        val repo = FakeInvoiceRepo()
        repo.save(staleIssued("inv-1"))
        val reconcile = ReconcileSubscriptionInvoicesUseCase(repo, FakeGateway(IpaymuTransactionStatus.PAID), ConfirmSubscriptionPaymentUseCase(repo))

        val summary = reconcile().getOrThrow()

        assertEquals(1, summary.checked)
        assertEquals(1, summary.confirmedInvoices.size)
        assertEquals(SubscriptionInvoiceStatus.PAID, repo.rows.first().status)
        assertTrue(repo.rows.first().paidNote.orEmpty().contains("rekonsiliasi"))
    }

    @Test
    fun `gateway returning another session id is never used to confirm`() = runTest {
        val repo = FakeInvoiceRepo()
        repo.save(staleIssued("inv-1"))
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID).apply { checkedSessionId = "session-ORANG" }
        val reconcile = ReconcileSubscriptionInvoicesUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val summary = reconcile().getOrThrow()

        assertEquals(0, summary.confirmedInvoices.size)
        assertTrue(summary.failures.single().contains("sessionId"))
        assertEquals(SubscriptionInvoiceStatus.ISSUED, repo.rows.first().status)
    }

    @Test
    fun `pending stays issued and expired is left for human decision`() = runTest {
        val repo = FakeInvoiceRepo()
        repo.save(staleIssued("inv-pending"))
        repo.save(staleIssued("inv-expired", numeric = "999"))
        val gateway = FakeGateway(IpaymuTransactionStatus.PENDING).apply {
            statusByTrx = mapOf("12345678" to IpaymuTransactionStatus.PENDING, "999" to IpaymuTransactionStatus.EXPIRED)
        }
        val reconcile = ReconcileSubscriptionInvoicesUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val summary = reconcile().getOrThrow()

        assertEquals(1, summary.stillPending)
        assertEquals(1, summary.expiredOrFailed)
        assertEquals(SubscriptionInvoiceStatus.ISSUED, repo.rows.first { it.id.value == "inv-pending" }.status)
        assertEquals(
            SubscriptionInvoiceStatus.ISSUED,
            repo.rows.first { it.id.value == "inv-expired" }.status,
            "EXPIRED tidak otomatis di-void — keputusan manusia"
        )
        assertEquals(0, summary.confirmedInvoices.size)
    }

    @Test
    fun `invoice without numeric trx and fresh invoice are skipped`() = runTest {
        val repo = FakeInvoiceRepo()
        repo.save(staleIssued("inv-tanpa-numeric", numeric = null))
        repo.save(staleIssued("inv-baru", issuedAt = Clock.System.now())) // baru terbit — callback mungkin masih di jalan
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        val reconcile = ReconcileSubscriptionInvoicesUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val summary = reconcile().getOrThrow()

        assertEquals(0, summary.checked, "tanpa numeric tidak bisa dicek; invoice baru diserahkan ke callback")
    }

    @Test
    fun `gateway outage is reported as failure not crash`() = runTest {
        val repo = FakeInvoiceRepo()
        repo.save(staleIssued("inv-1"))
        val reconcile = ReconcileSubscriptionInvoicesUseCase(repo, FakeGateway(IpaymuTransactionStatus.PAID).apply { fail = true }, ConfirmSubscriptionPaymentUseCase(repo))

        val summary = reconcile().getOrThrow()

        assertEquals(1, summary.failures.size)
        assertEquals(0, summary.confirmedInvoices.size)
        assertEquals(SubscriptionInvoiceStatus.ISSUED, repo.rows.first().status)
    }
}
