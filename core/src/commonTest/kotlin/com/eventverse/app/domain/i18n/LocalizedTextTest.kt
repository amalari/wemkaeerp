package com.eventverse.app.domain.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizedTextTest {

    @Test
    fun `of tanpa id kosong ditolak`() {
        assertFailsWith<IllegalArgumentException> { LocalizedText.of(id = " ") }
    }

    @Test
    fun `fromMap tanpa bahasa id ditolak`() {
        assertFailsWith<IllegalArgumentException> { LocalizedText.fromMap(mapOf(LanguageCode.EN to "Cutting")) }
    }

    @Test
    fun `terjemahan kosong ditolak`() {
        assertFailsWith<IllegalArgumentException> { LocalizedText.of(id = "Potong", en = "") }
    }

    @Test
    fun `teks melebihi batas ditolak`() {
        assertFailsWith<IllegalArgumentException> { LocalizedText.of(id = "x".repeat(LocalizedText.MAX_LENGTH + 1)) }
    }

    @Test
    fun `resolve bahasa yang ada mengembalikan teks tanpa fallback`() {
        val r = LocalizedText.of("Potong", "Cutting").resolve(LanguageCode.EN)
        assertEquals("Cutting", r.text)
        assertFalse(r.isFallback)
    }

    @Test
    fun `resolve en tanpa terjemahan kembali ke id dan ditandai fallback`() {
        val r = LocalizedText.of("Potong").resolve(LanguageCode.EN)
        assertEquals("Potong", r.text)
        assertEquals(LanguageCode.ID, r.usedLanguage)
        assertTrue(r.isFallback)
    }

    @Test
    fun `with menambah terjemahan tanpa mengubah aslinya`() {
        val base = LocalizedText.of("Potong")
        val extended = base.with(LanguageCode.EN, "Cutting")
        assertFalse(base.has(LanguageCode.EN))
        assertEquals("Cutting", extended.textOrNull(LanguageCode.EN))
    }

    @Test
    fun `entries mengurutkan id lebih dahulu`() {
        val e = LocalizedText.of("Potong", "Cutting").entries()
        assertEquals(listOf(LanguageCode.ID, LanguageCode.EN), e.map { it.first })
    }

    @Test
    fun `PackLanguages tanpa id ditolak`() {
        assertFailsWith<IllegalArgumentException> { PackLanguages(setOf(LanguageCode.EN)) }
    }

    @Test
    fun `validator pack id saja selalu lengkap`() {
        val entries = listOf("phases[0].displayName" to LocalizedText.of("Potong"))
        assertTrue(PackLanguageValidator.missing(PackLanguages.ID_ONLY, entries).isEmpty())
    }

    @Test
    fun `validator pack klaim en melaporkan jalur yang kurang`() {
        val claimed = PackLanguages(setOf(LanguageCode.ID, LanguageCode.EN))
        val entries = listOf(
            "phases[0].displayName" to LocalizedText.of("Potong", "Cutting"),
            "phases[1].displayName" to LocalizedText.of("Jahit"),
            "slots[2].displayName" to LocalizedText.of("Kemas")
        )
        val missing = PackLanguageValidator.missing(claimed, entries)
        assertEquals(listOf("phases[1].displayName", "slots[2].displayName"), missing.map { it.path })
        assertEquals(mapOf(LanguageCode.EN to 2), PackLanguageValidator.countByLanguage(missing))
    }
}
