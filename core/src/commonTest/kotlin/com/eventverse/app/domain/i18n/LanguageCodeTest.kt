package com.eventverse.app.domain.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageCodeTest {

    @Test
    fun languageCode_createsValidCodes() {
        assertEquals("id", LanguageCode("id").code)
        assertEquals("en", LanguageCode("en").code)
    }

    @Test
    fun languageCode_rejectsInvalidCodes() {
        assertFailsWith<IllegalArgumentException> {
            LanguageCode("fr")
        }
        assertFailsWith<IllegalArgumentException> {
            LanguageCode("de")
        }
        assertFailsWith<IllegalArgumentException> {
            LanguageCode("")
        }
    }

    @Test
    fun languageCode_parseAcceptsValidCodes() {
        assertEquals(LanguageCode.ID, LanguageCode.parse("id"))
        assertEquals(LanguageCode.EN, LanguageCode.parse("en"))
        assertEquals(LanguageCode.ID, LanguageCode.parse("ID"))
        assertEquals(LanguageCode.EN, LanguageCode.parse("EN"))
        assertEquals(LanguageCode.ID, LanguageCode.parse(" id "))
    }

    @Test
    fun languageCode_parseRejectsInvalidCodes() {
        assertNull(LanguageCode.parse("fr"))
        assertNull(LanguageCode.parse("de"))
        assertNull(LanguageCode.parse(""))
        assertNull(LanguageCode.parse(null))
    }

    @Test
    fun languageCode_parseOrThrowAcceptsValidCodes() {
        assertEquals(LanguageCode.ID, LanguageCode.parseOrThrow("id"))
        assertEquals(LanguageCode.EN, LanguageCode.parseOrThrow("en"))
    }

    @Test
    fun languageCode_parseOrThrowRejectsInvalidCodes() {
        assertFailsWith<IllegalStateException> {
            LanguageCode.parseOrThrow("fr")
        }
    }

    @Test
    fun languageCode_displayName() {
        assertEquals("Indonesia", LanguageCode.ID.displayName)
        assertEquals("English", LanguageCode.EN.displayName)
    }

    @Test
    fun languageCode_nativeName() {
        assertEquals("Bahasa Indonesia", LanguageCode.ID.nativeName)
        assertEquals("English", LanguageCode.EN.nativeName)
    }

    @Test
    fun languageCode_constants() {
        assertEquals(LanguageCode("id"), LanguageCode.ID)
        assertEquals(LanguageCode("en"), LanguageCode.EN)
    }
}
