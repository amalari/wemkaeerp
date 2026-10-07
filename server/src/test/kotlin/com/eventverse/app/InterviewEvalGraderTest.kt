package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.ModuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Penilai dites sendiri (plan IV-C0, AC).** Kasus lulus/gagal buatan tangan pada pack fixture — tanpa
 * LLM, tanpa jaringan. Sesi emas wajib lulus 100% (kalau tidak, penilainya yang rusak), dan setiap sesi
 * rusak wajib gagal di kriteria yang tepat — bukan gagal di mana pun.
 */
class InterviewEvalGraderTest {

    private val klinik = InterviewGoldenCases.all.first { it.name == "klinik" }
    private val bengkel = InterviewGoldenCases.all.first { it.name == "bengkel" }

    private fun gradeKlinik(session: InterviewSession): InterviewEvalVerdict =
        InterviewEvalGrader.grade(klinik, InterviewEvalPacks.draftOf(InterviewEvalPacks.klinikPack, session))

    private fun gradeBengkel(session: InterviewSession): InterviewEvalVerdict =
        InterviewEvalGrader.grade(bengkel, InterviewEvalPacks.draftOf(InterviewEvalPacks.bengkelPack, session))

    // --- kasus LULUS -------------------------------------------------------------------

    @Test
    fun `sesi emas klinik lulus 100 persen - kunci jawaban wajib lulus penilainya sendiri`() {
        val verdict = gradeKlinik(InterviewEvalPacks.klinikSession)
        assertTrue(verdict.passed, "Sesi emas wajib lulus: ${verdict.failedCriteria}")
        assertEquals(7, verdict.criteria.size, "Tujuh kriteria plan IV-C0")
    }

    @Test
    fun `sesi emas bengkel lulus dan cerita kecil menghasilkan draf kecil`() {
        val verdict = gradeBengkel(InterviewEvalPacks.bengkelSession)
        assertTrue(verdict.passed, "Sesi emas bengkel wajib lulus: ${verdict.failedCriteria}")
        assertEquals(1, InterviewEvalPacks.bengkelSession.divisions.size, "cerita kecil = satu divisi")
    }

    @Test
    fun `tambahan yang masuk akal tidak menghukum - penilai tidak kaku pada ejaan`() {
        val richer = InterviewEvalPacks.klinikSession.copy(
            divisions = InterviewEvalPacks.klinikSession.divisions +
                DivisionDraft(DivisionCode("gudang_obat"), "Gudang Obat", ItemSource.ANSWER),
            roles = InterviewEvalPacks.klinikSession.roles +
                RoleDraft(RoleKey("apoteker"), "Apoteker", DivisionCode("gudang_obat"), ItemSource.ANSWER)
        )
        val verdict = gradeKlinik(richer)
        assertTrue(verdict.passed, "Butir tambahan yang masuk akal tetap lulus: ${verdict.failedCriteria}")
    }

    // --- kasus GAGAL -------------------------------------------------------------------

    @Test
    fun `draf tanpa wawancara gagal kriteria valid saja`() {
        val verdict = InterviewEvalGrader.grade(klinik, InterviewEvalPacks.draftOf(InterviewEvalPacks.klinikPack, null))
        assertFalse(verdict.passed)
        assertEquals(listOf("valid"), verdict.failedCriteria.map { it.criterion })
        assertTrue(verdict.failedCriteria.first().detail.contains("interview"))
    }

    @Test
    fun `peran menunjuk divisi yang tidak ada ditolak validator dengan path`() {
        val broken = InterviewEvalPacks.klinikSession.copy(
            roles = InterviewEvalPacks.klinikSession.roles.mapIndexed { i, r ->
                if (i == 0) r.copy(divisionCode = DivisionCode("tidak_ada")) else r
            }
        )
        val verdict = gradeKlinik(broken)
        val valid = verdict.criteria.first { it.criterion == "valid" }
        assertFalse(valid.passed)
        assertTrue(valid.detail.contains("$.interview.roles[0].divisionCode"), "galat berpath: ${valid.detail}")
    }

    @Test
    fun `asal bohong gagal dua lapis - validator menolak dan kunci kasus menilai tidak masuk akal`() {
        val lying = InterviewEvalPacks.klinikSession.copy(
            links = InterviewEvalPacks.klinikSession.links.mapIndexed { i, l ->
                if (l.moduleId.value == "klinik_pendaftaran") l.copy(origin = ModuleOrigin.REUSE_PACK) else l
            }
        )
        val verdict = gradeKlinik(lying)
        val valid = verdict.criteria.first { it.criterion == "valid" }
        assertFalse(valid.passed, "REUSE_PACK untuk modul kustom ditolak validator: ${valid.detail}")
        assertTrue(valid.detail.contains("$.interview.links[0].origin"))
        val origins = verdict.criteria.first { it.criterion == "asal_modul" }
        assertFalse(origins.passed, "kunci kasus klinik hanya menerima NEW untuk modul kustom: ${origins.detail}")
    }

    @Test
    fun `divisi kunci yang hilang gagal kriteria divisi`() {
        val narrowed = InterviewEvalPacks.klinikSession.copy(
            divisions = InterviewEvalPacks.klinikSession.divisions.drop(1),
            roles = InterviewEvalPacks.klinikSession.roles.drop(1),
            links = InterviewEvalPacks.klinikSession.links.drop(1)
        )
        val verdict = gradeKlinik(narrowed)
        assertFalse(verdict.criteria.first { it.criterion == "divisi_masuk_akal" }.passed)
    }

