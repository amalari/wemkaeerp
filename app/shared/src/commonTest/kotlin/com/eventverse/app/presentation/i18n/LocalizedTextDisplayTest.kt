package com.eventverse.app.presentation.i18n

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizedTextDisplayTest {

    private fun phase(name: LocalizedText, subtitle: LocalizedText? = null) = PhaseDefinition(
        code = PhaseCode("p1"), order = 1, displayName = name.idText, subtitle = subtitle?.idText.orEmpty(),
        colorHex = 0xFF2563EB, displayNameText = name, subtitleText = subtitle
    )

    @Test
    fun resolveForDisplay_partialPackInEnglish_marksFallbackAndKeepsIdText() {
        val partial = phase(LocalizedText.of(id = "1. Pemotongan"))
        val r = partial.displayNameText.resolveForDisplay(LanguageCode.EN, StripPhaseOrdinal)
        assertTrue(r.isFallback)
        assertEquals("Pemotongan", r.text)
        assertEquals(LanguageCode.ID, r.usedLanguage)
    }

    @Test
    fun resolveForDisplay_completePackInEnglish_showsEnglishWithoutMarker() {
        val full = phase(LocalizedText.of(id = "1. Pemotongan", en = "1. Cutting"))
        val r = full.displayNameText.resolveForDisplay(LanguageCode.EN, StripPhaseOrdinal)
        assertFalse(r.isFallback)
        assertEquals("Cutting", r.text)
    }

    @Test
    fun resolveForDisplay_indonesian_identicalToLegacyStringForBothPackKinds() {
        val partial = phase(LocalizedText.of(id = "1. Pemotongan"), LocalizedText.of("Potong pola"))
        val full = phase(
            LocalizedText.of(id = "1. Pemotongan", en = "1. Cutting"),
            LocalizedText.of("Potong pola", "Cut patterns")
        )
        for (p in listOf(partial, full)) {
            val name = p.displayNameText.resolveForDisplay(LanguageCode.ID, StripPhaseOrdinal)
            assertFalse(name.isFallback)
            assertEquals(p.displayName.substringAfter(". "), name.text)
            assertEquals(p.subtitle, p.subtitleText?.resolveForDisplay(LanguageCode.ID)?.text)
        }
    }

    @Test
    fun resolveForDisplay_transformUppercase_keepsFallbackFlag() {
        val r = LocalizedText.of(id = "Jahit").resolveForDisplay(LanguageCode.EN) { it.uppercase() }
        assertTrue(r.isFallback)
        assertEquals("JAHIT", r.text)
    }
}
