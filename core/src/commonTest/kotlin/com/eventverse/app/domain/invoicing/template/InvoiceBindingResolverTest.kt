package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvoiceBindingResolverTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T10:00:00Z")
    private val today = LocalDate(2026, 9, 14)

    private fun createTestInvoice(): Pair<Invoice, InvoiceLine> {
        val line = InvoiceLine(
            id = InvoiceLineId("line-1"),
            description = "Kaos Sablon Plastisol",
            quantity = Quantity.pieces(200),
            unitPrice = Money.idr(75_000), // 15.000.000
            discount = Ratio.percent(5.0), // -750.000 = 14.250.000
            sortOrder = 0
        )

        val invoice = Invoice(
            id = InvoiceId("inv-001"),
            tenantId = tenantId,
            number = InvoiceNumber("INV/2026/09/0001"),
            kind = InvoiceKind.DOWN_PAYMENT,
            status = InvoiceStatus.DRAFT,
            billTo = BillToParty(
                name = "CV Maju Jaya",
                contactPerson = "Bpk. Hendra",
                address = "Jl. Sudirman No. 45, Bandung",
                phone = "08123456789",
                email = "hendra@majujaya.com",
                taxId = "01.234.567.8-901.000"
            ),
            issuer = IssuerProfile(
                companyName = "PT WeMade Manufaktur",
                address = "Kawasan Industri Cimahi Blok B",
                taxId = "02.345.678.9-012.000",
                phone = "022-987654",
                email = "finance@wemade.id",
                bankName = "Bank Central Asia (BCA)",
                bankAccountNumber = "123-456-7890",
                bankAccountHolder = "PT WeMade Manufaktur",
                logoAssetUrl = "https://wemade.id/logo.png"
            ),
            lines = listOf(line),
            taxRatio = Ratio.percent(11.0),
            globalDiscount = Ratio.ZERO,
            currency = CurrencyCode.IDR,
            issueDate = today,
            dueDate = LocalDate(2026, 9, 21),
            templateId = InvoiceTemplateId("tpl-std-id-001"),
            sourceKind = InvoiceSourceKind.SAMPLING,
            sourceRef = "SPK-1043",
            contractValue = Money.idr(30_000_000),
            notes = "Uang muka produksi 50%",
            terms = "Transfer tempo 7 hari",
            createdBy = "usr-sales",
            createdAt = now,
            updatedAt = now
        )

        return invoice to line
    }

    @Test
    fun allRegisteredTokens_mustResolveWithoutExceptions() {
        val (invoice, line) = createTestInvoice()

        // 1. Dokumen scope
        for (desc in InvoiceBindingRegistry.DOCUMENT) {
            val resolved = InvoiceBindingResolver.resolve(
                token = desc.token,
                invoice = invoice,
                line = line,
                paidAmount = Money.idr(5_000_000)
            )
            assertNotEquals(
                ResolvedBindingValue.Empty,
                resolved,
                "Token dokumen '${desc.token.value}' harus ter-resolve ke nilai non-empty"
            )
        }

        // 2. Line scope
        for (desc in InvoiceBindingRegistry.LINE) {
            val resolved = InvoiceBindingResolver.resolve(
                token = desc.token,
                invoice = invoice,
                line = line
            )
            assertNotEquals(
                ResolvedBindingValue.Empty,
                resolved,
                "Token baris '${desc.token.value}' harus ter-resolve ke nilai non-empty"
            )
        }
    }

    @Test
    fun terbilangRupiah_shouldProduceExactIndonesianWords() {
        assertEquals("Nol Rupiah", TerbilangRupiah.konversi(0L))
        assertEquals("Satu Rupiah", TerbilangRupiah.konversi(1L))
        assertEquals("Sepuluh Rupiah", TerbilangRupiah.konversi(10L))
        assertEquals("Sebelas Rupiah", TerbilangRupiah.konversi(11L))
        assertEquals("Lima Belas Rupiah", TerbilangRupiah.konversi(15L))
        assertEquals("Seratus Rupiah", TerbilangRupiah.konversi(100L))
        assertEquals("Seribu Rupiah", TerbilangRupiah.konversi(1000L))
        assertEquals("Sepuluh Juta Satu Rupiah", TerbilangRupiah.konversi(10_000_001L))
        assertEquals(
            "Lima Belas Juta Empat Ratus Ribu Rupiah",
            TerbilangRupiah.konversi(15_400_000L)
        )
        assertEquals(
            "Satu Miliar Dua Ratus Lima Puluh Juta Rupiah",
            TerbilangRupiah.konversi(1_250_000_000L)
        )
    }

    @Test
    fun formatMoney_shouldIncludeThousandsSeparator() {
        val money = Money.idr(15_400_000)
        val formatted = InvoiceBindingResolver.formatMoney(money)
        assertEquals("Rp 15.400.000", formatted)
    }
}
