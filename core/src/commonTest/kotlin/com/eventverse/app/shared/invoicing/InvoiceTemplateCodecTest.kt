package com.eventverse.app.shared.invoicing

import com.eventverse.app.domain.invoicing.template.InvoiceTemplateFactory
import com.eventverse.app.domain.invoicing.template.Mm10
import com.eventverse.app.domain.invoicing.template.TemplateElement
import com.eventverse.app.domain.invoicing.template.TemplateRect
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvoiceTemplateCodecTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-09-14T12:00:00Z")

    @Test
    fun standardSeedTemplate_roundTrip_shouldBeLossless() {
        val original = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)

        val encoded = InvoiceTemplateCodec.encode(original)
        val decoded = InvoiceTemplateCodec.decode(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.tenantId, decoded.tenantId)
        assertEquals(original.name, decoded.name)
        assertEquals(original.paperSize, decoded.paperSize)
        assertEquals(original.marginMm10, decoded.marginMm10)
        assertEquals(original.applicableKinds, decoded.applicableKinds)
        assertEquals(original.isDefault, decoded.isDefault)
        assertEquals(original.elements.size, decoded.elements.size)

        for (i in original.elements.indices) {
            val origEl = original.elements[i]
            val decEl = decoded.elements[i]
            assertEquals(origEl.elementId, decEl.elementId)
            assertEquals(origEl.rect, decEl.rect)
            assertEquals(origEl.zOrder, decEl.zOrder)
            assertEquals(origEl.anchorBelowTable, decEl.anchorBelowTable)
        }
    }

    /**
     * Bentuk JSON yang ditulis seed SQL `V32__register_invoicing_module.sql` memakai nama kunci
     * yang berbeda dari codec kanonik: `elementId` (bukan `id`), `headerText` (bukan `header`),
     * dan lebar kolom sebagai teks `"5/12"` (bukan objek `numerator`/`denominator`).
     *
     * Pembaca harus toleran terhadap keduanya. Kalau tidak, `toTemplate()` di repository server
     * membuang seluruh 23 elemen template standar dan kanvas A4 tampil **kosong** di web, tanpa
     * satu pun pesan kesalahan — persis bug yang membuat semua kolom isian faktur tidak muncul.
     */
    @Test
    fun sqlSeedShape_withElementIdKeyAndTextRatio_shouldStillDecodeElements() {
        val sqlSeedJson = """
            {
              "id": "tpl-std-id-001",
              "tenantId": "ten-demo-001",
              "name": "Template Faktur Standar Indonesia",
              "paperSize": "A4",
              "marginMm10": 150,
              "applicableKinds": ["SAMPLE", "DOWN_PAYMENT"],
              "elements": [
                {
                  "type": "bound_field",
                  "elementId": "issuer-name",
                  "rect": {"x": 150, "y": 150, "width": 1000, "height": 100},
                  "zOrder": 1.0,
                  "anchorBelowTable": false,
                  "binding": "issuer.companyName",
                  "prefix": "",
                  "suffix": "",
                  "style": {"fontSizePt": 14, "isBold": true, "isItalic": false, "align": "LEFT", "colorHex": 4280191211}
                },
                {
                  "type": "item_table",
                  "elementId": "invoice-table",
                  "rect": {"x": 150, "y": 950, "width": 1800, "height": 600},
                  "zOrder": 13.0,
                  "anchorBelowTable": false,
                  "columns": [
                    {"binding": "line.description", "headerText": "Deskripsi Barang / Jasa", "widthRatio": "5/12", "align": "LEFT"},
                    {"binding": "line.unitPrice", "headerText": "Harga Satuan", "widthRatio": "2/12", "align": "RIGHT"}
                  ],
                  "rowHeight": 80,
                  "zebraFillHex": 4294507260
                }
              ],
              "isDefault": true,
              "createdAt": "2026-09-15T06:15:10Z",
              "updatedAt": "2026-09-15T06:15:10Z"
            }
        """.trimIndent()

        val template = InvoiceTemplateCodec.decode(JsonParser.parseObject(sqlSeedJson))

        assertEquals(2, template.elements.size, "Elemen hasil seed SQL tidak boleh dibuang saat decode")

        val issuerName = template.elements[0] as TemplateElement.BoundField
        assertEquals("issuer-name", issuerName.elementId)
        assertEquals("issuer.companyName", issuerName.binding.value)
        assertTrue(issuerName.style.isBold)

        val table = template.elements[1] as TemplateElement.ItemTable
        assertEquals("invoice-table", table.elementId)
        assertEquals(2, table.columns.size)
        assertEquals("Deskripsi Barang / Jasa", table.columns[0].header)
        assertEquals(5, table.columns[0].widthRatio.numerator)
        assertEquals(12, table.columns[0].widthRatio.denominator)
    }

    /**
     * Teks multi-baris menyimpan `\n` di dalam nilai JSON. Kalau penulis JSON tidak meng-escape newline,
     * template yang disimpan akan rusak dan tidak bisa dibaca lagi — karena itu baris baru diuji lewat
     * round-trip penuh, bukan lewat pemeriksaan string.
     */
    @Test
    fun multilineStaticText_roundTrip_keepsLineBreaksAndDerivedHeight() {
        val base = InvoiceTemplateFactory.standardIndonesianInvoice(tenantId, now)
        val withParagraph = base.copy(
            elements = base.elements + TemplateElement.StaticText(
                elementId = "terms-paragraph",
                rect = TemplateRect(
                    x = Mm10(150),
                    y = Mm10(2400),
                    width = Mm10(1000),
                    height = Mm10(200)
                ),
                text = "Syarat pembayaran:\n1. Pelunasan 14 hari setelah terbit.\n2. Keterlambatan dikenakan denda 1%."
            )
        )

        val decoded = InvoiceTemplateCodec.decode(InvoiceTemplateCodec.encode(withParagraph))
        val paragraph = decoded.elements.first { it.elementId == "terms-paragraph" }

        assertEquals(withParagraph.elements.last(), paragraph)
        assertEquals(3, (paragraph as TemplateElement.StaticText).text.split('\n').size)
        assertEquals(Mm10(200), paragraph.rect.height, "Tinggi hasil ukur ikut tersimpan apa adanya")
    }
}
