package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.infrastructure.discovery.KoogDiscoveryPrompt
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Penilai evals dites sendiri** (plan SP-C4): kasus lulus/gagal **buatan tangan** untuk tiap kriteria §6,
 * supaya skor baseline dan skor LLM bisa dipercaya — grader yang salah justru meloloskan agent buruk atau
 * menuduh agent baik. Draf diambil dari sumber nyata (`DeterministicDiscoveryAgent`, contoh prompt) lalu
 * dirusak satu kriteria per kasus.
 */
class DiscoveryEvalGraderTest {

    private suspend fun klinikDraft() =
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik gigi dengan antrean dan tagihan.", "klinik")).getOrThrow()

    private val klinikCase = DiscoveryGoldenCases.all.first { it.name == "klinik" }
    private val bengkelCase = DiscoveryGoldenCases.all.first { it.name == "bengkel" }
    private val jasaItCase = DiscoveryGoldenCases.all.first { it.name == "jasa-it" }

    @Test
    fun `kasus sehat menghasilkan keenam kriteria dan verdict PASS`() = runBlocking {
        val verdict = DiscoveryEvalGrader.grade(klinikCase, Result.success(klinikDraft()))

        assertTrue(verdict.passed, "Baseline deterministik untuk klinik wajib lulus: ${verdict.criteria.filter { !it.passed }}")
        assertEquals(
            setOf("valid", "cakupan_modul", "jenis_tampilan", "status_bermakna", "field_memadai", "kemurnian_vertikal"),
            verdict.criteria.map { it.criterion }.toSet()
        )
        assertTrue(verdict.logLine("uji/deterministik").startsWith("evals | uji/deterministik | klinik | PASS | "))
    }

    @Test
    fun `draft tanpa layar membuat kriteria isi layar kosong secara jujur`() = runBlocking {
        val case = DiscoveryGoldenCases.all.first { it.name == "retail" }
        val draft = DeterministicDiscoveryAgent()
            .draft(DiscoveryRequest(case.narrative, case.industryHint)).getOrThrow()
            .copy(screens = emptyList())

        val verdict = DiscoveryEvalGrader.grade(case, Result.success(draft))

        assertTrue(verdict.passed)
        verdict.criteria.filter { it.criterion in setOf("jenis_tampilan", "status_bermakna", "field_memadai") }
            .forEach { assertTrue(it.detail.contains("tidak ada layar"), "${it.criterion} wajib menyebut sebabnya") }
    }

    @Test
    fun `baseline pasca-B3 berlayar dan kriteria isi layar dinilai nyata`() = runBlocking {
        val draft = klinikDraft()

        assertTrue(draft.screens.isNotEmpty(), "Pasca-B3 draf deterministik membawa layar ber-proposal")
        val verdict = DiscoveryEvalGrader.grade(klinikCase, Result.success(draft))

        assertTrue(verdict.passed, "Layar deterministik wajib lulus kriteria §6: ${verdict.criteria.filter { !it.passed }}")
        val jenis = verdict.criteria.first { it.criterion == "jenis_tampilan" }
        assertTrue(jenis.detail.contains("layar memakai jenis"), "Kriteria dinilai nyata, bukan kosong: ${jenis.detail}")
        val status = verdict.criteria.first { it.criterion == "status_bermakna" }
        assertTrue(status.detail.contains("status layar"), "Status layar benar-benar diperiksa: ${status.detail}")
    }

    @Test
    fun `cakupan meleset menghasilkan FAIL pada kriteria cakupan`() = runBlocking {
        val verdict = DiscoveryEvalGrader.grade(bengkelCase, Result.success(klinikDraft()))

        assertTrue(!verdict.passed)
        val cakupan = verdict.criteria.first { it.criterion == "cakupan_modul" }
        assertTrue(!cakupan.passed)
        assertTrue(cakupan.detail.contains("pack diharapkan bengkel"))
        assertTrue(verdict.criteria.first { it.criterion == "valid" }.passed, "Kriteria lain tidak ikut tertuduh")
    }

    @Test
    fun `istilah konveksi di proposal ditolak penilai kemurnian vertikal`() {
        val draft = KoogDiscoveryPrompt.exampleDraft().let { d ->
            d.copy(
                screens = d.screens.mapIndexed { i, s ->
                    if (i == 0) s.copy(proposal = s.proposal?.copy(rationale = "Dipilih karena alur SPK sampling berjalan cepat."))
                    else s
                }
            )
        }

        val verdict = DiscoveryEvalGrader.grade(jasaItCase, Result.success(draft))

        val kemurnian = verdict.criteria.first { it.criterion == "kemurnian_vertikal" }
        assertTrue(!kemurnian.passed, "Istilah SPK wajib terdeteksi: ${kemurnian.detail}")
        assertTrue(kemurnian.detail.contains("SPK", ignoreCase = true))
    }

