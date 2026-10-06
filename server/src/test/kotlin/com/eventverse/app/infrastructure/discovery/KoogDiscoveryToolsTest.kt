package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** **Bukti plan §2 A8**: kedua alat agent bekerja berdiri sendiri, tanpa LLM. */
class KoogDiscoveryToolsTest {

    private val serializer = KotlinxSerializer()

    private suspend fun klinikDraftJson(): String = DiscoveryDraftCodec.encodeToString(
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik dengan antrean pasien.", "klinik")).getOrThrow()
    )

    @Test
    fun `platform_modules memuat kode pack bawaan untuk jembatan useShipped`() = runBlocking {
        val payload = JsonParser.parseObject(PlatformModulesTool().execute(Unit))

        val pack = payload.objectArray("shippedPacks").single()
        assertEquals(GarmentDomainPack.CODE.value, pack.string("code"))
        assertTrue(pack.string("reuseWith")!!.contains("useShipped"))
        assertEquals(
            GarmentDomainPack.pack.modules.map { it.id.value },
            pack.objectArray("modules").mapNotNull { it.string("id") }
        )
        assertEquals(GarmentDomainPack.pack.slots.map { it.code.value }, pack.objectArray("slots").mapNotNull { it.string("code") })
        assertTrue(payload.stringArray("rules").any { it.contains("prefiks") })
    }

    @Test
    fun `validate_draft menerima objek maupun string dan melaporkan galat berpath`() = runBlocking {
        val tool = ValidateDraftTool()
        val valid = klinikDraftJson()

        // Bentuk argumen normal: model mengirim objek.
        val asObject = tool.execute(
            draftArgument(JSONObject(mapOf("draft" to serializer.decodeJSONElementFromString(valid))), serializer)
        )
        assertEquals(true, JsonParser.parseObject(asObject).boolean("valid"))

        // Bentuk lain yang sering dikirim model: string JSON yang dikutip.
        assertEquals(valid, draftArgument(JSONObject(mapOf("draft" to JSONPrimitive(valid))), serializer))

        // Dokumen rusak → laporan berpath, bukan lemparan yang membunuh percakapan.
        val broken = JsonParser.parseObject(tool.execute("""{"pack":{},"blueprint":{}}"""))
        assertEquals(false, broken.boolean("valid"))
        val issues = broken.objectArray("issues")
        assertTrue(issues.isNotEmpty())
        assertTrue(issues.all { it.string("path")?.startsWith("$") == true }, "Setiap isu wajib berpath: $issues")
    }

    @Test
    fun `argumen draft wajib ada`() {
        assertFailsWith<IllegalArgumentException> { draftArgument(JSONObject(emptyMap()), serializer) }
    }

    /**
     * Deskriptor dibuat tangan (bukan introspeksi kelas), jadi ia wajib dites lewat penerjemah yang
     * benar-benar dipakai provider — kalau skemanya rusak, ke dua alat itu tidak akan pernah bisa
     * dipanggil model, dan kegagalannya baru terlihat di produksi.
     */
    @Test
    fun `deskriptor alat diterjemahkan ke skema JSON provider`() {
        val generator = OpenAICompatibleToolDescriptorSchemaGenerator()

        val schemas = discoveryToolRegistry().tools.associate { it.name to generator.generate(it.descriptor).toString() }

        assertEquals(setOf(DiscoveryTools.PLATFORM_MODULES, DiscoveryTools.SCREEN_CATALOG, DiscoveryTools.VALIDATE_DRAFT), schemas.keys)
        val validate = schemas.getValue(DiscoveryTools.VALIDATE_DRAFT)
        assertTrue(validate.contains("\"draft\""))
        assertTrue(validate.contains("\"required\":[\"draft\"]"))
        assertTrue(validate.contains("\"additionalProperties\":true"), "Objek bebas: dokumen draf tidak dipatok skema di sini")
        assertTrue(schemas.getValue(DiscoveryTools.PLATFORM_MODULES).contains("\"required\":[]"))
    }

