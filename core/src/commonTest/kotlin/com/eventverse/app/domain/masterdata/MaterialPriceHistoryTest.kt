package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaterialPriceHistoryTest {

    private val tenantId = TenantId("ten-demo-001")
    private val materialId = MaterialId("mat-yarn-1")

    private val t1 = Instant.parse("2026-01-01T00:00:00Z")
    private val t2 = Instant.parse("2026-02-01T00:00:00Z")
    private val tFuture = Instant.parse("2026-04-01T00:00:00Z")

    private fun price(id: String, amountIdr: Long, effectiveFrom: Instant) = MaterialPrice(
        id = MaterialPriceId(id),
        tenantId = tenantId,
        materialId = materialId,
        unitPrice = UnitPrice(Money.idr(amountIdr), Quantity.kilograms(1.0)),
        source = PriceSource.STANDARD,
        effectiveFrom = effectiveFrom,
        recordedAt = t1
    )

    @Test
    fun priceAt_pointInTimeResolution_shouldSelectLatestEffectivePrice() {
        val history = MaterialPriceHistory(
            materialId = materialId,
            entries = listOf(
                price("p1", 135_000, t1),
                price("p2", 145_000, t2),
                price("p3", 152_000, tFuture)
            )
        )

        // Before t1 -> null
        assertNull(history.priceAt(Instant.parse("2025-12-31T00:00:00Z")))

        // At t1 -> p1 (135.000)
        assertEquals(135_000_00L, history.priceAt(t1)?.unitPrice?.amount?.minorUnits)

        // Between t2 and tFuture (e.g. 2026-03-01) -> p2 (145.000)
        val march = Instant.parse("2026-03-01T00:00:00Z")
        assertEquals(145_000_00L, history.priceAt(march)?.unitPrice?.amount?.minorUnits)

        // At tFuture -> p3 (152.000)
        assertEquals(152_000_00L, history.priceAt(tFuture)?.unitPrice?.amount?.minorUnits)
    }

    @Test
    fun futurePrices_shouldFilterEntriesAheadOfCurrentInstant() {
        val history = MaterialPriceHistory(
            materialId = materialId,
            entries = listOf(
                price("p1", 145_000, t1),
                price("p2", 152_000, tFuture)
            )
        )

        val futures = history.futurePrices(t1)
        assertEquals(1, futures.size)
        assertEquals(MaterialPriceId("p2"), futures.first().id)
    }
}
