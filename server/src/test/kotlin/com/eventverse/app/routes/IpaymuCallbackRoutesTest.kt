package com.eventverse.app.routes

import com.eventverse.app.domain.builder.ConfirmSubscriptionPaymentUseCase
import com.eventverse.app.domain.builder.HandleIpaymuNotificationUseCase
import com.eventverse.app.domain.builder.IpaymuCheckout
import com.eventverse.app.domain.builder.IpaymuTransactionCheck
import com.eventverse.app.domain.builder.IpaymuTransactionStatus
import com.eventverse.app.domain.builder.PaymentGateway
import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceId
import com.eventverse.app.domain.builder.SubscriptionInvoiceLine
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemorySubscriptionInvoiceRepository
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang 5 (fail-closed) untuk webhook iPaymu (FR-PAY-3.3, dicocokkan docs Callback
 * iPaymu 2026-10-01): callback **publik** tapi tidak boleh mengubah invoice kecuali lolos
 * X-Signature (secret = VA) dan verifikasi domain. Respons **selalu 200** — penolakan lewat
 * body `accepted:false` (non-200 memicu retry iPaymu tanpa akhir). Fixture tenant non-rajut
 * (Kontrak 6).
 */
class IpaymuCallbackRoutesTest {

    /** Secret VA untuk test X-Signature — konstanta HMAC di bawah dihitung untuk payload ini. */
    private val callbackSecret = "1179000899"

    private class FakeGateway(var status: IpaymuTransactionStatus) : PaymentGateway {
        var statusChecks = 0
        /** SessionId yang dikembalikan saat cek status — bisa dibedakan untuk test mismatch. */
        var checkedSessionId = "trx-1"

        override suspend fun createCheckout(invoice: SubscriptionInvoice): Result<IpaymuCheckout> =
            Result.failure(IllegalStateException("tidak dipakai di test ini"))

        override suspend fun checkStatus(transactionId: String): Result<IpaymuTransactionCheck> {
            statusChecks++
            return Result.success(IpaymuTransactionCheck(status, checkedSessionId))
        }
    }

    private fun installApp(
        repo: InMemorySubscriptionInvoiceRepository,
        gateway: PaymentGateway,
        secret: String? = null
    ): ApplicationTestBuilder.() -> Unit = {
        application {
            routing {
                ipaymuCallbackRoutes(
                    HandleIpaymuNotificationUseCase(
                        invoices = repo,
                        gateway = gateway,
                        confirm = ConfirmSubscriptionPaymentUseCase(repo)
                    ),
                    callbackSecret = secret
                )
            }
        }
    }

    private suspend fun issuedInvoiceWithSession(repo: InMemorySubscriptionInvoiceRepository) {
        repo.save(
            SubscriptionInvoice(
                id = SubscriptionInvoiceId("inv-uji-001"),
                tenantId = TenantId("ten-bordir-uji"),
                number = "INV-2026-09-001",
                period = "2026-09",
                lines = listOf(
                    SubscriptionInvoiceLine("sampling_order", "Order Sampling", MoneyIdr(150_000), "SUBSCRIPTION")
                ),
                totalIdr = MoneyIdr(150_000),
                status = com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.ISSUED,
                ipaymuTrxId = "trx-1" // = sid yang dikirim callback
            )
        )
    }

    private suspend fun ApplicationTestBuilder.post(body: String, signature: String? = null) =
        client.post("/api/payment/ipaymu/notify") {
            contentType(ContentType.Application.FormUrlEncoded)
            signature?.let { header("X-Signature", it) }
            setBody(body)
        }

    /**
     * Konstanta X-Signature untuk payload test di [callback_withValidXSignature_isProcessed]
     * menurut algoritma resmi docs (normalisasi tipe → sort A-Z → JSON.stringify → escape `/`
     * → HMAC-SHA256, secret = VA), dihitung independen — bukan lewat kode produksi — agar
     * algoritmanya ter-pin.
     */
    private val simpleSignature = "b965437d0dcc5445fa580e5cda38dbf71087bf0bee5ea0b43bc4d66eb181628e"

    @Test
    fun callback_whenVerifiedPaid_marksInvoicePaid() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        issuedInvoiceWithSession(repo)
        installApp(repo, gateway)()

