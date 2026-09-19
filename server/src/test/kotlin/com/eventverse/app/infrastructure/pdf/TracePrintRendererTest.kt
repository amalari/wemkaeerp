package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.domain.traceability.print.*
import org.apache.pdfbox.Loader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Membuktikan berkas PDF-nya benar-benar terbentuk dan halamannya berukuran A4.
 *
 * Yang TIDAK bisa dibuktikan di sini, dan karenanya tetap wajib dicek dengan mata: apakah QR 25 mm
 * terbaca kamera HP setelah dicetak dengan tinta dan kertas yang dipakai pabrik. Tidak ada test yang
 * bisa menjawab itu — hanya mencetaknya lalu memindainya.
 */
class TracePrintRendererTest {

    private val outputDir = File(System.getProperty("java.io.tmpdir"), "wemade-trace-print")

    private val snapshot = TraceWorkOrderSnapshot(
        ref = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, "smp_1"),
        tenantId = TenantId("tnt-1"),
        tenantOrdinal = 3,
        ordinal = 9,
        spkNumber = "SPK-SMP-0009",
        styleName = "Cardigan Rajut Gauge 7",
        clientName = "PT Buyer Sejahtera",
        sizes = listOf(TraceSizeLine("L", 300), TraceSizeLine("XL", 120)),
        panelRequirements = listOf(
            PanelRequirement(GarmentPanel.BODY_FRONT, 1),
            PanelRequirement(GarmentPanel.BODY_BACK, 1),
            PanelRequirement(GarmentPanel.SLEEVE_LEFT, 2)
        ),
        colorways = listOf("Navy")
    )

    private val plan = TraceAllocationPlan.plan(snapshot, setsPerBundle = 20, pcsPerSack = 60)

    private fun save(name: String, bytes: ByteArray): File {
        outputDir.mkdirs()
        val file = File(outputDir, name)
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun `bundle card sheet renders A4 pages with one page per eight cards`() {
        val sheet = TraceLabelSheetLayout.solvePlan(plan, TraceTier.BUNDLE, "L")
        val bytes = TraceLabelSheetPdfRenderer("trace.wemade.id")
            // Gaya berpanel banyak: tujuh baris harus tetap muat, bukan terpotong diam-diam.
            .render(
                sheet, snapshot.spkNumber,
                listOf(
                    "Badan Depan", "Badan Belakang", "Lengan (x2)", "Rib Leher",
                    "Placket", "Operator", "Shift / Tanggal"
                )
            )

        val file = save("kartu-bundel.pdf", bytes)
        assertTrue(bytes.size > 1000, "PDF kartu bundel terlalu kecil untuk berisi QR")

        Loader.loadPDF(file).use { doc ->
            assertEquals(sheet.pageCount, doc.numberOfPages)
            val page = doc.getPage(0).mediaBox
            assertEquals(595, page.width.toInt(), "Lebar halaman harus A4 (595,28 pt)")
            assertEquals(841, page.height.toInt(), "Tinggi halaman harus A4 (841,89 pt)")
        }
        println("Kartu bundel: ${file.absolutePath}")
    }

    @Test
    fun `sack card sheet renders`() {
        val sheet = TraceLabelSheetLayout.solvePlan(plan, TraceTier.SACK, "L")
        val bytes = TraceLabelSheetPdfRenderer("trace.wemade.id")
            .render(sheet, snapshot.spkNumber, listOf("Jumlah (pcs)", "Berat (kg)", "Bundel induk"))

        val file = save("kartu-karung.pdf", bytes)
        Loader.loadPDF(file).use { doc -> assertTrue(doc.numberOfPages >= 1) }
        println("Kartu karung: ${file.absolutePath}")
    }

    @Test
    fun `knit worksheet renders one page per size`() {
        val worksheetCodes = plan.labelsFor(TraceTier.WORKSHEET)
        assertEquals(2, worksheetCodes.size, "Satu lembar kerja per size")

        val worksheet = KnitWorksheet(
            spkNumber = snapshot.spkNumber,
            styleName = snapshot.styleName,
            clientName = snapshot.clientName,
            pages = snapshot.sizes.mapIndexed { index, size ->
                KnitWorksheetPage(
                    sizeLabel = size.sizeLabel,
                    orderedPcs = size.orderedPcs,
                    code = worksheetCodes[index].code,
                    panelRows = listOf(
                        WorksheetPanelRow("Badan Depan", 138.0, 42, "CAM-FRONT-07"),
                        WorksheetPanelRow("Badan Belakang", 136.0, 41, "CAM-BACK-07"),
                        // Baris warisan: lembar harus menandainya sebagai salinan, bukan hasil timbang.
                        WorksheetPanelRow("Lengan (x2)", 74.0, 26, "CAM-SLEEVE-07", inheritedFrom = "L")
                    ),
                    measurementRows = listOf(
                        WorksheetMeasurementRow("Lebar Dada", 56.0, 54.0, 1.0),
                        WorksheetMeasurementRow("Panjang Baju", 68.0, 66.0, 1.0)
                    ),
                    feederNotes = listOf("#1 Benang Utama Navy"),
                    colorway = "Navy"
                )
            }
        )

        val bytes = KnitWorksheetPdfRenderer("trace.wemade.id").render(worksheet)
        val file = save("lembar-kerja.pdf", bytes)

        Loader.loadPDF(file).use { doc -> assertEquals(2, doc.numberOfPages) }
        println("Lembar kerja: ${file.absolutePath}")
    }

    @Test
    fun `every printed code parses back to the card it belongs to`() {
        plan.labels.forEach { label ->
            val parts = TraceCodec.parse(label.code.value)
            assertTrue(parts != null, "Kode tercetak harus bisa diurai kembali: ${label.humanCode}")
            assertEquals(label.tier, parts.tier)
            assertEquals(label.sizeIndex, parts.sizeIndex)
        }
    }
}
