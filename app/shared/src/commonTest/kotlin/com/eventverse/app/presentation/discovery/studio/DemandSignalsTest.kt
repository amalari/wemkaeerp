package com.eventverse.app.presentation.discovery.studio

import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Sinyal gerbang Rule of Three (plan §6 E3/D5) dibaca dua layar Studio — Buku Demand dan kartu
 * gerbang di Studio Pola. Test di sini mengunci parsingnya: bentuk respons diperiksa pemanggil
 * (fail-loud, bukan fallback senyap — tenant-variability Kontrak 4), ambang bawaan mengikuti
 * `DemandLedger.RULE_OF_THREE`, dan urutan kandidat dari server tidak diacak ulang.
 */
class DemandSignalsTest {

    @Test
    fun parse_keepsCandidatesAndSamplesAsServerSentThem() {
        val raw = JsonParser.parseObject(
            """{"minimum":3,"candidates":[""" +
                """{"term":"gigi","demandCount":4,"samples":["Perlu pencatatan gigi per pasien"]},""" +
                """{"term":"rekam","demandCount":3,"samples":[]}]}"""
        )

        val signals = parseDemandSignals(raw)

        assertEquals(3, signals.minimum)
        assertEquals(listOf("gigi", "rekam"), signals.candidates.map { it.term })
        assertEquals(4, signals.candidates.first().demandCount)
        assertEquals(listOf("Perlu pencatatan gigi per pasien"), signals.candidates.first().samples)
        assertTrue(signals.candidates.last().samples.isEmpty())
    }

    @Test
    fun parse_missingKeysFallBackToClosedVocabularyDefaults() {
        val signals = parseDemandSignals(JsonParser.parseObject("{}"))

        // Ambang bawaan = DemandLedger.RULE_OF_THREE; tanpa kandidat = gerbang kosong, bukan error.
        assertEquals(3, signals.minimum)
        assertTrue(signals.candidates.isEmpty())
    }

    @Test
    fun parse_nonObjectResponseFailsLoudly() {
        assertFailsWith<IllegalStateException> {
            parseDemandSignals(JsonParser.parse("[]") as? com.eventverse.app.shared.json.JsonValue.Obj
                ?: error("Respons buku demand tidak dikenali"))
        }
    }
}