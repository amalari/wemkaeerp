package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.SelectOption
import com.eventverse.app.domain.customfield.SelectOptionId
import com.eventverse.app.presentation.crm.components.LeadFieldControl
import com.eventverse.app.presentation.crm.components.LeadFormState
import com.eventverse.app.presentation.crm.components.leadFieldControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paritas "kontrol input ada untuk tiap tipe" (CRM). Batasan: menguji pemeta tipe->kontrol, bukan rendering
 * Compose; jaminan sisi render datang dari `when` tanpa `else` di `LeadCustomField`.
 */
class LeadFieldControlParityTest {
    private val samples: Map<String, FieldType> = listOf(
        FieldType.Text,
        FieldType.LongText,
        FieldType.Number(),
        FieldType.SingleSelect(listOf(SelectOption(SelectOptionId("a"), "A", "#112233"))),
        FieldType.DateField(),
        FieldType.Checkbox,
        FieldType.UserRef()
    ).associateBy { it.code }

    @Test
    fun everyFieldTypeCode_hasSampleAndControl() {
        assertEquals(FieldType.ALL_CODES, samples.keys, "FieldType baru wajib ditambahkan ke sampel dan pemeta kontrol")
        samples.values.forEach { leadFieldControl(it) }
    }

    @Test
    fun dateField_withoutTime_usesDatePicker() {
        assertEquals(LeadFieldControl.DATE_PICKER, leadFieldControl(FieldType.DateField(withTime = false)))
    }

    @Test
    fun dateField_withTime_isNotFakedAsDatePicker() {
        assertEquals(LeadFieldControl.DATE_TIME_TEXT, leadFieldControl(FieldType.DateField(withTime = true)))
    }

    @Test
    fun leadForm_inputSupport_isExplicitlyTheKnownSubset() {
        val supported = samples.filterValues { LeadFormState.supportsInput(it) }.keys
        assertEquals(setOf("TEXT", "LONG_TEXT", "NUMBER", "SINGLE_SELECT"), supported)
        assertTrue("DATE" !in supported)
    }
}
