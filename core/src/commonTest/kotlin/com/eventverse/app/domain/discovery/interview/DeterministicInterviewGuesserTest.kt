package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.proposal.VerticalPurity
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.RoleHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeterministicInterviewGuesserTest {

    private fun h(word: String, label: String, module: String) = RoleHint(word, label, ModuleId(module))

    private val klinik = InterviewFixtures.klinikPack.copy(roleHints = listOf(
        h("resepsionis", "Resepsionis", "klinik_pendaftaran"), h("perawat", "Perawat", "klinik_poli"), h("kasir", "Kasir", "klinik_kasir")
    ))
    private val bengkel = InterviewFixtures.bengkelPack.copy(roleHints = listOf(
        h("mekanik", "Mekanik", "bengkel_servis"), h("penerima unit", "Penerima Unit", "bengkel_penerimaan")
    ))
    private val katering = InterviewFixtures.pack("katering", "Katering",
        InterviewFixtures.operational("katering_pesanan", "Pesanan"), InterviewFixtures.operational("katering_dapur", "Dapur"))
        .let { it.copy(roleHints = listOf(h("admin pesanan", "Admin Pesanan", "katering_pesanan"), h("koki", "Koki", "katering_dapur"))) }
    private val retail = InterviewFixtures.pack("retail", "Retail",
        InterviewFixtures.operational("retail_kasir", "Kasir Toko"), InterviewFixtures.operational("retail_stok", "Stok Rak"))
        .let { it.copy(roleHints = listOf(h("kasir", "Kasir", "retail_kasir"), h("penata rak", "Penata Rak", "retail_stok"))) }
    private val gudang = InterviewFixtures.pack("gudang", "Gudang", InterviewFixtures.operational("gudang_masuk", "Barang Masuk"), InterviewFixtures.operational("gudang_keluar", "Barang Keluar"))
        .let { it.copy(roleHints = listOf(h("checker", "Checker", "gudang_masuk"), h("picker", "Picker", "gudang_keluar"))) }

    private val cases: List<Pair<DomainPack, String>> = listOf(
        klinik to "Pasien datang, resepsionis mendaftarkan, lalu perawat memeriksa, terakhir kasir menagih.",
        bengkel to "Penerima unit mencatat kendaraan lalu mekanik mengerjakannya.",
        katering to "Admin pesanan menerima order, koki memasak.",
        retail to "Kasir melayani pembeli, penata rak mengisi stok.",
        gudang to "Checker memeriksa barang masuk, picker mengambil untuk keluar."
    )

    @Test
    fun `narasi lima industri non-garment selesai dengan terima semua dan lolos validator`() {
        cases.forEach { (pack, narrative) ->
            val proposed = DeterministicInterviewGuesser.propose(pack, narrative)
            assertTrue(proposed.links.isNotEmpty(), "${pack.code.value}: tak ada tebakan")
            val done = proposed.acceptAll()
            assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(pack, done)).map { "${it.path}: ${it.message}" }, pack.code.value)
            assertEquals(InterviewStep.DONE, done.step)
            assertTrue(done.links.all { it.confirmed == Confirmation.SKIPPED })
        }
    }

    @Test
    fun `klinik menebak divisi peran modul dan sambungan sesuai urutan cerita`() {
        val p = DeterministicInterviewGuesser.propose(klinik, cases[0].second)
        assertEquals(listOf("Pendaftaran", "Poli", "Kasir"), p.divisions.map { it.name })
        assertEquals(listOf("Resepsionis", "Perawat", "Kasir"), p.roles.map { it.label })
        assertEquals(listOf("klinik_pendaftaran", "klinik_poli", "klinik_kasir"), p.links.map { it.moduleId.value })
        assertEquals(listOf("klinik_pendaftaran>klinik_poli", "klinik_poli>klinik_kasir"), p.handoffs.map { "${it.from.value}>${it.to.value}" })
        assertTrue(p.roles.all { it.isHead })
    }

    @Test
    fun `hasil deterministik byte-per-byte dan tidak memuat kosakata konveksi`() {
        cases.forEach { (pack, narrative) ->
            val a = DeterministicInterviewGuesser.propose(pack, narrative)
            assertEquals(a, DeterministicInterviewGuesser.propose(pack, narrative))
            val texts = InterviewStep.entries.filter { it != InterviewStep.DONE }.flatMap { step ->
                val draft = draftOf(pack, a.copy(step = step))
                val guesses = DeterministicInterviewGuesser.guessNow(step, pack, draftOf(pack, null), narrative)
                listOfNotNull(a.copy(step = step).nextQuestion(draft, guesses)?.prompt) + guesses.map { it.label }
            }
            texts.forEach { assertNull(VerticalPurity.leak(it), "${pack.code.value}: '$it'") }
        }
    }

    @Test
    fun `pack tanpa kamus tidak menebak, bahkan untuk narasi bernada garment`() {
        val noHints = InterviewFixtures.klinikPack
        assertEquals(InterviewSession(InterviewStep.G1_DIVISI), DeterministicInterviewGuesser.propose(noHints, "operator jahit lalu qc"))
        InterviewStep.entries.forEach { assertEquals(emptyList(), DeterministicInterviewGuesser.guessNow(it, noHints, draftOf(noHints, null), "kasir")) }
        val q = InterviewSession(InterviewStep.G1_DIVISI).nextQuestion(draftOf(noHints, null))
        assertNotNull(q); assertTrue(q.guesses.isEmpty())
    }

    @Test
    fun `garment menebak dari kamusnya sendiri dan modul bawaan diberi asal yang benar`() {
        val p = DeterministicInterviewGuesser.propose(GarmentDomainPack.pack, "Admin gudang terima kain, lalu operator rajut, lalu qc.")
        assertEquals(listOf("inventory", "operator_exec", "quality_control"), p.links.map { it.moduleId.value })
        assertTrue(p.links.all { it.origin == ModuleOrigin.REUSE_PACK })
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(GarmentDomainPack.pack, p.acceptAll())))
    }

    @Test
    fun `frasa lebih panjang menyisihkan kata yang terkandung di dalamnya`() {
        val p = DeterministicInterviewGuesser.propose(GarmentDomainPack.pack, "admin gudang menerima barang")
        assertEquals(listOf("Admin Gudang"), p.roles.map { it.label })
    }

    @Test
    fun `nextQuestion melewati langkah yang tak perlu dan selesai di DONE atau batas giliran`() {
        val d = draftOf(klinik, null)
        assertEquals(InterviewStep.G5_RINGKASAN, InterviewSession(InterviewStep.G2_PERAN).nextQuestion(d)?.step)
        val oneRole = InterviewSession(InterviewStep.G4_SAMBUNGAN, divisions = InterviewFixtures.klinikSession.divisions, roles = InterviewFixtures.klinikSession.roles,
            links = InterviewFixtures.klinikSession.links.take(1))
        assertEquals(InterviewStep.G5_RINGKASAN, oneRole.nextQuestion(d)?.step)
        assertEquals(InterviewStep.G4_SAMBUNGAN, InterviewFixtures.klinikSession.copy(step = InterviewStep.G4_SAMBUNGAN).nextQuestion(d)?.step)
        assertNull(InterviewFixtures.klinikSession.nextQuestion(d))
        val answers = (1..8).map { InterviewAnswer(it, InterviewStep.G1_DIVISI, "q", Confirmation.CONFIRMED) }
        assertNull(InterviewSession(InterviewStep.G1_DIVISI, answers = answers).nextQuestion(d))
    }

    @Test
    fun `tebakan yang sudah ada di sesi tidak ditanyakan lagi`() {
        val known = InterviewSession(InterviewStep.G2_PERAN, divisions = listOf(DivisionDraft(DivisionCode("poli"), "Poli", ItemSource.ANSWER)))
        val left = DeterministicInterviewGuesser.guessNow(InterviewStep.G1_DIVISI, klinik, draftOf(klinik, known), cases[0].second)
        assertEquals(listOf("Pendaftaran", "Kasir"), left.map { it.label })
    }
}
