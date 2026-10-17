package com.eventverse.app.presentation.builder

import com.eventverse.app.domain.builder.BriefKeys
import com.eventverse.app.domain.builder.ScenarioKeys
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.presentation.builder.chat.getRecommendationsForQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SC3 (cek visual 2026-10-18): opsi bawaan "Sesuai SOP..." tidak boleh jadi jawaban pertanyaan skenario. */
class FollowUpRecommendationsTest {
    @Test
    fun `scenario question should have no canned recommendations`() {
        val id = ScenarioKeys.questionId(ScenarioId("alur-bordir"), "Apakah sampel harus disetujui pelanggan dulu?")
        assertTrue(getRecommendationsForQuestion(id, "Apakah sampel harus disetujui pelanggan dulu?").isEmpty())
        // juga bila teksnya kebetulan memuat kata kunci pertanyaan celah
        assertTrue(getRecommendationsForQuestion(id, "Siapa yang mengisi invoice DP?").isEmpty())
    }

    @Test
    fun `ai module question should have no canned recommendations either`() {
        val id = BriefKeys.followUpId("klinik_poli", "Berapa lama satu pasien biasanya diperiksa?")
        assertTrue(getRecommendationsForQuestion(id, "Berapa lama satu pasien biasanya diperiksa?").isEmpty())
        assertTrue(getRecommendationsForQuestion(id, "Siapa yang mengisi data Poli?").isEmpty(), "kata kunci celah tak membuatnya punya opsi")
    }

    @Test
    fun `template gap questions should keep their three recommendations`() {
        assertEquals(3, getRecommendationsForQuestion("gap:klinik_poli:who_fills", "Siapa yang mengisi data Poli?").size)
        assertEquals(3, getRecommendationsForQuestion("gap:klinik_poli:lain", "Pertanyaan celah lain?").size, "celah template tanpa kata kunci tetap punya opsi umum")
    }
}
