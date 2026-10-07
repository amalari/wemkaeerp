package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
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

    @Test
    fun testOldDraftWithoutInterviewParsesIdentically() {
        val oldJson = jsonObjectOf(
            "id" to jsonOf("draft-no-interview"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("garment"),
            "packDisplayName" to jsonOf("Konveksi"),
            "blueprintCode" to jsonOf("fob"),
            "blueprintDescription" to jsonOf("FOB"),
            "modules" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "id" to jsonOf("sewing_kanban"),
                        "displayName" to jsonOf("Kanban Jahit"),
                        "section" to jsonOf("PRODUKSI"),
                        "kind" to jsonOf("OPERATIONAL")
                    )
                )
            ),
            "activeModuleCodes" to jsonArrayOf(listOf(jsonOf("sewing_kanban"))),
            "screens" to jsonArrayOf(emptyList())
        )

        val uiModel = DiscoveryDraftUi.fromJson(oldJson)
        assertEquals("draft-no-interview", uiModel.id)
        assertNull(uiModel.interview)
        assertNull(uiModel.nextQuestion)
        assertEquals(1, uiModel.modules.size)
        assertNull(uiModel.modules.first().origin)
    }

    @Test
    fun testDraftWithInterviewSessionAndNextQuestion() {
        val draftJson = jsonObjectOf(
            "id" to jsonOf("draft-klinik-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("klinik"),
            "packDisplayName" to jsonOf("Klinik"),
            "blueprintCode" to jsonOf("klinik_starter"),
            "blueprintDescription" to jsonOf("Alur Klinik"),
            "modules" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "id" to jsonOf("klinik_pendaftaran"),
                        "displayName" to jsonOf("Pendaftaran"),
                        "section" to jsonOf("UTAMA"),
                        "kind" to jsonOf("OPERATIONAL"),
                        "origin" to jsonOf("new")
                    ),
                    jsonObjectOf(
                        "id" to jsonOf("org_chart"),
                        "displayName" to jsonOf("Bagan Organisasi"),
                        "section" to jsonOf("TATA_KELOLA"),
                        "kind" to jsonOf("GOVERNANCE"),
                        "origin" to jsonOf("reuse_platform")
                    )
                )
            ),
            "activeModuleCodes" to jsonArrayOf(listOf(jsonOf("klinik_pendaftaran"))),
            "screens" to jsonArrayOf(emptyList()),
            "interview" to jsonObjectOf(
                "step" to jsonOf("g1_divisi"),
                "divisions" to jsonArrayOf(
                    listOf(
                        jsonObjectOf("code" to jsonOf("pendaftaran"), "name" to jsonOf("Pendaftaran"), "source" to jsonOf("guess")),
                        jsonObjectOf("code" to jsonOf("poli"), "name" to jsonOf("Poli"), "source" to jsonOf("guess"))
                    )
                ),
                "roles" to jsonArrayOf(
                    listOf(
                        jsonObjectOf(
                            "roleKey" to jsonOf("resepsionis"),
                            "label" to jsonOf("Resepsionis"),
                            "divisionCode" to jsonOf("pendaftaran"),
                            "source" to jsonOf("guess"),
                            "isHead" to jsonOf(true)
                        )
                    )
                ),
                "links" to jsonArrayOf(
                    listOf(
                        jsonObjectOf(
                            "roleKey" to jsonOf("resepsionis"),
                            "moduleId" to jsonOf("klinik_pendaftaran"),
                            "origin" to jsonOf("new"),
                            "features" to jsonArrayOf(listOf(jsonOf("Antrean Pasien"))),
                            "confirmed" to jsonOf("confirmed"),
                            "confidence" to jsonOf(85)
                        )
                    )
                ),
                "handoffs" to jsonArrayOf(
                    listOf(
                        jsonObjectOf(
                            "from" to jsonOf("klinik_pendaftaran"),
                            "to" to jsonOf("klinik_poli"),
                            "portType" to jsonOf("Permintaan"),
                            "confirmed" to jsonOf("confirmed")
                        )
                    )
                ),
                "answers" to jsonArrayOf(
                    listOf(
                        jsonObjectOf(
                            "turn" to jsonOf(1),
                            "step" to jsonOf("g1_divisi"),
                            "questionId" to jsonOf("q-1"),
                            "outcome" to jsonOf("confirmed"),
                            "text" to jsonOf("Semua divisi cocok")
                        )
                    )
                )
            ),
            "nextQuestion" to jsonObjectOf(
                "id" to jsonOf("q-2"),
                "step" to jsonOf("g2_peran"),
                "prompt" to jsonOf("Siapa saja yang bertugas di Poli?"),
                "guesses" to jsonArrayOf(
                    listOf(
                        jsonObjectOf(
                            "key" to jsonOf("perawat"),
                            "label" to jsonOf("Perawat Poli"),
                            "confidence" to jsonOf(75)
                        )
                    )
                )
            )
        )

        val ui = DiscoveryDraftUi.fromJson(draftJson)
        val session = requireNotNull(ui.interview)
        assertEquals(InterviewStep.G1_DIVISI, session.step)
        assertEquals(2, session.divisions.size)
        assertEquals("pendaftaran", session.divisions.first().code.value)
        assertEquals(ItemSource.GUESS, session.divisions.first().source)

        assertEquals(1, session.roles.size)
        val role = session.roles.first()
        assertEquals("resepsionis", role.roleKey.value)
        assertEquals(true, role.isHead)

        assertEquals(1, session.links.size)
        val link = session.links.first()
        assertEquals(ModuleOrigin.NEW, link.origin)
        assertEquals(listOf("Antrean Pasien"), link.features)
        assertEquals(Confirmation.CONFIRMED, link.confirmed)
        assertEquals(85, link.confidence)

        assertEquals(1, session.handoffs.size)
        assertEquals(1, session.answers.size)

        // Module origins
        assertEquals(ModuleOrigin.NEW, ui.modules[0].origin)
        assertEquals("Baru", ui.modules[0].origin?.displayName)
        assertEquals(ModuleOrigin.REUSE_PLATFORM, ui.modules[1].origin)
        assertEquals("Pakai Ulang Platform", ui.modules[1].origin?.displayName)

        // Next Question
        val nextQ = requireNotNull(ui.nextQuestion)
        assertEquals("q-2", nextQ.id)
        assertEquals(InterviewStep.G2_PERAN, nextQ.step)
        assertEquals("Siapa saja yang bertugas di Poli?", nextQ.prompt)
        assertEquals(1, nextQ.guesses.size)
        assertEquals("perawat", nextQ.guesses.first().key)
        assertEquals(75, nextQ.guesses.first().confidence)
    }

    @Test
    fun testDraftWithMalformedInterviewHandlesGracefully() {
        val brokenJson = jsonObjectOf(
            "id" to jsonOf("draft-broken-01"),
            "status" to jsonOf("DRAFT"),
            "packCode" to jsonOf("klinik"),
            "packDisplayName" to jsonOf("Klinik"),
            "blueprintCode" to jsonOf("k"),
            "blueprintDescription" to jsonOf("d"),
            "modules" to jsonArrayOf(emptyList()),
            "activeModuleCodes" to jsonArrayOf(emptyList()),
            "screens" to jsonArrayOf(emptyList()),
            "interview" to jsonOf("this is not an object, it is a string!"),
            "nextQuestion" to jsonOf(12345)
        )

        val ui = DiscoveryDraftUi.fromJson(brokenJson)
        assertEquals("draft-broken-01", ui.id)
        assertNull(ui.interview)
        assertNull(ui.nextQuestion)
    }
}