    // ==== SP-C2: katalog layar & galat proposal berpath ====

    @Test
    fun `screen_catalog menjawab kosakata tertutup secara deterministik`() = runBlocking {
        val tool = ScreenCatalogTool()

        val first = JsonParser.parseObject(tool.execute(Unit))
        val second = JsonParser.parseObject(tool.execute(Unit))
        assertEquals(first.encode(), second.encode(), "Jawaban alat wajib deterministik")

        val widgets = first.objectArray("widgetKinds")
        assertEquals(
            WidgetKind.entries.map { it.code },
            widgets.map { it.string("code") },
            "Kosakata widget di katalog = kosakata validator, tidak lebih tidak kurang"
        )
        val kanban = widgets.first { it.string("code") == "KANBAN" }
        assertEquals("wajib", kanban.string("entity"))
        assertTrue(kanban.string("view")!!.contains("statusField"))
        val dashboard = widgets.first { it.string("code") == "DASHBOARD" }
        assertEquals("null", dashboard.string("entity"))

        assertEquals(
            FieldType.entries.map { it.name },
            first.objectArray("fieldTypes").map { it.string("name") }
        )
        assertEquals(listOf("TITLE", "TEXT", "BADGE", "DATE", "NUMBER", "FLAG"), first.stringArray("cardStyles"))

        // Batas diambil dari ProposalLimits — satu sumber kebenaran dengan validator.
        val limits = requireNotNull(first.obj("limits"))
        assertEquals(ProposalLimits.FIELDS, limits.int("fields"))
        assertEquals(ProposalLimits.SEED_ROWS, limits.int("seedRows"))
        assertEquals(ProposalLimits.TEXT, limits.int("text"))
    }

    @Test
    fun `screen_catalog memuat petunjuk peran ke tampilan dari pack bawaan`() = runBlocking {
        val payload = JsonParser.parseObject(ScreenCatalogTool().execute(Unit))
        val hints = requireNotNull(payload.obj("roleHints"))

        assertTrue(hints.string("note")!!.contains("bukan aturan keras"), "Petunjuk tidak boleh menyamar jadi aturan")
        val examples = hints.objectArray("examples")
        assertTrue(examples.isNotEmpty(), "Pack bawaan garment punya usulan layar; petunjuk tidak boleh kosong")
        val kanbanSampling = examples.first { it.string("module") == "sampling_order" }
        assertEquals("KANBAN", kanbanSampling.string("widget"))
        assertTrue(kanbanSampling.string("screenTitle")!!.isNotBlank())
    }

    @Test
    fun `validate_draft melaporkan galat proposal dengan path yang dalam`() = runBlocking {
        val tool = ValidateDraftTool()
        // Proposal sah dari contoh prompt, lalu satu tipe field diganti kosakata karangan.
        val root = JsonParser.parseObject(KoogDiscoveryPrompt.exampleDraftJson())
        val screen = root.array("screens").first() as JsonValue.Obj
        val proposal = requireNotNull(screen.obj("proposal"))
        val entity = requireNotNull(proposal.obj("entity"))
        val fields = entity.array("fields").mapIndexed { i, f ->
            if (i == 2) JsonValue.Obj((f as JsonValue.Obj).entries + ("type" to JsonValue.Str("KARANGAN"))) else f
        }
        val patched = JsonValue.Obj(
            root.entries + ("screens" to JsonValue.Arr(
                listOf(
                    JsonValue.Obj(
                        screen.entries + ("proposal" to JsonValue.Obj(
                            proposal.entries + ("entity" to JsonValue.Obj(entity.entries + ("fields" to JsonValue.Arr(fields))))
                        ))
                    )
                )
            ))
        ).encode()

        val report = JsonParser.parseObject(tool.execute(patched))

        assertEquals(false, report.boolean("valid"))
        val paths = report.objectArray("issues").mapNotNull { it.string("path") }
        assertTrue(
            paths.contains("$.screens[0].proposal.entity.fields[2].type"),
            "Galat proposal wajib berpath sampai ke field yang salah: $paths"
        )
    }
}
