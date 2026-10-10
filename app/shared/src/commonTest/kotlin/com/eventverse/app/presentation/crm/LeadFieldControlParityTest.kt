package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.prefill.LeadDraftFields
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.NumberFormat
import com.eventverse.app.domain.customfield.SelectOption
import com.eventverse.app.domain.customfield.SelectOptionId
import com.eventverse.app.presentation.crm.components.LeadFieldControl
import com.eventverse.app.presentation.crm.components.LeadFormState
import com.eventverse.app.presentation.crm.components.isBlankOrIsoDate
import com.eventverse.app.presentation.crm.components.isBlankOrIsoDateTime
import com.eventverse.app.presentation.crm.components.leadFieldControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        FieldType.UserRef(),
        FieldType.Relation(targetResource = "employees"),
        FieldType.File
    ).associateBy { it.code }

    @Test
    fun everyFieldTypeCode_hasSampleAndControl() {
        assertEquals(FieldType.ALL_CODES, samples.keys, "FieldType baru wajib ditambahkan ke sampel dan pemeta kontrol")
        samples.values.forEach { leadFieldControl(it) }
    }

    /** C7 (TRD-FIELD-001): Relation punya kontrol sendiri — tidak dipalsukan jadi teks atau UserRef. */
    @Test
    fun relationField_hasItsOwnControl() {
        assertEquals(LeadFieldControl.RELATION, leadFieldControl(FieldType.Relation(targetResource = "leads")))
    }

    /** C8 (TRD-FIELD-002): File punya kontrol sendiri — unggah/unduh, bukan kolom teks. */
    @Test
    fun fileField_hasItsOwnControl() {
        assertEquals(LeadFieldControl.FILE, leadFieldControl(FieldType.File))
    }

    @Test
    fun textAndLongText_useDistinctControls() {
        assertEquals(LeadFieldControl.TEXT, leadFieldControl(FieldType.Text))
        assertEquals(LeadFieldControl.LONG_TEXT, leadFieldControl(FieldType.LongText))
    }

    @Test
    fun dateField_withoutTime_usesDatePicker() {
        assertEquals(LeadFieldControl.DATE_PICKER, leadFieldControl(FieldType.DateField(withTime = false)))
    }

    @Test
    fun dateField_withTime_usesDateTimePicker() {
        // C6 (Irisan 2): ClayDateTimePicker — bukan kolom teks lama, bukan picker tanggal saja.
        assertEquals(LeadFieldControl.DATE_TIME_PICKER, leadFieldControl(FieldType.DateField(withTime = true)))
    }

    @Test
    fun leadForm_inputSupport_isExplicitlyTheKnownSubset() {
        val supported = samples.filterValues { LeadFormState.supportsInput(it) }.keys
        assertEquals(setOf("TEXT", "LONG_TEXT", "NUMBER", "SINGLE_SELECT", "DATE"), supported)
        // Tanggal berwaktu satu kode "DATE" dengan tanpa waktu; keduanya kini punya input di dialog.
        assertTrue(LeadFormState.supportsInput(FieldType.DateField(withTime = true)))
    }

    @Test
    fun numberFormatVariants_shareTheSameControl() {
        assertEquals(LeadFieldControl.NUMBER, leadFieldControl(FieldType.Number(format = NumberFormat.Percent)))
        assertEquals(LeadFieldControl.NUMBER, leadFieldControl(FieldType.Number(format = NumberFormat.Currency("IDR"))))
    }

    @Test
    fun dateValue_blankOrValidIsoAccepted_malformedRejected() {
        assertTrue(isBlankOrIsoDate(""))
        assertTrue(isBlankOrIsoDate("2026-10-08"))
        assertFalse(isBlankOrIsoDate("08-10-2026"))
        assertFalse(isBlankOrIsoDate("2026-1-8"), "ISO menuntut dua digit bulan/hari")
        assertFalse(isBlankOrIsoDate("2026-13-01"), "bulan 13 bukan tanggal kalender")
        assertFalse(isBlankOrIsoDate("kemarin"))
    }

    /** C6 (Irisan 2): validator tanggal berwaktu, parser yang sama dengan `ClayDateTimePicker`. */
    @Test
    fun dateTimeValue_blankOrValidIsoDateTimeAccepted_malformedRejected() {
        assertTrue(isBlankOrIsoDateTime(""))
        assertTrue(isBlankOrIsoDateTime("2026-10-08T14:30"))
        assertFalse(isBlankOrIsoDateTime("2026-10-08"), "nilai lama tanpa jam tidak sah untuk tanggal berwaktu")
        assertFalse(isBlankOrIsoDateTime("2026-10-08T24:00"), "jam 24 di luar 00-23")
        assertFalse(isBlankOrIsoDateTime("2026-10-08 14:30"), "pemisah wajib 'T', bukan spasi")
        assertFalse(isBlankOrIsoDateTime("kemarin"))
    }

    @Test
    fun submit_whenDateMalformed_isBlocked_evenWhenOptional() {
        val tanggal = LeadFieldDescriptor(
            "cf-tanggal", "Target Kirim", FieldType.DateField(),
            isRequired = false, isEditable = true, isDeletable = true, isCore = false
        )
        val form = LeadFormState(LeadStage.NEW_LEAD).apply { update(LeadDraftFields.CONTACT_PERSON, "Rina") }

        form.update("cf-tanggal", "2026-10-08")
        assertTrue(form.canSubmit(listOf(tanggal)))
        form.update("cf-tanggal", "08-10-2026")
        assertFalse(form.canSubmit(listOf(tanggal)), "tanggal rusak tidak boleh lolos validasi form")
        form.update("cf-tanggal", "")
        assertTrue(form.canSubmit(listOf(tanggal)), "kosong berarti belum diisi — sah untuk field opsional")
    }

    /** C6 (Irisan 2): tanggal berwaktu divalidasi ketat `TTTT-BB-HH'T'JJ:MM`, nilai lama tanpa jam ditolak. */
    @Test
    fun submit_whenDateTimeMalformed_isBlocked_evenWhenOptional() {
        val jadwal = LeadFieldDescriptor(
            "cf-jadwal", "Jadwal Kunjungan", FieldType.DateField(withTime = true),
            isRequired = false, isEditable = true, isDeletable = true, isCore = false
        )
        val form = LeadFormState(LeadStage.NEW_LEAD).apply { update(LeadDraftFields.CONTACT_PERSON, "Rina") }

        form.update("cf-jadwal", "2026-10-08T14:30")
        assertTrue(form.canSubmit(listOf(jadwal)))
        form.update("cf-jadwal", "2026-10-08")
        assertFalse(form.canSubmit(listOf(jadwal)), "tanggal tanpa jam tidak sah untuk field tanggal berwaktu")
        form.update("cf-jadwal", "")
        assertTrue(form.canSubmit(listOf(jadwal)), "kosong berarti belum diisi — sah untuk field opsional")
    }
}
