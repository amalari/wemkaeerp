package com.eventverse.app.domain.traceability.print

import com.eventverse.app.domain.printing.PaperSize
import com.eventverse.app.domain.sampling.SpkUrgencyLevel
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Geometri Kartu SPK dikunci di test karena bug layout tidak akan tertangkap kompilasi: halaman
 * yang meluap dari A6 atau QR yang menyusut di bawah ambang pemindaian hanya terlihat setelah
 * dicetak — dan saat itulah perbaikannya paling mahal.
 */
class SpkCardLayoutTest {

    private fun code(sizeIndex: Int = 0) = TraceCodec.encode(
        kind = TraceWorkOrderKind.SAMPLING,
        tier = com.eventverse.app.domain.traceability.TraceTier.WORKSHEET,
        tenantOrdinal = 1,
        workOrderOrdinal = 5,
        sizeIndex = sizeIndex,
        sequence = 0
    )

    /** 11 titik ukur — persis contoh nyata "HASIL UKURAN JADI" yang melahirkan fitur ini. */
    private val samplingRows = listOf(
        "P BADAN", "L BADAN", "ARMHOLE BADAN", "TURUN BAHU", "BUKAAN KERAH", "TURUN KERAH",
        "P TANGAN", "ARMHOLE TANGAN", "BUKAAN TANGAN", "KERAH", "RIB"
    ).map { SpkMeasurementRow(it, "55") }

    private val pomRows = listOf(
        SpkMeasurementRow("Lebar Dada", "56"),
        SpkMeasurementRow("Panjang Baju", "62")
    )

    private fun content(
        pom: List<SpkMeasurementRow> = pomRows,
        sampling: List<SpkMeasurementRow> = samplingRows,
        sizes: List<String> = listOf("ALL SIZE")
    ) = SpkCardContent(
        spkNumber = "SPK-SMP-0005",
        styleName = "Cardigan Rajut Kombinasi",
        clientName = "BKD Apparel",
        revision = 1,
        stageLabel = "Rajut Turun Mesin",
        stageNumber = 4,
        stageCount = 11,
        deadline = LocalDate(2026, 9, 30),
        urgencyLevel = SpkUrgencyLevel.URGENT,
        slackDays = -1,
        rank = 2,
        activeCount = 14,
        colorways = listOf("HITAM", "BW", "M71"),
        printedOn = LocalDate(2026, 9, 27),
        cards = sizes.mapIndexed { index, label ->
            SpkSizeCard(label, 2, code(index), TraceCodec.grouped(code(index)), pom, sampling)
        }
    )

    private fun SpkCardRegions.allRects() = listOf(
        page, spkNumber, revision, styleClient, qr, humanCode, colorwayLine, urgencyStrip, pomTitle, samplingTitle
    ) + identityLines + pomGrid.flatMap { listOf(it.left, it.right) } +
        samplingGrid.flatMap { listOf(it.left, it.right) } +
        listOfNotNull(pomOverflow, samplingOverflow)

    @Test
    fun `kartu tipikal dengan 11 titik ukur sampling tetap di dalam A6`() {
        val sheet = SpkCardLayout.solve(content())
        assertEquals(1, sheet.pages.size, "ALL SIZE = satu halaman")

        val regions = sheet.pages.single().regions
        regions.allRects().forEach { rect ->
            assertTrue(rect.width.value > 0 && rect.height.value > 0, "Bidang tidak boleh negatif: $rect")
            assertTrue(rect.x.value >= 0 && rect.y.value >= 0, "Bidang keluar kertas di kiri/atas: $rect")
            assertTrue(
                rect.right.value <= SpkCardLayout.PAGE_WIDTH && rect.bottom.value <= SpkCardLayout.PAGE_HEIGHT,
                "Bidang keluar kertas di kanan/bawah: $rect"
            )
        }
        assertTrue(
            regions.samplingGrid.last().left.bottom.value <= regions.urgencyStrip.y.value,
            "Grid ukuran tidak boleh menabrak strip urgensi"
        )
    }

    @Test
    fun `ukuran kertas cocok dengan PaperSize A6`() {
        assertEquals(PaperSize.A6.widthMm10, SpkCardLayout.PAGE_WIDTH)
        assertEquals(PaperSize.A6.heightMm10, SpkCardLayout.PAGE_HEIGHT)
    }

    @Test
    fun `qr tidak boleh menyusut di bawah ambang pemindaian kartu`() {
        val regions = SpkCardLayout.solve(content()).pages.single().regions
        assertTrue(
            regions.qr.width.value >= 250,
            "QR ${regions.qr.width.value} Mm10 di bawah ambang 25 mm kartu bundel yang terbukti terbaca"
        )
    }

    @Test
    fun `baris ukur melampaui cap menjadi ringkasan overflow bukan terpotong diam`() {
        val banyak = (1..20).map { SpkMeasurementRow("UKUR-$it", "$it") }
        val regions = SpkCardLayout.solve(content(sampling = banyak)).pages.single().regions

        assertEquals(SpkCardLayout.MAX_SAMPLING_GRID_ROWS, regions.samplingGrid.size)
        assertEquals(20 - SpkCardLayout.MAX_SAMPLING_GRID_ROWS * 2, regions.samplingOverflowCount)
        assertNotNull(regions.samplingOverflow, "Baris ringkasan wajib dicetak saat ada yang meluap")

        // Meski datanya banyak, halaman tetap tidak boleh meluap — cap-lah yang menjaga.
        assertTrue(regions.samplingOverflow!!.bottom.value <= regions.urgencyStrip.y.value)
    }

    @Test
    fun `satu halaman per ukuran aktif`() {
        val sheet = SpkCardLayout.solve(content(sizes = listOf("L", "XL")))
        assertEquals(2, sheet.pages.size)
        assertEquals(listOf("L", "XL"), sheet.pages.map { it.sizeLabel })
    }

    @Test
    fun `pom melampaui cap juga diringkas`() {
        val banyak = (1..10).map { SpkMeasurementRow("POM-$it", "$it") }
        val regions = SpkCardLayout.solve(content(pom = banyak)).pages.single().regions
        assertEquals(SpkCardLayout.MAX_POM_GRID_ROWS, regions.pomGrid.size)
        assertEquals(10 - SpkCardLayout.MAX_POM_GRID_ROWS * 2, regions.pomOverflowCount)
        assertNotNull(regions.pomOverflow)
    }
}