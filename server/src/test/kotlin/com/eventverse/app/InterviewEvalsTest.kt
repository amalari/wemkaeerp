package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.DeterministicInterviewGuesser
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.infrastructure.discovery.InterviewStepGuesses
import com.eventverse.app.infrastructure.discovery.deterministicKamusSeam
import com.eventverse.app.infrastructure.discovery.runInterviewFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Evals wawancara — kesiapan baseline (plan IV-C0).** Set kasus emas dinilai penilai berstruktur
 * [InterviewEvalGrader]. Sesi emas (kunci jawaban) wajib lulus **100%** — kalau tidak, penilainya yang
 * rusak, bukan kuncinya.
 *
 * Baseline deterministik penuh (menggerakkan `DeterministicInterviewGuesser` B1 melalui alur G1–G5)
 * menyusul begitu port `InterviewGuesser` merge — pelari [InterviewGuessFn]-nya sudah siap dan
 * tidak berubah. Yang dijalankan sekarang adalah setengah deterministik dari janji itu: kunci
 * jawaban statis dinilai penilai yang sama dengan yang akan menilai agent LLM.
 */
class InterviewEvalsTest {

    @Test
    fun `set kasus emas memuat minimal 10 usaha dengan garment dan non-garment`() {
        assertTrue(InterviewGoldenCases.all.size >= 10, "Set emas minimal 10 kasus (plan IV-C0)")
        assertTrue(InterviewGoldenCases.all.count { it.garmentPack } >= 3, "Garment minimal 3: FOB, CMT, D2C")
        assertTrue(
            InterviewGoldenCases.all.any { !it.garmentPack },
            "Kasus non-garment wajib ada — tenant/pack kedua wajib di test (variabilitas Kontrak 6)"
        )
        assertTrue(
            InterviewGoldenCases.all.any { it.name == "sablon-bordir" },
            "Kasus tekstil-adjacent wajib ada — dinilai dari kemampuan & asal modul, bukan pack baku (keputusan 2026-10-07)"
        )
        assertEquals(
            InterviewGoldenCases.all.map { it.name }.distinct().size,
            InterviewGoldenCases.all.size,
            "Nama kasus wajib unik agar log evals bisa dibandingkan"
        )
    }

    @Test
    fun `narasi kasus garment menunjuk blueprint bawaan yang benar`() {
        val byName = InterviewGoldenCases.all.associateBy { it.name }
        assertEquals("garment", draftFor(byName.getValue("garment-fob")).blueprint.pack.value)
        assertEquals("fob_full_package", draftFor(byName.getValue("garment-fob")).blueprint.code.value)
        assertEquals("garment", draftFor(byName.getValue("garment-cmt")).blueprint.pack.value)
        assertEquals(GarmentBlueprints.BRAND_D2C.code.value, draftFor(byName.getValue("garment-d2c")).blueprint.code.value)
    }

    @Test
    fun `kunci jawaban klinik dan bengkel lulus 100 persen - kalau tidak penilainya yang rusak`() {
        val byName = InterviewGoldenCases.all.associateBy { it.name }
        val klinik = InterviewEvalGrader.grade(
            byName.getValue("klinik"),
            InterviewEvalPacks.draftOf(InterviewEvalPacks.klinikPack, InterviewEvalPacks.klinikSession)
        )
        println(klinik.logLine("kunci-jawaban"))
        assertTrue(klinik.passed, "Kunci klinik wajib lulus: ${klinik.failedCriteria}")

        val bengkel = InterviewEvalGrader.grade(
            byName.getValue("bengkel"),
            InterviewEvalPacks.draftOf(InterviewEvalPacks.bengkelPack, InterviewEvalPacks.bengkelSession)
        )
        println(bengkel.logLine("kunci-jawaban"))
        assertTrue(bengkel.passed, "Kunci bengkel wajib lulus: ${bengkel.failedCriteria}")
    }

    @Test
    fun `baseline deterministik lulus 100 persen pada semua kasus emas`() = runBlocking {
        var passed = 0
        for (case in InterviewGoldenCases.all) {
            val flow = runInterviewFlow(case, guessFn = deterministicKamusSeam())
            println(flow.verdict.logLine("deterministik/kamus-v1"))
            assertTrue(flow.verdict.passed, "Baseline deterministik wajib 100% - kalau tidak, penilainya yang rusak: ${flow.verdict.failedCriteria}")
            passed++
        }
        println("evals | skor: $passed/${InterviewGoldenCases.all.size} (deterministik/kamus-v1, baseline)")
        assertEquals(InterviewGoldenCases.all.size, passed)
    }

    @Test
    fun `pack tanpa kamus tidak menebak dan pack berkamus menebak`() = runBlocking {
        // Skenario plan induk §11.3: tanpa kamus ⇒ pertanyaan terbuka, bukan tebakan karangan.
        val tanpaKamus = DeterministicInterviewGuesser.guess(
            InterviewStep.G1_DIVISI, InterviewEvalPacks.klinikPack,
            InterviewEvalPacks.draftOf(InterviewEvalPacks.klinikPack, null), "Kami klinik gigi."
        ).getOrThrow()
        assertTrue(tanpaKamus.isEmpty(), "Pack klinik tanpa roleHints wajib tidak menebak: $tanpaKamus")

        // Pack garment punya kamus (B2) ⇒ tebakan G1 tidak kosong.
        val berkamus = DeterministicInterviewGuesser.guess(
            InterviewStep.G1_DIVISI, GarmentDomainPack.pack,
            DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.FOB_FULL_PACKAGE),
            "Kami konveksi: admin gudang mengurus kain, kepala potong membagi kerja potong, operator jahit mengerjakan, qc memeriksa, dan packing mengirim."
        ).getOrThrow()
        assertTrue(berkamus.isNotEmpty(), "Pack garment berkamus wajib menebak divisi")
    }
}
