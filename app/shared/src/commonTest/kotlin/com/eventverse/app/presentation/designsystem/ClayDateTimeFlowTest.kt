package com.eventverse.app.presentation.designsystem

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClayDateTimeFlowTest {
    private val picked = LocalDate(2026, 10, 15)
    private val committed = LocalDateTime(2026, 3, 2, 8, 5)

    @Test
    fun back_afterPickingDayWithEmptyValue_keepsPickedDay() {
        val flow = DateTimeFlow().opened().datePicked(picked).backedToDate()
        assertEquals(DateTimeStep.DATE, flow.step)
        assertEquals(picked, flow.calendarInitialDate(null))
    }

    @Test
    fun back_afterPickingDayWithCommittedValue_prefersPickedDay() {
        val flow = DateTimeFlow().opened().datePicked(picked).backedToDate()
        assertEquals(picked, flow.calendarInitialDate(committed))
    }

    @Test
    fun opened_withCommittedValue_startsOnCommittedDay() {
        assertEquals(committed.date, DateTimeFlow().opened().calendarInitialDate(committed))
    }

    @Test
    fun opened_withEmptyValue_hasNoInitialDay() {
        assertNull(DateTimeFlow().opened().calendarInitialDate(null))
    }

    @Test
    fun dismissed_afterPickingDay_dropsPendingAndReopensClean() {
        val closed = DateTimeFlow().opened().datePicked(picked).dismissed()
        assertEquals(DateTimeStep.CLOSED, closed.step)
        assertNull(closed.pendingDate)
        assertNull(closed.opened().calendarInitialDate(null))
    }

    @Test
    fun datePicked_movesToTimeStepWithPendingDay() {
        val flow = DateTimeFlow().opened().datePicked(picked)
        assertEquals(DateTimeStep.TIME, flow.step)
        assertEquals(picked, flow.pendingDate)
    }
}
