package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.eventverse.app.domain.builder.ModuleEditRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.infrastructure.builder.KoogModuleEditor
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evaluasi deterministik (tanpa LLM) kosakata format angka untuk agent (Irisan 2 Track B, C4): katalog dan prompt
 * sepadan dengan `NumberFormat.entries` dan usulan CURRENCY/PERCENT pada pack non-garment (klinik) lolos validator
 * dan round-trip codec. Penegakan dua sisi `currencyCode` dan penyunting modul juga dicakup.
 */
class KoogDiscoveryNumberFormatTest {

    @Test
    fun `katalog numberFormats memuat tiap format enum dengan catatan tidak kosong`() {
        val catalog = JsonParser.parseObject(screenCatalogJson(emptyList()))
        val section = catalog["numberFormats"] as JsonValue.Obj
        val formats = section.objectArray("formats")

        assertEquals(NumberFormat.entries.map { it.name }, formats.map { it.string("name") })
        formats.forEach { assertTrue(it.string("note").orEmpty().isNotBlank(), "Catatan kosong: ${it.string("name")}") }
        assertTrue(KoogDiscoveryNumberFormatVocabulary.note(NumberFormat.CURRENCY).contains("currencyCode"))
    }

    @Test
    fun `prompt memuat tiap format enum dan daftar bertandanya persis enum`() {
        val system = KoogDiscoveryPrompt.system
        val rule = KoogDiscoveryNumberFormatVocabulary.promptRule

        NumberFormat.entries.forEach { assertTrue(system.contains(it.name), "Prompt tak menyebut ${it.name}") }
        assertTrue(system.contains(rule), "Aturan format angka wajib tampil apa adanya di prompt")
        val listed = Regex("format` ∈ \\{([A-Z_|]+)\\}").find(rule)?.groupValues?.get(1)?.split("|").orEmpty()
        assertEquals(NumberFormat.entries.map { it.name }, listed)
    }

    @Test
    fun `prompt penyunting modul memuat aturan format yang sama`() {
        val editor = KoogModuleEditor.SYSTEM
        assertTrue(editor.contains(KoogDiscoveryNumberFormatVocabulary.promptRule))
        NumberFormat.entries.forEach { assertTrue(editor.contains(it.name), "Penyunting tak menyebut ${it.name}") }
    }

    private fun klinik(): DiscoveryDraft = runBlocking {
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik dengan antrean pasien.", "klinik")).getOrThrow()
    }

    private fun tagihan(draft: DiscoveryDraft, fields: List<FieldProposal>, seed: Map<String, String>): ScreenProposal =
        ScreenProposal(
            screenId = "tagihan_form",
            moduleId = draft.pack.modules.first().id,
            title = "Catat Tagihan",
            widget = WidgetKind.FORM,
            rationale = "Dipilih karena kasir mencatat tagihan pasien satu per satu.",
            entity = EntityProposal(id = "tagihan", label = "Tagihan", fields = fields),
            view = ViewProposal.Form(fields = fields.map { it.key }, submitLabel = "Simpan"),
            seed = listOf(seed)
        )

    private val validFields = listOf(
        FieldProposal("pasien", "Nama pasien", FieldType.TEXT, required = true),
        FieldProposal("biaya", "Biaya", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "IDR"),
        FieldProposal("biaya_usd", "Biaya (USD)", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "USD"),
        FieldProposal("diskon", "Diskon", FieldType.NUMBER, format = NumberFormat.PERCENT),
        FieldProposal("jumlah", "Jumlah obat", FieldType.NUMBER)
    )
    private val validSeed = mapOf(
        "pasien" to "Ibu Sari", "biaya" to "250000", "biaya_usd" to "15.5", "diskon" to "12.5", "jumlah" to "3"
    )

