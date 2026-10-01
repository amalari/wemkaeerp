package com.eventverse.app.domain.builder

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * L1 Billing iPaymu (FR-PAY-3): yang dijaga di sini adalah **urutan kepercayaan** callback —
 * isi callback tidak pernah menjadi bukti lunas; nominal dan status dicek ulang. Fixture memakai
 * tenant non-rajut (`ten-bordir-uji`) sesuai tenant-variability-rules Kontrak 6.
 */
class IpaymuPaymentUseCaseTest {

    private class FakeInvoiceRepo : SubscriptionInvoiceRepository {
        val items = mutableListOf<SubscriptionInvoice>()
        override suspend fun findByTenant(tenantId: TenantId) = items.filter { it.tenantId == tenantId }
        override suspend fun findAll() = items.toList()
        override suspend fun findByIpaymuTrxId(trxId: String) =
            items.firstOrNull { it.ipaymuTrxId == trxId }
        override suspend fun save(invoice: SubscriptionInvoice): SubscriptionInvoice {
            items.removeAll { it.id == invoice.id }
            items += invoice
            return invoice
        }
    }

    /** Gateway palsu yang statusnya bisa diubah antar pemanggilan — meniru sisi iPaymu. */
    private class FakeGateway(var status: IpaymuTransactionStatus) : PaymentGateway {
        var createCount = 0
        var statusChecks = 0
        /** SessionId yang dikembalikan saat cek status — bisa dibedakan untuk test mismatch. */
        var checkedSessionId = "session-1"

        override suspend fun createCheckout(invoice: SubscriptionInvoice): Result<IpaymuCheckout> {
            createCount++
            return Result.success(IpaymuCheckout("91538218-5158-459B-8716-DD97FFE3EDAB", "https://sandbox.ipaymu.com/payment/x"))
        }

        override suspend fun checkStatus(transactionId: String): Result<IpaymuTransactionCheck> {
            statusChecks++
            return Result.success(IpaymuTransactionCheck(status, checkedSessionId))
        }
    }

    private val tenantId = TenantId("ten-bordir-uji")
    private val sessionId = "session-1"

    private suspend fun issuedInvoice(repo: FakeInvoiceRepo): SubscriptionInvoice {
        val invoice = SubscriptionInvoice(
            id = SubscriptionInvoiceId("inv-uji-001"),
            tenantId = tenantId,
            number = "INV-2026-09-001",
            period = "2026-09",
            lines = listOf(SubscriptionInvoiceLine("sampling_order", "Order Sampling", MoneyIdr(150_000), "SUBSCRIPTION")),
            totalIdr = MoneyIdr(150_000),
            status = SubscriptionInvoiceStatus.ISSUED
        )
        repo.save(invoice)
        return invoice
    }

