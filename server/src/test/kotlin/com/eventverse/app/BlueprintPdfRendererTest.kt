package com.eventverse.app

import com.eventverse.app.domain.discovery.print.BlueprintModuleLine
import com.eventverse.app.domain.discovery.print.BlueprintPdfDocument
import com.eventverse.app.domain.discovery.print.BlueprintSheetLayout
import com.eventverse.app.infrastructure.pdf.BlueprintPdfRenderer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renderer PDF blueprint (plan §5, Fase D).
 *
 * Yang diperiksa adalah hal yang tidak terlihat dari kode renderer: bahwa **isi yang diputuskan
 * domain benar-benar sampai ke kertas** (nama modul, parameter, nomor halaman) dan bahwa watermark
 * tercetak di **setiap** halaman. PDF yang halamannya kehilangan watermark justru itulah yang paling
 * sering difoto lalu dirujuk sebagai kesepakatan.
 */
class BlueprintPdfRendererTest {

    private val renderer = BlueprintPdfRenderer()

    @Test
    fun `pdf memuat paket modul parameter watermark dan nomor halaman`() {
        val document = document(moduleCount = 3)
        val sheet = BlueprintSheetLayout.layout(document)
        val bytes = renderer.render(sheet)

        assertTrue(bytes.size > 1000, "PDF terlalu kecil untuk memuat satu halaman teks")
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4), Charsets.ISO_8859_1))

        Loader.loadPDF(bytes).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Klinik & Layanan Kesehatan"), "Nama pack hilang dari PDF")
            assertTrue(text.contains("[aktif] Modul 1 (modul_1)"), "Baris modul hilang dari PDF")
            assertTrue(text.contains("costingBehavior = SERVICE_FEE_ONLY"), "Parameter modul hilang dari PDF")
            assertTrue(text.contains("Halaman 1 dari 1"), "Footer nomor halaman hilang dari PDF")
            // Teks miring dipotong baris oleh PDFTextStripper di tengah kata, jadi perbandingannya
            // dilakukan tanpa spasi — lihat `normalizedForMatch`.
            assertTrue(normalizedForMatch(text).contains("DRAF-BUKANPENAWARAN"), "Watermark hilang dari PDF")
        }
    }

    @Test
    fun `watermark tercetak di setiap halaman termasuk halaman lanjutan`() {
        val sheet = BlueprintSheetLayout.layout(document(moduleCount = 45))
        assertTrue(sheet.pages.size > 1, "Dokumen 45 modul seharusnya berhalaman lebih dari satu")

        val bytes = renderer.render(sheet)
        Loader.loadPDF(bytes).use { pdf ->
            assertEquals(sheet.pages.size, pdf.numberOfPages)
            val text = normalizedForMatch(PDFTextStripper().getText(pdf))
            assertEquals(
                sheet.pages.size,
                Regex("DRAF-BUKANPENAWARAN").findAll(text).count(),
                "Watermark harus ada di tiap halaman"
            )
            assertTrue(text.contains("(lanjutan)"), "Halaman lanjutan kehilangan judul lanjutannya")
        }
    }

    /**
     * Bukti mata versi mesin: halaman yang **sama** dicetak dua kali, sekali dengan watermark dan
     * sekali tanpa, lalu tinta di pita tengah halaman dibandingkan.
     *
     * Ekstraksi teks tidak bisa dipakai untuk ini — PDFTextStripper memotong teks miring di tengah
     * kata. Pita tengah dipilih karena teks isi yang identik ada di kedua versi, jadi selisih tinta
     * di sana hanya bisa berasal dari watermark diagonal.
     */
    @Test
    fun `setiap halaman punya tinta watermark di pita tengahnya`() {
        val withWatermark = document(moduleCount = 45)
        val sheet = BlueprintSheetLayout.layout(withWatermark)
        assertTrue(sheet.pages.size > 1)

        val inked = centerBandInk(renderer.render(sheet))
        val control = centerBandInk(renderer.render(BlueprintSheetLayout.layout(withWatermark.copy(watermark = ""))))

        assertEquals(sheet.pages.size, inked.size)
        inked.zip(control).forEachIndexed { index, (withInk, withoutInk) ->
            assertTrue(
                withInk > withoutInk + 200,
                "Halaman ${index + 1} tidak berwatermark (tinta=$withInk kontrol=$withoutInk)"
            )
        }
    }

    @Test
    fun `karakter tanpa glyph tidak menggagalkan seluruh dokumen`() {
        // Kosakata pack datang dari tenant: satu karakter yang tidak ada di font tidak boleh membuat
        // prospek menerima PDF kosong (dulu `showText` melempar untuk panah U+2192).
        val exotic = document(moduleCount = 1).copy(
            modules = listOf(
                BlueprintModuleLine(
                    moduleCode = "modul_x",
                    displayName = "Modul → 中文",
                    sectionName = "Operasional",
                    phaseName = null,
                    active = true,
                    parameters = listOf("catatan" to "—")
                )
            )
        )
        val bytes = renderer.render(BlueprintSheetLayout.layout(exotic))

        Loader.loadPDF(bytes).use { pdf ->
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Modul -> ??"), "Panah dipetakan ke ASCII, sisanya jadi '?'")
            assertTrue(text.contains("catatan = —"), "Em dash didukung font, jadi dicetak apa adanya")
        }
    }

    /**
     * Teks ekstraksi disiapkan untuk perbandingan: spasi dibuang (PDFTextStripper memotong teks miring
     * di tengah kata) dan semua jenis dash diseragamkan (watermark memakai em dash `—`).
     */
    private fun normalizedForMatch(text: String): String =
        text.filterNot { it.isWhitespace() }.replace('—', '-').replace('–', '-')

    /**
     * Jumlah piksel bertinta di pita tengah tiap halaman, pada 40 DPI (cukup untuk melihat blok
     * watermark tanpa membuat test lambat).
     */
    private fun centerBandInk(bytes: ByteArray): List<Int> = Loader.loadPDF(bytes).use { pdf ->
        val renderer = PDFRenderer(pdf)
        (0 until pdf.numberOfPages).map { index ->
            val image = renderer.renderImageWithDPI(index, 40f)
            var ink = 0
            for (y in image.height / 3 until image.height * 2 / 3) {
                for (x in image.width / 3 until image.width * 2 / 3) {
                    val red = (image.getRGB(x, y) shr 16) and 0xFF
                    if (red < 240) ink++
                }
            }
            ink
        }
    }

    private fun document(moduleCount: Int) = BlueprintPdfDocument(
        title = BlueprintPdfDocument.DEFAULT_TITLE,
        packName = "Klinik & Layanan Kesehatan",
        packCode = "klinik",
        blueprintName = "Klinik Sederhana",
        badge = "Klinik",
        description = "Klinik pratama dengan antrean per poli dan tagihan kasir.",
        targetClientProfile = "Klinik pratama 1-3 poli",
        generatedAtLabel = "30 Sep 2026 14:05 WIB",
        watermark = BlueprintPdfDocument.WATERMARK_DRAFT,
        footerNote = BlueprintPdfDocument.DEFAULT_FOOTER,
        terms = listOf("perusahaan" to "klinik", "dokumen" to "Kunjungan"),
        phases = listOf("1. Pendaftaran - Pasien datang & antre"),
        modules = (1..moduleCount).map { i ->
            BlueprintModuleLine(
                moduleCode = "modul_$i",
                displayName = "Modul $i",
                sectionName = "Operasional",
                phaseName = "Pelayanan",
                active = true,
                parameters = listOf("costingBehavior" to "SERVICE_FEE_ONLY")
            )
        },
        screens = listOf("Antrean Pasien (TABLE) - modul_1")
    )
}
