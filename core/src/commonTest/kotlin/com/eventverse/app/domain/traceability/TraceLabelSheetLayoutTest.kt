package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.printing.PaperSize
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.print.TraceLabelSheetLayout
import com.eventverse.app.domain.traceability.print.TraceLabelSheetSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceLabelSheetLayoutTest {

    private val plan = TraceAllocationPlan.plan(
        TraceWorkOrderSnapshot(
            ref = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, "smp_1"),
            tenantId = TenantId("tnt-1"),
            tenantOrdinal = 1,
            ordinal = 9,
            spkNumber = "SPK-SMP-0009",
            styleName = "Cardigan",
            clientName = "PT Buyer",
            sizes = listOf(TraceSizeLine("L", 300)),
            panelRequirements = listOf(PanelRequirement(GarmentPanel.BODY_FRONT, 1))
        ),
        setsPerBundle = 20, pcsPerSack = 60, sparePerSize = 0
    )

    @Test
    fun `both card grids fit inside the printable area of A4`() {
        TraceLabelSheetSpec.entries.forEach { spec ->
            assertTrue(
                TraceLabelSheetLayout.fitsOnPaper(spec, PaperSize.A4),
                "${spec.name} harus muat di A4 setelah margin aman"
            )
        }
    }

    @Test
    fun `fifteen bundle cards spill onto two sheets of eight`() {
        val sheet = TraceLabelSheetLayout.solvePlan(plan, TraceTier.BUNDLE, "L")
        assertEquals(15, sheet.labels.size)
        assertEquals(8, sheet.spec.cardsPerSheet)
        assertEquals(2, sheet.pageCount)
        assertEquals(0, sheet.labels[7].pageIndex)
        assertEquals(1, sheet.labels[8].pageIndex)
    }

    @Test
    fun `cards fill row by row from the top left`() {
        val sheet = TraceLabelSheetLayout.solvePlan(plan, TraceTier.BUNDLE, "L")
        val first = sheet.labels[0].regions.card
        val second = sheet.labels[1].regions.card
        val third = sheet.labels[2].regions.card

        assertEquals(first.y, second.y, "Kartu kedua harus sebaris dengan yang pertama")
        assertTrue(second.x.value > first.x.value)
        assertEquals(first.x, third.x, "Kartu ketiga turun ke baris berikutnya di kolom pertama")
        assertTrue(third.y.value > first.y.value)
    }

    @Test
    fun `the first card of every page starts at the same origin`() {
        val sheet = TraceLabelSheetLayout.solvePlan(plan, TraceTier.BUNDLE, "L")
        assertEquals(sheet.labels[0].regions.card, sheet.labels[8].regions.card)
    }

    @Test
    fun `no card region escapes the paper`() {
        listOf(TraceTier.BUNDLE, TraceTier.SACK).forEach { tier ->
            val sheet = TraceLabelSheetLayout.solvePlan(plan, tier, "L")
            sheet.labels.forEach { laid ->
                val r = laid.regions
                assertTrue(r.card.right.value <= PaperSize.A4.widthMm10, "Kartu melebihi lebar kertas")
                assertTrue(r.card.bottom.value <= PaperSize.A4.heightMm10, "Kartu melebihi tinggi kertas")
                listOf(r.qr, r.humanCode, r.caption, r.writingArea).forEach { inner ->
                    assertTrue(inner.x.value >= r.card.x.value, "Bidang keluar dari sisi kiri kartu")
                    assertTrue(inner.right.value <= r.card.right.value, "Bidang keluar dari sisi kanan kartu")
                    assertTrue(inner.bottom.value <= r.card.bottom.value, "Bidang keluar dari bawah kartu")
                }
            }
        }
    }

    @Test
    fun `qr is printed at the documented physical size`() {
        val bundle = TraceLabelSheetLayout.solvePlan(plan, TraceTier.BUNDLE, "L").labels.first()
        assertEquals(25.0, bundle.regions.qr.width.toMillimeters())

        val sack = TraceLabelSheetLayout.solvePlan(plan, TraceTier.SACK, "L").labels.first()
        assertEquals(30.0, sack.regions.qr.width.toMillimeters())
    }

    @Test
    fun `sack cards are taller because they must list their parent bundles`() {
        val bundle = TraceLabelSheetSpec.BUNDLE_CARD
        val sack = TraceLabelSheetSpec.SACK_CARD
        assertTrue(sack.cardHeight > bundle.cardHeight)
        assertTrue(sack.cardsPerSheet < bundle.cardsPerSheet)
    }
}
