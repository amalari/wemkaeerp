package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Evals agent LLM hidup (plan §2 A8).** Dinilai dengan grader yang sama seperti `DiscoveryEvalsTest`:
 * `DiscoveryDraftValidator` + cakupan modul yang diharapkan. Skor dicetak dengan format log yang sama
 * (`evals | <agent> | <kasus> | PASS|FAIL | …`) supaya bisa dibandingkan langsung dengan baseline
 * deterministik dan dipakai mendeteksi regresi prompt/model.
 *
 * **Opt-in**: butuh `DISCOVERY_LIVE_EVALS=1` **dan** `DEEPSEEK_API_KEY`. Tanpa keduanya test dilewati
 * (`assumeTrue`) sehingga `:server:test` biasa tetap tanpa jaringan dan tanpa biaya. Dijalankan manual:
 *
 * ```
 * DISCOVERY_LIVE_EVALS=1 DEEPSEEK_API_KEY=sk-… ./gradlew :server:test --tests '*KoogDiscoveryLiveEvalsTest'
 * ```
 *
 * Jalur cadangan deterministik sengaja **dimatikan** di sini: evals harus mengukur LLM, bukan menyamarkan
 * kegagalannya dengan draf kata kunci.
 */
class KoogDiscoveryLiveEvalsTest {

    /**
     * [expectedCapabilities] = daftar kemampuan yang harus ada; **satu kumpulan sinonim** per kemampuan
     * (`antrean` vs `pendaftaran`). Agent deterministik menamai modul dari kata kunci narasi, agent LLM
     * menamainya secara semantik — dua-duanya benar, jadi grader menguji **kemampuannya**, bukan ejaannya.
     */
    private data class GoldenCase(
        val name: String,
        val narrative: String,
        val industryHint: String?,
        val expectedPackCode: String,
        val expectedCapabilities: List<Set<String>> = emptyList()
    )

    private val cases = listOf(
        GoldenCase(
            name = "klinik",
            narrative = "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
            industryHint = "klinik",
            expectedPackCode = "klinik",
            expectedCapabilities = listOf(
                setOf("antrean", "pendaftaran", "jadwal"),
                setOf("tagihan", "kasir", "pembayaran", "invoice")
            )
        ),
        GoldenCase(
            name = "bengkel",
            narrative = "Bengkel servis motor: pelanggan booking servis lewat telepon dan ada stok sparepart.",
            industryHint = "bengkel",
            expectedPackCode = "bengkel",
            expectedCapabilities = listOf(setOf("pesanan", "booking", "servis"))
        ),
        GoldenCase(
            name = "katering",
            narrative = "Katering harian: pesanan langganan tiap minggu dan laporan pengiriman bulanan.",
            industryHint = "katering",
            expectedPackCode = "katering",
            expectedCapabilities = listOf(setOf("pesanan"), setOf("laporan", "rekap", "pengiriman"))
        ),
        GoldenCase(
            name = "garment-cmt",
            narrative = "Kami konveksi makloon, kain dari buyer, cukup jahit saja.",
            industryHint = null,
            expectedPackCode = "garment"
        )
    )

    @Test
    fun `narasi emas menghasilkan draf sah dari model hidup`() = runBlocking {
        assumeTrue("DISCOVERY_LIVE_EVALS bukan 1 — evals LLM hidup dilewati", System.getenv("DISCOVERY_LIVE_EVALS") == "1")
        val apiKey = System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
        assumeTrue("DEEPSEEK_API_KEY kosong — evals LLM hidup dilewati", apiKey != null)

        val agent = DiscoveryAgents.from(
            configured = DiscoveryAgents.KOOG,
            apiKey = apiKey,
            modelId = System.getenv("DISCOVERY_AGENT_MODEL"),
            fallbackEnabled = false
        )
        var passed = 0

        for (c in cases) {
            val result = agent.draft(DiscoveryRequest(c.narrative, c.industryHint))
            val graded = result.fold(
                onSuccess = { draft ->
                    val issues = DiscoveryDraftValidator.validate(draft)
                    val moduleIds = draft.pack.modules.map { it.id.value }
                    val coverage = draft.pack.code.value == c.expectedPackCode &&
                        c.expectedCapabilities.all { synonyms ->
                            moduleIds.any { id -> synonyms.any { suffix -> id.endsWith("_$suffix") } }
                        }
                    val ok = issues.isEmpty() && coverage
                    Triple(ok, "pack=${draft.pack.code.value} modules=$moduleIds", issues.map { it.path }.toString())
                },
                onFailure = { Triple(false, "gagal: ${it.message}", "-") }
            )
            if (graded.first) passed++
            println(
                "evals | ${agent.agentRef} | ${c.name} | ${if (graded.first) "PASS" else "FAIL"} | " +
                    "${graded.second} issues=${graded.third}"
            )
        }

        println("evals | skor: $passed/${cases.size} (${agent.agentRef})")
        assertTrue(passed > 0, "Tidak satu pun narasi emas lolos — prompt/kontrak atau kunci API yang salah")
        assertEquals(cases.size, passed, "Skor $passed/${cases.size} — lihat baris FAIL di log evals")
    }
}
