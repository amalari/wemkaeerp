package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProposalUiComponentsTest {

    @Test
    fun testProposalSourceDisplayName() {
        val packSource: ProposalSource = ProposalSource.Pack
        assertEquals("Pack", packSource.displayName)

        val detSource: ProposalSource = ProposalSource.Deterministic
        assertEquals("Deterministik", detSource.displayName)

        val agentSource: ProposalSource = ProposalSource.Agent("koog/deepseek-v3/draft-v1")
        assertEquals("Agent · draft-v1", agentSource.displayName)

        val agentSourceSimple: ProposalSource = ProposalSource.Agent("gemini-2.5-pro")
        assertEquals("Agent · gemini-2.5-pro", agentSourceSimple.displayName)
    }

    @Test
    fun testScreenUiWithProposalAndInteractiveBlock() {
        val proposal = ScreenProposal(
            screenId = "screen-triage",
            moduleId = ModuleId("clinic_triage"),
            title = "Antrean Pasien Triase",
            widget = WidgetKind.KANBAN,
            rationale = "Dipilih karena pasien berpindah dari antre periksa, observasi, hingga selesai.",
            entity = EntityProposal(
                id = "patient",
                label = "Pasien",
                fields = listOf(
                    FieldProposal("nama", "Nama Pasien", FieldType.TEXT, required = true),
                    FieldProposal("status", "Status", FieldType.ENUM, options = listOf("Menunggu", "Pemeriksaan", "Selesai"))
                ),
                statusField = "status"
            ),
            view = ViewProposal.Kanban(emptyList(), emptyMap(), emptyList()),
            seed = listOf(mapOf("nama" to "Budi", "status" to "Menunggu"))
        )

        val sampleRows = listOf(
            mapOf("Kolom" to "Menunggu", "Nama Pasien" to "Budi"),
            mapOf("Kolom" to "Pemeriksaan", "Nama Pasien" to "Siti")
        )

        val interactive = InteractiveScreenFactory.kanban(
            screenId = "screen-triage",
            title = "Antrean Pasien Triase",
            rows = sampleRows
        )

        val screenUi = DiscoveryScreenUi(
            screenId = "screen-triage",
            moduleId = "clinic_triage",
            title = "Antrean Pasien Triase",
            widget = "KANBAN",
            sampleRows = sampleRows,
            interactive = interactive,
            proposal = proposal,
            rationale = proposal.rationale,
            source = ProposalSource.Deterministic
        )

        assertEquals("screen-triage", screenUi.screenId)
        assertEquals("Antrean Pasien Triase", screenUi.title)
        assertEquals(ProposalSource.Deterministic, screenUi.source)
        assertEquals("Deterministik", screenUi.source?.displayName)
        assertNotNull(screenUi.proposal)
        assertEquals(2, screenUi.proposal?.entity?.fields?.size)
        assertNotNull(screenUi.interactive)

        val session = PrototypeSession(listOf(screenUi))
        val block = session.block("screen-triage")
        assertNotNull(block)
        assertTrue(block is PlayableState)
        assertEquals(2, block.rows?.size)
    }

    @Test
    fun testIncompleteProposalScreenHasNoBlock() {
        val screenUi = DiscoveryScreenUi(
            screenId = "screen-print",
            moduleId = "surat_jalan",
            title = "Cetak Surat Jalan",
            widget = "PRINT",
            sampleRows = emptyList(),
            interactive = null,
            proposal = null,
            rationale = "Dipilih karena dokumen diserahkan secara fisik.",
            source = ProposalSource.Pack
        )

        val session = PrototypeSession(listOf(screenUi))
        val block = session.block("screen-print")
        assertNull(block)
        assertTrue(screenUi.sampleRows.isEmpty())
        assertEquals("Dipilih karena dokumen diserahkan secara fisik.", screenUi.rationale)
        assertEquals(ProposalSource.Pack, screenUi.source)
    }
}
