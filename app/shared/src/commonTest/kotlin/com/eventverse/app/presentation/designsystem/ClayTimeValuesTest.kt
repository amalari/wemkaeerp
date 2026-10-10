package com.eventverse.app.presentation.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClayTimeValuesTest {
    @Test
    fun parse_validBoundaries() {
        assertEquals(ClayTime(0, 0), parseClayTimeOrNull("00:00"))
        assertEquals(ClayTime(14, 30), parseClayTimeOrNull("14:30"))
        assertEquals(ClayTime(23, 59), parseClayTimeOrNull("23:59"))
    }

    @Test
    fun parse_rejectsInvalidShapes() {
        listOf(
            "", "24:00", "23:60", "99:99", "9:30", "09:5", "09:500", "ab:cd", "12-30", "12.30", "12:30:00",
            "12:30Z", "12:30 ", " 12:30", "-1:30", "bukan", ":"
        ).forEach { assertNull(parseClayTimeOrNull(it), "harus ditolak: '$it'") }
    }

    @Test
    fun validity_blankIsValid_otherwiseStrict() {
        assertTrue(isValidClayTime(""))
        assertTrue(isValidClayTime("14:30"))
        assertFalse(isValidClayTime("24:00"))
        assertFalse(isValidClayTime("14:30:00"))
    }

    @Test
    fun format_padsAndRoundTrips() {
        assertEquals("09:05", formatClayTime(9, 5))
        assertEquals("00:00", formatClayTime(0, 0))
        assertEquals("23:59", formatClayTime(23, 59))
        for (h in 0 until HOURS_PER_DAY) for (m in 0 until MINUTES_PER_HOUR) {
            val back = requireNotNull(parseClayTimeOrNull(formatClayTime(h, m)))
            assertEquals(h, back.hour)
            assertEquals(m, back.minute)
        }
        assertFailsWith<IllegalArgumentException> { formatClayTime(24, 0) }
        assertFailsWith<IllegalArgumentException> { formatClayTime(0, 60) }
        assertFailsWith<IllegalArgumentException> { formatClayTime(-1, 0) }
    }

    @Test
    fun parity_withDateTimeClockPart() {
        // Waktu-murni sah harus persis sepadan dengan bagian jam parser tanggal-jam yang sudah ada.
        val samples = listOf(
            "00:00", "23:59", "14:30", "24:00", "23:60", "9:30", "12:00:00", "12:00Z", "12-30", "", "x"
        )
        samples.forEach { t ->
            assertEquals(
                parseIsoDateTimeOrNull("2000-01-01T$t") != null,
                parseClayTimeOrNull(t) != null,
                "paritas '$t'"
            )
        }
        assertNotNull(parseClayTimeOrNull(formatClayTime(21, 43)))
    }
}
