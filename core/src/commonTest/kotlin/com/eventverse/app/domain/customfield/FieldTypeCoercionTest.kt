package com.eventverse.app.domain.customfield

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FieldTypeCoercionTest {

    @Test
    fun classify_identicalType_isIdentity() {
        assertEquals(ConversionSafety.IDENTITY, FieldTypeConversion.classify(FieldType.Text, FieldType.Text))
    }

    @Test
    fun classify_numberToText_isLossless() {
        assertEquals(ConversionSafety.LOSSLESS, FieldTypeConversion.classify(FieldType.Number(), FieldType.Text))
    }

    @Test
    fun classify_textToNumber_isLossy() {
        assertEquals(ConversionSafety.LOSSY, FieldTypeConversion.classify(FieldType.Text, FieldType.Number()))
    }

    @Test
    fun classify_anythingToUserRef_isForbidden() {
        assertEquals(ConversionSafety.FORBIDDEN, FieldTypeConversion.classify(FieldType.Text, FieldType.UserRef()))
        assertEquals(ConversionSafety.FORBIDDEN, FieldTypeConversion.classify(FieldType.UserRef(), FieldType.Text))
    }

    @Test
    fun coerce_textToNumber_validNumericText_converts() {
        val cell = CustomAttributes.textCell("18500000")
        val result = FieldTypeConversion.coerce(cell, FieldType.Text, FieldType.Number())
        assertIs<CoercionResult.Converted>(result)
    }

    @Test
    fun coerce_textToNumber_nonNumericText_clearsWithOrphan() {
        val cell = CustomAttributes.textCell("sekitar 15 juta")
        val result = FieldTypeConversion.coerce(cell, FieldType.Text, FieldType.Number())
        assertIs<CoercionResult.Cleared>(result)
        assertEquals("sekitar 15 juta", (result as CoercionResult.Cleared).orphanedRaw)
    }

    @Test
    fun coerce_textToSingleSelect_matchingLabel_converts() {
        val option = SelectOption(SelectOptionId("opt_kaos"), "Kaos", "#2563EB")
        val cell = CustomAttributes.textCell("Kaos")
        val result = FieldTypeConversion.coerce(cell, FieldType.Text, FieldType.SingleSelect(listOf(option)))
        assertIs<CoercionResult.Converted>(result)
    }

    @Test
    fun canMintSelectOptions_refusesOverFiftyDistinctValues() {
        assertEquals(true, FieldTypeConversion.canMintSelectOptions(50))
        assertEquals(false, FieldTypeConversion.canMintSelectOptions(51))
    }
}
