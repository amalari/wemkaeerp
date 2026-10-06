package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.toInteractiveScreen
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.draft
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.kanbanAntrean
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.screenOf
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenProposalCodecTest {

    private fun roundTrip(d: com.eventverse.app.domain.discovery.DiscoveryDraft) =
        DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(d))

    @Test
    fun `round-trip semua jenis usulan lewat dokumen draf`() {
        val d = draft(*ScreenProposalFixtures.semua().map { screenOf(it) }.toTypedArray())
        assertEquals(d, roundTrip(d))
    }

    @Test
    fun `round-trip ketiga asal usulan`() {
        listOf(ProposalSource.Pack, ProposalSource.Deterministic, ProposalSource.Agent("koog/model/draft-v1")).forEach { src ->
            val d = draft(screenOf(kanbanAntrean(), src))
            assertEquals(src, roundTrip(d).screens.single().source)
        }
    }

    @Test
    fun `draf lama tanpa proposal tetap terbaca dan di-encode persis sama`() {
        val old = draft(PrototypeScreen("s1", ScreenProposalFixtures.moduleId, "Antrean", "KANBAN"))
        val json = DiscoveryDraftCodec.encodeToString(old)
        assertTrue(!json.contains("proposal") && !json.contains("source"), json)
        val decoded = DiscoveryDraftCodec.decode(json)
        assertNull(decoded.screens.single().proposal)
        assertNull(decoded.screens.single().source)
        assertEquals(json, DiscoveryDraftCodec.encodeToString(decoded))
    }

    private fun patched(edit: (String) -> String): String =
        edit(DiscoveryDraftCodec.encodeToString(draft(screenOf(kanbanAntrean()))))

    private fun failurePath(raw: String): String = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(raw) }.path

    @Test
    fun `kosakata tertutup ditolak dengan path`() {
        assertEquals("$.screens[0].proposal.widget", failurePath(patched { it.replace("\"widget\":\"KANBAN\",\"rationale\"", "\"widget\":\"PETA\",\"rationale\"") }))
        assertEquals("$.screens[0].proposal.entity.fields[0].type", failurePath(patched { it.replace("\"type\":\"TEXT\"", "\"type\":\"UANG\"") }))
        assertEquals("$.screens[0].proposal.view.card", failurePath(patched { it.replace("\"style\":\"TITLE\"", "\"style\":\"NEON\"") }).removeSuffix("[0]"))
        assertEquals("$.screens[0].source.kind", failurePath(patched { it.replace("\"DETERMINISTIC\"", "\"DUKUN\"") }))
    }

    @Test
    fun `bentuk salah ditolak bukan dibuang diam-diam`() {
        assertEquals("$.screens[0].proposal", failurePath(patched { it.replace("\"proposal\":{", "\"proposal\":[],\"x\":{") }))
        assertEquals("$.screens[0].proposal.seed[0].prioritas", failurePath(patched { it.replace("\"prioritas\":\"ya\"", "\"prioritas\":true") }))
        assertEquals("$.screens[0].proposal.rationale", failurePath(patched { it.replace(Regex("\"rationale\":\"[^\"]*\""), "\"rationale\":7") }))
    }

    @Test
    fun `proposal bisa diparse dari keluaran model berbentuk ringkas`() {
        val raw = """{"screenId":"s","moduleId":"klinik_antrean","title":"Tagihan","widget":"TABLE","rationale":"Dipilih karena daftar.",
            "entity":{"id":"tagihan","label":"Tagihan","fields":[{"key":"jumlah","label":"Jumlah","type":"NUMBER"}]},
            "view":{"columns":["jumlah"]},"seed":[{"jumlah":"5"}]}"""
        val p = ScreenProposalCodec.decode(JsonParser.parseObject(raw), "$")
        assertEquals(listOf("jumlah"), (p.view as com.eventverse.app.domain.discovery.proposal.ViewProposal.Table).columns)
        assertTrue(p.toInteractiveScreen().isSuccess)
    }

    // --- validator draf memanggil validator proposal -------------------------------------------
    @Test
    fun `validator draf menerima draf non-garment dengan proposal sah`() {
        val d = draft(*ScreenProposalFixtures.semua().map { screenOf(it) }.toTypedArray())
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(d))
    }

    @Test
    fun `galat proposal muncul di path layar pada draf`() {
        val bad = kanbanAntrean().copy(rationale = "")
        val issues = DiscoveryDraftValidator.validate(draft(screenOf(kanbanAntrean()), screenOf(bad.copy(screenId = "lain"))))
        assertEquals(listOf("$.screens[1].proposal.rationale"), issues.map { it.path })
    }

    @Test
    fun `identitas proposal harus sama dengan layar dan source wajib`() {
        val p = kanbanAntrean()
        val mismatch = PrototypeScreen("lain", p.moduleId, "Judul lain", "TABLE", p, null)
        val paths = DiscoveryDraftValidator.validate(draft(mismatch)).map { it.path }
        assertTrue("$.screens[0].proposal.screenId" in paths, "$paths")
        assertTrue("$.screens[0].proposal.widget" in paths, "$paths")
        assertTrue("$.screens[0].source" in paths, "$paths")
    }

    @Test
    fun `source tanpa proposal dilaporkan dan binding api dari agent ditolak`() {
        val lonely = PrototypeScreen("s", ScreenProposalFixtures.moduleId, "T", "TABLE", null, ProposalSource.Pack)
        assertEquals(listOf("$.screens[0].source"), DiscoveryDraftValidator.validate(draft(lonely)).map { it.path })
        val api: ScreenProposal = ScreenProposalFixtures.tabelTagihan()
            .copy(binding = com.eventverse.app.domain.prototype.DataBinding.Api("/api/x"))
        val issues = DiscoveryDraftValidator.validate(draft(screenOf(api, ProposalSource.Agent("koog/x"))))
        assertEquals(listOf("$.screens[0].proposal.binding"), issues.map { it.path })
    }
}
