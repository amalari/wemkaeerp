package com.eventverse.app.domain.builder.print

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tata letak PDF tagihan (FR-M2-5b).
 *
 * Yang diuji adalah hal-hal yang **tidak tertangkap mata** pada dokumen sembilan baris: overflow pada
 * tenant dengan puluhan modul, kolom angka yang tidak lurus, dan halaman lanjutan yang kehilangan
 * nomor halaman atau watermarknya. Semuanya dinyatakan sebagai bidang, bukan sebagai dugaan bahwa
 * renderer pasti beres.
 */
class SubscriptionInvoiceSheetLayoutTest {

    private val paper = SubscriptionInvoiceSheetLayout.PAPER
    private val bottomLimit = paper.heightMm10 - SubscriptionInvoiceSheetLayout.MARGIN_BOTTOM_MM10
    private val rightLimit = paper.widthMm10 - SubscriptionInvoiceSheetLayout.MARGIN_X_MM10

    @Test
    fun `sembilan modul cukup satu halaman dan memuat watermark`() {
        val sheet = SubscriptionInvoiceSheetLayout.layout(document(lineCount = 9))

        assertEquals(1, sheet.pages.size, "tagihan sembilan modul tidak boleh terpotong")
        assertEquals(SubscriptionInvoicePdfDocument.WATERMARK_UNPAID, sheet.pages.single().watermark.text)
        assertTrue(sheet.pages.single().body.isNotEmpty())
    }

    @Test
    fun `semua baris berada di dalam margin dan tidak saling menimpa`() {
        pages(lineCount = 60).forEach { page ->
            (page.body + page.footer).forEach { line ->
                assertTrue(
                    line.rect.x.value >= SubscriptionInvoiceSheetLayout.MARGIN_X_MM10,
                    "Baris '${line.text}' keluar margin kiri"
                )
                assertTrue(line.rect.right.value <= rightLimit, "Baris '${line.text}' keluar margin kanan")
                assertTrue(
                    line.rect.y.value >= SubscriptionInvoiceSheetLayout.MARGIN_TOP_MM10,
                    "Baris '${line.text}' naik ke atas margin"
                )
                assertTrue(line.rect.bottom.value <= bottomLimit, "Baris '${line.text}' tumpah ke area footer")
            }
            // Baris teks tidak boleh saling menimpa. Dua kolom dalam **satu baris** sengaja berbagi pita
            // `y` (itu definisi kolom), jadi yang diperiksa adalah pita-pita yang berbeda: puncak baris
            // berikutnya tidak boleh berada di atas dasar pita sebelumnya.
            page.body.filterNot { it.role.isRule }
                .zipWithNext()
                .forEach { (previous, next) ->
                    if (next.rect.y.value > previous.rect.y.value) {
                        assertTrue(
                            previous.rect.bottom.value <= next.rect.y.value,
                            "'${previous.text}' menimpa '${next.text}'"
                        )
                    } else {
                        assertEquals(
                            previous.rect.y.value,
                            next.rect.y.value,
                            "hanya pasangan kolom yang boleh berbagi pita: '${previous.text}' vs '${next.text}'"
                        )
                    }
                }
        }
    }

    @Test
    fun `kolom angka berakhir tepat di margin kanan`() {
        val amounts = pages(lineCount = 12).flatMap { it.body }
            .filter { it.role.align == SubscriptionInvoiceTextAlign.RIGHT && it.text.isNotBlank() }

        assertTrue(amounts.size >= 12, "kolom angka hilang dari lembar: ${amounts.size}")
        amounts.forEach { line ->
            assertTrue(line.rect.right.value <= rightLimit, "'${line.text}' melewati margin kanan")
            assertTrue(
                line.rect.right.value >= rightLimit - 2,
                "'${line.text}' tidak lurus ke margin kanan (jarak ${rightLimit - line.rect.right.value})"
            )
        }
    }

