package com.eventverse.app.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuantityTest {

    @Test
    fun plusAndMinus_sameUom_shouldBeExact() {
        val q1 = Quantity.grams(500.0)
        val q2 = Quantity.grams(250.0)

        val sum = q1 + q2
        assertEquals(750_000_000L, sum.micros)
        assertEquals(UnitOfMeasure.GRAM, sum.uom)

        val diff = q1 - q2
        assertEquals(250_000_000L, diff.micros)
    }

    @Test
    fun plus_convertibleUom_shouldConvertToLeftOperandUom() {
        val q1 = Quantity.kilograms(1.5) // 1.5 kg
        val q2 = Quantity.grams(500.0)   // 0.5 kg

        val sum = q1 + q2
        assertEquals(UnitOfMeasure.KILOGRAM, sum.uom)
        assertEquals(2_000_000L, sum.micros) // 2.0 kg

        val sumInGrams = q2 + q1
        assertEquals(UnitOfMeasure.GRAM, sumInGrams.uom)
        assertEquals(2_000_000_000L, sumInGrams.micros) // 2000 g
    }

    @Test
    fun plus_incompatibleUom_shouldThrow() {
        val q1 = Quantity.kilograms(1.0)
        val q2 = Quantity.pieces(5)
        assertFailsWith<IllegalArgumentException> {
            q1 + q2
        }
    }

    @Test
    fun convertTo_shouldBeExact() {
        val qKg = Quantity.kilograms(2.87)
        val qGram = qKg.convertTo(UnitOfMeasure.GRAM)
        assertEquals(2_870_000_000L, qGram.micros)
        assertEquals(UnitOfMeasure.GRAM, qGram.uom)
    }

    @Test
    fun timesRatio_shouldApplyExactRationalFactor() {
        val qty = Quantity.kilograms(10.0)
        val wasteRatio = Ratio.percent(5.0) // 5%
        val waste = qty * wasteRatio
        assertEquals(500_000L, waste.micros) // 0.5 kg
    }
}
