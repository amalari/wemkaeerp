package com.eventverse.app.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun plusAndMinus_sameCurrency_shouldSucceed() {
        val m1 = Money.idr(100_000)
        val m2 = Money.idr(45_000)

        val sum = m1 + m2
        assertEquals(14_500_000L, sum.minorUnits) // Rp 145.000,00

        val diff = m1 - m2
        assertEquals(5_500_000L, diff.minorUnits) // Rp 55.000,00
    }

    @Test
    fun plus_differentCurrency_shouldThrow() {
        val m1 = Money.idr(100_000)
        val m2 = Money(1000L, CurrencyCode.USD)
        assertFailsWith<IllegalArgumentException> {
            m1 + m2
        }
    }

    @Test
    fun allocate_threeWays_shouldNotLoseOnePenny() {
        // Rp 100 divided 3 ways (weights 1, 1, 1) -> 10000 minor units / 3
        val m = Money.idr(100) // 10000 minor units
        val allocated = m.allocate(listOf(1L, 1L, 1L))

        assertEquals(3, allocated.size)
        val sumMinor = allocated.sumOf { it.minorUnits }
        assertEquals(10_000L, sumMinor) // Total must equal exactly 10000

        // Two shares get 3333, one share gets 3334 (sum = 10000)
        assertEquals(3334L, allocated[0].minorUnits)
        assertEquals(3333L, allocated[1].minorUnits)
        assertEquals(3333L, allocated[2].minorUnits)
    }

    @Test
    fun timesRatio_shouldApplyRounding() {
        val price = Money.idr(100_000) // 10,000,000 minor
        val margin = Ratio.percent(15.5) // 15.5%
        val profit = price.times(margin, Rounding.HALF_UP)
        assertEquals(1_550_000L, profit.minorUnits) // Rp 15.500,00
    }
}
