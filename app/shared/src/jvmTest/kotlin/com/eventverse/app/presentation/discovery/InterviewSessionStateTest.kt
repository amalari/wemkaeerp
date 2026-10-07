package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.BusinessProfile
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.presentation.discovery.interview.ConsultantSuggestion
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
        // Tidak ada saran rekaan: saran hanya muncul bila ada sumbernya (bukan dikodekan tetap untuk narasi apa pun).
        assertTrue(state.consultantSuggestions.isEmpty())
        state.consultantSuggestions.add(ConsultantSuggestion("sug_uji", "Catat hasil harian", "Anda menyebut sering salah hitung", "Narasi", recommendedModuleId = null))
        val suggestion = state.consultantSuggestions.first()
        assertEquals(ConsultantSuggestionStatus.PENDING, suggestion.status)

        state.acceptSuggestion(suggestion.id)
        assertEquals(ConsultantSuggestionStatus.ACCEPTED, state.consultantSuggestions.first().status)

        state.rejectSuggestion(suggestion.id)
        assertEquals(ConsultantSuggestionStatus.REJECTED, state.consultantSuggestions.first().status)
    }

    @Test
    fun testB7SessionStateCarriesProfileSpecsAndBasisRefOnAdditions() {
        val profile = BusinessProfile(
            summary = "Bengkel Motor Terpadu",
            goals = listOf("Antrean servis teratur"),
            painPoints = listOf("Sparepart sering selisih")
        )
        val spec = RequirementSpec(
            areaKey = RoleKey("mekanik"),
            whoFills = "Kepala Mekanik",
            whatRecorded = "Sparepart terpakai",
            whoSees = "Kasir & Gudang",
            doneWhen = "Motor selesai diservis",
            basisRef = BasisRef(Basis.NARASI, quote = "Sparepart sering selisih")
        )
        val sessionWithB7 = sampleSession().copy(
            version = 2,
            profile = profile,
            specs = listOf(spec)
        )

        val state = InterviewSessionState(sessionWithB7, draftId = "draft-b7", narrative = "Bengkel motor terpadu butuh QC")
        assertEquals(2, state.version)
        assertEquals("Bengkel Motor Terpadu", state.profile?.summary)
        assertEquals(1, state.specs.size)
        assertEquals("mekanik", state.specs.first().areaKey.value)

        // Tambah divisi -> tanpa basisRef dari klien (server membubuhkan JAWABAN dengan id pertanyaan nyata)
        state.addDivision("Gudang Sparepart")
        val addedDiv = state.divisions.last()
        assertEquals("gudang_sparepart", addedDiv.code.value)
        assertNull(addedDiv.basisRef, "id lokal rekaan akan ditolak validator server")

        // Tambah peran -> idem
        state.addRole("Admin Gudang", addedDiv.code)
        val addedRole = state.roles.last()
        assertEquals("Admin Gudang", addedRole.label)
        assertNull(addedRole.basisRef, "id lokal rekaan akan ditolak validator server")

        // Tambah sambungan -> idem
        state.addHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Permintaan"))
        val addedHandoff = state.handoffs.last()
        assertNull(addedHandoff.basisRef, "id lokal rekaan akan ditolak validator server")

        // Ubah modul tautan -> idem
        val oldLink = state.links.first()
        state.changeModuleForRole(oldLink.roleKey, oldLink.moduleId, ModuleId("klinik_registrasi"), ModuleOrigin.EXTEND)
        val changedLink = state.links.first()
        assertNull(changedLink.basisRef, "id lokal rekaan akan ditolak validator server")

        // Ekspor toSession
        val exported = state.toSession()
        assertEquals(2, exported.version)
        assertEquals("Bengkel Motor Terpadu", exported.profile?.summary)
        assertEquals(1, exported.specs.size)
    }

    @Test
    fun testConsultantPhaseF0F2NavigationAndMutation() {
        val session = sampleSession().copy(step = InterviewStep.F0_BISNIS)
        val state = InterviewSessionState(session, draftId = "draft-f0", narrative = "Klinik 24 Jam")
        assertEquals(InterviewStep.F0_BISNIS, state.step)
        assertEquals(1, state.turnNumber)

        // Mutasi F0: Profile Summary
        state.updateProfileSummary("Klinik Pratama Rawat Inap")
        assertEquals("Klinik Pratama Rawat Inap", state.profile?.summary)

        // Navigasi ke F1
        state.nextTurn()
        assertEquals(InterviewStep.F1_TUJUAN, state.step)
        assertEquals(2, state.turnNumber)

        // Mutasi F1: Goals & Pain Points
        state.addGoal("Rekam medis digital")
        state.addGoal("Antrean cepat")
        assertEquals(2, state.profile?.goals?.size)
        state.removeGoal("Antrean cepat")
        assertEquals(listOf("Rekam medis digital"), state.profile?.goals)

        state.addPainPoint("Stok obat sering selisih")
        assertEquals(listOf("Stok obat sering selisih"), state.profile?.painPoints)
        state.removePainPoint("Stok obat sering selisih")
        assertTrue(state.profile?.painPoints.isNullOrEmpty())

        // Navigasi ke F2
        state.nextTurn()
        assertEquals(InterviewStep.F2_SPEK, state.step)
        assertEquals(3, state.turnNumber)

        // Mutasi F2: Specs
        state.addOrUpdateSpec(
            RequirementSpec(
                areaKey = RoleKey("apoteker"),
                whoFills = "Staf Farmasi",
                whatRecorded = "Resep obat",
                whoSees = "Kasir & Dokter",
                doneWhen = "Obat diserahkan ke pasien"
            )
        )
        assertEquals(1, state.specs.size)
        assertEquals("apoteker", state.specs.first().areaKey.value)

        // Navigasi ke G1
        state.nextTurn()
        assertEquals(InterviewStep.G1_DIVISI, state.step)
        assertEquals(4, state.turnNumber)

        // Mundur dari G1 kembali ke F2 -> F1 -> F0
        state.previousTurn()
        assertEquals(InterviewStep.F2_SPEK, state.step)
        state.previousTurn()
        assertEquals(InterviewStep.F1_TUJUAN, state.step)
        state.previousTurn()
        assertEquals(InterviewStep.F0_BISNIS, state.step)
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
            "Profil Usaha & Sasaran",
            "Tujuan Operasional:",
            "Kendala Saat Ini:",
            "Spesifikasi Area Kerja",
            "Dasar: Kutipan cerita",
            "Dasar: Jawaban Anda pada pertanyaan wawancara",
            "Dasar: Saran konsultan yang Anda terima",
            "Dasar: Saran konsultan (belum dikonfirmasi)",
            "Terima Semua Tebakan",
            "Lewati Wawancara",
            "Kunci Usulan & Lanjut ke Draf Blueprint",
            "Pakai Ulang Platform",
            "Pakai Ulang Pack",
            "Kembangkan",
            "Baru",
            "->", // ascii arrow
            "x",  // ascii close
            " | ",
            " - "
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

/** Sambungan server (B4/B7): state diganti oleh balasan server, tidak menghitung langkah sendiri. */
class InterviewSessionStateRemoteTest {

    private class FakeRemote(private val replies: ArrayDeque<Result<com.eventverse.app.presentation.discovery.DiscoveryDraftUi>>) :
        com.eventverse.app.presentation.discovery.interview.InterviewRemote {
        val sent = mutableListOf<String>()
        override suspend fun start(draftId: String, consultant: Boolean) = replies.removeFirst()
        override suspend fun answer(draftId: String, questionId: String, outcome: Confirmation, text: String?, session: InterviewSession): Result<com.eventverse.app.presentation.discovery.DiscoveryDraftUi> {
            sent += "answer:$questionId:${outcome.code}"; return replies.removeFirst()
        }
        override suspend fun acceptAll(draftId: String): Result<com.eventverse.app.presentation.discovery.DiscoveryDraftUi> { sent += "accept_all"; return replies.removeFirst() }
    }

    private fun draftJson(sessionStep: String, questionId: String?, questionStep: String?, divisions: List<String> = listOf("poli")): com.eventverse.app.shared.json.JsonValue.Obj {
        val divs = divisions.joinToString(",") { """{"code":"$it","name":"${it.replaceFirstChar(Char::uppercase)}","source":"guess"}""" }
        val q = if (questionId == null) "null" else """{"id":"$questionId","step":"$questionStep","prompt":"p","guesses":[]}"""
        return com.eventverse.app.shared.json.JsonParser.parseObject(
            """{"id":"d1","status":"DRAFT","schemaVersion":1,"packCode":"klinik","packDisplayName":"Klinik","blueprintCode":"b","blueprintDescription":"","moduleCount":0,"activeModuleCount":0,"screenCount":0,
               "modules":[],"sections":[],"activeModuleCodes":[],"screens":[],"portLabels":{},"slotLabels":{},
               "interview":{"step":"$sessionStep","version":2,"divisions":[$divs],"roles":[],"links":[],"handoffs":[],"answers":[]},
               "nextQuestion":$q}"""
        )
    }

    private fun ui(o: com.eventverse.app.shared.json.JsonValue.Obj) = Result.success(com.eventverse.app.presentation.discovery.DiscoveryDraftUi.fromJson(o))

    @Test
    fun nextTurnMengirimIdPertanyaanNyataDanMengikutiBalasanServer() = kotlinx.coroutines.test.runTest {
        val first = com.eventverse.app.presentation.discovery.DiscoveryDraftUi.fromJson(draftJson("g1_divisi", "g1_divisi_t1", "g1_divisi"))
        val remote = FakeRemote(ArrayDeque(listOf(ui(draftJson("g5_ringkasan", "g5_ringkasan_t2", "g5_ringkasan", listOf("poli", "kasir"))))))
        val state = InterviewSessionState(first.interview, first.nextQuestion, "d1", "cerita").also {
            it.remote = remote; it.scope = this; it.onServerDraft = { }
        }
        assertEquals(InterviewStep.G1_DIVISI, state.step)
        state.nextTurn()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("answer:g1_divisi_t1:confirmed"), remote.sent)
        assertEquals(InterviewStep.G5_RINGKASAN, state.step, "server melewati G2-G4: klien mengikuti, tidak menghitung sendiri")
        assertEquals(listOf("poli", "kasir"), state.divisions.map { it.code.value })
        assertEquals("g5_ringkasan_t2", state.currentQuestion?.id)
    }

    @Test
    fun galatServerMenjadiPesanDanStateTidakBerubah() = kotlinx.coroutines.test.runTest {
        val first = com.eventverse.app.presentation.discovery.DiscoveryDraftUi.fromJson(draftJson("g1_divisi", "g1_divisi_t1", "g1_divisi"))
        val remote = FakeRemote(ArrayDeque(listOf(Result.failure(IllegalStateException("Pertanyaan sudah berganti")))))
        val state = InterviewSessionState(first.interview, first.nextQuestion, "d1", "cerita").also { it.remote = remote; it.scope = this }
        state.nextTurn()
        testScheduler.advanceUntilIdle()
        assertEquals(InterviewStep.G1_DIVISI, state.step)
        assertEquals("Pertanyaan sudah berganti", state.errorMessage)
        assertFalse(state.busy)
    }

    @Test
    fun meninjauUlangLangkahSebelumnyaTidakMemanggilServer() = kotlinx.coroutines.test.runTest {
        val first = com.eventverse.app.presentation.discovery.DiscoveryDraftUi.fromJson(draftJson("g5_ringkasan", "g5_ringkasan_t1", "g5_ringkasan"))
        val remote = FakeRemote(ArrayDeque())
        val state = InterviewSessionState(first.interview, first.nextQuestion, "d1", "cerita").also { it.remote = remote; it.scope = this }
        state.goToStep(InterviewStep.G2_PERAN)
        state.nextTurn()
        testScheduler.advanceUntilIdle()
        assertEquals(InterviewStep.G3_MODUL, state.step)
        assertTrue(remote.sent.isEmpty())
    }

    @Test
    fun terimaSemuaDanSelesaiLewatServer() = kotlinx.coroutines.test.runTest {
        val first = com.eventverse.app.presentation.discovery.DiscoveryDraftUi.fromJson(draftJson("g1_divisi", "g1_divisi_t1", "g1_divisi"))
        val remote = FakeRemote(ArrayDeque(listOf(ui(draftJson("done", null, null)), ui(draftJson("done", null, null)))))
        val state = InterviewSessionState(first.interview, first.nextQuestion, "d1", "cerita").also { it.remote = remote; it.scope = this }
        state.acceptAllGuesses()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("accept_all"), remote.sent)
        assertEquals(InterviewStep.DONE, state.step)
        var done = false
        state.complete { done = true }
        testScheduler.advanceUntilIdle()
        assertTrue(done, "pertanyaan sudah tidak ada: langsung selesai tanpa panggilan server")
        assertEquals(listOf("accept_all"), remote.sent)
    }
}
