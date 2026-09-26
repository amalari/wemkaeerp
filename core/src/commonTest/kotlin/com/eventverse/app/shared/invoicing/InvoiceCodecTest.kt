package com.eventverse.app.shared.invoicing

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceCodecTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T12:00:00Z")
    private val today = LocalDate(2026, 9, 14)
    private val template = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

    @Test
    fun issuedInvoice_roundTrip_shouldPreserveAllDataAndCalculations() {
        val line = InvoiceLine(
            id = InvoiceLineId("line-1"),
            description = "Kemeja Tactical PDL",
            quantity = Quantity.pieces(150),
            unitPrice = Money.idr(85_000),
            discount = Ratio.percent(5.0),
            sortOrder = 0
        )

        val original = Invoice(
            id = InvoiceId("inv-100"),
            tenantId = tenantId,
            number = InvoiceNumber("INV/2026/09/0099"),
            kind = InvoiceKind.DOWN_PAYMENT,
            status = InvoiceStatus.ISSUED,
            billTo = BillToParty(
                name = "PT Logistik Prima",
                contactPerson = "Ibu Rina",
                address = "Gedung Cyber Lt. 5, Jakarta",
                phone = "021-555666",
                email = "finance@prima.com",
                taxId = "03.456.789.0-123.000"
            ),
            issuer = IssuerProfile(
                companyName = "PT WeMade Manufaktur",
                address = "Kawasan Industri Cimahi Blok B",
                taxId = "01.234.567.8-901.000",
                bankName = "BCA",
                bankAccountNumber = "888-999-000",
                bankAccountHolder = "PT WeMade Manufaktur"
            ),
            lines = listOf(line),
            taxRatio = Ratio.percent(11.0),
            globalDiscount = Ratio.percent(2.0),
            currency = CurrencyCode.IDR,
            issueDate = today,
            dueDate = LocalDate(2026, 9, 28),
            templateId = template.id,
            renderedTemplate = template,
            sourceKind = InvoiceSourceKind.SAMPLING,
            sourceRef = "SPK-9901",
            contractValue = Money.idr(25_000_000),
            notes = "Invoice uang muka",
            terms = "Net 14 hari",
            createdBy = "usr-sales",
            createdAt = now,
            updatedAt = now
        )

        val encoded = InvoiceCodec.encode(original)
        val decoded = InvoiceCodec.decode(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.number, decoded.number)
        assertEquals(original.kind, decoded.kind)
        assertEquals(original.status, decoded.status)
        assertEquals(original.billTo, decoded.billTo)
        assertEquals(original.issuer, decoded.issuer)
        assertEquals(original.lines.size, decoded.lines.size)
        assertEquals(original.lines[0].description, decoded.lines[0].description)
        assertEquals(original.lines[0].grossAmount, decoded.lines[0].grossAmount)
        assertEquals(original.lines[0].amount, decoded.lines[0].amount)
        assertEquals(original.subtotal, decoded.subtotal)
        assertEquals(original.discountAmount, decoded.discountAmount)
        assertEquals(original.taxAmount, decoded.taxAmount)
        assertEquals(original.total, decoded.total)
        assertEquals(original.renderedTemplate?.id, decoded.renderedTemplate?.id)
    }
}
