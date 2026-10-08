package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.presentation.discovery.fields.displayValue
import com.eventverse.app.presentation.discovery.fields.formatNumberForDisplay
import com.eventverse.app.presentation.discovery.fields.normalizeNumberTyping
import com.eventverse.app.presentation.discovery.fields.numberAffix
import com.eventverse.app.presentation.discovery.fields.parseNumberInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberFormattingTest {
    private fun cur(s: String, code: String = "IDR") = formatNumberForDisplay(s, NumberFormat.CURRENCY, code)
    private fun pct(s: String) = formatNumberForDisplay(s, NumberFormat.PERCENT, null)

    @Test
    fun currency_idr_usesRpAndDotThousands() {
        assertEquals("Rp 12.000", cur("12000"))
        assertEquals("Rp 0", cur("0"))
        assertEquals("Rp 999", cur("999"))
        assertEquals("Rp 1.234.567.890", cur("1234567890"))
    }

    @Test
    fun currency_otherCode_prefixedAsciiCode() {
        assertEquals("USD 1.500", cur("1500", "USD"))
        assertEquals("EUR 2.500,75", cur("2500.75", "EUR"))
        assertTrue(cur("1500", "JPY").all { it.code < 128 }, "tampilan wajib ASCII")
    }

    @Test
    fun percent_formatsWithSpaceAndComma() {
        assertEquals("12,5 %", pct("12.5"))
        assertEquals("100 %", pct("100"))
        assertEquals("0,25 %", pct("0.25"))
    }

    @Test
    fun decimals_notRoundedAndTrailingZerosTrimmed() {
        assertEquals("Rp 12.000", cur("12000.0000"))
        assertEquals("Rp 12.000,5", cur("12000.5000"))
        assertEquals("Rp 1,2345", cur("1.2345"))
        assertEquals("Rp 1,05", cur("1.05"))
    }

    @Test
    fun negative_keepsSignBeforeAffix() {
        assertEquals("-Rp 1.500", cur("-1500"))
        assertEquals("-3,5 %", pct("-3.5"))
    }

    @Test
    fun emptyAndLegacyNonNumeric_doNotCrash() {
        assertEquals("", cur(""))
        assertEquals("", cur("   "))
        assertEquals("abc", cur("abc"))
        assertEquals("12,5", formatNumberForDisplay("12,5", NumberFormat.PLAIN, null))
        assertEquals("1e5", pct("1e5"))
        assertEquals("12.", cur("12."))
    }

    @Test
    fun plain_isNeverTransformed() {
        assertEquals("12000", formatNumberForDisplay("12000", NumberFormat.PLAIN, null))
        assertEquals("12.5", formatNumberForDisplay("12.5", NumberFormat.PLAIN, null))
    }

    @Test
    fun parse_acceptsFormattedAndPlainText() {
        assertEquals("12000", parseNumberInput("Rp 12.000", NumberFormat.CURRENCY))
        assertEquals("12.5", parseNumberInput("12,5 %", NumberFormat.PERCENT))
        assertEquals("12.5", parseNumberInput("12.5", NumberFormat.PERCENT))
        assertEquals("1500", parseNumberInput("USD 1.500", NumberFormat.CURRENCY))
        assertEquals("1234567.5", parseNumberInput("1.234.567,5", NumberFormat.CURRENCY))
        assertEquals("-1500", parseNumberInput("-Rp 1.500", NumberFormat.CURRENCY))
        assertEquals("", parseNumberInput("", NumberFormat.CURRENCY))
        assertEquals("7", parseNumberInput("007", NumberFormat.PLAIN))
    }

    @Test
    fun parse_rejectsNonNumbers() {
        assertNull(parseNumberInput("Rp", NumberFormat.CURRENCY))
        assertNull(parseNumberInput("1,2,3", NumberFormat.PLAIN))
        assertNull(parseNumberInput("1.2.3", NumberFormat.PLAIN))
        assertNull(parseNumberInput("12.", NumberFormat.PLAIN))
        assertNull(parseNumberInput("1,23456", NumberFormat.PLAIN), "lebih dari 4 desimal ditolak, bukan dibulatkan")
        assertNull(parseNumberInput("-", NumberFormat.PLAIN))
    }

    @Test
    fun roundTrip_parseOfFormat_returnsStored() {
        val samples = listOf("0", "5", "999", "1000", "12000", "1234567890", "12.5", "0.5", "1500.75", "-1500", "-3.5", "100")
        NumberFormat.entries.forEach { fmt ->
            if (fmt == NumberFormat.PLAIN) return@forEach
            samples.forEach { stored ->
                val shown = formatNumberForDisplay(stored, fmt, "IDR")
                assertEquals(stored, parseNumberInput(shown, fmt), "round-trip $fmt $stored via '$shown'")
            }
        }
    }

    @Test
    fun typing_acceptsIntermediateStatesAndConvertsComma() {
        assertEquals("", normalizeNumberTyping("", NumberFormat.CURRENCY))
        assertEquals("-", normalizeNumberTyping("-", NumberFormat.CURRENCY))
        assertEquals("12.", normalizeNumberTyping("12.", NumberFormat.PERCENT))
        assertEquals("12.5", normalizeNumberTyping("12,5", NumberFormat.PERCENT))
        assertEquals("1500000", normalizeNumberTyping("Rp 1.500.000", NumberFormat.CURRENCY))
        assertNull(normalizeNumberTyping("1,23456", NumberFormat.PLAIN), "pecahan >4 digit ditolak")
        assertNull(normalizeNumberTyping("1-2", NumberFormat.PLAIN))
    }

    @Test
    fun everyNumberFormat_hasDisplayAffixAndInputPath() {
        NumberFormat.entries.forEach { fmt ->
            val code = if (fmt == NumberFormat.CURRENCY) "USD" else null
            val affix = numberAffix(fmt, code)
            val shown = formatNumberForDisplay("1500", fmt, code)
            val back = parseNumberInput(shown, fmt)
            assertEquals("1500", if (fmt == NumberFormat.PLAIN) shown else back, "format $fmt")
            when (fmt) {
                NumberFormat.PLAIN -> assertTrue(affix.prefix.isEmpty() && affix.suffix.isEmpty())
                NumberFormat.CURRENCY -> assertEquals("USD", affix.prefix)
                NumberFormat.PERCENT -> assertEquals("%", affix.suffix)
            }
        }
    }

    @Test
    fun fieldSpecDisplayValue_onlyTransformsNumber_inFormAndTableContexts() {
        val money = FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "IDR")
        val rate = FieldSpec("diskon", "Diskon", FieldType.NUMBER, format = NumberFormat.PERCENT)
        val name = FieldSpec("nama", "Nama", FieldType.TEXT)
        assertEquals("Rp 12.000", money.displayValue("12000"))
        assertEquals("12,5 %", rate.displayValue("12.5"))
        assertEquals("12000", name.displayValue("12000"))

        // Konteks form (non-garment: bordir) — nilai simpan tetap polos setelah submit.
        val entity = EntitySpec("pesanan_bordir", "Pesanan Bordir", listOf(name, money, rate))
        val spec = PrototypeSpec(
            listOf(entity),
            listOf(ScreenSpec("scr-bordir", "Form", WidgetKind.FORM, "pesanan_bordir", form = FormConfig(listOf("nama", "harga", "diskon"), "Simpan")))
        )
        val state = InteractiveFormState(InteractiveScreen(spec, emptyMap()))
        state.setFieldValue("harga", normalizeNumberTyping("12.000,5", NumberFormat.CURRENCY).orEmpty())
        assertEquals("12000.5", state.formValues["harga"])
        // Konteks sel tabel: entity.field(column) menyediakan format yang sama ke TableCell.
        assertEquals("Rp 12.000,5", entity.field("harga")?.displayValue(state.formValues.getValue("harga")))
    }
}