    @Test
    fun `usulan CURRENCY IDR USD dan PERCENT pada pack klinik lolos validator dan round-trip`() {
        val draft = klinik()
        val proposal = tagihan(draft, validFields, validSeed)

        assertEquals(emptyList(), ScreenProposalValidator.validate(proposal, verticalPurity = true))
        val screen = PrototypeScreen(
            proposal.screenId, proposal.moduleId, proposal.title, proposal.widget.code, proposal, ProposalSource.Agent("koog/uji")
        )
        val withScreen = draft.copy(screens = listOf(screen))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(withScreen))

        val fields = requireNotNull(DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(withScreen)).screens.single().proposal?.entity).fields
        assertEquals(listOf(NumberFormat.PLAIN, NumberFormat.CURRENCY, NumberFormat.CURRENCY, NumberFormat.PERCENT, NumberFormat.PLAIN), fields.map { it.format })
        assertEquals(listOf(null, "IDR", "USD", null, null), fields.map { it.currencyCode })
    }

    @Test
    fun `CURRENCY tanpa kode atau kode salah bentuk ditolak validator`() {
        val draft = klinik()
        listOf(null, "idr", "RP", "RUPIAH").forEach { code ->
            val fields = listOf(FieldProposal("biaya", "Biaya", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = code))
            val issues = ScreenProposalValidator.validate(tagihan(draft, fields, mapOf("biaya" to "1")), verticalPurity = true)
            assertTrue(issues.any { it.toString().contains("currencyCode") }, "Kode '$code' lolos: $issues")
        }
    }

    @Test
    fun `kode mata uang pada field non-CURRENCY ditolak validator`() {
        val draft = klinik()
        val cases = listOf(
            FieldProposal("diskon", "Diskon", FieldType.NUMBER, format = NumberFormat.PERCENT, currencyCode = "IDR"),
            FieldProposal("jumlah", "Jumlah", FieldType.NUMBER, currencyCode = "IDR"),
            FieldProposal("pasien", "Pasien", FieldType.TEXT, currencyCode = "IDR")
        )
        cases.forEach { f ->
            val issues = ScreenProposalValidator.validate(tagihan(draft, listOf(f), mapOf(f.key to "1")), verticalPurity = true)
            assertTrue(issues.any { it.toString().contains("currencyCode") }, "${f.key} lolos: $issues")
        }
    }

    private val proposal = tagihan(klinik(), validFields.take(2), mapOf("pasien" to "A", "biaya" to "1"))

    private fun edit(json: String) = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(json))
        KoogModuleEditor(executor, DeepSeekModels.DeepSeekV4Flash)
            .edit(ModuleEditRequest("klinik_poli", "Poli", proposal, "tambah", emptyList(), null))
    }

    @Test
    fun `penyunting modul membaca format dan kode, dan menolak yang tak sah tanpa fallback`() {
        val ok = edit("""{"reply":"ok","edits":[{"op":"add","field":{"key":"tarif","label":"Tarif","type":"NUMBER","format":"CURRENCY","currencyCode":"USD"}},
            {"op":"add","field":{"key":"pajak","label":"Pajak","type":"NUMBER","format":"percent"}}]}""").getOrThrow()
        val first = (ok.edits[0] as ProposalEdit.AddField).field
        assertEquals(NumberFormat.CURRENCY to "USD", first.format to first.currencyCode)
        val second = (ok.edits[1] as ProposalEdit.AddField).field
        assertEquals(NumberFormat.PERCENT to null, second.format to second.currencyCode)

        listOf(
            """"type":"NUMBER","format":"RUPIAH"""",
            """"type":"NUMBER","format":"CURRENCY"""",
            """"type":"NUMBER","format":"CURRENCY","currencyCode":"idr"""",
            """"type":"NUMBER","format":"PERCENT","currencyCode":"IDR"""",
            """"type":"NUMBER","currencyCode":"IDR"""",
            """"type":"TEXT","format":"CURRENCY","currencyCode":"IDR""""
        ).forEach { frag ->
            val r = edit("""{"reply":"x","edits":[{"op":"add","field":{"key":"a","label":"A",$frag}}]}""")
            assertTrue(r.isFailure, "Seharusnya ditolak: $frag")
            assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("field."), r.exceptionOrNull()?.message.orEmpty())
        }
    }
}
