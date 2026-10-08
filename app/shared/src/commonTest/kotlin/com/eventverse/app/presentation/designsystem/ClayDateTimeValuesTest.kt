package com.eventverse.app.presentation.designsystem

import com.eventverse.app.domain.prototype.DateFieldValues
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClayDateTimeValuesTest {
    @Test
    fun parse_validBoundaries() {
        assertNotNull(parseIsoDateTimeOrNull("2026-10-08T14:30"))
        assertNotNull(parseIsoDateTimeOrNull("2026-10-08T00:00"))
        assertNotNull(parseIsoDateTimeOrNull("2026-10-08T23:59"))
        assertNotNull(parseIsoDateTimeOrNull("2024-02-29T12:00"))
    }

    @Test
    fun parse_rejectsInvalidShapes() {
        listOf(
            "", "2026-10-08", "2026-10-08T24:00", "2026-10-08T23:60", "2026-10-08T14:30:00", "2026-10-08T14:30Z",
            "2026-10-08 14:30", "2026-10-08T1:30", "2026-02-29T10:00", "2026-13-01T10:00", "bukan-waktu"
        ).forEach { assertNull(parseIsoDateTimeOrNull(it), "harus ditolak: '$it'") }
    }

    @Test
    fun validity_blankIsValid_otherwiseStrict() {
        assertTrue(isValidIsoDateTime(""))
        assertTrue(isValidIsoDateTime("2026-10-08T14:30"))
        assertFalse(isValidIsoDateTime("2026-10-08"))
    }

    @Test
    fun format_padsAndRoundTrips() {
        val date = LocalDate(2026, 10, 8)
        assertEquals("2026-10-08T09:05", formatIsoDateTime(date, 9, 5))
        assertEquals("2026-10-08T00:00", formatIsoDateTime(date, 0, 0))
        assertEquals("2026-10-08T23:59", formatIsoDateTime(date, 23, 59))
        for (h in 0 until 24) for (m in 0 until 60) {
            val s = formatIsoDateTime(date, h, m)
            val back = requireNotNull(parseIsoDateTimeOrNull(s))
            assertEquals(h, back.hour)
            assertEquals(m, back.minute)
            assertEquals(date, back.date)
        }
        assertFailsWith<IllegalArgumentException> { formatIsoDateTime(date, 24, 0) }
        assertFailsWith<IllegalArgumentException> { formatIsoDateTime(date, 0, 60) }
        assertFailsWith<IllegalArgumentException> { formatIsoDateTime(date, -1, 0) }
    }

    @Test
    fun stepCyclic_wrapsBothDirections() {
        assertEquals(0, stepCyclic(23, 1, 24))
        assertEquals(23, stepCyclic(0, -1, 24))
        assertEquals(3, stepCyclic(58, 5, 60))
        assertEquals(57, stepCyclic(2, -5, 60))
        assertEquals(30, stepCyclic(30, 0, 60))
    }

    @Test
    fun display_validGetsSpace_invalidKeptAsIs() {
        assertEquals("2026-10-08 14:30", displayIsoDateTime("2026-10-08T14:30"))
        assertEquals("2026-10-08", displayIsoDateTime("2026-10-08"))
        assertEquals("rusak", displayIsoDateTime("rusak"))
        assertEquals("", displayIsoDateTime(""))
        assertTrue(displayIsoDateTime("2026-10-08T14:30").all { it.code < 128 })
    }

    @Test
    fun parity_withCoreDateFieldValues() {
        val samples = listOf(
            "2026-10-08T14:30", "2026-10-08T00:00", "2026-10-08T23:59", "2026-10-08T24:00", "2026-10-08T23:60",
            "2026-10-08", "2026-10-08T14:30:00", "2026-10-08T14:30Z", "2026-02-29T10:00", "2026-10-08 14:30", "x"
        )
        samples.forEach {
            assertEquals(DateFieldValues.isValid(it, withTime = true), parseIsoDateTimeOrNull(it) != null, "paritas '$it'")
        }
        assertTrue(parseIsoDateTimeOrNull(DateFieldValues.sample(withTime = true)) != null)
    }
}
