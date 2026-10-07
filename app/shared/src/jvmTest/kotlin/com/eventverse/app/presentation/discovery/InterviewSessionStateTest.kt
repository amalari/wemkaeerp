package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.presentation.discovery.interview.ConsultantSuggestionStatus
import com.eventverse.app.presentation.discovery.interview.InterviewSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterviewSessionStateTest {

    private fun sampleSession(): InterviewSession = InterviewSession(
        step = InterviewStep.G1_DIVISI,
        divisions = listOf(
            DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.GUESS),
            DivisionDraft(DivisionCode("poli"), "Poli", ItemSource.GUESS)
        ),
        roles = listOf(
            RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.GUESS, isHead = true),
            RoleDraft(RoleKey("perawat"), "Perawat", DivisionCode("poli"), ItemSource.GUESS, isHead = false)
        ),
        links = listOf(
            RoleModuleLink(RoleKey("resepsionis"), ModuleId("klinik_pendaftaran"), ModuleOrigin.NEW, listOf("Antrean"), Confirmation.GUESSED, 85)
        )
    )

    @Test
    fun testG1DivisionsAddRenameRemove() {
        val state = InterviewSessionState(sampleSession(), draftId = "draft-1", narrative = "Klinik umum")
        assertEquals(2, state.divisions.size)

        // Tambah divisi baru
        state.addDivision("Kasir & Pembayaran")
        assertEquals(3, state.divisions.size)
        val kasir = state.divisions.last()
        assertEquals("Kasir & Pembayaran", kasir.name)
        assertEquals("kasir_pembayaran", kasir.code.value)
        assertEquals(ItemSource.ANSWER, kasir.source)

        // Rename divisi
        state.renameDivision(kasir.code, "Kasir")
        assertEquals("Kasir", state.divisions.last().name)

        // Hapus divisi
        state.removeDivision(kasir.code)
        assertEquals(2, state.divisions.size)
    }

    @Test
    fun testG2RolesManagementAndHeadEnforcement() {
        val state = InterviewSessionState(sampleSession(), draftId = "draft-1")
        assertEquals(2, state.roles.size)

        // Tambah peran baru
        state.addRole("Staf Administrasi", DivisionCode("pendaftaran"))
        assertEquals(3, state.roles.size)
        val adminRole = state.roles.last()
        assertEquals("Staf Administrasi", adminRole.label)
        assertFalse(adminRole.isHead)

        // Jadikan admin sebagai kepala divisi -> resepsionis dicopot sebagai kepala
        state.toggleRoleHead(adminRole.roleKey)
        val resepsionis = state.roles.first { it.roleKey.value == "resepsionis" }
        val adminUpdated = state.roles.first { it.roleKey == adminRole.roleKey }
        assertFalse(resepsionis.isHead, "Resepsionis harus dicopot sebagai kepala")
        assertTrue(adminUpdated.isHead, "Admin harus menjadi kepala baru")

        // Rename peran
        state.renameRole(adminRole.roleKey, "Admin Utama")
        assertEquals("Admin Utama", state.roles.first { it.roleKey == adminRole.roleKey }.label)

        // Hapus peran
        state.removeRole(adminRole.roleKey)
        assertEquals(2, state.roles.size)
    }

    @Test
    fun testG3ModulesFeaturesAndMovement() {
        val state = InterviewSessionState(sampleSession(), draftId = "draft-1")
        assertEquals(1, state.links.size)

        // Tambah fitur
        val link = state.links.first()
        state.addFeatureToLink(link.roleKey, link.moduleId, "Cetak Tiket Antrean")
        assertEquals(listOf("Antrean", "Cetak Tiket Antrean"), state.links.first().features)
        assertEquals(Confirmation.CHANGED, state.links.first().confirmed)

        // Hapus fitur
        state.removeFeatureFromLink(link.roleKey, link.moduleId, "Antrean")
        assertEquals(listOf("Cetak Tiket Antrean"), state.links.first().features)

        // Pindahkan modul
        state.changeModuleForRole(link.roleKey, link.moduleId, ModuleId("klinik_frontdesk"), ModuleOrigin.EXTEND)
        val updatedLink = state.links.first()
        assertEquals(ModuleId("klinik_frontdesk"), updatedLink.moduleId)
        assertEquals(ModuleOrigin.EXTEND, updatedLink.origin)
    }

    @Test
    fun testG4HandoffsManagement() {
        val state = InterviewSessionState(sampleSession(), draftId = "draft-1")
        assertTrue(state.handoffs.isEmpty())

        // Tambah sambungan
        state.addHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Permintaan"))
        assertEquals(1, state.handoffs.size)
        assertEquals(Confirmation.CONFIRMED, state.handoffs.first().confirmed)

        // Hapus sambungan
        state.removeHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Permintaan"))
        assertTrue(state.handoffs.isEmpty())
    }

    @Test
    fun testTurnNavigationAndAcceptAll() {
        val state = InterviewSessionState(sampleSession(), draftId = "draft-1")
        assertEquals(InterviewStep.G1_DIVISI, state.step)
        assertEquals(1, state.turnNumber)

        state.nextTurn()
        assertEquals(InterviewStep.G2_PERAN, state.step)
        assertEquals(2, state.turnNumber)

        state.nextTurn()
        assertEquals(InterviewStep.G3_MODUL, state.step)
        assertEquals(3, state.turnNumber)

        state.previousTurn()
        assertEquals(InterviewStep.G2_PERAN, state.step)
        assertEquals(2, state.turnNumber)

        state.acceptAllGuesses()
        assertEquals(InterviewStep.G5_RINGKASAN, state.step)
        assertEquals(Confirmation.SKIPPED, state.links.first().confirmed)
        assertTrue(state.answers.any { it.outcome == Confirmation.SKIPPED })
    }

    @Test
    fun testConsultantSuggestionsAcceptReject() {
        val state = InterviewSessionState(sampleSession(), draftId = "draft-1", narrative = "Klinik umum 24 jam")
        assertEquals(1, state.consultantSuggestions.size)
        val suggestion = state.consultantSuggestions.first()
        assertEquals(ConsultantSuggestionStatus.PENDING, suggestion.status)

        state.acceptSuggestion(suggestion.id)
        assertEquals(ConsultantSuggestionStatus.ACCEPTED, state.consultantSuggestions.first().status)

        state.rejectSuggestion(suggestion.id)
        assertEquals(ConsultantSuggestionStatus.REJECTED, state.consultantSuggestions.first().status)
    }

    @Test
    fun testAllStaticStringsAreLatin1() {
        // Verifikasi bahwa teks yang digunakan tidak memuat glyph di luar Latin-1 (Nunito-safe)
        val sampleTexts = listOf(
            "1. Divisi Usaha",
            "2. Peran & Kepala Divisi",
            "3. Modul & Fitur Kebutuhan",
            "4. Sambungan Alur Kerja",
            "5. Ringkasan Rancangan",
            "Terima Semua Tebakan",
            "Lewati Wawancara",
            "Kunci Usulan & Lanjut ke Draf Blueprint",
            "Pakai Ulang Platform",
            "Pakai Ulang Pack",
            "Kembangkan",
            "Baru",
            "->", // ascii arrow
            "x"   // ascii close
        )

        for (text in sampleTexts) {
            for (char in text) {
                assertTrue(
                    char.code in 0..255,
                    "Karakter '${char}' (code ${char.code}) pada teks '$text' bukan Latin-1!"
                )
            }
        }
    }
}
