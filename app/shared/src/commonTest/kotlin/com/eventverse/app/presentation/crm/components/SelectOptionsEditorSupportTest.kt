package com.eventverse.app.presentation.crm.components

import com.eventverse.app.domain.customfield.CustomAttributesCodec
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.common.colorFromHex
import com.eventverse.app.presentation.common.toColorHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Kontrak editor opsi berwarna di dialog "Tambah kolom" (follow-up #1
 * `PLAN-unify-field-vocabulary.md` §3a): draf → opsi domain → config JSON → baca kembali
 **identik**; palet warna diambil dari token [com.eventverse.app.presentation.theme.WeMadeColors]
 * dan hex-nya bolak-balik tanpa perubahan. Murni — tanpa Compose runtime.
 */
class SelectOptionsEditorSupportTest {

    private val tenant = TenantId("ten-bordir-uji")

    @Test
    fun buildSelectOptions_skipsBlankRows_andSlugifiesIds() {
        val drafts = listOf(
            SelectOptionDraft("", "#2563EB"),
            SelectOptionDraft("  Warna Bahan  ", "#16A34A"),
            SelectOptionDraft("   ", "#D97706")
        )
        val options = buildSelectOptions(drafts)
        assertEquals(1, options.size)
        assertEquals("warna_bahan", options.single().id.value)
        assertEquals("Warna Bahan", options.single().label)
        assertEquals("#16A34A", options.single().colorHex)
    }

    @Test
    fun buildSelectOptions_duplicateLabels_getUniqueIds() {
        val options = buildSelectOptions(
            listOf(SelectOptionDraft("Satin", "#2563EB"), SelectOptionDraft("satin", "#16A34A"))
        )
        assertEquals(2, options.size)
        assertTrue(options[0].id.value != options[1].id.value, "id opsi kembar harus di-dedup")
        assertEquals("satin", options[0].id.value)
    }

    /** Round-trip penuh: draf dialog → ENUM/MULTI_SELECT → config JSON → baca kembali identik. */
    @Test
    fun buildSelectOptions_crmFieldType_roundTripsThroughCodec() {
        val options = buildSelectOptions(
            listOf(
                SelectOptionDraft("Satin", "#2563EB"),
                SelectOptionDraft("Katun", "#16A34A"),
                SelectOptionDraft("", "#D97706")
            )
        )
        listOf(
            CrmFieldType(FieldType.ENUM, options = options),
            CrmFieldType(FieldType.MULTI_SELECT, options = options)
        ).forEach { type ->
            val decoded = CustomAttributesCodec.decodeFieldType(type.code, CustomAttributesCodec.encodeConfig(type))
            assertEquals(type, decoded, "round-trip ${type.code}")
        }
    }

    @Test
    fun buildSelectOptions_malformedColorHex_failsLoudNotSilent() {
        assertFailsWith<IllegalArgumentException> {
            buildSelectOptions(listOf(SelectOptionDraft("Satin", "biru")))
        }
    }

    /** Palet = token peran; hex hasil format selalu `#RRGGBB`, unik, dan bolak-balik identik. */
    @Test
    fun palette_colorsFormatRoundTrip_andStayUnique() {
        val hexes = SelectOptionPalette.map { it.toColorHex() }
        SelectOptionPalette.forEachIndexed { i, color ->
            assertTrue(Regex("^#[0-9A-F]{6}$").matches(hexes[i]), "hex '${hexes[i]}' harus #RRGGBB")
            assertEquals(color, colorFromHex(hexes[i]), "round-trip warna ${hexes[i]}")
        }
        assertEquals(hexes.size, hexes.toSet().size, "warna palet harus unik")
    }

    @Test
    fun nextDraftColor_prefersUnusedToken_andRotatesWhenExhausted() {
        val first = nextDraftColor(emptyList())
        assertEquals("", first.label)
        val second = nextDraftColor(listOf(first))
        assertTrue(first.colorHex != second.colorHex)
        // Palet habis → berputar dari awal tanpa melempar; seluruh draf tetap berwarna sah.
        var drafts = listOf(first)
        repeat(SelectOptionPalette.size + 1) { drafts = drafts + nextDraftColor(drafts) }
        assertTrue(drafts.all { Regex("^#[0-9A-F]{6}$").matches(it.colorHex) })
    }
}
