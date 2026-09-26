package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Penyelesai geometri dokumen.
 *
 * Aturan yang diuji di sini dulu hanya hidup di renderer PDF, sehingga kanvas menampilkan posisi yang
 * berbeda dari hasil cetak. Karena sekarang kanvas dan PDF memakai penyelesai yang sama, setiap
 * perubahan aturan tereksekusi di kedua tempat sekaligus — dan salahnya harus tertangkap di sini.
 */
class InvoiceDocumentLayoutTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    private fun templateWithTable() = InvoiceTemplate(
        id = InvoiceTemplateId("tpl-layout-test"),
        tenantId = tenantId,
        name = "Template Uji Tata Letak",
        elements = listOf(
            TemplateElement.StaticText(
                elementId = "note",
                rect = TemplateRect(Mm10(150), Mm10(150), Mm10(1000), Mm10(60)),
                text = "Catatan: pelunasan paling lambat 14 hari setelah faktur diterbitkan."
            ),
            TemplateElement.ItemTable(
                elementId = "table",
                rect = TemplateRect(Mm10(150), Mm10(800), Mm10(1800), Mm10(360)),
                columns = listOf(
                    TableColumn(BindingToken("line.description"), "Deskripsi", Ratio.of(1, 1))
                ),
                rowHeight = Mm10(80),
                showHeader = true
            ),
            TemplateElement.StaticText(
                elementId = "sign-label",
                rect = TemplateRect(Mm10(1500), Mm10(1300), Mm10(500), Mm10(60)),
                anchorBelowTable = true,
                text = "Hormat Kami,"
            )
        ),
        createdAt = now,
        updatedAt = now
    )

    private fun invoiceWithLines(lineCount: Int) = Invoice(
        id = InvoiceId("inv-layout-test"),
        tenantId = tenantId,
        number = InvoiceNumber("INV/2026/03/0001"),
        kind = InvoiceKind.FULL,
        billTo = BillToParty(name = "PT Mitra Usaha Mandiri"),
        issuer = IssuerProfile(companyName = "PT WeMade Garment Indonesia"),
        lines = (1..lineCount).map { index ->
            InvoiceLine(
                id = InvoiceLineId("line-$index"),
                description = "Baris pekerjaan $index",
                quantity = Quantity(1_000_000L, UnitOfMeasure.PIECE),
                unitPrice = Money.idr(100_000L),
                discount = Ratio.ZERO,
                sortOrder = index
            )
        },
        currency = CurrencyCode.IDR,
        issueDate = LocalDate(2026, 3, 15),
        templateId = InvoiceTemplateId("tpl-layout-test"),
        createdBy = "tester",
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `text height is derived from its content, not from the stored rect`() {
        val template = templateWithTable()
        val note = InvoiceDocumentLayout.solve(template, invoiceWithLines(1))
            .first { it.element.elementId == "note" }

        assertTrue(
            note.textLines.size > 1,
            "Isi yang lebih panjang dari kotaknya harus terpecah; tinggi 6 mm tidak cukup untuk satu baris 10 pt"
        )
        assertEquals(
            note.textLines.size * InvoiceTextLayout.lineHeightMm10(TextStyleSpec(fontSizePt = 10)),
            note.rect.height.value,
            "Tinggi kotak teks wajib sama dengan jumlah baris dikali tinggi baris"
        )
    }

    @Test
    fun `table taller than needed does not shrink and does not shift anchored elements`() {
        val template = templateWithTable()
        val delta = InvoiceDocumentLayout.tableDeltaMm10(template, invoiceWithLines(2))

        assertEquals(0, delta, "Dua baris masih muat di tinggi tabel yang dirancang")

        val sign = InvoiceDocumentLayout.solve(template, invoiceWithLines(2))
            .first { it.element.elementId == "sign-label" }
        assertEquals(Mm10(1300), sign.rect.y)
    }

    @Test
    fun `anchored element follows the table when the invoice has more rows`() {
        val template = templateWithTable()
        val invoice = invoiceWithLines(10)

        val required = InvoiceDocumentLayout.requiredTableHeightMm10(
            template.itemTable!!,
            invoice.lines.size
        )
        val expectedDelta = required - template.itemTable!!.rect.height.value
        assertTrue(expectedDelta > 0)

        val laid = InvoiceDocumentLayout.solve(template, invoice)
        val table = laid.first { it.element.elementId == "table" }
        val sign = laid.first { it.element.elementId == "sign-label" }

        assertEquals(required, table.rect.height.value, "Tinggi tabel harus mengikuti jumlah baris faktur")
        assertEquals(
            Mm10(1300 + expectedDelta),
            sign.rect.y,
            "Elemen ber-anchor harus turun sejauh pertambahan tinggi tabel"
        )
    }

    @Test
    fun `repeated measurement never drifts the anchored element downward`() {
        // Menuliskan posisi hasil geser kembali ke model akan membuat pergeseran menumpuk setiap kali
        // teks diubah — elemen ber-anchor akan merayap turun tanpa batas. Karena itu `measureHeights`
        // hanya boleh menulis tinggi, bukan y.
        val invoice = invoiceWithLines(10)
        val once = InvoiceDocumentLayout.measureHeights(templateWithTable(), invoice)
        val twice = InvoiceDocumentLayout.measureHeights(once, invoice)

        assertEquals(
            once.elements.first { it.elementId == "sign-label" }.rect.y,
            twice.elements.first { it.elementId == "sign-label" }.rect.y
        )
        assertEquals(once.elements, twice.elements)
    }

    @Test
    fun `measureHeights writes derived text height back into the template`() {
        val measured = InvoiceDocumentLayout.measureHeights(templateWithTable(), invoiceWithLines(2))
        val note = measured.elements.first { it.elementId == "note" }

        assertTrue(
            note.rect.height.value > 60,
            "Tinggi hasil ukur harus menggantikan tinggi yang dirancang untuk elemen teks"
        )
    }

    @Test
    fun `element pushed past the paper edge is clamped instead of disappearing`() {
        val base = templateWithTable()
        val template = base.copy(
            elements = base.elements.map { element ->
                // Elemen ber-anchor di ambang bawah kertas: pergeseran tabel akan mendorongnya keluar.
                if (element.elementId == "sign-label") {
                    element.withRect(element.rect.copy(y = Mm10(2900)))
                } else {
                    element
                }
            }
        )

        val sign = InvoiceDocumentLayout.solve(template, invoiceWithLines(20))
            .first { it.element.elementId == "sign-label" }

        assertTrue(
            sign.rect.bottom <= template.paperSize.height,
            "Elemen tidak boleh keluar dari kertas setelah pergeseran dinamis"
        )
    }

    @Test
    fun `line shape keeps its designed height because its height is the stroke`() {
        val base = templateWithTable()
        val template = base.copy(
            elements = base.elements + TemplateElement.LineShape(
                elementId = "divider",
                rect = TemplateRect(Mm10(150), Mm10(500), Mm10(1800), Mm10(20)),
                strokeMm10 = 2
            )
        )

        val divider = InvoiceDocumentLayout.solve(template, invoiceWithLines(1))
            .first { it.element.elementId == "divider" }

        assertEquals(20, divider.rect.height.value)
        assertTrue(divider.textLines.isEmpty(), "Elemen garis bukan teks, jadi tidak punya daftar baris")
    }
}
