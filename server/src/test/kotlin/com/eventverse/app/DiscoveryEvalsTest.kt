package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Evals generator discovery (plan §2 A9).** Narasi emas dari empat vertikal digrade dengan dua kriteria
 * yang sama dengan yang dipakai produksi:
 *
 * 1. **Validator** (`DiscoveryDraftValidator`) — draf rusak tidak boleh lolos ke penyimpanan;
 * 2. **Cakupan modul yang diharapkan** — kode pack + kemampuan (suffix modul) yang seharusnya tertangkap.
 *
 * Agent deterministik adalah baseline: skor LLM (A8, `KoogDiscoveryAgent`) dicatat dengan format log
 * `evals | <kasus> | PASS|FAIL | <detail>` yang sama, sehingga regresi prompt/model ketahuan sebelum
 * ganti model. Deterministik harus selalu 4/4 — kalau tidak, grader-nya yang rusak.
 */
class DiscoveryEvalsTest {

    private data class GoldenCase(
        val name: String,
        val narrative: String,
        val industryHint: String?,
        val expectedPackCode: String,
        val expectedModuleSuffixes: Set<String> = emptySet(),
        val expectedBlueprintCode: String? = null
    )

    private val cases = listOf(
        GoldenCase(
            name = "klinik",
            narrative = "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
            industryHint = "klinik",
            expectedPackCode = "klinik",
            expectedModuleSuffixes = setOf("antrean", "tagihan")
        ),
        GoldenCase(
            name = "bengkel",
            narrative = "Bengkel servis motor, pelanggan bisa booking pesanan servis lewat telepon.",
            industryHint = "bengkel",
            expectedPackCode = "bengkel",
            expectedModuleSuffixes = setOf("pesanan")
        ),
        GoldenCase(
            name = "katering",
            narrative = "Katering harian: pesanan langganan tiap minggu dan laporan pengiriman bulanan.",
            industryHint = "katering",
            expectedPackCode = "katering",
            expectedModuleSuffixes = setOf("pesanan", "laporan")
        ),
        GoldenCase(
            name = "garment-cmt",
            narrative = "Kami konveksi makloon, kain dari buyer, cukup jahit saja.",
            industryHint = null,
            expectedPackCode = "garment",
            expectedBlueprintCode = "cmt_makloon"
        )
    )

    @Test
    fun `semua narasi emas lolos validator dan cakupan`() = runBlocking {
        val agent = DeterministicDiscoveryAgent()
        var passed = 0

        for (c in cases) {
            val draft = agent.draft(DiscoveryRequest(c.narrative, c.industryHint)).getOrThrow()
            val issues = DiscoveryDraftValidator.validate(draft)

            val moduleIds = draft.pack.modules.map { it.id.value }
            val coverageOk = c.expectedModuleSuffixes.all { suffix -> moduleIds.any { it.endsWith("_$suffix") } } &&
                draft.pack.code.value == c.expectedPackCode &&
                (c.expectedBlueprintCode == null || draft.blueprint.code.value == c.expectedBlueprintCode)

            val ok = issues.isEmpty() && coverageOk
            println("evals | ${c.name} | ${if (ok) "PASS" else "FAIL"} | pack=${draft.pack.code.value} modules=$moduleIds issues=${issues.map { it.path }}")
            assertTrue(ok, "Kasus ${c.name} gagal: issues=$issues coverageOk=$coverageOk")
            passed++
        }

        println("evals | skor: $passed/${cases.size} (baseline deterministik/keyword-v1)")
        assertEquals(cases.size, passed)
    }
}
