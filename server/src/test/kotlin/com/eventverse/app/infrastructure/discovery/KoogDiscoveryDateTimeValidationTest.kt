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
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.infrastructure.builder.KoogModuleEditor
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evaluasi deterministik (tanpa LLM) kosakata `withTime` (C6) dan `validation` (C9) untuk agent (Irisan 2 Track B):
 * katalog dan prompt sepadan dengan `TextValidation.entries`, usulan pada pack non-garment (klinik) lolos validator
 * dan round-trip codec, seed/parameter tak sah ditolak, dan penyunting modul menolak tanpa membuang diam-diam.
 */
class KoogDiscoveryDateTimeValidationTest {

    @Test
    fun `katalog fieldParams memuat tiap validasi enum dengan catatan tidak kosong dan withTime`() {
        val catalog = JsonParser.parseObject(screenCatalogJson(emptyList()))
        val section = catalog["fieldParams"] as JsonValue.Obj
        val validations = section.objectArray("validations")

        assertEquals(TextValidation.entries.map { it.name }, validations.map { it.string("name") })
        validations.forEach { assertTrue(it.string("note").orEmpty().isNotBlank(), "Catatan kosong: ${it.string("name")}") }
        assertTrue(section.string("withTime").orEmpty().contains("YYYY-MM-DDTHH:MM"))
    }

    @Test
    fun `catatan tipe DATE dan TEXT menunjuk ke parameter`() {
        assertTrue(KoogDiscoveryFieldTypeVocabulary.note(FieldType.DATE).contains("withTime"))
        assertTrue(KoogDiscoveryFieldTypeVocabulary.note(FieldType.TEXT).contains("validation"))
    }

    @Test
    fun `prompt memuat aturan apa adanya, tiap validasi enum, dan daftar bertandanya persis enum`() {
        val system = KoogDiscoveryPrompt.system
        val rule = KoogDiscoveryDateTimeValidationVocabulary.promptRule

        assertTrue(system.contains(rule), "Aturan withTime/validation wajib tampil apa adanya di prompt")
        TextValidation.entries.forEach { assertTrue(system.contains(it.name), "Prompt tak menyebut ${it.name}") }
        val listed = Regex("validation` ∈ \\{([A-Z_|]+)\\}").find(rule)?.groupValues?.get(1)?.split("|").orEmpty()
        assertEquals(TextValidation.entries.map { it.name }, listed)
        // Dua arah: nama karangan tidak boleh ada di daftar bertanda.
        assertTrue(listed.none { it in setOf("URL", "ZIP", "NIK") })
    }

    @Test
    fun `prompt penyunting modul memuat aturan yang sama`() {
        val editor = KoogModuleEditor.SYSTEM
        assertTrue(editor.contains(KoogDiscoveryDateTimeValidationVocabulary.promptRule))
        TextValidation.entries.forEach { assertTrue(editor.contains(it.name), "Penyunting tak menyebut ${it.name}") }
    }

    private fun klinik(): DiscoveryDraft = runBlocking {
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik dengan antrean pasien.", "klinik")).getOrThrow()
    }

    private fun kunjungan(draft: DiscoveryDraft, fields: List<FieldProposal>, seed: Map<String, String>): ScreenProposal =
        ScreenProposal(
            screenId = "kunjungan_form",
            moduleId = draft.pack.modules.first().id,
            title = "Catat Kunjungan",
            widget = WidgetKind.FORM,
            rationale = "Dipilih karena resepsionis mencatat kunjungan pasien satu per satu.",
            entity = EntityProposal(id = "kunjungan", label = "Kunjungan", fields = fields),
            view = ViewProposal.Form(fields = fields.map { it.key }, submitLabel = "Simpan"),
            seed = listOf(seed)
        )

    private val validFields = listOf(
        FieldProposal("pasien", "Nama pasien", FieldType.TEXT, required = true),
        FieldProposal("surel", "Surel", FieldType.TEXT, validation = TextValidation.EMAIL),
        FieldProposal("telepon", "Telepon", FieldType.TEXT, validation = TextValidation.PHONE),
        FieldProposal("jadwal", "Jadwal", FieldType.DATE, withTime = true),
        FieldProposal("lahir", "Tanggal lahir", FieldType.DATE)
    )
    private val validSeed = mapOf(
        "pasien" to "Ibu Sari", "surel" to "sari@klinik.id", "telepon" to "+62 812-3456-789",
        "jadwal" to "2026-10-08T14:30", "lahir" to "1990-05-17"
    )

    @Test
    fun `usulan EMAIL PHONE dan DATE withTime pada pack klinik lolos validator dan round-trip`() {
        val draft = klinik()
        val proposal = kunjungan(draft, validFields, validSeed)

        assertEquals(emptyList(), ScreenProposalValidator.validate(proposal, verticalPurity = true))
        val screen = PrototypeScreen(
            proposal.screenId, proposal.moduleId, proposal.title, proposal.widget.code, proposal, ProposalSource.Agent("koog/uji")
        )
        val withScreen = draft.copy(screens = listOf(screen))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(withScreen))

