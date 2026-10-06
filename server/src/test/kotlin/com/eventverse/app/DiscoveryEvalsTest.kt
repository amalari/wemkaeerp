package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Evals baseline deterministik (plan SP-C4).** Seluruh kasus emas [DiscoveryGoldenCases] (≥ 10 vertikal)
 * dinilai penilai berstruktur [DiscoveryEvalGrader] dengan kriteria §6 plan induk — bukan kecocokan teks.
 * Baseline deterministik wajib **100%**: kalau tidak, penilainya yang rusak. Skor agent LLM dicatat dengan
 * format log yang sama oleh `KoogDiscoveryLiveEvalsTest`, sehingga bisa dibandingkan baris per baris.
 */
class DiscoveryEvalsTest {

    @Test
    fun `baseline deterministik lulus 100 persen kriteria otomatis pada semua kasus emas`() = runBlocking {
        val agent = DeterministicDiscoveryAgent()
        var passed = 0

        for (case in DiscoveryGoldenCases.all) {
            val verdict = DiscoveryEvalGrader.grade(case, agent.draft(DiscoveryRequest(case.narrative, case.industryHint)))
            println(verdict.logLine(agent.agentRef))
            assertTrue(verdict.passed, "Kasus ${case.name} gagal: ${verdict.criteria.filter { !it.passed }}")
            passed++
        }

        println("evals | skor: $passed/${DiscoveryGoldenCases.all.size} (${agent.agentRef}, baseline deterministik)")
        assertEquals(
            DiscoveryGoldenCases.all.size,
            passed,
            "Baseline deterministik wajib 100% — kalau tidak, penilainya yang rusak"
        )
    }

    @Test
    fun `set kasus emas memuat minimal 10 vertikal dengan garment dan non-garment`() {
        assertTrue(DiscoveryGoldenCases.all.size >= 10, "Set emas minimal 10 kasus (plan SP-C4)")
        assertTrue(DiscoveryGoldenCases.all.count { it.garmentPack } >= 3, "Garment minimal 3: FOB, CMT, D2C")
        assertTrue(
            DiscoveryGoldenCases.all.any { !it.garmentPack },
            "Kasus non-garment wajib ada — tenant/pack kedua wajib di test (variabilitas Kontrak 6)"
        )
        assertEquals(
            DiscoveryGoldenCases.all.map { it.name }.distinct().size,
            DiscoveryGoldenCases.all.size,
            "Nama kasus wajib unik agar log evals bisa dibandingkan"
        )
    }
}