    @Test
    fun `kolom kiri tidak menabrak kolom angka saat nama modul panjang`() {
        val sheet = SubscriptionInvoiceSheetLayout.layout(document(lineCount = 3, longNames = true))
        val labelLines = sheet.pages.single().body.filter { it.role == SubscriptionInvoiceLineRole.LABEL }
        val amountLines = sheet.pages.single().body.filter { it.role == SubscriptionInvoiceLineRole.AMOUNT }

        assertTrue(labelLines.size >= 3)
        assertEquals(amountLines.size, labelLines.size, "setiap label punya pasangan angkanya")
        labelLines.zip(amountLines).forEach { (label, amount) ->
            assertTrue(
                label.rect.right.value <= amount.rect.x.value,
                "'${label.text}' bersinggungan dengan '${amount.text}'"
            )
        }
    }

    @Test
    fun `halaman lanjutan diberi judul lanjutan dan halaman pertama tidak`() {
        val pages = pages(lineCount = 60)

        assertTrue(pages.size > 1, "dokumen 60 modul seharusnya lebih dari satu halaman")
        assertTrue(!pages.first().body.first().text.contains(SubscriptionInvoiceSheetLayout.CONTINUATION_SUFFIX))
        pages.drop(1).forEach { page ->
            assertTrue(
                page.body.first().text.contains(SubscriptionInvoiceSheetLayout.CONTINUATION_SUFFIX),
                "Halaman ${page.index} kehilangan judul lanjutan"
            )
        }
    }

    @Test
    fun `setiap halaman punya watermark nomor halaman dan waktu cetak`() {
        val pages = pages(lineCount = 60)

        pages.forEachIndexed { index, page ->
            assertEquals(SubscriptionInvoicePdfDocument.WATERMARK_UNPAID, page.watermark.text)
            val head = page.footer.first().text
            assertTrue(head.startsWith("Halaman ${index + 1} dari ${pages.size}"), "footer: $head")
            assertTrue(head.contains("INV-2026-09-001"), "nomor invoice hilang dari footer: $head")
            assertTrue(head.contains("Dicetak 30 Sep 2026 14:05 WIB"), "waktu cetak hilang dari footer: $head")
        }
    }

    @Test
    fun `baris garis selebar badan dokumen dan bukan teks`() {
        val rules = pages(lineCount = 9).flatMap { it.body }.filter { it.role.isRule }

        assertEquals(3, rules.size, "tiga pemisah: kepala dokumen, sebelum kolom, dan sebelum total")
        rules.forEach { rule ->
            assertEquals("", rule.text, "baris garis tidak mencetak teks")
            assertEquals(SubscriptionInvoiceSheetLayout.MARGIN_X_MM10, rule.rect.x.value)
            assertEquals(rightLimit, rule.rect.right.value)
        }
    }

    private fun pages(lineCount: Int): List<SubscriptionInvoicePage> =
        SubscriptionInvoiceSheetLayout.layout(document(lineCount)).pages

    private fun document(lineCount: Int, longNames: Boolean = false) = SubscriptionInvoicePdfDocument(
        title = SubscriptionInvoicePdfDocument.DEFAULT_TITLE,
        number = "INV-2026-09-001",
        period = "2026-09",
        tenantName = "Bordir Uji",
        tenantId = "ten-uji",
        statusLabel = "Menunggu pembayaran",
        watermark = SubscriptionInvoicePdfDocument.WATERMARK_UNPAID,
        issuedAtLabel = "30 Sep 2026 14:05 WIB",
        paidAtLabel = null,
        paidNote = null,
        generatedAtLabel = "30 Sep 2026 14:05 WIB",
        footerNote = SubscriptionInvoicePdfDocument.DEFAULT_FOOTER,
        notes = listOf(SubscriptionInvoicePdfDocument.PAYMENT_NOTE),
        lines = (1..lineCount).map { index ->
            SubscriptionInvoicePdfLine(
                moduleId = "modul_$index",
                displayName = if (longNames) {
                    "Pengawasan Mutu & Inspeksi Akhir Lini $index dengan Nama Sangat Panjang"
                } else {
                    "Modul $index"
                },
                kindLabel = "Langganan",
                priceLabel = "Rp 150.000"
            )
        },
        totalLabel = "Rp 1.500.000",
        totalIdr = 1_500_000L
    )
}
