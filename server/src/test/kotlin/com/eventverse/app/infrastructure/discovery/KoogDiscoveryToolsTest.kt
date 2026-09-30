package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
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

        assertEquals(setOf(DiscoveryTools.PLATFORM_MODULES, DiscoveryTools.VALIDATE_DRAFT), schemas.keys)
        val validate = schemas.getValue(DiscoveryTools.VALIDATE_DRAFT)
        assertTrue(validate.contains("\"draft\""))
        assertTrue(validate.contains("\"required\":[\"draft\"]"))
        assertTrue(validate.contains("\"additionalProperties\":true"), "Objek bebas: dokumen draf tidak dipatok skema di sini")
        assertTrue(schemas.getValue(DiscoveryTools.PLATFORM_MODULES).contains("\"required\":[]"))
    }
}
