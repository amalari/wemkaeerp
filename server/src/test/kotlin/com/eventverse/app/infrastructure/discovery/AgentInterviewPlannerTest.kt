package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.InterviewGoldenCases
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.isPlanned
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.shared.discovery.InterviewSessionCodec
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Perencana alur penuh, **tanpa jaringan dan tanpa biaya** (`ScriptedPromptExecutor`): satu panggilan mengisi G1–G4. */
class AgentInterviewPlannerTest {

    private val model = DeepSeekModels.DeepSeekV4Flash
    private val pack = InterviewEvalPacks.klinikPack
    private val narasi = InterviewGoldenCases.all.first { it.name == "klinik" }.narrative
    private val draft = InterviewEvalPacks.draftOf(pack, null)

    private fun basis(quote: String) = BasisRef(Basis.NARASI, quote = quote)

    /** Rencana lengkap klinik: 3 divisi, 3 peran, 3 tautan — semua dari kutipan narasi. */
    private val plan = InterviewSession(
        step = InterviewStep.G1_DIVISI,
        divisions = listOf(
            DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.GUESS, basis("pasien mendaftar")),
            DivisionDraft(DivisionCode("poli"), "Poli", ItemSource.GUESS, basis("antrean per poli")),
            DivisionDraft(DivisionCode("kasir"), "Kasir", ItemSource.GUESS, basis("pembayaran kasir"))
        ),
        roles = listOf(
            RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.GUESS, isHead = true, basisRef = basis("pasien mendaftar")),
            RoleDraft(RoleKey("perawat"), "Perawat", DivisionCode("poli"), ItemSource.GUESS, isHead = true, basisRef = basis("antrean per poli")),
            RoleDraft(RoleKey("kasir"), "Kasir", DivisionCode("kasir"), ItemSource.GUESS, isHead = true, basisRef = basis("pembayaran kasir"))
        ),
        links = listOf(
            RoleModuleLink(RoleKey("resepsionis"), ModuleId("klinik_pendaftaran"), ModuleOrigin.NEW, emptyList(), Confirmation.GUESSED, 80, basis("pasien mendaftar")),
            RoleModuleLink(RoleKey("perawat"), ModuleId("klinik_poli"), ModuleOrigin.NEW, emptyList(), Confirmation.GUESSED, 80, basis("antrean per poli")),
            RoleModuleLink(RoleKey("kasir"), ModuleId("klinik_kasir"), ModuleOrigin.NEW, emptyList(), Confirmation.GUESSED, 80, basis("pembayaran kasir"))
        ),
        version = InterviewSession.BASED_ON_STORY,
        narrative = narasi
    )

    private fun answer(s: InterviewSession) = """{"interview":${InterviewSessionCodec.encode(s).encode()}}"""

    @Test
    fun `satu panggilan mengisi divisi, peran, dan tautan sekaligus dan lolos validator`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(answer(plan)))
        val planner = AgentInterviewPlanner(AgentInterviewGuesser(executor, model = model))
        val base = InterviewSession(step = InterviewStep.F0_BISNIS, version = InterviewSession.BASED_ON_STORY, narrative = narasi)
        val out = planner.plan(draft, base, narasi)
        assertEquals(1, executor.calls, "rencana = satu percakapan")
        assertEquals(listOf("pendaftaran", "poli", "kasir"), out.divisions.map { it.code.value })
        assertEquals(3, out.roles.size)
        assertEquals(3, out.links.size)
        assertTrue(out.isPlanned)
        assertEquals(emptyList(), InterviewValidator.validate(out, pack))
    }

    @Test
    fun `keluaran sampah menjadi galat sehingga pemanggil mempertahankan sesi`() = runBlocking<Unit> {
        val executor = ScriptedPromptExecutor(listOf("bukan json", "juga bukan", "tetap bukan"))
        val planner = AgentInterviewPlanner(AgentInterviewGuesser(executor, model = model))
        val base = InterviewSession(step = InterviewStep.F0_BISNIS, version = InterviewSession.BASED_ON_STORY, narrative = narasi)
        assertFailsWith<IllegalStateException> { planner.plan(draft, base, narasi) }
    }

    private val ask = Clarification("c1", "Pasien dilayani per poli atau satu antrean?")
    private fun asking() = """{"interview":${InterviewSessionCodec.encode(InterviewSession(InterviewStep.G1_DIVISI, clarifications = listOf(ask))).encode()}}"""

    @Test
    fun `bila hal pokok tak jelas perencana bertanya dulu, tanpa rencana`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(asking()))
        val planner = AgentInterviewPlanner(AgentInterviewGuesser(executor, model = model))
        val base = InterviewSession(step = InterviewStep.F0_BISNIS, version = InterviewSession.BASED_ON_STORY, narrative = narasi)
        val out = planner.plan(draft, base, narasi)
        assertEquals(listOf(ask), out.clarifications)
        assertTrue(out.divisions.isEmpty() && out.links.isEmpty())
        assertTrue(out.awaitingClarification)
        assertEquals(emptyList(), InterviewValidator.validate(out, pack))
    }

    @Test
    fun `setelah dijawab perencana dilarang bertanya lagi dan harus menyusun rencana`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(asking(), answer(plan)))
        val planner = AgentInterviewPlanner(AgentInterviewGuesser(executor, model = model))
        val answered = InterviewSession(step = InterviewStep.F0_BISNIS, version = InterviewSession.BASED_ON_STORY,
            narrative = narasi, clarifications = listOf(ask.copy(answer = "Satu antrean")))
        val out = planner.plan(InterviewEvalPacks.draftOf(pack, answered), answered, narasi)
        assertEquals(2, executor.calls, "putaran pertama bertanya lagi ditolak, koreksi menghasilkan rencana")
        assertTrue(out.isPlanned)
        assertTrue(!out.awaitingClarification)
    }
}
