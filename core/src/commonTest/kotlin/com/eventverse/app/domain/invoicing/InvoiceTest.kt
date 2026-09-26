package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvoiceTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T10:00:00Z")
    private val today = LocalDate(2026, 9, 14)
    private val seedTemplate = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

    private fun createSampleInvoice(
        status: InvoiceStatus = InvoiceStatus.DRAFT,
        taxRatio: Ratio = Ratio.percent(11.0),
        globalDiscount: Ratio = Ratio.ZERO
    ): Invoice {
        val lines = listOf(
            InvoiceLine(
                id = InvoiceLineId("line-1"),
                description = "Jasa Jahit Kaos Polo",
                quantity = Quantity.pieces(100),
                unitPrice = Money.idr(50_000), // 100 * 50.000 = 5.000.000
                discount = Ratio.percent(10.0), // -10% = 500.000 -> 4.500.000
                sortOrder = 0
            ),
            InvoiceLine(
                id = InvoiceLineId("line-2"),
                description = "Bordir Logo Dada",
                quantity = Quantity.pieces(100),
                unitPrice = Money.idr(5_000), // 100 * 5.000 = 500.000
                discount = Ratio.ZERO,
                sortOrder = 1
            )
        )

        return Invoice(
            id = InvoiceId("inv-001"),
            tenantId = tenantId,
            number = InvoiceNumber("INV/2026/09/0001"),
            kind = InvoiceKind.FULL,
            status = status,
            billTo = BillToParty(name = "PT Busana Indah"),
            issuer = IssuerProfile(companyName = "PT WeMade Garmen"),
            lines = lines,
            taxRatio = taxRatio,
            globalDiscount = globalDiscount,
            currency = CurrencyCode.IDR,
            issueDate = today,
            dueDate = LocalDate(2026, 9, 28),
            templateId = seedTemplate.id,
            renderedTemplate = if (status != InvoiceStatus.DRAFT) seedTemplate else null,
            createdBy = "usr-sales",
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun financialCalculation_shouldBeExact() {
        val invoice = createSampleInvoice(
            taxRatio = Ratio.percent(11.0),
            globalDiscount = Ratio.percent(5.0) // 5% diskon global
        )

        // Line 1: gross = 5.000.000, discount = 500.000, amount = 4.500.000
        // Line 2: gross = 500.000, discount = 0, amount = 500.000
        // Subtotal = 5.000.000
        assertEquals(Money.idr(5_000_000), invoice.subtotal)

        // Global discount 5% atas 5.000.000 = 250.000
        assertEquals(Money.idr(250_000), invoice.discountAmount)

        // DPP (Taxable Base) = 5.000.000 - 250.000 = 4.750.000
        assertEquals(Money.idr(4_750_000), invoice.taxableBase)

        // PPN 11% atas 4.750.000 = 522.500
        assertEquals(Money.idr(522_500), invoice.taxAmount)

        // Grand Total = 4.750.000 + 522.500 = 5.272.500
        assertEquals(Money.idr(5_272_500), invoice.total)
    }

    @Test
    fun issuedInvoice_shouldDisallowMutations() {
        val issuedInvoice = createSampleInvoice(status = InvoiceStatus.ISSUED)

        val newLine = InvoiceLine(
            id = InvoiceLineId("line-3"),
            description = "Sablon Belakang",
            quantity = Quantity.pieces(50),
            unitPrice = Money.idr(10_000),
            sortOrder = 2
        )

        assertFailsWith<IllegalArgumentException>("Mutasi harus ditolak saat invoice sudah terbit") {
            issuedInvoice.addLine(newLine, now)
        }

        assertFailsWith<IllegalArgumentException> {
            issuedInvoice.removeLine(InvoiceLineId("line-1"), now)
        }

        assertFailsWith<IllegalArgumentException> {
            issuedInvoice.updateHeader(
                newBillTo = BillToParty(name = "Nama Baru"),
                newKind = InvoiceKind.SAMPLE,
                newTaxRatio = Ratio.ZERO,
                newGlobalDiscount = Ratio.ZERO,
                newIssueDate = today,
                newDueDate = null,
                newNotes = "",
                newTerms = "",
                at = now
            )
        }
    }

    @Test
    fun issue_shouldFreezeTemplateSnapshot() {
        val draft = createSampleInvoice(status = InvoiceStatus.DRAFT)
        assertNull(draft.renderedTemplate)

        val issued = draft.issue(seedTemplate, now)
        assertEquals(InvoiceStatus.ISSUED, issued.status)
        assertNotNull(issued.renderedTemplate)
        assertEquals(seedTemplate.id, issued.renderedTemplate?.id)
    }

    @Test
    fun paymentStatusTransitions_shouldReflectAccumulatedPayments() {
        val issued = createSampleInvoice(status = InvoiceStatus.ISSUED)
        val total = issued.total // 5.550.000

        // Pembayaran sebagian
        val partial = issued.evaluatePaymentStatus(paidAmount = Money.idr(2_000_000), at = now)
        assertEquals(InvoiceStatus.PARTIALLY_PAID, partial.status)

        // Pembayaran lunas
        val paid = issued.evaluatePaymentStatus(paidAmount = total, at = now)
        assertEquals(InvoiceStatus.PAID, paid.status)
    }

    @Test
    fun voidInvoice_shouldRequireReasonAndBlockPayment() {
        val issued = createSampleInvoice(status = InvoiceStatus.ISSUED)

        assertFailsWith<IllegalArgumentException> {
            issued.void(reason = "   ", at = now)
        }

        val voided = issued.void(reason = "Pesanan dibatalkan oleh klien", at = now)
        assertEquals(InvoiceStatus.VOID, voided.status)
        assertEquals("Pesanan dibatalkan oleh klien", voided.voidReason)
    }
}
