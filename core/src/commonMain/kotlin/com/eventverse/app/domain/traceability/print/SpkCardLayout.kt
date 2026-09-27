package com.eventverse.app.domain.traceability.print

import com.eventverse.app.domain.printing.Mm10
import com.eventverse.app.domain.printing.TemplateRect

/** Satu baris grid ukuran: dua pasang `label : nilai` bersebelahan. */
data class SpkCardGridRow(val left: TemplateRect, val right: TemplateRect)

/**
 * Bidang-bidang satu halaman Kartu SPK A6.
 *
 * Dihitung di domain dengan alasan yang sama dengan [TraceLabelRegions]: posisi yang sama bisa
 * diuji tanpa membuka PDF, dan pratinjau di layar (kalau nanti ada) memakai angka yang identik
 * dengan hasil cetak.
 */
data class SpkCardRegions(
    val page: TemplateRect,
    val spkNumber: TemplateRect,
    val revision: TemplateRect,
    val styleClient: TemplateRect,
    val qr: TemplateRect,
    val humanCode: TemplateRect,
    /** Tepat [SpkCardLayout.IDENTITY_LINES] baris: client, size, jumlah, tahap, deadline. */
    val identityLines: List<TemplateRect>,
    val pomTitle: TemplateRect,
    val pomGrid: List<SpkCardGridRow>,
    val pomOverflow: TemplateRect?,
    val pomOverflowCount: Int,
    val samplingTitle: TemplateRect,
    val samplingGrid: List<SpkCardGridRow>,
    val samplingOverflow: TemplateRect?,
    val samplingOverflowCount: Int,
    /** Kiri: kode warna benang; kanan: tanggal cetak. */
    val colorwayLine: TemplateRect,
    val urgencyStrip: TemplateRect
)

data class SpkCardPage(
    val sizeLabel: String,
    val regions: SpkCardRegions
)

data class SpkCardSheet(
    val content: SpkCardContent,
    val pages: List<SpkCardPage>
)

/**
 * Geometri Kartu SPK A6 (105 × 148 mm, potret).
 *
 * Kartu ini jalan di printer kantor yang sama dengan kartu telusur, jadi margin 4,5 mm — dan QR
 * 30 mm sengaja di atas ambang 25 mm kartu bundel: kartu SPK menempel di dinding meja dan
 * dipindai dari sudut miring, bukan di atas meja rata.
 *
 * Tabel ukuran memakai **grid dua kolom** (dua pasang `label : nilai` per baris) supaya belasan
 * titik ukur hasil sampling muat tanpa mengecilkan font di bawah batas keterbacaan. Baris yang
 * melebihi cap tidak pernah terpotong diam-diam — ia jadi baris ringkasan yang menunjuk ke lembar
 * kerja rajut, sehingga jumlah baris bebas dari lembar CAM tidak pernah merusak halaman.
 */
object SpkCardLayout {

    const val PAGE_WIDTH = 1050
    const val PAGE_HEIGHT = 1480

    /** Margin tidak tercetak printer kantor (4,5 mm — sama dengan kartu telusur). */
    const val MARGIN = 45

    const val QR_SIZE = 300

    /** Strip urgensi di tepi bawah: warna + teks level, tetap terbaca saat dicetak hitam-putih. */
    const val STRIP_HEIGHT = 60

    /** Tinggi satu baris grid / baris identitas. */
    const val ROW_PITCH = 45

    /** Client, size, jumlah, tahap, deadline. */
    const val IDENTITY_LINES = 5

    /** Cap grid POM client: 4 baris × 2 = 8 nilai ukur. */
    const val MAX_POM_GRID_ROWS = 4

    /** Cap grid hasil sampling: 8 baris × 2 = 16 titik ukur. */
    const val MAX_SAMPLING_GRID_ROWS = 8

    fun solve(content: SpkCardContent): SpkCardSheet =
        SpkCardSheet(content, content.cards.map { SpkCardPage(it.sizeLabel, regionsFor(it)) })

