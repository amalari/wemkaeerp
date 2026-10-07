package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.ModuleId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti AC C3** (plan IV-C): `AgentInterviewGuesser` diuji dengan [ScriptedPromptExecutor] — tanpa
 * jaringan, tanpa kunci, tanpa biaya — tetapi lewat seluruh jalur produksi (singleRunStrategy, alat,
 * jembatan dekoder, validator). Sampah → gagal terkontrol; keluaran sah → lolos dengan provenance
 * yang dibubuhkan server.
 */
class KoogInterviewGuesserTest {

    private val model = DeepSeekModels.DeepSeekV4Flash
    private val pack = InterviewEvalPacks.klinikPack
    private val narasi = "Kami klinik gigi: pasien mendaftar antrean per poli, dan tagihan pembayaran kasir."

    /** Sesi berjalan G3: divisi & peran sudah diterima pada giliran sebelumnya. */
    private val g3Draft = InterviewEvalPacks.draftOf(
        pack,
        InterviewSession(
            step = InterviewStep.G3_MODUL,
            divisions = listOf(DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.ANSWER)),
            roles = listOf(RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.ANSWER, isHead = true))
        )
    )

    /** Sesi berjalan G3 versi **berdasar-cerita** (C6): butir lama berbasis, jejak giliran F0 tersedia. */
    private val v2Draft = InterviewEvalPacks.draftOf(
        pack,
        InterviewSession(
            step = InterviewStep.G3_MODUL,
            divisions = listOf(DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.ANSWER, BasisRef(Basis.JAWABAN, answerId = "f0_bisnis_t1"))),
            roles = listOf(RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.ANSWER, isHead = true, basisRef = BasisRef(Basis.JAWABAN, answerId = "f0_bisnis_t1"))),
            answers = listOf(InterviewAnswer(1, InterviewStep.F0_BISNIS, "f0_bisnis_t1", Confirmation.CONFIRMED)),
            version = InterviewSession.BASED_ON_STORY,
            narrative = narasi
        )
    )

    /** Jawaban G3 berdasar-cerita: tautan membawa basisRef, atau sengaja tanpa/basi untuk uji koreksi. */
    private fun g3AnswerV2(moduleId: String, basis: String = "narasi"): String {
        val ref = when (basis) {
            "narasi" -> ",\"basisRef\":{\"basis\":\"narasi\",\"quote\":\"tagihan pembayaran kasir\"}"
            "belum" -> ",\"basisRef\":{\"basis\":\"saran_belum_dijawab\"}"
            else -> ""
        }
        return """
            {"interview":{"step":"g3_modul","divisions":[],"roles":[],
            "links":[{"roleKey":"resepsionis","moduleId":"$moduleId","origin":"new","features":[],"confirmed":"confirmed","confidence":90$ref}],
            "handoffs":[],"answers":[]}}
        """.trimIndent()
    }

    private fun g3Answer(moduleId: String, origin: String = "new", confidence: String? = "90"): String {
        val conf = confidence?.let { ",\"confidence\":$it" } ?: ""
        return """
            {"interview":{"step":"g3_modul","divisions":[],"roles":[],
            "links":[{"roleKey":"resepsionis","moduleId":"$moduleId","origin":"$origin","features":[],"confirmed":"confirmed"$conf}],
            "handoffs":[],"answers":[]}}
        """.trimIndent()
    }

    @Test
    fun `keluaran sah lolos dengan provenance dibubuhkan server`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(g3Answer("klinik_pendaftaran")))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi).getOrThrow()

        assertEquals(1, executor.calls)
        assertEquals(listOf(ModuleId("klinik_pendaftaran")), guesses.links.map { it.moduleId })
        val link = guesses.links.single()
        assertEquals(Confirmation.GUESSED, link.confirmed, "model menulis confirmed - server membubuhkan GUESSED")
        assertEquals(90, link.confidence)
        assertTrue(executor.toolsSeen.single().map { it.name }.containsAll(listOf("interview_state", "interview_catalog")))
    }

    @Test
    fun `sampah gagal terkontrol setelah seluruh putaran koreksi`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf("bukan json", "juga bukan json", "tetap bukan json"))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val result = guesser.guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi)

        assertTrue(result.isFailure)
        assertEquals(AgentInterviewGuesser.DEFAULT_MAX_CORRECTION_ROUNDS, executor.calls)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("setelah 3 putaran"))
    }

    @Test
    fun `tautan ke modul hantu dikirim balik berpath lalu dikoreksi`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(g3Answer("modul_hantu"), g3Answer("klinik_pendaftaran")))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi).getOrThrow()

        assertEquals(2, executor.calls, "Putaran pertama wajib ditolak validator")
        val feedback = executor.lastPromptText()
        assertTrue(feedback.contains("$.interview.links[0].moduleId"), "galat berpath ke model: ${feedback.take(400)}")
        assertEquals(ModuleId("klinik_pendaftaran"), guesses.links.single().moduleId)
    }

    @Test
    fun `asal bohong untuk modul kustom dikirim balik berpath`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(g3Answer("klinik_pendaftaran", origin = "reuse_pack"), g3Answer("klinik_pendaftaran")))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi).getOrThrow()

        assertEquals(2, executor.calls)
        assertTrue(executor.lastPromptText().contains("$.interview.links[0].origin"))
        assertEquals(ModuleOrigin.NEW, guesses.links.single().origin)
    }

    @Test
    fun `confidence di luar rentang dipatok dan yang kosong diberi bawaan`() = runBlocking {
        val executor = ScriptedPromptExecutor(
            listOf(g3Answer("klinik_pendaftaran", confidence = "150"), g3Answer("klinik_pendaftaran", confidence = null))
        )
        val guesser = AgentInterviewGuesser(executor, model = model)

        // 150 ditolak validator (0-100) -> koreksi; jawaban tanpa confidence diberi bawaan server.
        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi).getOrThrow()

        assertEquals(70, guesses.links.single().confidence)
        assertTrue(executor.calls >= 2)
    }

    @Test
    fun `useCurrent menghasilkan usulan kosong untuk langkah itu`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf("""{"interview":{"useCurrent":true}}"""))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G4_SAMBUNGAN, pack, g3Draft, narasi).getOrThrow()

        assertEquals(1, executor.calls)
        assertTrue(guesses.links.isEmpty() && guesses.handoffs.isEmpty() && guesses.divisions.isEmpty())
    }

    @Test
    fun `model yang membalas dokumen langkah lain tidak membocorkan butir ke langkah diminta`() = runBlocking {
        // Model bandel membalas divisions padahal diminta G3: butir itu tidak boleh lolos sebagai tautan.
        val defiant = """
            {"interview":{"step":"g1_divisi","divisions":[{"code":"poli","name":"Poli","source":"guess"}],
            "roles":[],"links":[],"handoffs":[],"answers":[]}}
        """.trimIndent()
        val executor = ScriptedPromptExecutor(listOf(defiant))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi).getOrThrow()

        assertTrue(guesses.divisions.isEmpty(), "Butir langkah lain tidak boleh lolos: ${guesses.divisions}")
        assertTrue(guesses.links.isEmpty(), "Usulan G3 memang kosong - pengguna akan ditanya terbuka")
        assertEquals(1, executor.calls, "Usulan kosong sah - tidak memicu koreksi")
    }

    @Test
    fun `sesi berdasar-cerita menolak tebakan tanpa dasar lalu menerima yang berkutipan`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(g3AnswerV2("klinik_pendaftaran", basis = "tanpa"), g3AnswerV2("klinik_pendaftaran")))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, v2Draft, narasi).getOrThrow()

        assertEquals(2, executor.calls, "Putaran pertama wajib ditolak validator (basisRef wajib)")
        assertTrue(executor.lastPromptText().lowercase().contains("basisref"), "galat berpath basisRef dikirim balik")
        val link = guesses.links.single()
        assertEquals(Basis.NARASI, link.basisRef?.basis, "saran pengguna bingung tetap berkutipan cerita")
        assertEquals("tagihan pembayaran kasir", link.basisRef?.quote, "kutipan persis dari narasi")
    }

    @Test
    fun `saran yang belum dijawab ditolak lalu dikosongkan - pengguna menolak saran`() = runBlocking {
        val executor = ScriptedPromptExecutor(
            listOf(g3AnswerV2("klinik_pendaftaran", basis = "belum"), """{"interview":{"useCurrent":true}}""")
        )
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.G3_MODUL, pack, v2Draft, narasi).getOrThrow()

        assertEquals(2, executor.calls)
        assertTrue(executor.lastPromptText().lowercase().contains("saran"), "pesan tolak menyebut saran yang belum dijawab")
        assertTrue(guesses.links.isEmpty(), "Saran yang ditolak pengguna tidak boleh masuk usulan")
    }

    @Test
    fun `fase konsultan F0 mengisi profil bisnis dari narasi`() = runBlocking {
        val answer = """
            {"interview":{"step":"f0_bisnis","divisions":[],"roles":[],"links":[],"handoffs":[],
            "profile":{"summary":"Klinik gigi dengan antrean per poli dan kasir.","goals":[],"painPoints":[]},
            "specs":[],"answers":[]}}
        """.trimIndent()
        val executor = ScriptedPromptExecutor(listOf(answer))
        val guesser = AgentInterviewGuesser(executor, model = model)

        val guesses = guesser.guess(InterviewStep.F0_BISNIS, pack, InterviewEvalPacks.draftOf(pack, null), narasi).getOrThrow()

        assertEquals("Klinik gigi dengan antrean per poli dan kasir.", guesses.profile?.summary)
        assertTrue(guesses.divisions.isEmpty() && guesses.links.isEmpty(), "F0 bukan tempat menebak G1-G4")
    }

    @Test
    fun `penggabungan F2 menggabungkan spesifikasi per area tanpa duplikat`() {
        val session = InterviewSession(
            step = InterviewStep.F2_SPEK,
            specs = listOf(RequirementSpec(RoleKey("resepsionis"), whoFills = "isi lama")),
            version = InterviewSession.BASED_ON_STORY,
            narrative = narasi
        )
        val guesses = InterviewStepGuesses(
            step = InterviewStep.F2_SPEK,
            specs = listOf(
                RequirementSpec(RoleKey("resepsionis"), whoFills = "Resepsionis", whoSees = "Kasir", basisRef = BasisRef(Basis.NARASI, quote = "antrean per poli")),
                RequirementSpec(RoleKey("kasir"), doneWhen = "Tagihan lunas")
            )
        )
        val merged = mergeStepGuesses(session, guesses)
        assertEquals(2, merged.specs.size, "area sama diganti, area baru ditambah")
        assertEquals("Resepsionis", merged.specs.first { it.areaKey.value == "resepsionis" }.whoFills)
    }

    @Test
    fun `penggabungan murni mempertahankan yang lama dan menggantikan kunci sama`() {
        val session = InterviewSession(
            step = InterviewStep.G1_DIVISI,
            divisions = listOf(
                DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.ANSWER),
                DivisionDraft(DivisionCode("poli"), "Poli", ItemSource.ANSWER)
            )
        )
        val guesses = InterviewStepGuesses(
            step = InterviewStep.G1_DIVISI,
            divisions = listOf(
                DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran Pasien", ItemSource.GUESS),
                DivisionDraft(DivisionCode("kasir"), "Kasir", ItemSource.GUESS)
            )
        )
        val merged = mergeStepGuesses(session, guesses)
        assertEquals(listOf("Poli", "Pendaftaran Pasien", "Kasir"), merged.divisions.map { it.name })
        assertEquals(session.step, merged.step, "Penebak tidak memajukan langkah - itu milik pemanggil")
    }

    @Test
    fun `adapter port core memproyeksikan usulan ke Guess berkunci B`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(g3Answer("klinik_pendaftaran")))
        val guesser = AgentInterviewGuesser(executor, model = model)

        // Port tipis core: Result<List<Guess>> dengan kunci persis pelaksana B (role:module).
        val guesses = guesser.asInterviewGuesser().guess(InterviewStep.G3_MODUL, pack, g3Draft, narasi).getOrThrow()

        assertEquals(1, guesses.size)
        assertEquals("resepsionis:klinik_pendaftaran", guesses.single().key)
        assertEquals(com.eventverse.app.domain.discovery.interview.ModuleOrigin.NEW, guesses.single().origin)
        assertEquals(90, guesses.single().confidence)
    }

    @Test
    fun `provenance menempel tanpa memandang klaim model`() {
        // confidence > 100 ditolak lebih awal oleh konstruktor/codec; clamp di sini pertahanan lapis kedua.
        val stamped = stampGuessProvenance(
            InterviewStepGuesses(
                step = InterviewStep.G3_MODUL,
                links = listOf(
                    RoleModuleLink(RoleKey("resepsionis"), ModuleId("klinik_pendaftaran"), ModuleOrigin.NEW, emptyList(), Confirmation.CONFIRMED, 100),
                    RoleModuleLink(RoleKey("kasir"), ModuleId("klinik_kasir"), ModuleOrigin.NEW, emptyList(), Confirmation.CONFIRMED, null)
                )
            )
        )
        assertTrue(stamped.links.all { it.confirmed == Confirmation.GUESSED })
        assertEquals(100, stamped.links[0].confidence, "100 dipertahankan")
        assertEquals(70, stamped.links[1].confidence, "kosong diberi bawaan 70")
    }
}