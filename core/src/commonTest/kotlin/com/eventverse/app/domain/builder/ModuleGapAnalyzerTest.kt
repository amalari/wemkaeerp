package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
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

    private fun screen(module: String, fields: List<FieldProposal>, widget: WidgetKind = WidgetKind.TABLE, source: ProposalSource = ProposalSource.Agent("uji/v1"),
                       statusField: String? = null, transitions: Map<String, List<String>> = emptyMap(),
                       view: ViewProposal = ViewProposal.Table(fields.map { it.key })): PrototypeScreen {
        val proposal = ScreenProposal("s_$module", ModuleId(module), "Layar $module", widget, "karena uji",
            EntityProposal("pasien", "Pasien", fields, statusField, transitions), view)
        return PrototypeScreen("s_$module", ModuleId(module), "Layar $module", widget.code, proposal, source)
    }

    private fun draft(vararg screens: PrototypeScreen) = draftOf(pack, null).copy(screens = screens.toList())

    @Test
    fun `modul tanpa layar berproposal ditanya isian utamanya, dan modul bukan operasional atau tak dikenal tidak ditanya`() {
        val d = draft()
        val g = ModuleGapAnalyzer.analyze(d, "klinik_poli")
        assertEquals(listOf(ModuleGapKind.NO_SCREEN), g.map { it.kind })
        assertTrue(g.single().question.contains("Poli"), "pertanyaan memakai label modul milik pack, bukan kosakata konveksi")
        assertEquals(emptyList(), ModuleGapAnalyzer.analyze(d, "org_chart"), "modul governance tidak punya layar untuk ditanyakan")
        assertEquals(emptyList(), ModuleGapAnalyzer.analyze(d, "modul_hantu"))
    }

    @Test
    fun `celah dihitung dari data layar, urut kepentingan, dan dibatasi`() {
        val thin = screen("klinik_poli", listOf(f("nama"), f("keluhan")), source = ProposalSource.Deterministic)
        val kinds = ModuleGapAnalyzer.analyze(draft(thin), "klinik_poli", limit = 10).map { it.kind }
        assertEquals(listOf(ModuleGapKind.FEW_FIELDS, ModuleGapKind.NO_REQUIRED, ModuleGapKind.GENERIC_SOURCE), kinds)
        assertEquals(2, ModuleGapAnalyzer.analyze(draft(thin), "klinik_poli", limit = 2).size)
    }

    @Test
    fun `layar lengkap buatan agent tanpa celah, dan kanban tanpa urutan status ditanya alurnya`() {
        val full = screen("klinik_poli", listOf(f("a", true), f("b"), f("c"), f("d")))
        assertEquals(emptyList(), ModuleGapAnalyzer.analyze(draft(full), "klinik_poli"))

        val statusField = FieldProposal("status", "Status", FieldType.ENUM, true, listOf("baru", "selesai"))
        val kanban = screen("klinik_pendaftaran", listOf(f("a", true), f("b"), f("c"), statusField), WidgetKind.KANBAN, statusField = "status",
            view = ViewProposal.Kanban())
        assertEquals(listOf(ModuleGapKind.NO_STATUS_FLOW), ModuleGapAnalyzer.analyze(draft(kanban), "klinik_pendaftaran").map { it.kind })
    }

    @Test
    fun `celah yang sudah pernah ditanyakan di utas tidak ditanyakan ulang`() {
        val thin = screen("klinik_poli", listOf(f("nama")), source = ProposalSource.Deterministic)
        val gaps = ModuleGapAnalyzer.analyze(draft(thin), "klinik_poli", limit = 10)
        val asked = ChatMessage(
            ChatMessageId("q"), BuilderConversationId("c"), TenantId("ten-uji"), ChatRole.AGENT, "tanya", moduleId = "klinik_poli",
            kind = ChatMessageKind.QUESTION, questions = gaps.take(2).map { com.eventverse.app.domain.discovery.interview.Clarification(it.code, it.question) }
        )
        assertEquals(gaps.drop(2).map { it.code }, ModuleGapAnalyzer.unasked(gaps, listOf(asked)).map { it.code })
    }
}
