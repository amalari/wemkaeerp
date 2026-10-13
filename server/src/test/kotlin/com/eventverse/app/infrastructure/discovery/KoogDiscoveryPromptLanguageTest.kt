package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.i18n.LanguageCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * B5 (PLAN-i18n Fase 1, Track B): prompt discovery menerima bahasa keluaran, dan
 * hasilnya bisa dinilai **deterministik** tanpa memanggil LLM.
 *
 * Yang dinilai adalah prompt murni: untuk tiap bahasa ada penanda yang wajib muncul
 * dan penanda bahasa lain yang wajib absen. Ini kontrak yang sama dipakai model,
 * sehingga regresi "bahasa keluaran hilang" tertangkap tanpa biaya API.
 */
class KoogDiscoveryPromptLanguageTest {

    private val idPrompt = KoogDiscoveryPrompt.system(LanguageCode.ID)
    private val enPrompt = KoogDiscoveryPrompt.system(LanguageCode.EN)

    @Test
    fun `prompt bawaan tetap bahasa indonesia`() {
        assertEquals(idPrompt, KoogDiscoveryPrompt.system, "Properti `system` harus sama dengan system(ID)")
        assertTrue(idPrompt.contains("BAHASA KELUARAN: Bahasa Indonesia"), idPrompt.take(200))
        assertFalse(idPrompt.contains("OUTPUT LANGUAGE: English"), "Prompt id tidak boleh memuat direktif Inggris")
    }

    @Test
    fun `prompt en memuat direktif bahasa inggris dan bahasan indonesia hilang`() {
        assertTrue(enPrompt.contains("OUTPUT LANGUAGE: English"), enPrompt.take(200))
        assertFalse(enPrompt.contains("BAHASA KELUARAN: Bahasa Indonesia"), "Prompt en tidak boleh memuat direktif id")
    }

    @Test
    fun `struktur dan alat tetap sama apa pun bahasanya`() {
        // Hanya bahasa yang berubah — aturan inti (JSON, alat, validator) tak boleh hilang.
        listOf("platform_modules()", "screen_catalog()", "validate_draft", "\"pack\"").forEach { marker ->
            assertTrue(idPrompt.contains(marker), "Prompt id kehilangan '$marker'")
            assertTrue(enPrompt.contains(marker), "Prompt en kehilangan '$marker'")
        }
        assertNotEquals(idPrompt, enPrompt, "Prompt id dan en harus berbeda")
    }

    @Test
    fun `prompt tidak melewati anggaran panjang`() {
        // Direktif bahasa menambah ±330 karakter; batas dinaikkan dari 8.000 ke 8.600 agar tetap terjaga.
        assertTrue(enPrompt.length <= 8_600, "Prompt en melebihi anggaran: ${enPrompt.length}")
        assertTrue(idPrompt.length <= 8_600, "Prompt id melebihi anggaran: ${idPrompt.length}")
    }
}