    @Test
    fun `peran di divisi yang salah gagal kriteria peran_ke_divisi`() {
        val misplaced = InterviewEvalPacks.klinikSession.copy(
            roles = InterviewEvalPacks.klinikSession.roles.map { r ->
                if (r.roleKey.value == "kasir") r.copy(divisionCode = DivisionCode("poli")) else r
            }
        )
        val verdict = gradeKlinik(misplaced)
        assertFalse(verdict.criteria.first { it.criterion == "peran_ke_divisi" }.passed)
        assertTrue(
            verdict.criteria.first { it.criterion == "valid" }.passed,
            "secara struktur sah — yang salah adalah maknanya, bukan bentuknya"
        )
    }

    @Test
    fun `tautan ke modul di luar pack ditolak validator dengan path`() {
        val broken = InterviewEvalPacks.klinikSession.copy(
            links = InterviewEvalPacks.klinikSession.links.mapIndexed { i, l ->
                if (i == 0) l.copy(moduleId = ModuleId("modul_lain")) else l
            }
        )
        val verdict = gradeKlinik(broken)
        val valid = verdict.criteria.first { it.criterion == "valid" }
        assertFalse(valid.passed)
        assertTrue(valid.detail.contains("$.interview.links[0].moduleId"), "galat berpath: ${valid.detail}")
    }

    @Test
    fun `tautan kunci yang hilang gagal kriteria tautan_modul`() {
        val missing = InterviewEvalPacks.klinikSession.copy(
            links = InterviewEvalPacks.klinikSession.links.filter { it.roleKey.value != "kasir" }
        )
        val verdict = gradeKlinik(missing)
        assertFalse(verdict.criteria.first { it.criterion == "tautan_modul" }.passed)
    }

    @Test
    fun `istilah konveksi di pack non-garment gagal valid dan kemurnian_vertikal`() {
        val leaked = InterviewEvalPacks.klinikSession.copy(
            divisions = InterviewEvalPacks.klinikSession.divisions.map { d ->
                if (d.code.value == "poli") d.copy(name = "Poli Jahit") else d
            }
        )
        val verdict = gradeKlinik(leaked)
        assertFalse(verdict.criteria.first { it.criterion == "valid" }.passed, "validator menolak lebih dulu (B0)")
        assertFalse(verdict.criteria.first { it.criterion == "kemurnian_vertikal" }.passed, "grader menilai juga, independen")
    }

    @Test
    fun `kata sablon bordir kain bukan kebocoran - keputusan 2026-10-07`() {
        val textile = InterviewEvalPacks.bengkelSession.copy(
            divisions = InterviewEvalPacks.bengkelSession.divisions.map { d -> d.copy(name = "Servis Kain & Bordir") }
        )
        val verdict = gradeBengkel(textile)
        assertTrue(verdict.criteria.first { it.criterion == "kemurnian_vertikal" }.passed, "sablon/bordir/kain bukan istilah konveksi")
    }

    @Test
    fun `giliran melebihi batas kasus gagal jumlah_giliran`() {
        val chatty = InterviewEvalPacks.bengkelSession.copy(
            answers = (1..5).map { turn -> InterviewAnswer(turn, InterviewStep.G1_DIVISI, "g$turn", Confirmation.CONFIRMED) }
        )
        val verdict = gradeBengkel(chatty)
        val turns = verdict.criteria.first { it.criterion == "jumlah_giliran" }
        assertFalse(turns.passed, "5 giliran > batas kasus bengkel 4: ${turns.detail}")
        assertTrue(turns.detail.contains("melebihi"))
    }

    @Test
    fun `draf bengkak di kasus negatif gagal batas maxDivisions`() {
        val bloated = InterviewEvalPacks.bengkelSession.copy(
            divisions = listOf("servis", "kasir", "gudang", "pemasaran").map { code ->
                DivisionDraft(DivisionCode(code), code.replaceFirstChar { it.uppercase() }, ItemSource.GUESS)
            },
            roles = InterviewEvalPacks.bengkelSession.roles + listOf(
                RoleDraft(RoleKey("kasir"), "Kasir", DivisionCode("kasir"), ItemSource.GUESS),
                RoleDraft(RoleKey("penjaga"), "Penjaga Gudang", DivisionCode("gudang"), ItemSource.GUESS)
            )
        )
        val verdict = gradeBengkel(bloated)
        val divisions = verdict.criteria.first { it.criterion == "divisi_masuk_akal" }
        assertFalse(divisions.passed, "cerita kecil tidak boleh memunculkan 4 divisi: ${divisions.detail}")
        assertTrue(divisions.detail.contains("maxDivisions=3"))
    }

    @Test
    fun `baris log memuat nama agent dan status`() {
        val pass = gradeKlinik(InterviewEvalPacks.klinikSession).logLine("agent/uji")
        assertTrue(pass.contains("PASS") && pass.contains("agent/uji") && pass.contains("klinik"))
        val fail = gradeKlinik(InterviewEvalPacks.klinikSession.copy(divisions = emptyList())).logLine("agent/uji")
        assertTrue(fail.contains("FAIL"))
    }
}
