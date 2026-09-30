package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
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
        assertTrue(system.contains("berprefiks `<pack.code>_`"))
        assertTrue(system.contains("\$.pack.modules[2].id"), "Contoh galat berpath wajib tampil apa adanya, bukan escape")
        assertTrue(system.contains("panggil `platform_modules()` maksimal sekali"))
        // A4: istilah chrome & label aksi adalah data pack — model wajib tahu, dan tahu batasnya.
        assertTrue(system.contains("pack.vocabulary"))
        assertTrue(system.contains("\"WORKPLACE\":\"klinik\""))
        assertTrue(system.contains("Jangan pakai istilah konveksi"))
        assertTrue(system.contains("`pack.actions`"))
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