    private fun regionsFor(card: SpkSizeCard): SpkCardRegions {
        val page = TemplateRect(Mm10(0), Mm10(0), Mm10(PAGE_WIDTH), Mm10(PAGE_HEIGHT))
        val x = Mm10(MARGIN)
        val right = PAGE_WIDTH - MARGIN

        val spkNumber = TemplateRect(x, Mm10(45), Mm10(right - MARGIN - 180), Mm10(80))
        val revision = TemplateRect(Mm10(right - 170), Mm10(50), Mm10(170), Mm10(70))
        val styleClient = TemplateRect(x, Mm10(130), Mm10(right - MARGIN), Mm10(ROW_PITCH))

        val qr = TemplateRect(Mm10(right - QR_SIZE), Mm10(185), Mm10(QR_SIZE), Mm10(QR_SIZE))
        val humanCode = TemplateRect(Mm10(right - QR_SIZE), Mm10(185 + QR_SIZE + 10), Mm10(QR_SIZE), Mm10(ROW_PITCH))

        val identityWidth = right - QR_SIZE - 20 - MARGIN
        val identityLines = (0 until IDENTITY_LINES).map { index ->
            TemplateRect(x, Mm10(190 + index * ROW_PITCH), Mm10(identityWidth), Mm10(ROW_PITCH))
        }

        // QR + kode manusia menjulur sampai 540; section ukuran mulai setelahnya.
        var y = 185 + QR_SIZE + 10 + ROW_PITCH + 15

        val pomTitle = TemplateRect(x, Mm10(y), Mm10(right - MARGIN), Mm10(ROW_PITCH))
        y += ROW_PITCH + 5
        val pomValues = card.pomRows.size
        val pomGrid = gridRows(card.pomRows.take(MAX_POM_GRID_ROWS * 2), MARGIN, right, y)
        y += pomGrid.size * ROW_PITCH + 5
        val pomOverflowCount = (pomValues - MAX_POM_GRID_ROWS * 2).coerceAtLeast(0)
        val pomOverflow = overflowRect(pomOverflowCount, MARGIN, right, y)?.also { y += ROW_PITCH + 5 }

        val samplingTitle = TemplateRect(x, Mm10(y), Mm10(right - MARGIN), Mm10(ROW_PITCH))
        y += ROW_PITCH + 5
        val samplingValues = card.samplingRows.size
        val samplingGrid = gridRows(card.samplingRows.take(MAX_SAMPLING_GRID_ROWS * 2), MARGIN, right, y)
        y += samplingGrid.size * ROW_PITCH + 5
        val samplingOverflowCount = (samplingValues - MAX_SAMPLING_GRID_ROWS * 2).coerceAtLeast(0)
        val samplingOverflow = overflowRect(samplingOverflowCount, MARGIN, right, y)?.also { y += ROW_PITCH + 10 }

        val colorwayLine = TemplateRect(x, Mm10(y), Mm10(right - MARGIN), Mm10(ROW_PITCH))
        y += ROW_PITCH

        val stripTop = PAGE_HEIGHT - MARGIN - STRIP_HEIGHT
        require(y <= stripTop) {
            "Kartu SPK melebihi lebar kertas: konten sampai $y, strip urgensi mulai $stripTop — " +
                "cap baris grid harus diturunkan, bukan membiarkan halaman meluap"
        }

        return SpkCardRegions(
            page = page,
            spkNumber = spkNumber,
            revision = revision,
            styleClient = styleClient,
            qr = qr,
            humanCode = humanCode,
            identityLines = identityLines,
            pomTitle = pomTitle,
            pomGrid = pomGrid,
            pomOverflow = pomOverflow,
            pomOverflowCount = pomOverflowCount,
            samplingTitle = samplingTitle,
            samplingGrid = samplingGrid,
            samplingOverflow = samplingOverflow,
            samplingOverflowCount = samplingOverflowCount,
            colorwayLine = colorwayLine,
            urgencyStrip = TemplateRect(x, Mm10(stripTop), Mm10(right - MARGIN), Mm10(STRIP_HEIGHT))
        )
    }

    private fun gridRows(rows: List<SpkMeasurementRow>, x: Int, right: Int, startY: Int): List<SpkCardGridRow> =
        rows.chunked(2).mapIndexed { index, pair ->
            val y = startY + index * ROW_PITCH
            SpkCardGridRow(
                left = TemplateRect(Mm10(x), Mm10(y), Mm10((right - MARGIN) / 2 - 10), Mm10(ROW_PITCH)),
                right = TemplateRect(Mm10(x + (right - MARGIN) / 2 + 10), Mm10(y), Mm10((right - MARGIN) / 2 - 10), Mm10(ROW_PITCH))
            )
        }

    private fun overflowRect(count: Int, x: Int, right: Int, y: Int): TemplateRect? =
        if (count <= 0) null
        else TemplateRect(Mm10(x), Mm10(y), Mm10(right - MARGIN), Mm10(ROW_PITCH))
}