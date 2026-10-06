package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.shared.discovery.ScreenProposalCodec
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryUiModelParseTest {

    @Test
    fun testOldDraftWithoutProposalParsesIdentically() {
        val oldJson = jsonObjectOf(
            "id" to jsonOf("draft-old-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("garment"),
            "packDisplayName" to jsonOf("Garment Factory"),
            "blueprintCode" to jsonOf("cmt"),
            "blueprintDescription" to jsonOf("CMT flow"),
            "modules" to jsonArrayOf(emptyList()),
            "activeModuleCodes" to jsonArrayOf(emptyList()),
            "screens" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "screenId" to jsonOf("screen-po"),
                        "moduleId" to jsonOf("sales_order"),
                        "title" to jsonOf("Daftar PO"),
                        "widget" to jsonOf("TABLE"),
                        "sampleRows" to jsonArrayOf(
                            listOf(
                                jsonObjectOf("Pembeli" to jsonOf("Acme Corp"), "Status" to jsonOf("Aktif"))
                            )
                        )
                    )
                )
            )
        )

        val uiModel = DiscoveryDraftUi.fromJson(oldJson)
        assertEquals("draft-old-01", uiModel.id)
        assertEquals(1, uiModel.screens.size)
        val screen = uiModel.screens.first()
        assertEquals("screen-po", screen.screenId)
        assertEquals("Daftar PO", screen.title)
        assertEquals("TABLE", screen.widget)
        assertEquals(1, screen.sampleRows.size)
        assertNull(screen.proposal)
        assertNull(screen.rationale)
        assertNull(screen.source)
    }

    @Test
    fun testDraftWithPackProposal() {
        val proposal = ScreenProposal(
            screenId = "screen-po",
            moduleId = ModuleId("sales_order"),
            title = "Daftar PO & Pelanggan",
            widget = WidgetKind.TABLE,
            rationale = "Dipilih karena PO dan prospek dibandingkan berderet.",
            entity = EntityProposal(
                id = "order",
                label = "Pesanan",
                fields = listOf(
                    FieldProposal("pembeli", "Pembeli", FieldType.TEXT, required = true),
                    FieldProposal("status", "Status", FieldType.ENUM, options = listOf("Draft", "Konfirmasi"))
                ),
                statusField = "status"
            ),
            view = ViewProposal.Table(listOf("pembeli", "status"), inlineCreate = true, editableFields = listOf("status")),
            seed = listOf(mapOf("pembeli" to "PT Maju", "status" to "Draft"))
        )

        val draftJson = jsonObjectOf(
            "id" to jsonOf("draft-pack-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("garment"),
            "packDisplayName" to jsonOf("Garment Factory"),
            "blueprintCode" to jsonOf("cmt"),
            "blueprintDescription" to jsonOf("CMT"),
            "modules" to jsonArrayOf(emptyList()),
            "activeModuleCodes" to jsonArrayOf(emptyList()),
            "screens" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "screenId" to jsonOf("screen-po"),
                        "moduleId" to jsonOf("sales_order"),
                        "title" to jsonOf("Daftar PO & Pelanggan"),
                        "widget" to jsonOf("TABLE"),
                        "sampleRows" to jsonArrayOf(emptyList()),
                        "proposal" to ScreenProposalCodec.encode(proposal),
                        "source" to ScreenProposalCodec.encodeSource(ProposalSource.Pack)
                    )
                )
            )
        )

        val uiModel = DiscoveryDraftUi.fromJson(draftJson)
        val screen = uiModel.screens.first()
        assertNotNull(screen.proposal)
        assertEquals("Dipilih karena PO dan prospek dibandingkan berderet.", screen.rationale)
        assertEquals(ProposalSource.Pack, screen.source)
        assertEquals("Pack", screen.source?.displayName)
    }

    @Test
    fun testDraftWithDeterministicProposal() {
        val draftJson = jsonObjectOf(
            "id" to jsonOf("draft-det-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("clinic"),
            "packDisplayName" to jsonOf("Klinik Pratama"),
            "blueprintCode" to jsonOf("clinic-standard"),
            "blueprintDescription" to jsonOf("Alur Klinik"),
            "modules" to jsonArrayOf(emptyList()),
            "activeModuleCodes" to jsonArrayOf(emptyList()),
            "screens" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "screenId" to jsonOf("screen-antrean"),
                        "moduleId" to jsonOf("queue_triage"),
                        "title" to jsonOf("Papan Antrean Pasien"),
                        "widget" to jsonOf("KANBAN"),
                        "sampleRows" to jsonArrayOf(emptyList()),
                        "rationale" to jsonOf("Dipilih karena antrean pasien bergerak dari registrasi ke poli dan kasir."),
                        "source" to jsonObjectOf("kind" to jsonOf("DETERMINISTIC"))
                    )
                )
            )
        )

        val uiModel = DiscoveryDraftUi.fromJson(draftJson)
        val screen = uiModel.screens.first()
        assertEquals("Dipilih karena antrean pasien bergerak dari registrasi ke poli dan kasir.", screen.rationale)
        assertEquals(ProposalSource.Deterministic, screen.source)
        assertEquals("Deterministik", screen.source?.displayName)
    }

    @Test
    fun testDraftWithAgentProposal() {
        val draftJson = jsonObjectOf(
            "id" to jsonOf("draft-agent-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("workshop"),
            "packDisplayName" to jsonOf("Bengkel Mobil"),
            "blueprintCode" to jsonOf("workshop-flow"),
            "blueprintDescription" to jsonOf("Alur Bengkel"),
            "modules" to jsonArrayOf(emptyList()),
            "activeModuleCodes" to jsonArrayOf(emptyList()),
            "screens" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "screenId" to jsonOf("screen-wo"),
                        "moduleId" to jsonOf("work_order"),
                        "title" to jsonOf("Papan Work Order Mekanik"),
                        "widget" to jsonOf("KANBAN"),
                        "sampleRows" to jsonArrayOf(emptyList()),
                        "rationale" to jsonOf("Dipilih karena mekanik memantau status servis kendaraan per stall."),
                        "source" to jsonObjectOf(
                            "kind" to jsonOf("AGENT"),
                            "agentRef" to jsonOf("koog/deepseek-v3/draft-v1")
                        )
                    )
                )
            )
        )

        val uiModel = DiscoveryDraftUi.fromJson(draftJson)
        val screen = uiModel.screens.first()
        assertEquals("Dipilih karena mekanik memantau status servis kendaraan per stall.", screen.rationale)
        assertTrue(screen.source is ProposalSource.Agent)
        val agent = screen.source as ProposalSource.Agent
        assertEquals("koog/deepseek-v3/draft-v1", agent.agentRef)
        assertEquals("Agent · draft-v1", screen.source?.displayName)
    }

    @Test
    fun testDraftWithStringSourceFallback() {
        val draftJson = jsonObjectOf(
            "id" to jsonOf("draft-str-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("catering"),
            "packDisplayName" to jsonOf("Katering"),
            "blueprintCode" to jsonOf("catering-flow"),
            "blueprintDescription" to jsonOf("Alur Katering"),
            "modules" to jsonArrayOf(emptyList()),
            "activeModuleCodes" to jsonArrayOf(emptyList()),
            "screens" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "screenId" to jsonOf("screen-menu"),
                        "moduleId" to jsonOf("menu_planning"),
                        "title" to jsonOf("Perencanaan Menu Harian"),
                        "widget" to jsonOf("TABLE"),
                        "sampleRows" to jsonArrayOf(emptyList()),
                        "rationale" to jsonOf("Dipilih untuk menyusun porsi dan menu harian berderet."),
                        "source" to jsonOf("AGENT:koog/claude-3-7-sonnet")
                    )
                )
            )
        )

        val uiModel = DiscoveryDraftUi.fromJson(draftJson)
        val screen = uiModel.screens.first()
        assertEquals(ProposalSource.Agent("koog/claude-3-7-sonnet"), screen.source)
        assertEquals("Agent · claude-3-7-sonnet", screen.source?.displayName)
    }
}
