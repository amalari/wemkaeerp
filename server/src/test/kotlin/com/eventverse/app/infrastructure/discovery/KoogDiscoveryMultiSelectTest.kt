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
import com.eventverse.app.infrastructure.builder.KoogModuleEditor
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evaluasi deterministik (tanpa LLM) kosakata tipe field `MULTI_SELECT` untuk agent (TRD-FIELD-003 Track B, C5):
 * katalog dan prompt menyebut tipe ini beserta aturan pembeda dari `ENUM` dan larangan jadi `statusField`;
 * paritas dua arah terhadap `FieldType.entries` (tipe baru tanpa catatan → build gagal; katalog tak boleh
 * menyimpang dari enum). Penyunting modul membaca `maxSelections` ketat dan menolak nilai salah tanpa fallback.
 */
class KoogDiscoveryMultiSelectTest {

    // ---- katalog & paritas dua arah -------------------------------------------------------------

    @Test
    fun `katalog fieldTypes memuat tiap tipe enum dengan catatan tidak kosong`() {
        val catalog = JsonParser.parseObject(screenCatalogJson(emptyList()))
        val entries = catalog.objectArray("fieldTypes")

        // Dua arah: setiap entri enum punya catatan, dan katalog tidak memuat tipe di luar enum.
        assertEquals(FieldType.entries.map { it.name }, entries.map { it.string("name") })
        entries.forEach { assertTrue(it.string("note").orEmpty().isNotBlank(), "Catatan kosong: ${it.string("name")}") }
    }

    @Test
    fun `catatan MULTI_SELECT membedakannya dari ENUM dan melarang status`() {
        val multi = KoogDiscoveryFieldTypeVocabulary.note(FieldType.MULTI_SELECT)
        val enum = KoogDiscoveryFieldTypeVocabulary.note(FieldType.ENUM)

        assertTrue("MULTI_SELECT" in multi, "Catatan tipe harus menyebut namanya: $multi")
        assertTrue("ENUM" in multi, "Catatan MULTI_SELECT wajib menyebut pembeda dari ENUM: $multi")
        assertTrue("statusField" in multi, "Catatan MULTI_SELECT wajib melarang statusField: $multi")
        assertTrue("maxSelections" in multi, "Catatan MULTI_SELECT wajib menyebut maxSelections: $multi")
        assertTrue("MULTI_SELECT" in enum, "Catatan ENUM wajib menunjuk ke MULTI_SELECT: $enum")
    }

    // ---- prompt --------------------------------------------------------------------------------

    @Test
    fun `prompt memuat tiap tipe enum dan aturan MULTI_SELECT vs ENUM`() {
        val system = KoogDiscoveryPrompt.system
        val rule = KoogDiscoveryFieldTypeVocabulary.promptRule

        FieldType.entries.forEach { assertTrue(system.contains(it.name), "Prompt tak menyebut ${it.name}") }
        assertTrue(system.contains(rule), "Aturan tipe field wajib tampil apa adanya di prompt")
        // Daftar "{A|B|…}" dalam aturan hanya boleh berisi nama enum, persis isinya.
        val listed = Regex("\\{([A-Z_|]+)\\}").find(rule)?.groupValues?.get(1)?.split("|").orEmpty()
        assertEquals(FieldType.entries.map { it.name }, listed)
        // Aturan pembeda: MULTI_SELECT vs ENUM + larangan jadi status.
        assertTrue("MULTI_SELECT" in rule, rule)
        assertTrue("statusField" in rule, "Prompt wajib melarang MULTI_SELECT sebagai statusField: $rule")
        assertTrue("maxSelections" in rule, "Prompt wajib menyebut maxSelections: $rule")
    }

    @Test
    fun `prompt penyunting modul memuat tiap tipe enum dan aturan MULTI_SELECT`() {
        val editor = KoogModuleEditor.SYSTEM
        FieldType.entries.forEach { assertTrue(editor.contains(it.name), "Penyunting tak menyebut ${it.name}") }
        assertTrue("maxSelections" in editor, "Penyunting wajib menyebut maxSelections: ${KoogModuleEditor.SYSTEM}")
        assertTrue("status" in editor, "Penyunting wajib melarang MULTI_SELECT jadi field status")
    }

    // ---- validator & round-trip codec pada pack non-garment --------------------------------------

    private fun klinik(): DiscoveryDraft = runBlocking {
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik dengan antrean pasien.", "klinik")).getOrThrow()
    }

    private val alergiOptions = listOf("Gigi", "Jantung", "Debu")

    private fun pasien(draft: DiscoveryDraft): ScreenProposal {
        val entity = EntityProposal(
            id = "pasien",
            label = "Pasien",
            fields = listOf(
                FieldProposal("nama", "Nama pasien", FieldType.TEXT, required = true),
                FieldProposal("alergi", "Alergi", FieldType.MULTI_SELECT, options = alergiOptions, maxSelections = 2)
            )
        )
        return ScreenProposal(
            screenId = "pasien_form",
            moduleId = draft.pack.modules.first().id,
            title = "Catat Pasien",
            widget = WidgetKind.FORM,
            rationale = "Dipilih karena petugas mencatat pasien baru satu per satu lewat formulir.",
            entity = entity,
            view = ViewProposal.Form(fields = listOf("nama", "alergi"), submitLabel = "Simpan"),
            seed = listOf(mapOf("nama" to "Ibu Sari", "alergi" to "[\"Gigi\",\"Jantung\"]"))
        )
    }

