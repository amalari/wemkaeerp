package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceTemplateId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Agregat template tata letak dokumen invoice.
 * Bersifat immutable; perubahan elemen menghasilkan salinan baru.
 */
data class InvoiceTemplate(
    val id: InvoiceTemplateId,
    val tenantId: TenantId,
    val name: String,
    val paperSize: PaperSize = PaperSize.A4,
    val marginMm10: Int = 150, // 15 mm
    val applicableKinds: Set<InvoiceKind> = setOf(InvoiceKind.SAMPLE),
    val elements: List<TemplateElement> = emptyList(),
    val isDefault: Boolean = false,
    val archivedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "Nama template invoice tidak boleh kosong." }
        require(elements.count { it is TemplateElement.ItemTable } <= 1) {
            "Template invoice maksimal hanya boleh memiliki 1 tabel item."
        }
        val ids = elements.map { it.elementId }
        require(ids.distinct().size == ids.size) {
            "ID elemen template harus unik: terdeteksi duplikasi."
        }
    }

    /**
     * Jenis tagihan tunggal yang didukung template ini.
     * Aturan bisnis: 1 template didedikasikan untuk tepat 1 jenis tagihan.
     */
    val targetKind: InvoiceKind
        get() = applicableKinds.firstOrNull() ?: InvoiceKind.SAMPLE

    fun withTargetKind(kind: InvoiceKind): InvoiceTemplate =
        copy(applicableKinds = setOf(kind))

    val itemTable: TemplateElement.ItemTable?
        get() = elements.filterIsInstance<TemplateElement.ItemTable>().firstOrNull()

    val isArchived: Boolean get() = archivedAt != null

    fun addElement(element: TemplateElement): InvoiceTemplate {
        require(elements.none { it.elementId == element.elementId }) {
            "Elemen dengan ID '${element.elementId}' sudah ada di template."
        }
        if (element is TemplateElement.ItemTable) {
            require(itemTable == null) { "Template sudah memiliki tabel item." }
        }
        return copy(elements = elements + element)
    }

    fun updateElement(element: TemplateElement): InvoiceTemplate =
        copy(elements = elements.map { if (it.elementId == element.elementId) element else it })

    fun removeElement(elementId: String): InvoiceTemplate =
        copy(elements = elements.filterNot { it.elementId == elementId })

    fun moveElement(elementId: String, newRect: TemplateRect): InvoiceTemplate =
        copy(elements = elements.map { if (it.elementId == elementId) it.withRect(newRect) else it })

    fun bringToFront(elementId: String): InvoiceTemplate {
        val maxZ = elements.maxOfOrNull { it.zOrder } ?: 0.0
        return copy(elements = elements.map { el ->
            if (el.elementId == elementId) {
                when (el) {
                    is TemplateElement.StaticText -> el.copy(zOrder = maxZ + 1.0)
                    is TemplateElement.BoundField -> el.copy(zOrder = maxZ + 1.0)
                    is TemplateElement.ImageBox -> el.copy(zOrder = maxZ + 1.0)
                    is TemplateElement.RectShape -> el.copy(zOrder = maxZ + 1.0)
                    is TemplateElement.LineShape -> el.copy(zOrder = maxZ + 1.0)
                    is TemplateElement.ItemTable -> el.copy(zOrder = maxZ + 1.0)
                }
            } else el
        })
    }
}

/**
 * Pabrik template bawaan siap pakai (seed).
 * Mencegah kanvas kosong saat tenant baru pertama kali membuka modul invoice.
 */
object InvoiceTemplateFactory {

