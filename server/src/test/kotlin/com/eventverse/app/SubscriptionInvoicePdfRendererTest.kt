package com.eventverse.app

import com.eventverse.app.domain.builder.print.SubscriptionInvoicePdfDocument
import com.eventverse.app.domain.builder.print.SubscriptionInvoicePdfLine
import com.eventverse.app.domain.builder.print.SubscriptionInvoiceSheetLayout
import com.eventverse.app.infrastructure.pdf.SubscriptionInvoicePdfRenderer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renderer PDF tagihan (FR-M2-5b).
 *
 * Yang diperiksa adalah hal yang tidak terlihat dari kode renderer: bahwa **isi yang diputuskan domain
 * benar-benar sampai ke kertas** — nomor invoice, nama tenant, baris harga, total — dan bahwa
 * statusnya tercetak sebagai watermark di **setiap** halaman. Tagihan yang halamannya kehilangan
 * watermark justru itulah yang paling sering difoto lalu dirujuk sebagai "sudah dibayar".
 */
class SubscriptionInvoicePdfRendererTest {

    private val renderer = SubscriptionInvoicePdfRenderer()

    @Test
    fun `pdf memuat nomor tenant baris harga total dan watermark`() {
        val bytes = renderer.render(SubscriptionInvoiceSheetLayout.layout(document(lineCount = 3)))

        assertTrue(bytes.size > 1000, "PDF terlalu kecil untuk memuat satu halaman teks")
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4), Charsets.ISO_8859_1))

        Loader.loadPDF(bytes).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("INV-2026-09-001"), "Nomor invoice hilang dari PDF")
            assertTrue(text.contains("Bordir Uji"), "Nama tenant hilang dari PDF")
            assertTrue(text.contains("Rp 3.400.000"), "Total hilang dari PDF")
            assertTrue(text.contains("Modul 1 (modul_1)"), "Baris modul hilang dari PDF")
            assertTrue(text.contains("Total per bulan"), "Label total hilang dari PDF")
            assertTrue(text.contains("Halaman 1 dari 1"), "Footer nomor halaman hilang dari PDF")
            // Teks miring dipotong baris oleh PDFTextStripper di tengah kata, jadi perbandingannya
            // dilakukan tanpa spasi — sama seperti uji renderer blueprint.
            assertTrue(
                normalizedForMatch(text).contains("BELUMDIBAYAR"),
                "Watermark status hilang dari PDF"
            )
        }
    }

    @Test
    fun `invoice lunas mencetak watermark lunas dan waktu bayarnya`() {
        val document = document(
            lineCount = 2,
            watermark = SubscriptionInvoicePdfDocument.WATERMARK_PAID,
            statusLabel = "Lunas",
            paidAtLabel = "30 Sep 2026 16:20 WIB",
            paidNote = "TRF-2026-09-1"
        )
        val bytes = renderer.render(SubscriptionInvoiceSheetLayout.layout(document))

        Loader.loadPDF(bytes).use { pdf ->
            val text = PDFTextStripper().getText(pdf)
            assertTrue(normalizedForMatch(text).contains("LUNAS"), "Watermark LUNAS hilang")
            assertTrue(text.contains("Dibayar pada: 30 Sep 2026 16:20 WIB"), "Waktu bayar hilang dari PDF")
            assertTrue(text.contains("Catatan pembayaran: TRF-2026-09-1"), "Catatan pembayaran hilang")
            // Yang berbahaya: invoice lunas yang masih terlihat "belum dibayar" akan ditagih dua kali.
            assertTrue(
                !normalizedForMatch(text).contains("BELUMDIBAYAR"),
                "Invoice lunas tidak boleh masih membawa watermark belum dibayar"
            )
        }
    }

    @Test
    fun `karakter tanpa glyph tidak menggagalkan seluruh dokumen`() {
        // Nama modul bisa datang dari katalog tenant: satu karakter yang tidak ada di font tidak boleh
        // membuat tenant menerima PDF kosong (dulu `showText` melempar untuk panah U+2192).
        val exotic = document(lineCount = 1).copy(
            lines = listOf(
                SubscriptionInvoicePdfLine(
                    moduleId = "modul_x",
                    displayName = "Pengawasan → 中文",
                    kindLabel = "Langganan",
                    priceLabel = "Rp 150.000"
                )
            )
        )
        val bytes = renderer.render(SubscriptionInvoiceSheetLayout.layout(exotic))

        Loader.loadPDF(bytes).use { pdf ->
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Pengawasan -> ?? "), "Panah dipetakan ke ASCII, sisanya jadi '?': $text")
            assertTrue(text.contains("Rp 150.000"), "Harga tetap tercetak")
        }
    }

    @Test
    fun `dokumen puluhan modul berhalaman banyak dan setiap halaman berwatermark`() {
        val sheet = SubscriptionInvoiceSheetLayout.layout(document(lineCount = 60))
        assertTrue(sheet.pages.size > 1, "Dokumen 60 modul seharusnya lebih dari satu halaman")

        val bytes = renderer.render(sheet)
        Loader.loadPDF(bytes).use { pdf ->
            assertEquals(sheet.pages.size, pdf.numberOfPages)
            val text = normalizedForMatch(PDFTextStripper().getText(pdf))
            assertEquals(
                sheet.pages.size,
                Regex("BELUMDIBAYAR").findAll(text).count(),
                "Watermark harus ada di tiap halaman"
            )
            assertTrue(text.contains("(lanjutan)"), "Halaman lanjutan kehilangan judul lanjutannya")
        }
    }

    /**
     * Teks ekstraksi disiapkan untuk perbandingan: spasi dibuang (PDFTextStripper memotong teks miring
     * di tengah kata) dan semua jenis dash diseragamkan (watermark memakai em dash `—`).
     */
    private fun normalizedForMatch(text: String): String =
        text.filterNot { it.isWhitespace() }.replace('—', '-').replace('–', '-')

    private fun document(
        lineCount: Int,
        watermark: String = SubscriptionInvoicePdfDocument.WATERMARK_UNPAID,
        statusLabel: String = "Menunggu pembayaran",
        paidAtLabel: String? = null,
        paidNote: String? = null
    ) = SubscriptionInvoicePdfDocument(
        title = SubscriptionInvoicePdfDocument.DEFAULT_TITLE,
        number = "INV-2026-09-001",
        period = "2026-09",
        tenantName = "Bordir Uji",
        tenantId = "ten-bordir-uji",
        statusLabel = statusLabel,
        watermark = watermark,
        issuedAtLabel = "30 Sep 2026 14:05 WIB",
        paidAtLabel = paidAtLabel,
        paidNote = paidNote,
        generatedAtLabel = "30 Sep 2026 14:05 WIB",
        footerNote = SubscriptionInvoicePdfDocument.DEFAULT_FOOTER,
        notes = listOf(SubscriptionInvoicePdfDocument.PAYMENT_NOTE),
        lines = (1..lineCount).map { index ->
            SubscriptionInvoicePdfLine(
                moduleId = "modul_$index",
                displayName = "Modul $index",
                kindLabel = "Langganan",
                priceLabel = "Rp 340.000"
            )
        },
        totalLabel = "Rp 3.400.000",
        totalIdr = 3_400_000L
    )
}