    @Test
    fun `pack garment tidak dinilai kemurniannya`() = runBlocking {
        val garmentCase = DiscoveryGoldenCases.all.first { it.name == "garment-cmt" }
        val draft = DeterministicDiscoveryAgent()
            .draft(DiscoveryRequest(garmentCase.narrative, garmentCase.industryHint)).getOrThrow()
        val verdict = DiscoveryEvalGrader.grade(garmentCase, Result.success(draft))

        val kemurnian = verdict.criteria.first { it.criterion == "kemurnian_vertikal" }
        assertTrue(kemurnian.passed)
        assertEquals("pack garment — tidak dinilai", kemurnian.detail)
        assertTrue(verdict.passed, "Draf garment bawaan wajib lulus semua kriteria: ${verdict.criteria.filter { !it.passed }}")
    }

    private fun withTransitions(transitions: (List<String>) -> Map<String, List<String>>) =
        KoogDiscoveryPrompt.exampleDraft().let { d ->
            d.copy(
                screens = d.screens.map { s ->
                    val p = s.proposal ?: return@map s
                    val options = p.entity?.fields?.firstOrNull { it.key == p.entity?.statusField }?.options ?: return@map s
                    s.copy(proposal = p.copy(entity = p.entity?.copy(transitions = transitions(options))))
                }
            )
        }

    @Test
    fun `siklus penuh lolos karena alur bolak-balik sah (rekalibrasi 2026-10-07)`() {
        val verdict = DiscoveryEvalGrader.grade(jasaItCase, Result.success(withTransitions { o -> o.associateWith { from -> o.filter { it != from } } }))
        val status = verdict.criteria.first { it.criterion == "status_bermakna" }
        assertTrue(status.passed, "Level stok/status bayar boleh bolak-balik: ${status.detail}")
    }

    @Test
    fun `status yatim yang tak terhubung ke alur ditandai penilai status`() {
        val verdict = DiscoveryEvalGrader.grade(jasaItCase, Result.success(withTransitions { o -> mapOf(o[0] to listOf(o[1])) }))
        val status = verdict.criteria.first { it.criterion == "status_bermakna" }
        assertTrue(!status.passed, "Status tanpa hubungan ke alur wajib ditandai: ${status.detail}")
        assertTrue(status.detail.contains("yatim"))
    }

    @Test
    fun `kode pack bebas bila tanpa petunjuk dan bukan garment tetapi persis bila ada petunjuk`() {
        val draft = KoogDiscoveryPrompt.exampleDraft()           // kode pack 'contoh'
        val coverage = { case: DiscoveryGoldenCase -> DiscoveryEvalGrader.grade(case, Result.success(draft)).criteria.first { it.criterion == "cakupan_modul" } }
        assertTrue(coverage(jasaItCase.copy(industryHint = null, expectedPackCode = "kustom", expectedCapabilities = emptyList())).passed)
        assertTrue(!coverage(jasaItCase.copy(industryHint = "klinik", expectedPackCode = "klinik", expectedCapabilities = emptyList())).passed)
        assertTrue(!coverage(DiscoveryGoldenCases.all.first { it.name == "garment-cmt" }.copy(expectedBlueprintCode = null)).passed, "garment selalu wajib pack garment")
    }

    @Test
    fun `entity tanpa field teks ditandai penilai field`() {
        val draft = KoogDiscoveryPrompt.exampleDraft().let { d ->
            d.copy(
                screens = d.screens.map { s ->
                    val p = s.proposal ?: return@map s
                    val entity = p.entity?.copy(
                        fields = listOf(
                            FieldProposal("jumlah", "Jumlah", FieldType.NUMBER, required = true),
                            FieldProposal("status", "Status", FieldType.ENUM, options = listOf("Baru", "Selesai"))
                        ),
                        statusField = "status"
                    )
                    s.copy(proposal = p.copy(entity = entity))
                }
            )
        }

        val verdict = DiscoveryEvalGrader.grade(jasaItCase, Result.success(draft))

        val field = verdict.criteria.first { it.criterion == "field_memadai" }
        assertTrue(!field.passed, "Entity tanpa field teks wajib ditandai: ${field.detail}")
    }

    @Test
    fun `jenis tampilan di luar himpunan per peran ditandai penilai jenis`() {
        val ketat = jasaItCase.copy(
            expectedCapabilities = listOf(setOf("pesanan")),
            allowedWidgets = mapOf("pesanan" to setOf("KANBAN", "CHECKLIST"))
        )
        val verdict = DiscoveryEvalGrader.grade(ketat, Result.success(KoogDiscoveryPrompt.exampleDraft()))

        val jenis = verdict.criteria.first { it.criterion == "jenis_tampilan" }
        assertTrue(!jenis.passed, "TABLE/FORM di luar himpunan {KANBAN, CHECKLIST} wajib ditandai: ${jenis.detail}")
    }

    @Test
    fun `hasil gagal total menghasilkan satu kriteria valid yang gagal`() {
        val verdict = DiscoveryEvalGrader.grade(klinikCase, Result.failure(DiscoveryDraftGagal()))

        assertTrue(!verdict.passed)
        assertEquals(listOf("valid"), verdict.criteria.map { it.criterion })
        assertTrue(verdict.logLine("uji").contains("FAIL"))
        assertTrue(verdict.logLine("uji").contains("gagal:"))
    }

    /** Kasus lulus/gagal buatan tangan: transisi siklus penuh (Baru→Diproses→Selesai→Baru). */
    private class DiscoveryDraftGagal : RuntimeException("simulasi agent gagal total")
}
