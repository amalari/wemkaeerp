package com.eventverse.app.domain.discovery.print

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tata letak PDF blueprint (plan §5 Fase D).
 *
 * Yang diuji di sini adalah hal-hal yang **tidak tertangkap mata** saat memeriksa PDF 1 halaman:
 * overflow di dokumen 40 modul, baris yang saling menimpa setelah pemenggalan halaman, dan halaman
 * lanjutan yang kehilangan nomor halaman. Semuanya dinyatakan sebagai bidang, bukan sebagai dugaan
 * bahwa renderer pasti beres.
 */
class BlueprintSheetLayoutTest {

    private val paper = BlueprintSheetLayout.PAPER
    private val bottomLimit = paper.heightMm10 - BlueprintSheetLayout.MARGIN_BOTTOM_MM10
    private val rightLimit = paper.widthMm10 - BlueprintSheetLayout.MARGIN_X_MM10

    @Test
    fun `satu modul cukup satu halaman dan memuat watermark`() {
        val sheet = BlueprintSheetLayout.layout(document(moduleCount = 1))

        assertEquals(1, sheet.pages.size)
        assertTrue(sheet.pages.single().watermark.text.isNotBlank())
        assertTrue(sheet.pages.single().body.isNotEmpty())
    }

    @Test
    fun `semua baris berada di dalam margin dan tidak saling menimpa`() {
        sheetPages(moduleCount = 40).forEach { page ->
            (page.body + page.footer).forEach { line ->
                assertTrue(line.rect.x.value >= BlueprintSheetLayout.MARGIN_X_MM10, "Baris '${line.text}' keluar margin kiri")
                assertTrue(line.rect.right.value <= rightLimit, "Baris '${line.text}' keluar margin kanan")
                assertTrue(line.rect.y.value >= BlueprintSheetLayout.MARGIN_TOP_MM10, "Baris '${line.text}' naik ke atas margin")
                assertTrue(line.rect.bottom.value <= bottomLimit, "Baris '${line.text}' tumpah ke area footer")
            }
            page.body.zipWithNext().forEach { (previous, next) ->
                assertTrue(previous.rect.bottom.value <= next.rect.y.value, "'${previous.text}' menimpa '${next.text}'")
            }
        }
    }

    @Test
    fun `halaman lanjutan diberi judul lanjutan dan halaman pertama tidak`() {
        val pages = sheetPages(moduleCount = 40)

        assertTrue(pages.size > 1, "Dokumen 40 modul seharusnya lebih dari satu halaman")
        assertTrue(!pages.first().body.first().text.contains(BlueprintSheetLayout.CONTINUATION_SUFFIX))
        pages.drop(1).forEach { page ->
            assertTrue(
                page.body.first().text.contains(BlueprintSheetLayout.CONTINUATION_SUFFIX),
                "Halaman ${page.index} kehilangan judul lanjutan"
            )
        }
    }


    @Test
    fun `setiap halaman punya watermark dan nomor halaman`() {
        val pages = sheetPages(moduleCount = 40)

        pages.forEachIndexed { index, page ->
            assertEquals(BlueprintPdfDocument.WATERMARK_DRAFT, page.watermark.text)
            assertEquals("Halaman ${index + 1} dari ${pages.size}", page.footer.first().text)
        }
    }

    @Test
    fun `setiap modul tercetak tepat sekali walau terpotong antar halaman`() {
        val count = 40
        val texts = sheetPages(moduleCount = count).flatMap { page -> page.body.map { it.text } }

        (1..count).forEach { i ->
            assertEquals(1, texts.count { it.contains("(modul_$i)") }, "Modul $i harus tercetak tepat sekali")
        }
        assertEquals(count, texts.count { it.contains("[aktif]") || it.contains("[bypass]") })
    }

    @Test
    fun `deskripsi panjang mengalir antar halaman tanpa meluber`() {
        val long = document(moduleCount = 2, description = (1..800).joinToString(" ") { "kalimat$it" })
        val pages = BlueprintSheetLayout.layout(long).pages

        assertTrue(pages.size > 1, "Deskripsi 800 kata seharusnya lebih dari satu halaman")
        assertTrue(pages.last().body.maxOf { it.rect.bottom.value } <= bottomLimit)
    }

    @Test
    fun `tata letak deterministik untuk masukan yang sama`() {
        val doc = document(moduleCount = 12)
        assertEquals(BlueprintSheetLayout.layout(doc), BlueprintSheetLayout.layout(doc))
    }

    @Test
    fun `dokumen tanpa modul dan tanpa layar tetap satu halaman`() {
        val empty = document(moduleCount = 0).copy(phases = emptyList(), screens = emptyList())
        val sheet = BlueprintSheetLayout.layout(empty)

        assertEquals(1, sheet.pages.size)
        assertTrue(sheet.pages.single().body.any { it.text.contains("0 aktif dari 0") })
    }

    private fun sheetPages(moduleCount: Int) = BlueprintSheetLayout.layout(document(moduleCount)).pages

    private fun document(moduleCount: Int, description: String = "Klinik pratama dengan antrean per poli.") =
        BlueprintPdfDocument(
            title = BlueprintPdfDocument.DEFAULT_TITLE,
            packName = "Klinik & Layanan Kesehatan",
            packCode = "klinik",
            blueprintName = "Klinik Sederhana",
            badge = "Klinik",
            description = description,
            targetClientProfile = "Klinik pratama 1-3 poli",
            generatedAtLabel = "30 Sep 2026 14:05 WIB",
            watermark = BlueprintPdfDocument.WATERMARK_DRAFT,
            footerNote = BlueprintPdfDocument.DEFAULT_FOOTER,
            terms = listOf("perusahaan" to "klinik", "dokumen" to "Kunjungan"),
            phases = listOf("1. Pendaftaran - Pasien datang & antre", "2. Pelayanan - Pemeriksaan dokter"),
            modules = (1..moduleCount).map { i ->
                BlueprintModuleLine(
                    moduleCode = "modul_$i",
                    displayName = "Modul $i",
                    sectionName = "Operasional",
                    phaseName = "Pelayanan",
                    active = i % 4 != 0,
                    parameters = listOf("costingBehavior" to "SERVICE_FEE_ONLY")
                )
            },
            screens = listOf("Antrean Pasien (TABLE) - modul_1")
        )
}
