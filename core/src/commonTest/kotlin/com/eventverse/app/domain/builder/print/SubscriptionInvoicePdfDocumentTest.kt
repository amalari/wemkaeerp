package com.eventverse.app.domain.builder.print

import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceId
import com.eventverse.app.domain.builder.SubscriptionInvoiceLine
import com.eventverse.app.domain.builder.SubscriptionInvoiceStatus
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Isi dokumen tagihan (FR-M2-5b).
 *
 * Yang dijaga di sini adalah hal-hal yang **tidak akan tertangkap** saat orang membuka PDF-nya sekali
 * dan melihat angkanya benar:
 * 1. **Total dicetak dari angka beku**, bukan dihitung ulang dari baris — dokumen yang menghitung
 *    ulang bisa mencetak angka berbeda dari yang tercatat di invoice, dan itu jenis kesalahan yang
 *    baru ketahuan setelah klien membayar jumlah yang salah.
 * 2. **Watermark menyebut status yang benar** — `BELUM DIBAYAR` pada invoice terbit, `LUNAS` pada yang
 *    lunas. Tertukarnya keduanya berarti klien bisa berhenti membayar tagihan yang belum dibayar.
 * 3. **Jenis baris tak dikenal tidak menyamar** menjadi "Langganan" (`tenant-variability-rules`
 *    Kontrak 4: tolak atau tampakkan, jangan fallback senyap).
 */
class SubscriptionInvoicePdfDocumentTest {

    private fun invoice(
        status: SubscriptionInvoiceStatus = SubscriptionInvoiceStatus.ISSUED,
        totalIdr: Long = 400_000L,
        paidNote: String? = null,
        kind: String = "SUBSCRIPTION"
    ) = SubscriptionInvoice(
        id = SubscriptionInvoiceId("inv-ten-uji-2026-09-1"),
        tenantId = TenantId("ten-uji"),
        number = "INV-2026-09-001",
        period = "2026-09",
        lines = listOf(
            SubscriptionInvoiceLine("quality_control", "Quality Control", MoneyIdr(150_000), kind),
            SubscriptionInvoiceLine("fulfillment", "Fulfillment", MoneyIdr(250_000), kind)
        ),
        totalIdr = MoneyIdr(totalIdr),
        status = status,
        // Invarian domain: invoice PAID wajib punya waktu bayar.
        paidAt = if (status == SubscriptionInvoiceStatus.PAID) Instant.parse("2026-09-30T09:20:00Z") else null,
        paidNote = paidNote
    )

    private fun document(
        status: SubscriptionInvoiceStatus = SubscriptionInvoiceStatus.ISSUED,
        totalIdr: Long = 400_000L,
        paidNote: String? = null,
        kind: String = "SUBSCRIPTION"
    ) = SubscriptionInvoicePdfDocument.of(
        invoice = invoice(status = status, totalIdr = totalIdr, paidNote = paidNote, kind = kind),
        tenantName = "Bordir Uji",
        generatedAtLabel = "30 Sep 2026 14:05 WIB",
        issuedAtLabel = "30 Sep 2026 14:05 WIB",
        paidAtLabel = if (status == SubscriptionInvoiceStatus.PAID) "30 Sep 2026 16:20 WIB" else null
    )

    @Test
    fun `total dicetak apa adanya walau tidak sama dengan jumlah baris`() {
        // Angka beku yang menang. Dokumen yang menjumlahkan ulang baris akan mencetak Rp 400.000 di
        // sini — berbeda dari yang tercatat sebagai tagihan tenant.
        val doc = document(totalIdr = 999_999L)

        assertEquals("Rp 999.999", doc.totalLabel)
        assertEquals(999_999L, doc.totalIdr)
        assertEquals(listOf("Rp 150.000", "Rp 250.000"), doc.lines.map { it.priceLabel })
    }

    @Test
    fun `harga baris dan total memakai format rupiah yang sama`() {
        val doc = document()

        assertTrue(doc.lines.all { it.priceLabel.startsWith("Rp ") }, "baris harga tanpa label Rp")
        assertEquals("Rp 400.000", doc.totalLabel)
    }

    @Test
    fun `status menentukan watermark dan label yang terbaca`() {
        assertEquals(SubscriptionInvoicePdfDocument.WATERMARK_UNPAID, document(SubscriptionInvoiceStatus.ISSUED).watermark)
        assertEquals("Menunggu pembayaran", document(SubscriptionInvoiceStatus.ISSUED).statusLabel)

        assertEquals(SubscriptionInvoicePdfDocument.WATERMARK_PAID, document(SubscriptionInvoiceStatus.PAID).watermark)
        assertEquals("Lunas", document(SubscriptionInvoiceStatus.PAID).statusLabel)

        assertEquals(SubscriptionInvoicePdfDocument.WATERMARK_VOID, document(SubscriptionInvoiceStatus.VOID).watermark)
        assertEquals("Dibatalkan", document(SubscriptionInvoiceStatus.VOID).statusLabel)

        assertEquals(SubscriptionInvoicePdfDocument.WATERMARK_DRAFT, document(SubscriptionInvoiceStatus.DRAFT).watermark)
    }

    @Test
    fun `jenis baris tak dikenal tidak menyamar jadi langganan`() {
        val doc = document(kind = "CUSTOM_BUILD")

        assertEquals(listOf("CUSTOM_BUILD", "CUSTOM_BUILD"), doc.lines.map { it.kindLabel })
        assertEquals("Kustomisasi", SubscriptionInvoicePdfDocument.kindLabelFor("CUSTOMIZATION"))
        assertEquals("Langganan", SubscriptionInvoicePdfDocument.kindLabelFor("SUBSCRIPTION"))
    }

    @Test
    fun `catatan pembayaran kosong tidak ikut tercetak`() {
        assertNull(document(paidNote = "   ").paidNote, "catatan berisi spasi bukan catatan")
        assertEquals("TRF-2026-09-1", document(paidNote = "TRF-2026-09-1").paidNote)
    }

    @Test
    fun `identitas tagihan lengkap supaya bisa ditagih tanpa membuka aplikasi`() {
        val doc = document()

        assertEquals("INV-2026-09-001", doc.number)
        assertEquals("2026-09", doc.period)
        assertEquals("Bordir Uji", doc.tenantName)
        assertEquals("ten-uji", doc.tenantId)
        assertEquals(2, doc.lineCount)
        assertEquals(listOf("quality_control", "fulfillment"), doc.lines.map { it.moduleId })
        assertTrue(
            doc.notes.any { it.contains("manual", ignoreCase = true) },
            "dokumen wajib menyebut pembayaran dicatat manual — MVP tanpa payment gateway"
        )
        assertTrue(
            doc.footerNote.contains("dibekukan"),
            "catatan kaki menyebut kapan angkanya berlaku: ${doc.footerNote}"
        )
    }
}
