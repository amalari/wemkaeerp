package com.eventverse.app.domain.sampling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Kontrak rumus slack — skenarionya adalah contoh nyata lantai yang melahirkan rumus ini:
 * deadline terdekat belum tentu paling genting kalau qty-nya kecil, dan deadline yang lebih jauh
 * bisa jadi paling genting justru karena qty-nya besar.
 */
class SpkUrgencyTest {

    private val today = LocalDate(2026, 9, 27)

    @Test
    fun `deadline besok qty kecil dan deadline lusa qty besar sama sama segera`() {
        // A: sisa 1 hari, butuh ⌈400 × 0,55 × 2 ÷ 480⌉ = 1 hari → slack 0 (SEGERA)
        // B: sisa 2 hari, butuh ⌈800 × 0,55 × 2 ÷ 480⌉ = 2 hari → slack 0 (SEGERA)
        // Deadline B lebih jauh tapi qty-nya makan lead time-nya — dua-duanya harus dikejar.
        val result = assessUrgency(
            listOf(
                SpkUrgencyInput("A", SamplingPipelineStage.MACHINE_KNITTING, LocalDate(2026, 9, 28), 400, 2),
                SpkUrgencyInput("B", SamplingPipelineStage.MACHINE_KNITTING, LocalDate(2026, 9, 29), 800, 2)
            ),
            today
        )

        assertEquals(2, result.size)
        assertEquals(SpkUrgencyLevel.SEGERA, result[0].level)
        assertEquals(SpkUrgencyLevel.SEGERA, result[1].level)
        assertEquals(0, result[0].slackDays)
        assertEquals(0, result[1].slackDays)
        // Tie di slack: deadline yang lebih dekat menang.
        assertEquals("A", result[0].spkId)
        assertEquals("B", result[1].spkId)
    }

    @Test
    fun `deadline lusa qty sangat besar menjadi urgent dan melampaui deadline besok`() {
        // A: sisa 1 hari, butuh 1 → slack 0 (SEGERA)
        // B: sisa 2 hari, butuh ⌈1300 × 0,55 × 2 ÷ 480⌉ = 3 hari → slack −1 (URGENT)
        val result = assessUrgency(
            listOf(
                SpkUrgencyInput("A", SamplingPipelineStage.MACHINE_KNITTING, LocalDate(2026, 9, 28), 400, 2),
                SpkUrgencyInput("B", SamplingPipelineStage.MACHINE_KNITTING, LocalDate(2026, 9, 29), 1300, 2)
            ),
            today
        )

        assertEquals("B", result[0].spkId, "Yang kapasitasnya tidak cukup harus diprioritaskan duluan")
        assertEquals(SpkUrgencyLevel.URGENT, result[0].level)
        assertEquals(-1, result[0].slackDays)
        assertEquals(1, result[0].rank)
        assertEquals(SpkUrgencyLevel.SEGERA, result[1].level)
        assertEquals(2, result[1].rank)
        assertEquals(2, result[0].activeCount)
    }

    @Test
    fun `deadline sama tapi tahap berbeda yang masih di hulu lebih genting`() {
        // Deadline 3 hari lagi (2026-09-30), 444 menit standar, qty 5:
        // Setrika (×0,15): ⌈66,6 × 5 ÷ 480⌉ = 1 hari → slack 2 (AMAN)
        // SPK Masuk (×1,0): ⌈444 × 5 ÷ 480⌉ = 5 hari → slack −2 (URGENT)
        val result = assessUrgency(
            listOf(
                SpkUrgencyInput("setrika", SamplingPipelineStage.SETRIKA_UAP, LocalDate(2026, 9, 30), 444, 5),
                SpkUrgencyInput("hulu", SamplingPipelineStage.NEW_INTAKE, LocalDate(2026, 9, 30), 444, 5)
            ),
            today
        )

        assertEquals("hulu", result[0].spkId)
        assertEquals(SpkUrgencyLevel.URGENT, result[0].level)
        assertEquals(SpkUrgencyLevel.AMAN, result[1].level)
    }

    @Test
    fun `spk tanpa deadline diberi level khusus dan diperingkat paling akhir`() {
        val result = assessUrgency(
            listOf(
                SpkUrgencyInput("tanpa-dl", SamplingPipelineStage.MACHINE_KNITTING, null, 400, 2),
                SpkUrgencyInput("aman", SamplingPipelineStage.SETRIKA_UAP, LocalDate(2026, 10, 15), 400, 2)
            ),
            today
        )

        assertEquals(2, result.size)
        assertEquals("aman", result[0].spkId)
        assertEquals("tanpa-dl", result[1].spkId)
        assertEquals(SpkUrgencyLevel.TANPA_DEADLINE, result[1].level)
        assertNull(result[1].slackDays)
    }

    @Test
    fun `menit standar nol membuat slack sama dengan sisa hari kalender`() {
        val result = assessUrgency(
            listOf(SpkUrgencyInput("kosong", SamplingPipelineStage.NEW_INTAKE, LocalDate(2026, 9, 30), 0, 3)),
            today
        )

        assertEquals(3, result[0].slackDays, "3 hari sisa − 0 hari kerja = selisih kalender murni")
        assertEquals(SpkUrgencyLevel.AMAN, result[0].level)
    }

    @Test
    fun `faktor sisa default monoton turun dari hulu ke hilir`() {
        val stages = SamplingPipelineStage.entries.sortedBy { it.order }
        val factors = stages.map { DefaultStageWorkProfile.remainingFactor(it) }
        assertTrue(
            factors.zipWithNext().all { (current, next) -> current >= next },
            "Faktor harus tidak naik sepanjang alur: $factors"
        )
        assertEquals(1.0, factors.first(), "SPK baru belum mengerjakan apa pun")
        assertEquals(0.0, factors.last(), "SPK yang ACC tidak menyisakan pekerjaan")
    }
}