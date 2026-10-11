package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleGapAnalyzerTest {

    private val pack = InterviewFixtures.klinikPack
    private fun f(key: String, required: Boolean = false) = FieldProposal(key, key.replaceFirstChar { it.uppercase() }, FieldType.TEXT, required)

    private fun screen(module: String, fields: List<FieldProposal>): PrototypeScreen {
        val proposal = ScreenProposal(
            "s_$module", ModuleId(module), "Layar $module", WidgetKind.TABLE, "karena uji",
            EntityProposal("pasien", "Pasien", fields), ViewProposal.Table(fields.map { it.key })
        )
        return PrototypeScreen("s_$module", ModuleId(module), "Layar $module", WidgetKind.TABLE.code, proposal, ProposalSource.Agent("uji/v1"))
    }

    private fun draft(specs: List<RequirementSpec> = emptyList(), screens: List<PrototypeScreen> = emptyList()) =
        InterviewFixtures.draftOf(pack, InterviewFixtures.klinikSession.copy(specs = specs)).copy(screens = screens)

    @Test
    fun `empat aspek spesifikasi resmi yang kosong menjadi celah deterministik`() {
        val gaps = ModuleGapAnalyzer.analyze(draft(), "klinik_poli", limit = 10)
        assertEquals(
            listOf(ModuleGapKind.WHO_FILLS, ModuleGapKind.WHAT_RECORDED, ModuleGapKind.WHO_SEES, ModuleGapKind.DONE_WHEN),
            gaps.map { it.kind }
        )
        assertTrue(gaps.all { it.question.contains("Poli") }, "pertanyaan memakai label modul tenant")
        assertEquals(emptyList(), ModuleGapAnalyzer.analyze(draft(), "org_chart"), "governance tidak ditanya")
        assertEquals(emptyList(), ModuleGapAnalyzer.analyze(draft(), "modul_hantu"))
    }

    @Test
    fun `aspek yang sudah punya dasar tidak ditanya lagi dan hasil dibatasi`() {
        val spec = RequirementSpec(RoleKey("perawat"), whoFills = "Perawat", whatRecorded = "Nama dan keluhan")
        assertEquals(
            listOf(ModuleGapKind.WHO_SEES, ModuleGapKind.DONE_WHEN),
            ModuleGapAnalyzer.analyze(draft(listOf(spec)), "klinik_poli", limit = 10).map { it.kind }
        )
        assertEquals(1, ModuleGapAnalyzer.analyze(draft(), "klinik_poli", limit = 1).size)
    }

    @Test
    fun `field wajib tanpa penjelasan menghasilkan celah khusus`() {
        val spec = RequirementSpec(
            RoleKey("perawat"), whoFills = "Perawat", whatRecorded = "Keluhan",
            whoSees = "Dokter", doneWhen = "Pemeriksaan selesai"
        )
        val gaps = ModuleGapAnalyzer.analyze(draft(listOf(spec), listOf(screen("klinik_poli", listOf(f("nama", true), f("keluhan"))))), "klinik_poli", limit = 10)
        assertEquals(listOf(ModuleGapKind.REQUIRED_UNEXPLAINED), gaps.map { it.kind })
        assertTrue(gaps.single().question.contains("Nama"))
    }

    @Test
    fun `celah yang sudah pernah ditanyakan di utas tidak ditanyakan ulang`() {
        val gaps = ModuleGapAnalyzer.analyze(draft(), "klinik_poli", limit = 10)
        val asked = ChatMessage(
            ChatMessageId("q"), BuilderConversationId("c"), TenantId("ten-uji"), ChatRole.AGENT, "tanya", moduleId = "klinik_poli",
            kind = ChatMessageKind.QUESTION,
            questions = gaps.take(2).map { com.eventverse.app.domain.discovery.interview.Clarification(it.code, it.question) }
        )
        assertEquals(gaps.drop(2).map { it.code }, ModuleGapAnalyzer.unasked(gaps, listOf(asked)).map { it.code })
    }
}
