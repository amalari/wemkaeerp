package com.eventverse.app.domain.traceability.print

import com.eventverse.app.domain.printing.Mm10
import com.eventverse.app.domain.printing.PaperSize
import com.eventverse.app.domain.printing.TemplateRect
import com.eventverse.app.domain.traceability.TraceAllocationPlan
import com.eventverse.app.domain.traceability.TraceLabelPlan
import com.eventverse.app.domain.traceability.TraceTier

/**
 * Ukuran kartu dan grid A4-nya.
 *
 * Angkanya bukan hasil membagi 210x297 begitu saja: printer kantor tidak bisa mencetak sampai tepi
 * (sekitar 5 mm mati di tiap sisi), jadi grid dihitung di dalam area aman 200 x 287 mm. Kartu yang
 * dipaskan ke ukuran kertas penuh akan kehilangan tepinya — dan yang hilang biasanya justru sudut
 * tempat QR berada.
 */
enum class TraceLabelSheetSpec(
    val tier: TraceTier,
    val cardWidth: Int,
    val cardHeight: Int,
    val columns: Int,
    val rows: Int,
    val qrSize: Int
) {
    /** 100 x 70 mm, 8 kartu per lembar. QR 25 mm. */
    BUNDLE_CARD(TraceTier.BUNDLE, cardWidth = 1000, cardHeight = 700, columns = 2, rows = 4, qrSize = 250),

    /** 100 x 140 mm, 4 kartu per lembar. Lebih besar karena memuat daftar bundel induk. QR 30 mm. */
    SACK_CARD(TraceTier.SACK, cardWidth = 1000, cardHeight = 1400, columns = 2, rows = 2, qrSize = 300);

    val cardsPerSheet: Int get() = columns * rows

    companion object {
        /** Margin tidak tercetak pada printer kantor kebanyakan. */
        const val SAFE_MARGIN = 50

        const val CARD_PADDING = 40

        fun forTier(tier: TraceTier): TraceLabelSheetSpec = entries.firstOrNull { it.tier == tier }
            ?: error("${tier.displayName} tidak dicetak sebagai kartu potong — ia punya lembarnya sendiri")
    }
}

/**
 * Bidang-bidang di dalam satu kartu.
 *
 * Dihitung di domain, bukan di renderer, supaya posisi yang sama bisa diuji tanpa membuka PDF — dan
 * supaya kalau nanti ada pratinjau di layar, ia memakai angka yang sama persis dengan hasil cetak.
 */
data class TraceLabelRegions(
    val card: TemplateRect,
    val qr: TemplateRect,
    /** Kode berkelompok empat, dicetak besar tepat di bawah QR agar bisa diketik ulang. */
    val humanCode: TemplateRect,
    /** Baris identitas manusiawi: nomor SPK, size, nomor urut kartu. */
    val caption: TemplateRect,
    /** Ruang tulis tangan: hitungan panel untuk bundel, daftar induk & timbangan untuk karung. */
    val writingArea: TemplateRect
)

data class LaidOutTraceLabel(
    val plan: TraceLabelPlan,
    val pageIndex: Int,
    val regions: TraceLabelRegions
)

data class TraceLabelSheet(
    val spec: TraceLabelSheetSpec,
    val paper: PaperSize,
    val labels: List<LaidOutTraceLabel>
) {
    val pageCount: Int get() = (labels.maxOfOrNull { it.pageIndex } ?: -1) + 1
}

/**
 * Menyusun kartu ke dalam lembar A4.
 *
 * Kartu diisi baris demi baris dari kiri atas — arah yang sama dengan cara orang memotongnya dengan
 * penggaris, dan cara nomor urutnya dibaca saat setumpuk kartu dibagikan ke operator.
 */
object TraceLabelSheetLayout {

    fun solve(
        labels: List<TraceLabelPlan>,
        spec: TraceLabelSheetSpec,
        paper: PaperSize = PaperSize.A4
    ): TraceLabelSheet {
        require(fitsOnPaper(spec, paper)) {
            "Grid ${spec.name} tidak muat di ${paper.displayName} setelah margin aman diperhitungkan"
        }
        val laidOut = labels.mapIndexed { index, plan ->
            val slot = index % spec.cardsPerSheet
            val column = slot % spec.columns
            val row = slot / spec.columns

            val card = TemplateRect(
                x = Mm10(TraceLabelSheetSpec.SAFE_MARGIN + column * spec.cardWidth),
                y = Mm10(TraceLabelSheetSpec.SAFE_MARGIN + row * spec.cardHeight),
                width = Mm10(spec.cardWidth),
                height = Mm10(spec.cardHeight)
            )
            LaidOutTraceLabel(plan, index / spec.cardsPerSheet, regionsFor(card, spec))
        }
        return TraceLabelSheet(spec, paper, laidOut)
    }

    fun solvePlan(plan: TraceAllocationPlan, tier: TraceTier, sizeLabel: String? = null): TraceLabelSheet =
        solve(plan.labelsFor(tier, sizeLabel), TraceLabelSheetSpec.forTier(tier))

    fun fitsOnPaper(spec: TraceLabelSheetSpec, paper: PaperSize): Boolean {
        val usableWidth = paper.widthMm10 - 2 * TraceLabelSheetSpec.SAFE_MARGIN
        val usableHeight = paper.heightMm10 - 2 * TraceLabelSheetSpec.SAFE_MARGIN
        return spec.columns * spec.cardWidth <= usableWidth && spec.rows * spec.cardHeight <= usableHeight
    }

    private fun regionsFor(card: TemplateRect, spec: TraceLabelSheetSpec): TraceLabelRegions {
        val pad = TraceLabelSheetSpec.CARD_PADDING
        val innerX = card.x.value + pad
        val innerY = card.y.value + pad
        val innerWidth = spec.cardWidth - 2 * pad

        val qr = TemplateRect(Mm10(innerX), Mm10(innerY), Mm10(spec.qrSize), Mm10(spec.qrSize))

        // Kode dicetak tepat di bawah QR, selebar QR-nya: saat label sobek, dua hal yang paling harus
        // selamat bersama adalah kode dan QR-nya, jadi keduanya dijaga berdekatan.
        val humanCodeHeight = 45
        val humanCode = TemplateRect(
            Mm10(innerX), Mm10(innerY + spec.qrSize + 10), Mm10(spec.qrSize), Mm10(humanCodeHeight)
        )

        val captionX = innerX + spec.qrSize + 50
        val caption = TemplateRect(
            Mm10(captionX), Mm10(innerY), Mm10((innerX + innerWidth - captionX).coerceAtLeast(0)), Mm10(220)
        )

        val writingTop = innerY + spec.qrSize + 10 + humanCodeHeight + 20
        val writingArea = TemplateRect(
            Mm10(innerX),
            Mm10(writingTop),
            Mm10(innerWidth),
            Mm10((card.y.value + spec.cardHeight - pad - writingTop).coerceAtLeast(0))
        )
        return TraceLabelRegions(card, qr, humanCode, caption, writingArea)
    }
}
