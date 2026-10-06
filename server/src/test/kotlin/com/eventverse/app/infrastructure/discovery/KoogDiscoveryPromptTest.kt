package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti plan §2 A8** untuk prompt: contoh kerangka yang ditanam ke prompt adalah dokumen **sah**
 * (dibangkitkan `DiscoveryDraftCodec`, bukan teks tempelan), dan pesan koreksi benar-benar membawa
 * galat berpath ke model.
 */
class KoogDiscoveryPromptTest {

    @Test
    fun `contoh kerangka prompt sah menurut validator produksi`() {
        val draft = DiscoveryDraftCodec.decode(KoogDiscoveryPrompt.exampleDraftJson())

        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draft))
        assertEquals("contoh", draft.pack.code.value, "Contoh tidak boleh memakai kode pack vertikal nyata")
        assertTrue(draft.screens.isNotEmpty(), "Contoh wajib menunjukkan bentuk screens")
        assertTrue(draft.pack.modules.all { it.id.value.startsWith("contoh_") })
    }

    @Test
    fun `prompt sistem menutup kosakata widget dan melarang penulisan ulang pack bawaan`() {
        val system = KoogDiscoveryPrompt.system

        assertTrue(system.contains("FORM, TABLE, KANBAN, DASHBOARD, CHECKLIST, PRINT, CUSTOM_SCREEN"))
        assertTrue(system.contains("useShipped"))
        assertTrue(system.contains("platform_modules()"))
        assertTrue(system.contains("validate_draft(draft)"))
        assertTrue(system.contains("screen_catalog()"))
        assertTrue(system.contains("berprefiks `<pack.code>_`"))
        assertTrue(system.contains("\$.pack.modules[2].id"), "Contoh galat berpath wajib tampil apa adanya, bukan escape")
        assertTrue(system.contains("maksimal sekali\n"), "Anggaran langkah per alat wajib tertulis")
        // A4: istilah chrome & label aksi adalah data pack — model wajib tahu, dan tahu batasnya.
        assertTrue(system.contains("pack.vocabulary"))
        assertTrue(system.contains("\"WORKPLACE\":\"klinik\""))
        assertTrue(system.contains("Jangan pakai istilah konveksi"))
        assertTrue(system.contains("`pack.actions`"))
        // SP-C1: aturan proposal & pemilihan widget dari watak kerja modul.
        assertTrue(system.contains("\"screenId\",\"moduleId\",\"title\",\"widget\",\"rationale\",\"entity\",\"view\",\"seed\",\"source\""))
        assertTrue(system.contains("Dipilih karena"))
        assertTrue(system.contains("statusField"))
        assertTrue(system.contains("salin PERSIS blok \"source\""))
    }

    @Test
    fun `contoh prompt membawa proposal sah bersumber agent yang disebut`() {
        val ref = "koog/deepseek-v4-flash/draft-v2"
        val draft = DiscoveryDraftCodec.decode(KoogDiscoveryPrompt.exampleDraftJson(ref))

        assertTrue(draft.screens.isNotEmpty())
        assertTrue(draft.screens.all { it.proposal != null }, "Contoh wajib menunjukkan bentuk proposal")
        assertTrue(
            draft.screens.all { it.source == ProposalSource.Agent(ref) },
            "Blok source contoh wajib membawa agentRef yang disuntik"
        )
        val entities = draft.screens.mapNotNull { it.proposal?.entity }
        assertTrue(entities.size >= 2)
        assertEquals(entities.first(), entities.last(), "Dua layar contoh memakai entity yang didefinisikan identik")
        assertTrue(entities.first().fields.size <= 12)
        assertTrue(draft.screens.maxOf { it.proposal!!.seed.size } <= 8, "Seed contoh wajib patuh batasnya sendiri")
    }

    @Test
    fun `panjang prompt dicatat dan dibatasi`() {
        val system = KoogDiscoveryPrompt.system
        val example = KoogDiscoveryPrompt.exampleDraftJson()

        // Batas tertulis (SP-C0 §4): sistem < 8 rb karakter, contoh < 12 rb — hemat token tiap putaran.
        println("prompt | sistem=${system.length} karakter, contoh=${example.length} karakter")
        assertTrue(system.length <= 8_000, "Prompt sistem membengkak: ${system.length} karakter")
        assertTrue(example.length <= 12_000, "Contoh dokumen membengkak: ${example.length} karakter")
    }

    @Test
    fun `umpan balik koreksi dipotong jumlah dan panjangnya`() {
        val request = DiscoveryRequest("Klinik gigi dengan antrean dan tagihan.", "klinik")
        val banyak = (1..15).map { i ->
            DiscoveryValidationIssue("$.screens[$i].proposal.rationale", "galat ke-$i " + "x".repeat(400))
        }

        val message = KoogDiscoveryPrompt.userMessage(request, banyak, previousAnswer = null, round = 2)

        assertTrue(message.contains("Putaran koreksi ke-2"))
        assertTrue(message.contains("$.screens[12].proposal.rationale"), "12 galat teratas wajib tercantum")
        assertTrue(!message.contains("$.screens[13].proposal.rationale"), "Galat ke-13 ke bawah dipotong")
        assertTrue(message.contains("(+3 galat lain"), "Sisa galat dirangkum, bukan dibuang diam-diam")
        // Cap panjang berlaku ke baris galat, bukan ke contoh dokumen (JSON satu baris memang panjang).
        val longestFeedbackLine = message.lines().filter { it.startsWith("- $.") }.maxOf { it.length }
        assertTrue(
            longestFeedbackLine <= 300,
            "Tidak ada baris galat yang melebihi cap karakter: $longestFeedbackLine"
        )
    }

    @Test
    fun `pesan pengguna memuat narasi, petunjuk industri, dan galat berpath pada putaran koreksi`() {
        val request = DiscoveryRequest("Bengkel servis motor dengan booking pesanan lewat telepon.", "bengkel")

        val first = KoogDiscoveryPrompt.userMessage(request, emptyList(), null, round = 1)
        assertTrue(first.contains("Bengkel servis motor dengan booking pesanan lewat telepon."))
        assertTrue(first.contains("Petunjuk industri: bengkel"))
        assertTrue(first.contains("Susun dokumen untuk narasi di atas"))

        val correction = KoogDiscoveryPrompt.userMessage(
            request,
            listOf(DiscoveryValidationIssue("$.pack.modules[0].id", "Modul baru wajib berprefiks 'bengkel_'")),
            previousAnswer = """{"pack":{}}""",
            round = 2
        )
        assertTrue(correction.contains("Putaran koreksi ke-2"))
        assertTrue(correction.contains("$.pack.modules[0].id"))
        assertTrue(correction.contains("""{"pack":{}}"""), "Jawaban sebelumnya wajib ikut supaya model bisa mengoreksi")
    }
}
