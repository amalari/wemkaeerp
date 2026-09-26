package com.eventverse.app.services

import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip nyata: bikin `.xlsx` dengan POI, baca kembali lewat pipeline impor.
 *
 * Test string-grid saja tidak cukup — ia tidak pernah membuktikan bahwa sel numerik Excel
 * (yang POI kembalikan sebagai `Double`) diratakan tanpa `.0` menempel, dan bahwa sel rupiah
 * berformat angka tetap terbaca sebagai nominal penuh.
 */
class HistoricalCostingWorkbookReaderTest {

    private fun writeSampleWorkbook(target: File) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("HPP")
            val rows = listOf(
                "NAMA ARTIKEL" to "CARDIGAN PARINARA",
                "BUYER" to "PT Sinar Busana",
                "JENIS RAJUT" to "Jacquard",
                "BENANG" to "Acrylic 2/32"
            )
            rows.forEachIndexed { index, (label, value) ->
                sheet.createRow(index).also {
                    it.createCell(0).setCellValue(label)
                    it.createCell(1).setCellValue(value)
                }
            }
            // Sel numerik asli — inilah yang tidak diuji oleh test berbasis teks.
            val numeric = listOf(
                "GAUGE" to 7.0,
                "GRAMASI" to 494.0,
                "MENIT RAJUT" to 97.0,
                "KANCING" to 7.0,
                "Benang" to 79_040.0,
                "Jahit & Linking" to 48_500.0,
                "HPP" to 145_000.0,
                "HARGA JUAL" to 174_000.0
            )
            numeric.forEachIndexed { offset, (label, value) ->
                sheet.createRow(rows.size + offset).also {
                    it.createCell(0).setCellValue(label)
                    it.createCell(1).setCellValue(value)
                }
            }
            target.outputStream().use { workbook.write(it) }
        }
    }

    @Test
    fun read_realXlsx_flattensNumericCellsWithoutTrailingDecimalZero() {
        val file = File.createTempFile("wemade-hpp-test-", ".xlsx")
        try {
            writeSampleWorkbook(file)
            val extract = HistoricalCostingWorkbookReader().read(file)

            assertTrue(extract.gridText.contains("### SHEET: HPP"))
            assertTrue(extract.gridText.contains("GRAMASI\t494"), "Grid: ${extract.gridText}")
            assertTrue(!extract.gridText.contains("494.0"), "Angka bulat tidak boleh ber-'.0'")
        } finally {
            file.delete()
        }
    }

    @Test
    fun readThenParse_realXlsx_yieldsArchiveReadyRecord() {
        val file = File.createTempFile("wemade-hpp-test-", ".xlsx")
        try {
            writeSampleWorkbook(file)
            val extract = HistoricalCostingWorkbookReader().read(file)
            val parsed = runBlocking { HeuristicCostingParser().parse(extract, mockupImageUrl = null) }

            assertEquals("CARDIGAN PARINARA", parsed.styleName)
            assertEquals("PT Sinar Busana", parsed.clientName)
            assertEquals(494.0, parsed.netWeightGrams)
            assertEquals(97, parsed.knittingMinutes)
            assertEquals(7, parsed.gauge)
            assertEquals(145_000_00L, parsed.hppPerUnitMinor)
            assertEquals(174_000_00L, parsed.sellingPricePerUnitMinor)
            assertTrue(parsed.costBreakdown.any { it.label.contains("Benang") })
        } finally {
            file.delete()
        }
    }
}