    fun standardIndonesianInvoice(tenantId: TenantId, now: Instant): InvoiceTemplate {
        val elements = listOf(
            // Header Perusahaan Penerbit (Kiri Atas)
            TemplateElement.BoundField(
                elementId = "issuer-name",
                rect = TemplateRect(Mm10(150), Mm10(150), Mm10(1000), Mm10(100)),
                zOrder = 1.0,
                binding = BindingToken("issuer.companyName"),
                style = TextStyleSpec(fontSizePt = 14, isBold = true)
            ),
            TemplateElement.BoundField(
                elementId = "issuer-address",
                rect = TemplateRect(Mm10(150), Mm10(260), Mm10(1000), Mm10(120)),
                zOrder = 2.0,
                binding = BindingToken("issuer.address"),
                style = TextStyleSpec(fontSizePt = 9, colorHex = 0xFF475569L)
            ),
            TemplateElement.BoundField(
                elementId = "issuer-contact",
                rect = TemplateRect(Mm10(150), Mm10(390), Mm10(1000), Mm10(80)),
                zOrder = 3.0,
                binding = BindingToken("issuer.phone"),
                prefix = "Telp: ",
                style = TextStyleSpec(fontSizePt = 9, colorHex = 0xFF475569L)
            ),

            // Judul Faktur & Nomor (Kanan Atas)
            TemplateElement.BoundField(
                elementId = "invoice-title",
                rect = TemplateRect(Mm10(1200), Mm10(150), Mm10(750), Mm10(100)),
                zOrder = 4.0,
                binding = BindingToken("invoice.kind"),
                style = TextStyleSpec(fontSizePt = 16, isBold = true, align = TextAlign.RIGHT, colorHex = 0xFF2563EBL)
            ),
            TemplateElement.BoundField(
                elementId = "invoice-number",
                rect = TemplateRect(Mm10(1200), Mm10(260), Mm10(750), Mm10(80)),
                zOrder = 5.0,
                binding = BindingToken("invoice.number"),
                prefix = "No: ",
                style = TextStyleSpec(fontSizePt = 10, isBold = true, align = TextAlign.RIGHT)
            ),
            TemplateElement.BoundField(
                elementId = "invoice-date",
                rect = TemplateRect(Mm10(1200), Mm10(350), Mm10(750), Mm10(70)),
                zOrder = 6.0,
                binding = BindingToken("invoice.issueDate"),
                prefix = "Tgl Terbit: ",
                style = TextStyleSpec(fontSizePt = 9, align = TextAlign.RIGHT)
            ),
            TemplateElement.BoundField(
                elementId = "invoice-due-date",
                rect = TemplateRect(Mm10(1200), Mm10(420), Mm10(750), Mm10(70)),
                zOrder = 7.0,
                binding = BindingToken("invoice.dueDate"),
                prefix = "Jatuh Tempo: ",
                style = TextStyleSpec(fontSizePt = 9, align = TextAlign.RIGHT, colorHex = 0xFFDC2626L)
            ),

            // Garis Pemisah Header
            TemplateElement.LineShape(
                elementId = "divider-header",
                rect = TemplateRect(Mm10(150), Mm10(520), Mm10(1800), Mm10(10)),
                zOrder = 8.0,
                strokeHex = 0xFFCBD5E1L,
                strokeMm10 = 2
            ),

            // Data Pembeli / Klien (Bill To)
            TemplateElement.StaticText(
                elementId = "bill-to-label",
                rect = TemplateRect(Mm10(150), Mm10(560), Mm10(600), Mm10(60)),
                zOrder = 9.0,
                text = "DITAGIHKAN KEPADA:",
                style = TextStyleSpec(fontSizePt = 9, isBold = true, colorHex = 0xFF64748BL)
            ),
            TemplateElement.BoundField(
                elementId = "bill-to-name",
                rect = TemplateRect(Mm10(150), Mm10(630), Mm10(800), Mm10(80)),
                zOrder = 10.0,
                binding = BindingToken("billTo.name"),
                style = TextStyleSpec(fontSizePt = 11, isBold = true)
            ),
            TemplateElement.BoundField(
                elementId = "bill-to-address",
                rect = TemplateRect(Mm10(150), Mm10(720), Mm10(800), Mm10(120)),
                zOrder = 11.0,
                binding = BindingToken("billTo.address"),
                style = TextStyleSpec(fontSizePt = 9, colorHex = 0xFF475569L)
            ),
            TemplateElement.BoundField(
                elementId = "bill-to-phone",
                rect = TemplateRect(Mm10(150), Mm10(850), Mm10(800), Mm10(60)),
                zOrder = 12.0,
                binding = BindingToken("billTo.phone"),
                prefix = "Kontak: ",
                style = TextStyleSpec(fontSizePt = 9, colorHex = 0xFF475569L)
            ),

            // Tabel Item Dinamis
            TemplateElement.ItemTable(
                elementId = "invoice-table",
                rect = TemplateRect(Mm10(150), Mm10(950), Mm10(1800), Mm10(600)),
                zOrder = 13.0,
                columns = listOf(
                    TableColumn(BindingToken("line.no"), "#", Ratio.of(1, 12), TextAlign.CENTER),
                    TableColumn(BindingToken("line.description"), "Deskripsi Barang / Jasa", Ratio.of(5, 12), TextAlign.LEFT),
                    TableColumn(BindingToken("line.quantity"), "Qty", Ratio.of(2, 12), TextAlign.RIGHT),
                    TableColumn(BindingToken("line.unitPrice"), "Harga Satuan", Ratio.of(2, 12), TextAlign.RIGHT),
                    TableColumn(BindingToken("line.amount"), "Subtotal", Ratio.of(2, 12), TextAlign.RIGHT)
                ),
                rowHeight = Mm10(80),
                zebraFillHex = 0xFFF8FAFC
            ),

            // Blok Total & Terbilang (Ikat di Bawah Tabel)
            TemplateElement.BoundField(
                elementId = "total-in-words",
                rect = TemplateRect(Mm10(150), Mm10(1600), Mm10(1050), Mm10(150)),
                zOrder = 14.0,
                anchorBelowTable = true,
                binding = BindingToken("invoice.totalInWords"),
                prefix = "Terbilang: # ",
                suffix = " #",
                style = TextStyleSpec(fontSizePt = 9, isItalic = true, colorHex = 0xFF334155L)
            ),
            TemplateElement.BoundField(
                elementId = "subtotal-label",
                rect = TemplateRect(Mm10(1250), Mm10(1600), Mm10(700), Mm10(60)),
                zOrder = 15.0,
                anchorBelowTable = true,
                binding = BindingToken("invoice.subtotal"),
                prefix = "Subtotal: ",
                style = TextStyleSpec(fontSizePt = 10, align = TextAlign.RIGHT)
            ),
            TemplateElement.BoundField(
                elementId = "tax-amount-label",
                rect = TemplateRect(Mm10(1250), Mm10(1670), Mm10(700), Mm10(60)),
                zOrder = 16.0,
                anchorBelowTable = true,
                binding = BindingToken("invoice.taxAmount"),
                prefix = "PPN (11%): ",
                style = TextStyleSpec(fontSizePt = 10, align = TextAlign.RIGHT)
            ),
            TemplateElement.BoundField(
                elementId = "grand-total-label",
                rect = TemplateRect(Mm10(1250), Mm10(1750), Mm10(700), Mm10(80)),
                zOrder = 17.0,
                anchorBelowTable = true,
                binding = BindingToken("invoice.total"),
                prefix = "TOTAL: ",
                style = TextStyleSpec(fontSizePt = 13, isBold = true, align = TextAlign.RIGHT, colorHex = 0xFF2563EBL)
            ),

            // Informasi Bank Pembayaran (Ikat di Bawah Tabel)
            TemplateElement.StaticText(
                elementId = "bank-payment-header",
                rect = TemplateRect(Mm10(150), Mm10(1800), Mm10(800), Mm10(60)),
                zOrder = 18.0,
                anchorBelowTable = true,
                text = "PEMBAYARAN DITRANSFER KE:",
                style = TextStyleSpec(fontSizePt = 9, isBold = true, colorHex = 0xFF64748BL)
            ),
            TemplateElement.BoundField(
                elementId = "bank-details",
                rect = TemplateRect(Mm10(150), Mm10(1870), Mm10(800), Mm10(100)),
                zOrder = 19.0,
                anchorBelowTable = true,
                binding = BindingToken("issuer.bankName"),
                suffix = " A.N. Rekening Penerbit",
                style = TextStyleSpec(fontSizePt = 9, isBold = true)
            ),
            TemplateElement.BoundField(
                elementId = "bank-acc-no",
                rect = TemplateRect(Mm10(150), Mm10(1980), Mm10(800), Mm10(70)),
                zOrder = 20.0,
                anchorBelowTable = true,
                binding = BindingToken("issuer.bankAccountNumber"),
                prefix = "No. Rekening: ",
                style = TextStyleSpec(fontSizePt = 9)
            ),

            // Kolom Tanda Tangan (Ikat di Bawah Tabel)
            TemplateElement.StaticText(
                elementId = "sign-label",
                rect = TemplateRect(Mm10(1450), Mm10(1900), Mm10(500), Mm10(60)),
                zOrder = 21.0,
                anchorBelowTable = true,
                text = "Hormat Kami,",
                style = TextStyleSpec(fontSizePt = 9, align = TextAlign.CENTER)
            ),
            TemplateElement.LineShape(
                elementId = "sign-line",
                rect = TemplateRect(Mm10(1450), Mm10(2150), Mm10(500), Mm10(10)),
                zOrder = 22.0,
                anchorBelowTable = true,
                strokeHex = 0xFF1E293BL,
                strokeMm10 = 2
            ),
            TemplateElement.StaticText(
                elementId = "sign-title",
                rect = TemplateRect(Mm10(1450), Mm10(2170), Mm10(500), Mm10(60)),
                zOrder = 23.0,
                anchorBelowTable = true,
                text = "( Bagian Keuangan )",
                style = TextStyleSpec(fontSizePt = 9, align = TextAlign.CENTER, colorHex = 0xFF64748BL)
            )
        )

        return InvoiceTemplate(
            id = InvoiceTemplateId("tpl-std-id-001"),
            tenantId = tenantId,
            name = "Template Faktur Standar Indonesia",
            paperSize = PaperSize.A4,
            marginMm10 = 150,
            applicableKinds = setOf(InvoiceKind.SAMPLE),
            elements = elements,
            isDefault = true,
            archivedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
