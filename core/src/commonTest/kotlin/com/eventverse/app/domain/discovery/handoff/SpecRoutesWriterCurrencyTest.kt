package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecRoutesWriterCurrencyTest {

    @Test
    fun entityLiteral_currencyField_carriesFormatAndCode() {
        val entity = EntitySpec("paket", "Paket", listOf(
            FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "USD")
        ))
        val literal = SpecRoutesWriter.entityLiteral(entity)
        assertTrue(", NumberFormat.CURRENCY, \"USD\")" in literal, literal)
    }

    @Test
    fun entityLiteral_plainAndPercent_omitCode() {
        val entity = EntitySpec("paket", "Paket", listOf(
            FieldSpec("jumlah", "Jumlah", FieldType.NUMBER),
            FieldSpec("diskon", "Diskon", FieldType.NUMBER, format = NumberFormat.PERCENT)
        ))
        val literal = SpecRoutesWriter.entityLiteral(entity)
        assertTrue(", NumberFormat.PERCENT)" in literal, literal)
        assertFalse("CURRENCY" in literal, literal)
    }
}
