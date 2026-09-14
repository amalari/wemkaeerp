package com.eventverse.app.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals

class UnitPriceTest {

    @Test
    fun costOf_pricePerKgAndQuantityInGrams_shouldNotLosePrecision() {
        // Rp 145.000 / kg (14,500,000 minor units per 1,000,000 micros kg)
        val unitPrice = UnitPrice(
            amount = Money.idr(145_000),
            per = Quantity.kilograms(1.0)
        )

        // 287 grams (287,000,000 micros in gram)
        val garmentWeight = Quantity.grams(287.0)

        // Cost calculation: (14_500_000 * 287_000) / 1_000_000 = 4_161_500 minor units (Rp 41.615)
        val cost = unitPrice.costOf(garmentWeight)
        assertEquals(4_161_500L, cost.minorUnits)
        assertEquals("Rp 41615", cost.formatted())

        // Scale to 3,000 garments order:
        // 3,000 * 287g = 861 kg
        val totalOrderWeight = garmentWeight * 3000
        val totalCost = unitPrice.costOf(totalOrderWeight)
        assertEquals(124_845_000_00L, totalCost.minorUnits) // Rp 124.845.000
    }

    @Test
    fun convertedTo_differentUnitOfMeasure_shouldPreserveUnitValue() {
        val pricePerKg = UnitPrice(
            amount = Money.idr(100_000),
            per = Quantity.kilograms(1.0)
        )

        val pricePerGram = pricePerKg.convertedTo(UnitOfMeasure.GRAM)
        assertEquals(UnitOfMeasure.GRAM, pricePerGram.per.uom)
        // 1 gram cost = Rp 100
        assertEquals(10_000L, pricePerGram.amount.minorUnits)
    }
}
