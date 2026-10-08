package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.discovery.proposal.SkeletonHint
import com.eventverse.app.domain.discovery.proposal.SkeletonWidth
import com.eventverse.app.routes.summaryObj
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.discovery.ScreenProposalCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Evaluasi deterministik (tanpa LLM) kerangka CUSTOM_SCREEN yang dinyatakan agent (Irisan 3b Track B): usulan
 * ber-blok sah lolos, petunjuk tak sah ditolak, katalog/prompt sepadan dengan validator, dan klien menerima
 * baris blok di jalur ber-proposal. Dijalankan pada pack klinik (non-garment).
 */
class KoogDiscoverySkeletonTest {

    private fun proposalJson(view: String, moduleId: String) =
        """{"screenId":"kasir","moduleId":"$moduleId","title":"Kasir","widget":"CUSTOM_SCREEN",""" +
            """"rationale":"Dipilih karena kasir klinik belum punya jenis baku.","entity":null,"view":$view,"seed":[]}"""

    private fun decode(json: String): ScreenProposal =
        ScreenProposalCodec.decode(JsonParser.parseObject(json), "$.screens[0].proposal")

    private fun klinik(): DiscoveryDraft = runBlocking {
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik dengan antrean pasien.", "klinik")).getOrThrow()
    }

    private fun moduleOf(d: DiscoveryDraft) = d.pack.modules.first().id.value

    private fun screenOf(p: ScreenProposal) =
        PrototypeScreen("kasir", p.moduleId, p.title, WidgetKind.CUSTOM_SCREEN.code, p, ProposalSource.Agent("koog/uji"))

    private val keranjangPembayaran =
        """{"blocks":[{"label":"Keranjang","width":"FULL","hint":"TABLE"},{"label":"Pembayaran","width":"HALF","hint":"FORM"}]}"""

    @Test
    fun `usulan custom screen dengan blok sah lolos validator dan draf`() {
        val d = klinik()
        val proposal = decode(proposalJson(keranjangPembayaran, moduleOf(d)))

        assertEquals(emptyList(), ScreenProposalValidator.validate(proposal, verticalPurity = true))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(d.copy(screens = listOf(screenOf(proposal)))))
    }

    @Test
    fun `usulan custom screen dengan hint atau lebar tak dikenal ditolak dengan path`() {
        val moduleId = moduleOf(klinik())
        val hintBurung = """{"blocks":[{"label":"Keranjang","hint":"CAROUSEL"}]}"""
        val lebarBesar = """{"blocks":[{"label":"Keranjang","width":"THIRD"}]}"""
        listOf(hintBurung to "hint", lebarBesar to "width").forEach { (view, key) ->
            val e = assertFailsWith<DiscoveryDraftDecodeException> { decode(proposalJson(view, moduleId)) }
            assertTrue(e.path.contains(key), e.path)
        }
    }

    @Test
    fun `blok melebihi batas atau label kembar ditolak validator`() {
        val moduleId = moduleOf(klinik())
        val banyak = (1..ProposalLimits.BLOCKS + 1).joinToString(",", "{\"blocks\":[", "]}") { "{\"label\":\"Blok $it\"}" }
        assertTrue(ScreenProposalValidator.validate(decode(proposalJson(banyak, moduleId))).any { it.path.endsWith(".view.blocks") })
        val kembar = """{"blocks":[{"label":"Keranjang"},{"label":"keranjang"}]}"""
        assertTrue(ScreenProposalValidator.validate(decode(proposalJson(kembar, moduleId))).any { it.message.contains("kembar") })
    }

    @Test
    fun `katalog dan prompt memuat batas dan kosakata yang sama dengan validator`() {
        val catalog = JsonParser.parseObject(screenCatalogJson(emptyList()))
        val sk = requireNotNull(catalog.obj("skeleton"))
        assertEquals(ProposalLimits.BLOCKS, sk.int("maxBlocks"))
        assertEquals(ProposalLimits.TEXT, sk.int("maxLabelChars"))
        assertEquals(SkeletonHint.entries.map { it.name }, sk.stringArray("hints"))
        assertEquals(SkeletonWidth.entries.map { it.name }, sk.stringArray("widths"))
        assertEquals(false, sk.boolean("interactive"))

        val custom = catalog.objectArray("widgetKinds").first { it.string("code") == WidgetKind.CUSTOM_SCREEN.code }
        assertTrue(custom.string("view")!!.contains("blocks"))
        assertTrue(custom.string("note")!!.contains("non-interaktif"))

        val prompt = KoogDiscoveryPrompt.system
        assertTrue(prompt.contains("1–${ProposalLimits.BLOCKS} blok"))
        assertTrue(prompt.contains(SkeletonHint.entries.joinToString("|") { it.name }))
        assertTrue(prompt.contains("non-interaktif"))
    }

    @Test
    fun `ringkasan jalur ber-proposal mengirim baris blok dan proposal membawa view blocks`() {
        val d = klinik()
        val proposal = decode(proposalJson(keranjangPembayaran, moduleOf(d)))
        val stored = StoredDiscoveryDraft(DiscoveryDraftId("draft-uji"), UserId("usr-uji"), d.copy(screens = listOf(screenOf(proposal))))

        val sent = summaryObj(stored).objectArray("screens").single()

        val rows = sent.array("sampleRows").filterIsInstance<JsonValue.Obj>()
        assertEquals(listOf("Keranjang", "Pembayaran"), rows.map { it.string("Blok") })
        assertEquals(listOf("penuh", "separuh"), rows.map { it.string("Lebar") })
        assertEquals(listOf("tabel", "formulir"), rows.map { it.string("Petunjuk") })
        val blocks = requireNotNull(requireNotNull(sent.obj("proposal")).obj("view")).objectArray("blocks")
        assertEquals(listOf("TABLE", "FORM"), blocks.map { it.string("hint") })
    }
}
