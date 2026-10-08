package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evaluasi deterministik (tanpa LLM) kosakata tipe field untuk agent (Irisan 2 Track B, C3 `LONG_TEXT`):
 * katalog dan prompt sepadan dengan `FieldType.entries` — tipe baru tanpa padanan menggagalkan tes ini —
 * dan usulan ber-`LONG_TEXT` pada pack non-garment (klinik) lolos validator dan round-trip codec.
 */
class KoogDiscoveryFieldTypeTest {

    private val tokenPattern = Regex("[A-Z][A-Z_]+")

    @Test
    fun `katalog fieldTypes memuat tiap tipe enum dengan catatan tidak kosong`() {
        val catalog = JsonParser.parseObject(screenCatalogJson(emptyList()))
        val entries = catalog.objectArray("fieldTypes")

        assertEquals(FieldType.entries.map { it.name }, entries.map { it.string("name") })
        entries.forEach { assertTrue(it.string("note").orEmpty().isNotBlank(), "Catatan kosong: ${it.string("name")}") }
    }

    @Test
    fun `catatan LONG_TEXT membedakannya dari TEXT`() {
        val long = KoogDiscoveryFieldTypeVocabulary.note(FieldType.LONG_TEXT)
        val short = KoogDiscoveryFieldTypeVocabulary.note(FieldType.TEXT)
        assertTrue(long.contains("multibaris") && long.contains("TEXT"), long)
        assertTrue(short.contains("satu baris"), short)
    }

    @Test
    fun `prompt memuat tiap tipe enum dan tidak menyebut tipe yang tidak ada`() {
        val system = KoogDiscoveryPrompt.system
        val rule = KoogDiscoveryFieldTypeVocabulary.promptRule

        FieldType.entries.forEach { assertTrue(system.contains(it.name), "Prompt tak menyebut ${it.name}") }
        assertTrue(system.contains(rule), "Aturan tipe field wajib tampil apa adanya di prompt")
        // Daftar bertanda kurung kurawal "{A|B|…}" dalam aturan hanya boleh berisi nama enum.
        val listed = Regex("\\{([A-Z_|]+)\\}").find(rule)?.groupValues?.get(1)?.split("|").orEmpty()
        assertEquals(FieldType.entries.map { it.name }, listed)
        assertTrue(listed.all { tokenPattern.matches(it) })
    }

    @Test
    fun `prompt penyunting modul memuat tiap tipe enum`() {
        val editor = com.eventverse.app.infrastructure.builder.KoogModuleEditor.SYSTEM
        FieldType.entries.forEach { assertTrue(editor.contains(it.name), "Penyunting tak menyebut ${it.name}") }
    }

    private fun klinik(): DiscoveryDraft = runBlocking {
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik dengan antrean pasien.", "klinik")).getOrThrow()
    }

    private fun kunjungan(draft: DiscoveryDraft): ScreenProposal {
        val entity = EntityProposal(
            id = "kunjungan",
            label = "Kunjungan",
            fields = listOf(
                FieldProposal("pasien", "Nama pasien", FieldType.TEXT, required = true),
                FieldProposal("keluhan", "Keluhan", FieldType.LONG_TEXT, required = true),
                FieldProposal("catatan", "Catatan dokter", FieldType.LONG_TEXT)
            )
        )
        return ScreenProposal(
            screenId = "kunjungan_form",
            moduleId = draft.pack.modules.first().id,
            title = "Catat Kunjungan",
            widget = WidgetKind.FORM,
            rationale = "Dipilih karena petugas mencatat keluhan pasien satu per satu.",
            entity = entity,
            view = ViewProposal.Form(fields = listOf("pasien", "keluhan", "catatan"), submitLabel = "Simpan"),
            seed = listOf(mapOf("pasien" to "Ibu Sari", "keluhan" to "Gigi ngilu\nsejak dua hari", "catatan" to "Kontrol minggu depan"))
        )
    }

    @Test
    fun `usulan ber-LONG_TEXT pada pack klinik dengan seed multibaris lolos validator dan round-trip`() {
        val draft = klinik()
        val proposal = kunjungan(draft)

        assertEquals(emptyList(), ScreenProposalValidator.validate(proposal, verticalPurity = true))

        val screen = PrototypeScreen(
            proposal.screenId, proposal.moduleId, proposal.title, proposal.widget.code, proposal, ProposalSource.Agent("koog/uji")
        )
        val withScreen = draft.copy(screens = listOf(screen))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(withScreen))

        val decoded = DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(withScreen))
        val entity = requireNotNull(decoded.screens.single().proposal?.entity)
        assertEquals(
            listOf(FieldType.TEXT, FieldType.LONG_TEXT, FieldType.LONG_TEXT),
            entity.fields.map { it.type }
        )
        assertEquals("Gigi ngilu\nsejak dua hari", decoded.screens.single().proposal?.seed?.single()?.get("keluhan"))
    }
}