    @Test
    fun `checkout assigns trx id and is idempotent`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PENDING)
        issuedInvoice(repo)
        val checkout = CreateInvoiceCheckoutUseCase(repo, gateway)

        val first = checkout(SubscriptionInvoiceId("inv-uji-001")).getOrThrow()
        val second = checkout(SubscriptionInvoiceId("inv-uji-001")).getOrThrow()

        assertEquals("91538218-5158-459B-8716-DD97FFE3EDAB", first.invoice.ipaymuTrxId)
        assertEquals(1, gateway.createCount, "klik bayar dua kali tidak membuat dua transaksi")
        assertEquals(first.invoice.ipaymuTrxId, second.invoice.ipaymuTrxId)
    }

    @Test
    fun `checkout refuses void invoices and does not write trx id on failure`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PENDING)
        issuedInvoice(repo)
        val useCase = CreateInvoiceCheckoutUseCase(repo, gateway)

        repo.save(repo.items.first().copy(status = SubscriptionInvoiceStatus.VOID))
        assertTrue(useCase(SubscriptionInvoiceId("inv-uji-001")).isFailure, "VOID tidak boleh dibuatkan trx")
        assertTrue(repo.items.none { it.ipaymuTrxId != null }, "gagal checkout tidak menulis trx_id")
    }

    @Test
    fun `notification parser is strict`() {
        val ok = IpaymuNotification.fromFields(
            mapOf(
                "trx_id" to "12345678", "sid" to "SESSION-1", "status" to "berhasil",
                "amount" to "150000", "reference_id" to "inv-x"
            )
        )
        assertEquals(IpaymuTransactionStatus.PAID, ok.status)
        assertEquals("SESSION-1", ok.sid)

        // Fallback status_code resmi (1=Sukses) bila status teks kosong.
        val byCode = IpaymuNotification.fromFields(
            mapOf("trx_id" to "1", "sid" to "s", "status_code" to "1", "amount" to "10")
        )
        assertEquals(IpaymuTransactionStatus.PAID, byCode.status)

        assertFailsWith<IllegalStateException> {
            IpaymuNotification.fromFields(
                mapOf("trx_id" to "1", "sid" to "s", "status" to "huruf-dalem", "amount" to "1")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            IpaymuNotification.fromFields(mapOf("status" to "PAID", "amount" to "1"))
        }
        assertFailsWith<IllegalArgumentException> {
            IpaymuNotification.fromFields(mapOf("trx_id" to "1", "status" to "PAID", "amount" to "1"))
        }
    }

    @Test
    fun `official status codes map per documentation`() {
        assertEquals(IpaymuTransactionStatus.PAID, IpaymuTransactionStatus.fromApi("1"))
        assertEquals(IpaymuTransactionStatus.PAID, IpaymuTransactionStatus.fromApi("6")) // unsettled
        assertEquals(IpaymuTransactionStatus.PENDING, IpaymuTransactionStatus.fromApi("0"))
        assertEquals(IpaymuTransactionStatus.PENDING, IpaymuTransactionStatus.fromApi("7")) // escrow
        assertEquals(IpaymuTransactionStatus.EXPIRED, IpaymuTransactionStatus.fromApi("-2"))
        assertEquals(IpaymuTransactionStatus.FAILED, IpaymuTransactionStatus.fromApi("2")) // cancelled
        assertEquals(IpaymuTransactionStatus.FAILED, IpaymuTransactionStatus.fromApi("3")) // refund
        assertEquals(IpaymuTransactionStatus.FAILED, IpaymuTransactionStatus.fromApi("5"))
    }

    // ------------------------------------------------------------------ callback

    private fun notification(
        amount: Long = 150_000,
        trxId: String = "12345678",
        sid: String = sessionId,
    ) = IpaymuNotification(
        trxId = trxId,
        sid = sid,
        referenceId = "inv-uji-001",
        status = IpaymuTransactionStatus.PAID,
        amountIdr = amount
    )

    private suspend fun repoWithSession(repo: FakeInvoiceRepo) {
        issuedInvoice(repo).let { repo.save(it.copy(ipaymuTrxId = sessionId)) }
    }

    @Test
    fun `paid callback with matching amount is confirmed via re-check`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        repoWithSession(repo)
        val handle = HandleIpaymuNotificationUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val result = handle(notification()).getOrThrow()

        assertEquals(SubscriptionInvoiceStatus.PAID, result.status)
        assertEquals(1, gateway.statusChecks, "status dicek ulang ke iPaymu, isi callback tidak dipercaya")
        assertTrue(result.paidNote.orEmpty().contains(sessionId))
    }

    @Test
    fun `re-check returning another session id is rejected`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID).apply { checkedSessionId = "session-ORANG-LAIN" }
        repoWithSession(repo)
        val handle = HandleIpaymuNotificationUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val result = handle(notification(trxId = "999")) // transactionId transaksi orang lain

        assertTrue(result.isFailure, "sessionId hasil cek ≠ sid tersimpan = pencurian trx_id, tolak")
        assertEquals(SubscriptionInvoiceStatus.ISSUED, repo.items.first().status)
    }

    @Test
    fun `callback claiming paid but gateway says pending changes nothing`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PENDING)
        repoWithSession(repo)
        val handle = HandleIpaymuNotificationUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val result = handle(notification()).getOrThrow()

        assertEquals(SubscriptionInvoiceStatus.ISSUED, result.status, "bukti lunas hanya dari API iPaymu")
    }

    @Test
    fun `callback with wrong amount is rejected and invoice stays issued`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        repoWithSession(repo)
        val handle = HandleIpaymuNotificationUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val result = handle(notification(amount = 15_000))

        assertTrue(result.isFailure, "nominal ≠ totalIdr harus ditolak")
        assertEquals(SubscriptionInvoiceStatus.ISSUED, repo.items.first().status)
    }

    @Test
    fun `unknown sid and void invoice are rejected`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        repoWithSession(repo)
        val handle = HandleIpaymuNotificationUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        assertTrue(handle(notification(sid = "session-asing")).isFailure, "sid tidak dikenal ditolak")

        repo.save(repo.items.first().copy(status = SubscriptionInvoiceStatus.VOID))
        assertTrue(handle(notification()).isFailure, "invoice VOID tidak boleh dikonfirmasi callback")
    }

    @Test
    fun `repeat paid callback is idempotent`() = runTest {
        val repo = FakeInvoiceRepo()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        repoWithSession(repo)
        val handle = HandleIpaymuNotificationUseCase(repo, gateway, ConfirmSubscriptionPaymentUseCase(repo))

        val first = handle(notification()).getOrThrow()
        val second = handle(notification()).getOrThrow()

        assertEquals(SubscriptionInvoiceStatus.PAID, second.status)
        assertEquals(first.paidAt, second.paidAt, "callback ulang tidak mengubah jam bayar")
    }
}