        val fields = requireNotNull(DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(withScreen)).screens.single().proposal?.entity).fields
        assertEquals(listOf(false, false, false, true, false), fields.map { it.withTime })
        assertEquals(
            listOf(TextValidation.NONE, TextValidation.EMAIL, TextValidation.PHONE, TextValidation.NONE, TextValidation.NONE),
            fields.map { it.validation }
        )
    }

    private fun issuesOf(field: FieldProposal, value: String) =
        ScreenProposalValidator.validate(kunjungan(klinik(), listOf(field), mapOf(field.key to value)), verticalPurity = true)

    @Test
    fun `seed tak sah ditolak validator`() {
        listOf("tanpa-at", "a@b", "a b@c.id", "a@@c.id", "@c.id").forEach {
            val issues = issuesOf(FieldProposal("surel", "Surel", FieldType.TEXT, validation = TextValidation.EMAIL), it)
            assertTrue(issues.isNotEmpty(), "Email '$it' lolos")
        }
        listOf("12345", "abc12345678", "+62 (812) 34", "1234567890123456").forEach {
            val issues = issuesOf(FieldProposal("telepon", "Telepon", FieldType.TEXT, validation = TextValidation.PHONE), it)
            assertTrue(issues.isNotEmpty(), "Telepon '$it' lolos")
        }
        listOf("2026-10-08", "2026-10-08T14:30:00", "2026-10-08T14:30Z", "2026-10-08 14:30").forEach {
            val issues = issuesOf(FieldProposal("jadwal", "Jadwal", FieldType.DATE, withTime = true), it)
            assertTrue(issues.isNotEmpty(), "Tanggal-jam '$it' lolos pada withTime")
        }
        val dateOnly = issuesOf(FieldProposal("lahir", "Lahir", FieldType.DATE), "2026-10-08T14:30")
        assertTrue(dateOnly.isNotEmpty(), "Tanggal-jam lolos pada field tanggal-saja")
    }

    @Test
    fun `parameter pada tipe yang salah ditolak validator`() {
        val cases = listOf(
            FieldProposal("a", "A", FieldType.TEXT, withTime = true) to "withTime",
            FieldProposal("a", "A", FieldType.LONG_TEXT, withTime = true) to "withTime",
            FieldProposal("a", "A", FieldType.DATE, validation = TextValidation.EMAIL) to "validation",
            FieldProposal("a", "A", FieldType.LONG_TEXT, validation = TextValidation.PHONE) to "validation",
            FieldProposal("a", "A", FieldType.NUMBER, validation = TextValidation.EMAIL) to "validation"
        )
        cases.forEach { (f, key) ->
            val issues = ScreenProposalValidator.validate(kunjungan(klinik(), listOf(f), mapOf("a" to "1")), verticalPurity = true)
            assertTrue(issues.any { it.toString().contains(key) }, "${f.type} $key lolos: $issues")
        }
    }

    private val proposal = kunjungan(klinik(), validFields.take(2), validSeed.filterKeys { it in setOf("pasien", "surel") })

    private fun edit(json: String) = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(json))
        KoogModuleEditor(executor, DeepSeekModels.DeepSeekV4Flash)
            .edit(ModuleEditRequest("klinik_poli", "Poli", proposal, "tambah", emptyList(), null))
    }

    @Test
    fun `penyunting modul membaca withTime dan validation`() {
        val ok = edit("""{"reply":"ok","edits":[{"op":"add","field":{"key":"jadwal","label":"Jadwal","type":"DATE","withTime":true}},
            {"op":"add","field":{"key":"wa","label":"WA","type":"TEXT","validation":"phone"}},
            {"op":"add","field":{"key":"biasa","label":"Biasa","type":"TEXT"}}]}""").getOrThrow()
        val jadwal = (ok.edits[0] as ProposalEdit.AddField).field
        assertEquals(true, jadwal.withTime)
        assertEquals(TextValidation.PHONE, (ok.edits[1] as ProposalEdit.AddField).field.validation)
        val biasa = (ok.edits[2] as ProposalEdit.AddField).field
        assertEquals(false to TextValidation.NONE, biasa.withTime to biasa.validation)
    }

    @Test
    fun `penyunting modul menolak parameter tak sah tanpa membuang diam-diam`() {
        listOf(
            """"type":"DATE","withTime":"true"""",
            """"type":"DATE","withTime":1""",
            """"type":"TEXT","withTime":true""",
            """"type":"NUMBER","withTime":true""",
            """"type":"TEXT","validation":"URL"""",
            """"type":"TEXT","validation":true""",
            """"type":"TEXT","validation":["EMAIL"]""",
            """"type":"LONG_TEXT","validation":"EMAIL"""",
            """"type":"DATE","validation":"PHONE"""",
            """"type":"NUMBER","validation":"EMAIL""""
        ).forEach { frag ->
            val r = edit("""{"reply":"x","edits":[{"op":"add","field":{"key":"a","label":"A",$frag}}]}""")
            assertTrue(r.isFailure, "Seharusnya ditolak: $frag")
            assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("field."), r.exceptionOrNull()?.message.orEmpty())
        }
    }

    @Test
    fun `pesan pengguna penyunting menampilkan withTime dan validation field yang ada`() {
        val editor = KoogModuleEditor(ScriptedPromptExecutor(emptyList()), DeepSeekModels.DeepSeekV4Flash)
        val p = kunjungan(klinik(), validFields, validSeed)
        val msg = editor.userMessage(ModuleEditRequest("klinik_poli", "Poli", p, "x", emptyList(), null))
        assertTrue(msg.contains("withTime=true"))
        assertTrue(msg.contains("validation=EMAIL") && msg.contains("validation=PHONE"))
    }
}
