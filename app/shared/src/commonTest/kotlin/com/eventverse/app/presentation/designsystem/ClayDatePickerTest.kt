package com.eventverse.app.presentation.designsystem

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClayDatePickerTest {

    @Test
    fun testIsValidIsoDate_validAndBlank() {
        assertTrue(isValidIsoDate(""), "String kosong harus sah (belum dipilih)")
        assertTrue(isValidIsoDate("   "), "String spasi harus sah")
        assertTrue(isValidIsoDate("2026-10-08"), "Format ISO YYYY-MM-DD standar harus sah")
        assertTrue(isValidIsoDate("2024-02-29"), "Tahun kabisat 29 Feb harus sah")
        assertTrue(isValidIsoDate("2000-02-29"), "Tahun abad kabisat 2000 harus sah")
    }

    @Test
    fun testIsValidIsoDate_invalidDates() {
        assertFalse(isValidIsoDate("2026-02-29"), "29 Feb pada tahun bukan kabisat harus tidak sah")
        assertFalse(isValidIsoDate("2026-13-01"), "Bulan 13 harus tidak sah")
        assertFalse(isValidIsoDate("2026-00-10"), "Bulan 0 harus tidak sah")
        assertFalse(isValidIsoDate("2026-10-32"), "Hari 32 harus tidak sah")
        assertFalse(isValidIsoDate("bukan-tanggal"), "Teks acak harus tidak sah")
        assertFalse(isValidIsoDate("2026/10/08"), "Pemisah slash harus tidak sah")
        assertFalse(isValidIsoDate("2026-1-8"), "Format tanpa padding dua digit harus tidak sah")
    }

    @Test
    fun testParseIsoDateOrNull() {
        val parsed = parseIsoDateOrNull("2026-10-08")
        assertNotNull(parsed)
        assertEquals(2026, parsed.year)
        assertEquals(10, parsed.monthNumber)
        assertEquals(8, parsed.dayOfMonth)

        assertNull(parseIsoDateOrNull(""), "Kosong harus bernilai null")
        assertNull(parseIsoDateOrNull("   "), "Blank harus bernilai null")
        assertNull(parseIsoDateOrNull("invalid"), "Format rusak harus bernilai null")
        assertNull(parseIsoDateOrNull("2026-02-29"), "Tanggal tidak ada harus bernilai null")
    }

    @Test
    fun testDaysInMonth_gregorianRules() {
        // Bulan 31 hari
        assertEquals(31, daysInMonth(2026, 1))
        assertEquals(31, daysInMonth(2026, 3))
        assertEquals(31, daysInMonth(2026, 5))
        assertEquals(31, daysInMonth(2026, 7))
        assertEquals(31, daysInMonth(2026, 8))
        assertEquals(31, daysInMonth(2026, 10))
        assertEquals(31, daysInMonth(2026, 12))

        // Bulan 30 hari
        assertEquals(30, daysInMonth(2026, 4))
        assertEquals(30, daysInMonth(2026, 6))
        assertEquals(30, daysInMonth(2026, 9))
        assertEquals(30, daysInMonth(2026, 11))

        // Februari kabisat vs non-kabisat
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(29, daysInMonth(2000, 2))
        assertEquals(28, daysInMonth(1900, 2))
    }

    @Test
    fun testIsLeapYear_divisibilityRules() {
        assertTrue(isLeapYear(2024), "Habis dibagi 4")
        assertFalse(isLeapYear(2026), "Tidak habis dibagi 4")
        assertTrue(isLeapYear(2000), "Habis dibagi 400")
        assertFalse(isLeapYear(1900), "Habis dibagi 100 tapi tidak 400")
    }

    @Test
    fun testDayOfWeekOffset() {
        assertEquals(0, dayOfWeekOffset(DayOfWeek.MONDAY))
        assertEquals(1, dayOfWeekOffset(DayOfWeek.TUESDAY))
        assertEquals(2, dayOfWeekOffset(DayOfWeek.WEDNESDAY))
        assertEquals(3, dayOfWeekOffset(DayOfWeek.THURSDAY))
        assertEquals(4, dayOfWeekOffset(DayOfWeek.FRIDAY))
        assertEquals(5, dayOfWeekOffset(DayOfWeek.SATURDAY))
        assertEquals(6, dayOfWeekOffset(DayOfWeek.SUNDAY))
    }

    @Test
    fun testMonthNameIndonesian() {
        assertEquals("Januari", monthNameIndonesian(1))
        assertEquals("Oktober", monthNameIndonesian(10))
        assertEquals("Desember", monthNameIndonesian(12))
        assertEquals("", monthNameIndonesian(0))
        assertEquals("", monthNameIndonesian(13))
    }

    @Test
    fun testFormatIsoDate() {
        val date = LocalDate(2026, 10, 8)
        assertEquals("2026-10-08", formatIsoDate(date))
    }
}