    @Test
    fun `usulan MULTI_SELECT pada pack klinik lolos validator dan round-trip codec`() {
        val draft = klinik()
        val proposal = pasien(draft)

        assertEquals(emptyList(), ScreenProposalValidator.validate(proposal, verticalPurity = true))

        val screen = PrototypeScreen(
            proposal.screenId, proposal.moduleId, proposal.title, proposal.widget.code, proposal, ProposalSource.Agent("koog/uji")
        )
        val withScreen = draft.copy(screens = listOf(screen))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(withScreen))

        val decoded = requireNotNull(DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(withScreen)).screens.single().proposal)
        val fields = requireNotNull(decoded.entity).fields
        assertEquals(FieldType.MULTI_SELECT, fields[1].type)
        assertEquals(2, fields[1].maxSelections, "maxSelections ikut kawat codec")
        assertEquals(alergiOptions, fields[1].options)
        assertEquals("[\"Gigi\",\"Jantung\"]", decoded.seed.single()["alergi"], "seed MULTI_SELECT kanonik ikut terbaca")
    }

    @Test
    fun `MULTI_SELECT sebagai statusField ditolak validator`() {
        val entity = EntityProposal(
            id = "pasien",
            label = "Pasien",
            fields = listOf(
                FieldProposal("nama", "Nama", FieldType.TEXT),
                FieldProposal("layanan", "Layanan", FieldType.MULTI_SELECT, options = listOf("a", "b"), maxSelections = 1)
            ),
            statusField = "layanan"
        )
        val proposal = ScreenProposal(
            "s", klinik().pack.modules.first().id, "T", WidgetKind.TABLE, "alasan jelas", entity,
            ViewProposal.Table(listOf("nama"), inlineCreate = false, editableFields = listOf("nama"))
        )
        val issues = ScreenProposalValidator.validate(proposal, "$.proposal")
        assertTrue(
            issues.any { it.path == "$.proposal.entity.statusField" && "ENUM" in it.message },
            "MULTI_SELECT sebagai statusField harus ditolak dengan pesan ENUM: ${issues.map { it.path + ": " + it.message }}"
        )
    }

    // ---- penyunting modul: baca & tolak ----------------------------------------------------------

    private fun proposal() = pasien(klinik())

    private fun edit(json: String) = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(json))
        KoogModuleEditor(executor, DeepSeekModels.DeepSeekV4Flash)
            .edit(ModuleEditRequest("klinik_poli", "Poli", proposal(), "tambah layanan", emptyList(), null))
    }

    @Test
    fun `penyunting modul membaca MULTI_SELECT dan maxSelections, dan menolak yang tak sah tanpa fallback`() {
        val ok = edit(
            """{"reply":"ok","edits":[{"op":"add","field":{"key":"layanan","label":"Layanan dibeli","type":"MULTI_SELECT","required":false,"options":["Digitizing","Hooping"],"maxSelections":2}}]}"""
        ).getOrThrow()
        val field = (ok.edits.single() as ProposalEdit.AddField).field
        assertEquals(FieldType.MULTI_SELECT, field.type)
        assertEquals(listOf("Digitizing", "Hooping"), field.options)
        assertEquals(2, field.maxSelections)

        listOf(
            // maxSelections pada tipe selain MULTI_SELECT
            """"type":"TEXT","maxSelections":1""",
            """"type":"ENUM","options":["a","b"],"maxSelections":1""",
            // di luar 1..options.size
            """"type":"MULTI_SELECT","options":["a","b"],"maxSelections":5""",
            """"type":"MULTI_SELECT","options":["a","b"],"maxSelections":0""",
            // maxSelections bertipe salah (string/pecahan)
            """"type":"MULTI_SELECT","options":["a","b"],"maxSelections":"2"""",
            """"type":"MULTI_SELECT","options":["a","b"],"maxSelections":2.5""",
            // options wajib & unik
            """"type":"MULTI_SELECT"""",
            """"type":"MULTI_SELECT","options":["a","a"]""",
            // kelebihan opsi ditolak, bukan dipotong diam-diam (9 > ProposalLimits.OPTIONS)
            """"type":"MULTI_SELECT","options":["a","b","c","d","e","f","g","h","i"]"""
        ).forEach { frag ->
            val r = edit("""{"reply":"x","edits":[{"op":"add","field":{"key":"a","label":"A",$frag}}]}""")
            assertTrue(r.isFailure, "Seharusnya ditolak: $frag")
            assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("field."), r.exceptionOrNull()?.message.orEmpty())
        }
    }
}
