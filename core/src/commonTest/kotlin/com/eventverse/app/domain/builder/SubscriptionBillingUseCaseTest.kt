package com.eventverse.app.domain.builder

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.usecases.BillingLine
import com.eventverse.app.domain.moduledev.usecases.TenantBillingPreview
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tagihan langganan (FR-M2-5): yang dijaga di sini bukan "invoice terbentuk", melainkan **harga
 * tetap beku setelah diterbitkan** — inilah alasan keberadaan kelas-kelas ini.
 */
class SubscriptionBillingUseCaseTest {

    private class FakeInvoiceRepo : SubscriptionInvoiceRepository {
        val items = mutableListOf<SubscriptionInvoice>()
        override suspend fun findByTenant(tenantId: TenantId) = items.filter { it.tenantId == tenantId }
        override suspend fun findAll() = items.toList()
        override suspend fun save(invoice: SubscriptionInvoice): SubscriptionInvoice {
            items.removeAll { it.id == invoice.id }
            items += invoice
            return invoice
        }
    }

    /** Sumber harga yang bisa naik antar pemanggilan — meniru katalog yang dinaikkan. */
    private class MovingPrice(private var price: MoneyIdr) {
        var current: MoneyIdr
            get() = price
            set(value) { price = value }

        fun source() = TenantBillingPreviewSource { tenantId ->
            Result.success(
                TenantBillingPreview(
                    tenantId,
                    listOf(
                        BillingLine(
                            moduleId = "sampling_order",
                            displayName = "Order Sampling",
                            monthlyPrice = price,
                            kind = BillingLine.Kind.SUBSCRIPTION
                        )
                    )
                )
            )
        }
    }

    private val tenantId = TenantId("ten-bordir-uji")

    @Test
    fun `issue locks the price so a later catalogue rise cannot change an issued invoice`() = runTest {
        val invoices = FakeInvoiceRepo()
        val price = MovingPrice(MoneyIdr(150_000))
        val issue = IssueSubscriptionInvoiceUseCase(price.source(), invoices)

        val first = issue(tenantId, "2026-09").getOrThrow()
        price.current = MoneyIdr(400_000)
        val second = issue(tenantId, "2026-10").getOrThrow()

        assertEquals(150_000, first.totalIdr.amount, "invoice September tetap pada harga saat terbit")
        assertEquals(400_000, second.totalIdr.amount, "harga baru berlaku untuk tagihan berikutnya")
        assertEquals(150_000, invoices.findByTenant(tenantId).first().totalIdr.amount)
    }

    @Test
    fun `issue twice in the same period is rejected, not silently renumbered`() = runTest {
        val invoices = FakeInvoiceRepo()
        val issue = IssueSubscriptionInvoiceUseCase(MovingPrice(MoneyIdr(100_000)).source(), invoices)

        issue(tenantId, "2026-09").getOrThrow()
        val repeat = issue(tenantId, "2026-09")

        assertTrue(repeat.isFailure, "periode sama tanpa VOID harus ditolak")
        assertTrue(repeat.exceptionOrNull()?.message?.contains("sudah diterbitkan") == true)
    }

    @Test
    fun `issue without billable lines is refused rather than sending a zero invoice`() = runTest {
        val invoices = FakeInvoiceRepo()
        val empty = TenantBillingPreviewSource { tenantId ->
            Result.success(TenantBillingPreview(tenantId, emptyList()))
        }

        val result = IssueSubscriptionInvoiceUseCase(empty, invoices)(tenantId, "2026-09")

        assertTrue(result.isFailure)
        assertTrue(invoices.items.isEmpty(), "invoice kosong tidak tersimpan")
    }

    @Test
    fun `document number is deterministic per tenant and period`() = runTest {
        val invoices = FakeInvoiceRepo()
        val issue = IssueSubscriptionInvoiceUseCase(MovingPrice(MoneyIdr(90_000)).source(), invoices)

        val invoice = issue(tenantId, "2026-09").getOrThrow()

        assertEquals("INV-2026-09-001", invoice.number)
    }

    @Test
    fun `confirm marks paid and keeps the original locked total`() = runTest {
        val invoices = FakeInvoiceRepo()
        val price = MovingPrice(MoneyIdr(150_000))
        val issued = IssueSubscriptionInvoiceUseCase(price.source(), invoices)(tenantId, "2026-09").getOrThrow()
        price.current = MoneyIdr(999_000)

        val paid = ConfirmSubscriptionPaymentUseCase(invoices)(
            issued.id, note = "transfer BCA 12 Sep"
        ).getOrThrow()

        assertEquals(SubscriptionInvoiceStatus.PAID, paid.status)
        assertNotNull(paid.paidAt)
        assertEquals(150_000, paid.totalIdr.amount, "konfirmasi tidak menghitung ulang harga")
        assertEquals("transfer BCA 12 Sep", paid.paidNote)
    }

    @Test
    fun `confirm is idempotent for paid invoices but refuses a voided one`() = runTest {
        val invoices = FakeInvoiceRepo()
        val issue = IssueSubscriptionInvoiceUseCase(MovingPrice(MoneyIdr(150_000)).source(), invoices)
        val confirm = ConfirmSubscriptionPaymentUseCase(invoices)
        val issued = issue(tenantId, "2026-09").getOrThrow()

        val first = confirm(issued.id).getOrThrow()
        val second = confirm(issued.id).getOrThrow()
        assertEquals(first.paidAt, second.paidAt, "klik dua kali tidak mengubah jam bayar")

        invoices.save(issued.copy(status = SubscriptionInvoiceStatus.VOID))
        assertTrue(confirm(issued.id).isFailure, "konfirmasi atas invoice VOID harus ditolak")
    }

    @Test
    fun `invoice refuses to exist in a paid state without a payment time`() = runTest {
        val broken = runCatching {
            SubscriptionInvoice(
                id = SubscriptionInvoiceId("inv-x"),
                tenantId = tenantId,
                number = "INV-2026-09-001",
                period = "2026-09",
                lines = listOf(
                    SubscriptionInvoiceLine("sampling_order", "Order Sampling", MoneyIdr(1), "SUBSCRIPTION")
                ),
                totalIdr = MoneyIdr(1),
                status = SubscriptionInvoiceStatus.PAID
            )
        }

        assertTrue(broken.isFailure, "PAID tanpa paidAt adalah data rusak, bukan sekadar aneh")
    }
}