        val response = post("trx_id=12345678&sid=trx-1&status=berhasil&amount=150000&reference_id=inv-uji-001")

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"paid\":true"))
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.PAID,
            repo.rows.first().status
        )
        assertEquals(1, gateway.statusChecks, "isi callback diverifikasi ulang ke API iPaymu")
    }

    @Test
    fun callback_withValidXSignature_isProcessed() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        issuedInvoiceWithSession(repo)
        installApp(repo, gateway, secret = callbackSecret)()

        val response = post(
            "additional_info=[]&amount=150000&is_escrow=0&is_payment=1&merchant=$callbackSecret" +
                "&no_ref=12345&paid_off=150000&reference_id=inv-uji-001&sid=trx-1&status=berhasil" +
                "&status_code=1&trx_id=12345678&transaction_status_code=1",
            signature = simpleSignature
        )

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"paid\":true"), response.bodyAsText())
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.PAID,
            repo.rows.first().status
        )
    }

    @Test
    fun callback_withInvalidXSignature_isNotProcessedButStill200() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        issuedInvoiceWithSession(repo)
        installApp(repo, gateway, secret = callbackSecret)()

        val response = post(
            "trx_id=12345678&sid=trx-1&status=berhasil&amount=150000&reference_id=inv-uji-001",
            signature = "deadbeef"
        )

        assertEquals(200, response.status.value, "docs: selalu 200; non-200 = retry tanpa akhir")
        assertTrue(response.bodyAsText().contains("\"accepted\":false"))
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.ISSUED,
            repo.rows.first().status
        )
        assertEquals(0, gateway.statusChecks, "signature gagal = tidak sampai ke API iPaymu")
    }

    @Test
    fun callback_withStatusCode6_marksPaid() = testApplication {
        // `status` kosong → parser memakai `status_code`; 6 (Success-Unsettled) = PAID (docs).
        val repo = InMemorySubscriptionInvoiceRepository()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID)
        issuedInvoiceWithSession(repo)
        installApp(repo, gateway)()

        val response = post("trx_id=12345678&sid=trx-1&status_code=6&amount=150000&reference_id=inv-uji-001")

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"paid\":true"))
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.PAID,
            repo.rows.first().status
        )
    }

    @Test
    fun callback_withForeignTransactionId_isRejected_viaSessionIdVerification() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        val gateway = FakeGateway(IpaymuTransactionStatus.PAID).apply { checkedSessionId = "trx-ORANG-LAIN" }
        issuedInvoiceWithSession(repo)
        installApp(repo, gateway)()

        val response = post("trx_id=999&sid=trx-1&status=berhasil&amount=150000&reference_id=inv-uji-001")

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"accepted\":false"), "sessionId ≠ sid tersimpan ditolak")
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.ISSUED,
            repo.rows.first().status
        )
    }

    @Test
    fun callback_withTamperedAmount_isNotProcessedButStill200() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        issuedInvoiceWithSession(repo)
        installApp(repo, FakeGateway(IpaymuTransactionStatus.PAID))()

        val response = post("trx_id=12345678&sid=trx-1&status=berhasil&amount=15000&reference_id=inv-uji-001")

        assertEquals(200, response.status.value, "penolakan tetap 200 — beda channel, bukan retry")
        assertTrue(response.bodyAsText().contains("\"accepted\":false"))
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.ISSUED,
            repo.rows.first().status
        )
    }

    @Test
    fun callback_withMalformedPayload_isNotProcessedButStill200() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        installApp(repo, FakeGateway(IpaymuTransactionStatus.PAID))()

        val response = post("status=berhasil&amount=1")

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"accepted\":false"), "callback tanpa trx_id/sid ditolak")
    }

    @Test
    fun callback_withUnknownStatus_isNotProcessed_notSilentlyPending() = testApplication {
        val repo = InMemorySubscriptionInvoiceRepository()
        issuedInvoiceWithSession(repo)
        installApp(repo, FakeGateway(IpaymuTransactionStatus.PENDING))()

        val response = post("trx_id=12345678&sid=trx-1&status=huruf-dalem&amount=150000")

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"accepted\":false"), "status tak dikenal — tanpa fallback senyap")
        assertEquals(
            com.eventverse.app.domain.builder.SubscriptionInvoiceStatus.ISSUED,
            repo.rows.first().status
        )
    }
}
