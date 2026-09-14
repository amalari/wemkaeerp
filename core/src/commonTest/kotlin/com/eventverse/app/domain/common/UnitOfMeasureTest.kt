package com.eventverse.app.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class UnitOfMeasureTest {

    @Test
    fun canConvertTo_sameDimension_shouldReturnTrue() {
        assertTrue(UnitOfMeasure.GRAM.canConvertTo(UnitOfMeasure.KILOGRAM))
        assertTrue(UnitOfMeasure.KILOGRAM.canConvertTo(UnitOfMeasure.GRAM))
        assertTrue(UnitOfMeasure.METER.canConvertTo(UnitOfMeasure.YARD))
        assertTrue(UnitOfMeasure.PIECE.canConvertTo(UnitOfMeasure.LUSIN))
    }

    @Test
    fun canConvertTo_differentDimension_shouldReturnFalse() {
        assertFalse(UnitOfMeasure.GRAM.canConvertTo(UnitOfMeasure.METER))
        assertFalse(UnitOfMeasure.PIECE.canConvertTo(UnitOfMeasure.KILOGRAM))
        assertFalse(UnitOfMeasure.MINUTE.canConvertTo(UnitOfMeasure.INCH))
    }

    @Test
    fun packagingUnits_canConvertTo_shouldReturnFalseForOthers() {
        assertFalse(UnitOfMeasure.CONE.canConvertTo(UnitOfMeasure.KILOGRAM))
        assertFalse(UnitOfMeasure.KILOGRAM.canConvertTo(UnitOfMeasure.CONE))
        assertFalse(UnitOfMeasure.ROLL.canConvertTo(UnitOfMeasure.METER))
        assertTrue(UnitOfMeasure.CONE.canConvertTo(UnitOfMeasure.CONE))
    }

    @Test
    fun packagingUnits_toBaseMicros_shouldThrow() {
        assertFailsWith<IllegalArgumentException> {
            UnitOfMeasure.CONE.toBaseMicros(1_000_000L)
        }
    }

    @Test
    fun massConversion_gramToKgAndBack_shouldBeExact() {
        // 2500 grams in micros
        val gramsMicros = 2_500_000_000L
        val baseMicros = UnitOfMeasure.GRAM.toBaseMicros(gramsMicros)
        assertEquals(2_500_000_000L, baseMicros)

        val kgMicros = UnitOfMeasure.KILOGRAM.fromBaseMicros(baseMicros)
        assertEquals(2_500_000L, kgMicros) // 2.5 kg

        val backToGram = UnitOfMeasure.GRAM.fromBaseMicros(UnitOfMeasure.KILOGRAM.toBaseMicros(kgMicros))
        assertEquals(gramsMicros, backToGram)
    }

    @Test
    fun fromCode_caseInsensitive_shouldResolveCorrectly() {
        assertEquals(UnitOfMeasure.KILOGRAM, UnitOfMeasure.fromCode("kg"))
        assertEquals(UnitOfMeasure.KILOGRAM, UnitOfMeasure.fromCode("KG"))
        assertEquals(UnitOfMeasure.KILOGRAM, UnitOfMeasure.fromCode("kilogram"))
        assertEquals(UnitOfMeasure.CONE, UnitOfMeasure.fromCode("cone"))
    }
}
