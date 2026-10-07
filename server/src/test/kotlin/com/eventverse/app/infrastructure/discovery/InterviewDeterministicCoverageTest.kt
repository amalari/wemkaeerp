package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.InterviewGoldenCases
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** Mengukur cakupan tebakan deterministik yang sebenarnya (kunci tertebak / total kunci), bukan skor setelah suplemen pengguna. */
class InterviewDeterministicCoverageTest {
    @Test
    fun `cakupan tebakan deterministik per kasus`() = runBlocking {
        var total = 0
        var covered = 0
        InterviewGoldenCases.all.forEach { case ->
            val flow = runInterviewFlow(case, guessFn = deterministicKamusSeam())
            val t = flow.turns.sumOf { it.keyTotal }
            val c = flow.turns.sumOf { it.keyCovered }
            total += t; covered += c
            println("coverage | ${case.name} | $c/$t | guesses=" + flow.turns.joinToString { "${it.step}:${it.guessCount}" })
        }
        println("coverage | TOTAL | $covered/$total")
    }
}
