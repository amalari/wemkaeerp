package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VendorTest {

    private val now = Instant.parse("2026-09-01T00:00:00Z")
    private val sep1 = LocalDate(2026, 9, 1)
    private val oct1 = LocalDate(2026, 10, 1)

    private fun vendor() = Vendor(
        id = VendorId("vnd-1"),
        tenantId = TenantId("ten-demo-001"),
        name = VendorName("CV Sablon Jaya"),
        createdAt = now,
        updatedAt = now
    )

    private fun sablon(price: Long, from: LocalDate, unit: VendorPriceUnit = VendorPriceUnit.PER_PRINT_POINT) =
        VendorServiceRate(serviceCode = "sablon", serviceName = "Sablon Plastisol", priceIdr = price, unit = unit, effectiveFrom = from)

    @Test
    fun `setRate new price on same track should close previous rate at new start date`() {
        val updated = vendor()
            .setRate(sablon(3_500, sep1), now)
            .setRate(sablon(4_000, oct1), now)

        assertEquals(2, updated.rates.size)
        assertEquals(oct1, updated.rates.first { it.priceIdr == 3_500L }.effectiveTo)
        assertEquals(3_500L, updated.ratesFor("SABLON", LocalDate(2026, 9, 30)).single().priceIdr)
        assertEquals(4_000L, updated.ratesFor("SABLON", oct1).single().priceIdr)
    }

    @Test
    fun `setRate same start date should replace entry instead of adding history`() {
        val updated = vendor()
            .setRate(sablon(3_500, sep1), now)
            .setRate(sablon(3_700, sep1), now)

        assertEquals(listOf(3_700L), updated.rates.map { it.priceIdr })
    }

    @Test
    fun `setRate backdated price should be rejected`() {
        val withOct = vendor().setRate(sablon(4_000, oct1), now)
        assertFailsWith<IllegalArgumentException> { withOct.setRate(sablon(3_500, sep1), now) }
    }

    @Test
    fun `setRate different unit should keep an independent price track`() {
        val updated = vendor()
            .setRate(sablon(3_500, sep1), now)
            .setRate(sablon(12_000, oct1, VendorPriceUnit.PER_PIECE), now)

        assertTrue(updated.rates.all { it.isOpenEnded })
        assertEquals(2, updated.ratesFor("sablon", oct1).size)
    }

    @Test
    fun `totalFor each unit should follow vendor billing convention`() {
        assertEquals(700_000L, VendorPriceUnit.PER_PRINT_POINT.totalFor(3_500, 100, unitsPerPiece = 2))
        assertEquals(1_200_000L, VendorPriceUnit.PER_PIECE.totalFor(6_000, 200))
        assertEquals(90_000L, VendorPriceUnit.PER_DOZEN.totalFor(30_000, 25)) // 25 pcs = 3 lusin
        assertEquals(500_000L, VendorPriceUnit.PER_ORDER.totalFor(500_000, 200))
    }

    @Test
    fun `queue pending items should come first ordered by nearest due date`() {
        fun need(id: String, due: LocalDate?) = SubcontractNeed(id, "SPK-$id", "Buyer", "Kaos", "SABLON", "Sablon", 10, due)
        val assigned = VendorAssignment(
            id = VendorAssignmentId("vas-1"), tenantId = TenantId("ten-demo-001"),
            subjectId = "a", subjectLabel = "SPK-a", processCode = "sablon", processName = "Sablon",
            vendorId = VendorId("vnd-1"), vendorName = VendorName("CV Sablon Jaya"),
            pricePerUnitIdr = 3_500, unit = VendorPriceUnit.PER_PRINT_POINT, quantityPcs = 10,
            priceSource = VendorPriceSource.PRICE_LIST, assignedAt = now
        )

        val queue = VendorAssignmentQueue.build(
            needs = listOf(need("a", sep1), need("b", null), need("c", oct1), need("d", sep1)),
            activeAssignments = listOf(assigned)
        )

        assertEquals(listOf("d", "c", "b", "a"), queue.map { it.need.subjectId })
        assertEquals(assigned, queue.last().assignment)
    }
}
