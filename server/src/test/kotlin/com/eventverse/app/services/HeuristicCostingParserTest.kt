package com.eventverse.app.services

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lembar HPP tiruan yang meniru tata letak arsip Parinara: label di kolom kiri, nilai di kanan,
 * angka rupiah bergaya Indonesia dengan titik sebagai pemisah ribuan.
 */
private val PARINARA_SHEET = """
    ### SHEET: HPP
    NAMA ARTIKEL	CARDIGAN PARINARA
    BUYER	PT Sinar Busana
    JENIS RAJUT	Jacquard
    BENANG	Acrylic 2/32
    GAUGE	7
    GRAMASI	494
    MENIT RAJUT	97
    KANCING	7
    Benang	Rp 79.040
    Jahit & Linking	Rp 48.500
    Kancing	Rp 2.450
    Label woven	Rp 900
    Overhead	Rp 6.000
    HPP	Rp 145.000
    HARGA JUAL	Rp 174.000
""".trimIndent()

class HeuristicCostingParserTest {

    private val parser = HeuristicCostingParser()

    private fun parse(grid: String, fileName: String = "HPP CARDIGAN PARINARA.xlsx") = runBlocking {
        parser.parse(WorkbookExtract(fileName = fileName, gridText = grid), mockupImageUrl = null)
    }

    @Test
    fun parse_parinaraSheet_extractsWeightAndKnittingMinutesExactly() {
        val parsed = parse(PARINARA_SHEET)

        assertEquals(494.0, parsed.netWeightGrams)
        assertEquals(97, parsed.knittingMinutes)
        assertEquals(7, parsed.gauge)
        assertEquals(7, parsed.buttonCount)
    }

    @Test
    fun parse_parinaraSheet_readsIndonesianThousandSeparatorsAsFullRupiah() {
        val parsed = parse(PARINARA_SHEET)

        // Rp 145.000 harus jadi 14.500.000 minor units (sen), bukan Rp 145.
        assertEquals(145_000_00L, parsed.hppPerUnitMinor)
        assertEquals(174_000_00L, parsed.sellingPricePerUnitMinor)
    }

    @Test
    fun parse_parinaraSheet_capturesComponentLinesButNotTotals() {
        val parsed = parse(PARINARA_SHEET)
        val labels = parsed.costBreakdown.map { it.label }

        assertTrue(labels.any { it.contains("Benang", ignoreCase = true) })
        assertTrue(labels.any { it.contains("Jahit", ignoreCase = true) })
        assertTrue(labels.none { it.equals("HPP", ignoreCase = true) }, "Baris total ikut terbawa: $labels")
        assertTrue(labels.none { it.contains("HARGA JUAL", ignoreCase = true) })
    }

    @Test
    fun parse_specificationRows_neverLeakIntoTheCostBreakdown() {
        val parsed = parse(PARINARA_SHEET)
        val labels = parsed.costBreakdown.map { it.label }

        // "MENIT RAJUT 97" pernah tersimpan sebagai biaya Rp 97 karena kata "rajut",
        // dan "KANCING 7" sebagai Rp 7. Keduanya baris spesifikasi, bukan rupiah.
        assertTrue(labels.none { it.contains("MENIT", ignoreCase = true) }, "Bocor: $labels")
        assertTrue(
            parsed.costBreakdown.none { it.amountPerUnit.minorUnits < 100_00L },
            "Nominal receh menandakan baris spesifikasi terbaca sebagai biaya: ${parsed.costBreakdown}"
        )
        // Yang tersisa hanya lima baris biaya yang sah. Perhatikan bahwa "Kancing Rp 2.450"
        // TETAP masuk — yang dibuang adalah baris spesifikasi "KANCING 7" (jumlah, bukan rupiah).
        assertEquals(
            listOf("Benang", "Jahit & Linking", "Kancing", "Label woven", "Overhead"),
            labels
        )
    }

    @Test
    fun parse_sheetWithoutStyleLabel_fallsBackToFileName() {
        val parsed = parse("GRAMASI\t300", fileName = "HPP_VEST_ALFA.xlsx")

        assertEquals("HPP VEST ALFA", parsed.styleName)
        assertEquals(300.0, parsed.netWeightGrams)
        assertNull(parsed.hppPerUnitMinor)
    }

    @Test
    fun parseIndonesianNumber_distinguishesThousandSeparatorFromDecimalPoint() {
        assertEquals(45_000.0, "Rp 45.000".parseIndonesianNumber())
        assertEquals(45_000.5, "45.000,50".parseIndonesianNumber())
        assertEquals(45_000.5, "45,000.50".parseIndonesianNumber())
        assertEquals(494.0, "494".parseIndonesianNumber())
        assertNull("n/a".parseIndonesianNumber())
        assertNull("".parseIndonesianNumber())
    }
}
