package com.eventverse.app.presentation.discovery

import androidx.compose.ui.text.input.KeyboardType
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.domain.prototype.TextValidations
import com.eventverse.app.presentation.discovery.fields.displayValue
import com.eventverse.app.presentation.discovery.fields.keyboardTypeFor
import com.eventverse.app.presentation.discovery.fields.validationErrorText
import com.eventverse.app.presentation.discovery.fields.validationMessageFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paritas kontrol C6 (DATE x withTime) dan C9 (TEXT x TextValidation) pada entitas non-garment (klinik). */
class PrototypeDateTimeTextValidationParityTest {
    private val kunjungan = FieldSpec("jadwal", "Jadwal", FieldType.DATE, withTime = true)
    private val tanggalLahir = FieldSpec("lahir", "Lahir", FieldType.DATE)
    private val email = FieldSpec("email", "Email", FieldType.TEXT, validation = TextValidation.EMAIL)
    private val telp = FieldSpec("telp", "Telepon", FieldType.TEXT, validation = TextValidation.PHONE)
    private val bebas = FieldSpec("nama", "Nama", FieldType.TEXT)
    private val entity = EntitySpec("kunjungan_klinik", "Kunjungan Klinik", listOf(bebas, email, telp, kunjungan, tanggalLahir))

    @Test
    fun everyTextValidation_hasKeyboardMessageAndCoreSample() {
        TextValidation.entries.forEach { v ->
            val keyboard = keyboardTypeFor(v)
            when (v) {
                TextValidation.NONE -> assertEquals(KeyboardType.Text, keyboard)
                TextValidation.EMAIL -> assertEquals(KeyboardType.Email, keyboard)
                TextValidation.PHONE -> assertEquals(KeyboardType.Phone, keyboard)
            }
            val message = validationErrorText(v)
            assertEquals(v == TextValidation.NONE, message == null, "pesan galat untuk $v")
            assertTrue(message.orEmpty().all { it.code < 128 }, "pesan wajib ASCII")
            val field = FieldSpec("f", "F", FieldType.TEXT, validation = v)
            assertNull(field.validationMessageFor(TextValidations.sample(v)), "contoh sah core tidak boleh galat: $v")
        }
    }

    @Test
    fun validationMessage_followsCoreAccepts_emptyIsNotAnError_noNormalization() {
        assertNull(email.validationMessageFor(""))
        assertNotNull(email.validationMessageFor("bukan-email"))
        assertNull(email.validationMessageFor("a@b.id"))
        assertNotNull(telp.validationMessageFor("123"))
        assertNull(telp.validationMessageFor("+62 812-3456-789"))
        assertNotNull(email.validationMessageFor(" a@b.id"), "spasi tidak dipangkas diam-diam")
        assertNull(bebas.validationMessageFor("apa saja !!"))
        assertNull(kunjungan.validationMessageFor("rusak"), "bukan TEXT -> tidak ada galat teks")
    }

    @Test
    fun dateWithTime_displayInTableCellAndCard_isSafeAndKeepsHour() {
        assertEquals("2026-10-08 14:30", kunjungan.displayValue("2026-10-08T14:30"))
        assertEquals("2026-10-08", tanggalLahir.displayValue("2026-10-08"))
        assertEquals("", kunjungan.displayValue(""))
        assertEquals("nilai-lama", kunjungan.displayValue("nilai-lama"), "nilai tak sah tidak disembunyikan")
    }

    @Test
    fun formContext_keepsWithTimeValueAsIs_andCoreRejectsWrongShape() {
        val spec = PrototypeSpec(
            listOf(entity),
            listOf(
                ScreenSpec(
                    "scr-klinik", "Form", WidgetKind.FORM, "kunjungan_klinik",
                    form = FormConfig(listOf("nama", "email", "telp", "jadwal", "lahir"), "Simpan")
                )
            )
        )
        val state = InteractiveFormState(InteractiveScreen(spec, emptyMap()))
        state.setFieldValue("jadwal", "2026-10-08T14:30")
        state.setFieldValue("email", "Pasien@Klinik.id")
        assertEquals("2026-10-08T14:30", state.formValues["jadwal"])
        assertEquals("Pasien@Klinik.id", state.formValues["email"], "tanpa normalisasi")
        assertTrue(kunjungan.accepts("2026-10-08T14:30"))
        assertTrue(!kunjungan.accepts("2026-10-08"))
        assertTrue(!tanggalLahir.accepts("2026-10-08T14:30"))
    }
}
