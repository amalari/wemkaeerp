package com.eventverse.app.infrastructure.pdf

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.eventverse.app.domain.sampling.SpkUrgencyLevel
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.print.SpkCardContent
import com.eventverse.app.domain.traceability.print.SpkCardLayout
import com.eventverse.app.domain.traceability.print.SpkMeasurementRow
import com.eventverse.app.domain.traceability.print.SpkSizeCard
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer

/**
 * Membuktikan Kartu SPK benar-benar terbentuk dan halamannya berukuran A6.
 *
 * Yang tidak bisa dibuktikan di sini — dan wajib dicek dengan mata — adalah keterbacaan font 7,5pt
 * di kertas dan QR 30 mm yang terbaca kamera HP dari sudut miring.
 */
class SpkCardPdfRendererTest {

    private val outputDir = File(System.getProperty("java.io.tmpdir"), "wemade-spk-card")

    private val samplingRows = listOf(
        "P BADAN", "L BADAN", "ARMHOLE BADAN", "TURUN BAHU", "BUKAAN KERAH", "TURUN KERAH",
        "P TANGAN", "ARMHOLE TANGAN", "BUKAAN TANGAN", "KERAH", "RIB"
    ).map { SpkMeasurementRow(it, "55") }

    private fun content(sizes: List<String>, sampling: List<SpkMeasurementRow> = samplingRows) =
        SpkCardContent(
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
            colorways = listOf("HITAM", "BW", "M71", "DARK GREY"),
            printedOn = LocalDate(2026, 9, 27),
            cards = sizes.mapIndexed { index, label ->
                SpkSizeCard(
                    sizeLabel = label,
                    qtyPcs = 2,
                    code = TraceCodec.encode(
                        kind = TraceWorkOrderKind.SAMPLING,
                        tier = TraceTier.WORKSHEET,
                        tenantOrdinal = 1,
                        workOrderOrdinal = 5,
                        sizeIndex = index,
                        sequence = 0
                    ),
                    humanCode = "",
                    pomRows = listOf(
                        SpkMeasurementRow("Lebar Dada", "56"),
                        SpkMeasurementRow("Panjang Baju", "62")
                    ),
                    samplingRows = sampling
                )
            }
        )

    private fun save(name: String, bytes: ByteArray): File {
        outputDir.mkdirs()
        return File(outputDir, name).apply { writeBytes(bytes) }
    }

    @Test
    fun `kartu spk renders A6 pages one per size`() {
        val sheet = SpkCardLayout.solve(content(listOf("ALL SIZE")))
        val bytes = SpkCardPdfRenderer("trace.wemade.id").render(sheet)
        val file = save("kartu-spk-all-size.pdf", bytes)
        assertTrue(bytes.size > 1000, "PDF kartu SPK terlalu kecil untuk berisi QR")

        Loader.loadPDF(file).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val page = doc.getPage(0).mediaBox
            assertEquals(297.64, page.width.toDouble(), 1.0, "Lebar halaman harus A6 (297,6 pt)")
            assertEquals(419.53, page.height.toDouble(), 1.0, "Tinggi halaman harus A6 (419,5 pt)")
        }
        println("Kartu SPK: ${file.absolutePath}")
    }

    @Test
    fun `multi size spk renders one page per size`() {
        val sheet = SpkCardLayout.solve(content(listOf("L", "XL")))
        val bytes = SpkCardPdfRenderer("trace.wemade.id").render(sheet)
        val file = save("kartu-spk-multi-size.pdf", bytes)

        Loader.loadPDF(file).use { doc -> assertEquals(2, doc.numberOfPages) }
        println("Kartu SPK multi-size: ${file.absolutePath}")
    }

    @Test
    fun `dua puluh titik ukur tidak merusak halaman - overflow diringkas`() {
        val banyak = (1..20).map { SpkMeasurementRow("UKUR-$it", "$it") }
        val sheet = SpkCardLayout.solve(content(listOf("ALL SIZE"), sampling = banyak))
        val bytes = SpkCardPdfRenderer("trace.wemade.id").render(sheet)
        val file = save("kartu-spk-overflow.pdf", bytes)

        Loader.loadPDF(file).use { doc -> assertEquals(1, doc.numberOfPages) }
        println("Kartu SPK overflow: ${file.absolutePath}")
    }

    @Test
    fun `qr pada kartu terbaca kembali ke kode worksheet`() {
        val sheet = SpkCardLayout.solve(content(listOf("ALL SIZE")))
        val bytes = SpkCardPdfRenderer("trace.wemade.id").render(sheet)
        val file = save("kartu-spk-decode.pdf", bytes)

        Loader.loadPDF(file).use { doc ->
            val image = PDFRenderer(doc).renderImageWithDPI(0, 300f)
            val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image)))
            val result = MultiFormatReader().decode(bitmap)
            assertEquals(
                TraceCodec.toScanUrl(sheet.content.cards.single().code, "trace.wemade.id"),
                result.text,
                "QR yang tercetak harus mengarah ke halaman lembar kerja ukuran yang sama"
            )
        }
    }
}

/** LuminanceSource minimal di atas BufferedImage — zxing-javase tidak ada di classpath server. */
private class BufferedImageLuminanceSource(image: java.awt.image.BufferedImage) :
    com.google.zxing.LuminanceSource(image.width, image.height) {

    private val pixels = IntArray(width * height).also {
        image.getRGB(0, 0, width, height, it, 0, width)
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val out = row ?: ByteArray(width)
        require(out.size >= width) { "Buffer baris terlalu kecil" }
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            out[x] = (((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3).toByte()
        }
        return out
    }

    override fun getMatrix(): ByteArray = ByteArray(width * height) { i ->
        val pixel = pixels[i]
        (((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3).toByte()
    }
}