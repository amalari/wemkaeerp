package com.eventverse.app.domain.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatterTest {

    @Test
    fun numberStyle_fromLanguageCode() {
        assertEquals(NumberStyle.INDONESIAN, NumberStyle.fromLanguageCode(LanguageCode.ID))
        assertEquals(NumberStyle.ENGLISH, NumberStyle.fromLanguageCode(LanguageCode.EN))
    }

    @Test
    fun numberStyle_default() {
        assertEquals(NumberStyle.INDONESIAN, NumberStyle.default())
    }

    @Test
    fun formatDecimal_indonesian() {
        assertEquals("1.234", NumberFormatter.formatDecimal(1234.0, NumberStyle.INDONESIAN, 0))
        assertEquals("1.234.567", NumberFormatter.formatDecimal(1234567.0, NumberStyle.INDONESIAN, 0))
        assertEquals("100", NumberFormatter.formatDecimal(100.0, NumberStyle.INDONESIAN, 0))
    }

    @Test
    fun formatDecimal_english() {
        assertEquals("1,234", NumberFormatter.formatDecimal(1234.0, NumberStyle.ENGLISH, 0))
        assertEquals("1,234,567", NumberFormatter.formatDecimal(1234567.0, NumberStyle.ENGLISH, 0))
        assertEquals("100", NumberFormatter.formatDecimal(100.0, NumberStyle.ENGLISH, 0))
    }

    @Test
    fun formatCurrency_idr_indonesian() {
        assertEquals("Rp 1.234", NumberFormatter.formatCurrency(1234, NumberStyle.INDONESIAN, "IDR"))
        assertEquals("Rp 1.234.567", NumberFormatter.formatCurrency(1234567, NumberStyle.INDONESIAN, "IDR"))
        assertEquals("Rp 0", NumberFormatter.formatCurrency(0, NumberStyle.INDONESIAN, "IDR"))
    }

    @Test
    fun formatCurrency_usd_english() {
        assertEquals("USD 1,234", NumberFormatter.formatCurrency(1234, NumberStyle.ENGLISH, "USD"))
        assertEquals("USD 1,234,567", NumberFormatter.formatCurrency(1234567, NumberStyle.ENGLISH, "USD"))
    }

    @Test
    fun formatPercent_indonesian() {
        assertEquals("12,5 %", NumberFormatter.formatPercent(12.5, NumberStyle.INDONESIAN, 1))
        assertEquals("100 %", NumberFormatter.formatPercent(100.0, NumberStyle.INDONESIAN, 1))
        assertEquals("0,25 %", NumberFormatter.formatPercent(0.25, NumberStyle.INDONESIAN, 2))
    }

    @Test
    fun formatPercent_english() {
        assertEquals("12.5 %", NumberFormatter.formatPercent(12.5, NumberStyle.ENGLISH, 1))
        assertEquals("100 %", NumberFormatter.formatPercent(100.0, NumberStyle.ENGLISH, 1))
        assertEquals("0.25 %", NumberFormatter.formatPercent(0.25, NumberStyle.ENGLISH, 2))
    }

    @Test
    fun formatDecimal_withDecimals_indonesian() {
        assertEquals("1.234,5", NumberFormatter.formatDecimal(1234.5, NumberStyle.INDONESIAN, 1))
        assertEquals("1.234.567,89", NumberFormatter.formatDecimal(1234567.89, NumberStyle.INDONESIAN, 2))
    }

    @Test
    fun formatDecimal_withDecimals_english() {
        assertEquals("1,234.5", NumberFormatter.formatDecimal(1234.5, NumberStyle.ENGLISH, 1))
        assertEquals("1,234,567.89", NumberFormatter.formatDecimal(1234567.89, NumberStyle.ENGLISH, 2))
    }
}
