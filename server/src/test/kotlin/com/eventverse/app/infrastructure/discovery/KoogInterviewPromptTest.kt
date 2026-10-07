package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti AC C1** (plan IV-C): contoh dokumen di prompt **dirakit dari kode** dan wajib lolos validator
 * penuh — contoh yang melenceng dari kontrak tidak boleh sampai ke model. Persona konsultan (C6)
 * dites dari sisi yang bisa diuji tanpa LLM: aturan F0-F2, tebak-dulu, katalog, dan useCurrent tertulis.
 */
class KoogInterviewPromptTest {

    @Test
    fun `contoh sesi lolos interview validator terhadap pack contoh`() {
        val issues = InterviewValidator.validate(KoogInterviewPrompt.exampleSession(), KoogInterviewPrompt.examplePack())
        assertTrue(issues.isEmpty(), "Contoh wajib bersih: $issues")
    }

    @Test
    fun `contoh draf utuh lolos discovery draft validator penuh`() {
        val issues = DiscoveryDraftValidator.validate(KoogInterviewPrompt.exampleDraft())
        assertTrue(issues.isEmpty(), "Contoh draf wajib bersih: $issues")
    }

    @Test
    fun `contoh json dibangun lewat parser produksi dan terbaca lagi utuh`() {
        val json = KoogInterviewPrompt.exampleInterviewJson()
        val decoded = KoogInterviewBridge.decodeInterviewAnswer(json, KoogInterviewPrompt.exampleDraft())
        assertEquals(KoogInterviewPrompt.exampleSession(), decoded, "encode-decode round-trip harus identik")
    }

    @Test
    fun `prompt sistem memuat persona konsultan dan aturan keras`() {
        val system = KoogInterviewPrompt.system.lowercase()
        for (must in listOf("f0", "f1", "f2", "konsultan", "2-3 pilihan", "interview_catalog", "usecurrent", "asal jujur", "lewati")) {
            assertTrue(system.contains(must), "Prompt sistem wajib menyebut '$must'")
        }
        assertTrue("g1" in system && "g5" in system, "Terjemahan G1-G5 disebut")
    }

    @Test
    fun `pesan pengguna memuat narasi keadaan langkah dan contoh`() {
        val draft = KoogInterviewPrompt.exampleDraft()
        val message = KoogInterviewPrompt.userMessage(
            InterviewStep.G1_DIVISI, draft, "Kami bengkel servis motor.", emptyList(), null, 1
        )
        assertTrue(message.contains("bengkel servis motor"))
        assertTrue(message.contains(KoogInterviewBridge.interviewStateJson(draft)), "Keadaan pakai jembatan yang sama dengan alat")
        assertTrue(message.contains(KoogInterviewPrompt.stepInstruction(InterviewStep.G1_DIVISI)))
        assertTrue(message.contains(KoogInterviewPrompt.exampleInterviewJson()))
    }

    @Test
    fun `pesan koreksi membawa galat berpath dan jawaban sebelumnya terpotong`() {
        val draft = KoogInterviewPrompt.exampleDraft()
        val feedback = listOf(
            com.eventverse.app.domain.discovery.DiscoveryValidationIssue("$.interview.links[2].origin", "asal tidak jujur")
        )
        val message = KoogInterviewPrompt.userMessage(
            InterviewStep.G3_MODUL, draft, "narasi", feedback, "jawaban-lama", 2
        )
        assertTrue(message.contains("Putaran koreksi ke-2"))
        assertTrue(message.contains("$.interview.links[2].origin"))
        assertTrue(message.contains("asal tidak jujur"))
        assertTrue(message.contains("jawaban-lama"))
    }

    @Test
    fun `instruksi langkah G5 tidak meminta tebakan`() {
        assertTrue(KoogInterviewPrompt.stepInstruction(InterviewStep.G5_RINGKASAN).contains("useCurrent"))
        assertTrue(KoogInterviewPrompt.stepInstruction(InterviewStep.DONE).contains("useCurrent"))
    }
}
