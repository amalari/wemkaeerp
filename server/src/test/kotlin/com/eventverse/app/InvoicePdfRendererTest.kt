package com.eventverse.app

import com.eventverse.app.domain.common.*
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.invoicing.template.Mm10
import com.eventverse.app.domain.invoicing.template.TemplateElement
import com.eventverse.app.domain.invoicing.template.TemplateRect
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.pdf.InvoicePdfRenderer
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvoicePdfRendererTest {

    @Test
    fun render_invoice_to_pdf_should_produce_valid_pdf_bytes() {
        val tenantId = TenantId("ten-demo-001")
        val now = Clock.System.now()
        val template = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

        val line1 = InvoiceLine(
            id = InvoiceLineId("line-001"),
            description = "Kemeja PDH Drill Navy bordir logo dada",
            quantity = Quantity.of(100.0, UnitOfMeasure.PIECE),
            unitPrice = Money.idr(125_000L * 100),
            discount = Ratio.ZERO,
            sortOrder = 1
        )
        val line2 = InvoiceLine(
            id = InvoiceLineId("line-002"),
            description = "Polo Shirt Cotton Pique Putih",
            quantity = Quantity.of(50.0, UnitOfMeasure.PIECE),
            unitPrice = Money.idr(85_000L * 100),
            discount = Ratio.of(5, 100),
            sortOrder = 2
        )

        val invoice = Invoice(
            id = InvoiceId("inv-test-001"),
            tenantId = tenantId,
            number = InvoiceNumber("INV/2026/03/0001"),
            kind = InvoiceKind.DOWN_PAYMENT,
            status = InvoiceStatus.ISSUED,
            billTo = BillToParty(
                name = "PT Sinar Jaya Abadi",
                contactPerson = "Ibu Dewi Lestari",
                address = "Jl. Gatot Subroto No. 45, Jakarta Selatan",
                phone = "0812-9988-7766",
                email = "procurement@sinarjaya.co.id",
                taxId = "01.888.777.6-012.000"
            ),
            issuer = IssuerProfile(
                companyName = "PT WeMade Garment Indonesia",
                address = "Kawasan Industri Rancaekek Kav. 12, Bandung",
                taxId = "02.345.678.9-429.000",
                phone = "+62 812-3456-7890",
                email = "finance@wemade.co.id",
                bankName = "Bank Central Asia (BCA)",
                bankAccountNumber = "8420-123-999",
                bankAccountHolder = "PT WEMADE GARMENT INDONESIA"
            ),
            lines = listOf(line1, line2),
            taxRatio = Ratio.of(11, 100),
            globalDiscount = Ratio.ZERO,
            currency = CurrencyCode.IDR,
            issueDate = LocalDate(2026, 3, 15),
            dueDate = LocalDate(2026, 3, 29),
            templateId = template.id,
            renderedTemplate = template,
            sourceKind = InvoiceSourceKind.MANUAL,
            notes = "Uang Muka 50% untuk produksi seragam kantor 150 pcs.",
            terms = "Pembayaran via transfer bank ke rekening tertera di atas.",
            createdBy = "sales-head",
            createdAt = now,
            updatedAt = now
        )

        val renderer = InvoicePdfRenderer()
        val pdfBytes = renderer.render(invoice, template)

        assertTrue(pdfBytes.isNotEmpty(), "PDF bytes tidak boleh kosong")
        val header = String(pdfBytes.sliceArray(0..4))
        assertEquals("%PDF-", header, "File output harus diawali dengan header PDF valid")
    }

    /**
     * Teks panjang wajib tercetak sebagai beberapa baris.
     *
     * Sebelum perbaikan, renderer mengganti `\n` menjadi spasi dan menggambar semuanya dalam satu baris,
     * sehingga catatan panjang meluber keluar tepi kanan kertas dan hilang saat dicetak. Tes ini membaca
     * ulang teks dari PDF hasil render supaya pembuktiannya bukan sekadar "byte-nya tidak kosong".
     */
    @Test
    fun render_long_multiline_text_should_print_every_paragraph_line() {
        val tenantId = TenantId("ten-demo-001")
        val now = Clock.System.now()
        val base = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

        val paragraph = "Syarat pembayaran:\n1. Pelunasan paling lambat 14 hari.\n2. Denda 1% per bulan."
        val template = base.copy(
            elements = base.elements + TemplateElement.StaticText(
                elementId = "terms-paragraph",
                rect = TemplateRect(Mm10(150), Mm10(2450), Mm10(1800), Mm10(60)),
                text = paragraph
            )
        )

        val invoice = Invoice(
            id = InvoiceId("inv-test-002"),
            tenantId = tenantId,
            number = InvoiceNumber("INV/2026/03/0002"),
            kind = InvoiceKind.FULL,
            status = InvoiceStatus.ISSUED,
            billTo = BillToParty(name = "PT Sinar Jaya Abadi"),
            issuer = IssuerProfile(companyName = "PT WeMade Garment Indonesia"),
            lines = emptyList(),
            currency = CurrencyCode.IDR,
            issueDate = LocalDate(2026, 3, 15),
            templateId = template.id,
            renderedTemplate = template,
            createdBy = "tester",
            createdAt = now,
            updatedAt = now
        )

        val pdfBytes = InvoicePdfRenderer().render(invoice, template)

        val extracted = PDFTextStripper().getText(Loader.loadPDF(pdfBytes))
        assertTrue(
            extracted.contains("Syarat pembayaran"),
            "Baris pertama catatan harus muncul di PDF hasil render"
        )
        assertTrue(
            extracted.contains("Denda 1% per bulan"),
            "Baris terakhir catatan harus ikut tercetak, bukan terpotong"
        )
    }
}
