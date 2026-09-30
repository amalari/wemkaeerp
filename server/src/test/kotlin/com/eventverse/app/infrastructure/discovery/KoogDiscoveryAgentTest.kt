package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti plan §2 A8** untuk bagian yang bisa diuji tanpa jaringan: agent LLM memakai jalur produksi
 * (Koog `singleRunStrategy` + alat discovery + `DiscoveryDraftCodec` + `DiscoveryDraftValidator`), dan
 * loop koreksi diri benar-benar mengirim **galat berpath** ke putaran berikutnya.
 *
 * Jawaban LLM berasal dari [ScriptedPromptExecutor]; satu kasus memakai dokumen yang benar-benar
 * dihasilkan `DeterministicDiscoveryAgent` supaya yang diuji adalah plumbing-nya, bukan kualitas prompt.
 */
class KoogDiscoveryAgentTest {

    private val model = DeepSeekModels.DeepSeekV4Flash

    private val narasiKlinik = DiscoveryRequest(
        "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
        "klinik"
    )

    private suspend fun validKlinikJson(): String =
        DiscoveryDraftCodec.encodeToString(DeterministicDiscoveryAgent().draft(narasiKlinik).getOrThrow())

    /** Merusak satu modul blueprint supaya draf tidak sah (invarian lintas-bagian blueprint ↔ pack). */
    private fun withUnknownBlueprintModule(json: String): String {
        val root = JsonParser.parseObject(json)
        val blueprint = requireNotNull(root.obj("blueprint"))
        val modules = blueprint.array("modules").mapIndexed { index, module ->
            val obj = module as JsonValue.Obj
            if (index == 0) JsonValue.Obj(obj.entries + ("moduleCode" to JsonValue.Str("tidak_ada_di_pack"))) else module
        }
        val patched = JsonValue.Obj(blueprint.entries + ("modules" to JsonValue.Arr(modules)))
        return JsonValue.Obj(root.entries + ("blueprint" to patched)).encode()
    }

    /** Jawaban bergaya "model memakai pack bawaan" — singkatan yang dijembatani sebelum validasi. */
    private fun garmentBridgeJson(): String {
        val full = DiscoveryDraftCodec.encodeToString(
            DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.FOB_FULL_PACKAGE)
        )
        val root = JsonParser.parseObject(full)
        return JsonValue.Obj(root.entries + ("pack" to jsonObjectOf("useShipped" to jsonOf("garment")))).encode()
    }

    @Test
    fun `narasi menghasilkan draf sah dan kedua alat discovery dikirim ke model`() = runBlocking {
        val expectedJson = validKlinikJson()
        val executor = ScriptedPromptExecutor(listOf("Berikut hasilnya:\n```json\n$expectedJson\n```"))
        val agent = KoogDiscoveryAgent(executor, model = model)

        val draft = agent.draft(narasiKlinik).getOrThrow()

        assertEquals(DiscoveryDraftCodec.decode(expectedJson), draft)
        assertEquals(1, executor.calls)
        assertEquals(
            listOf(DiscoveryTools.PLATFORM_MODULES, DiscoveryTools.VALIDATE_DRAFT),
            executor.toolsSeen.single().map { it.name }
        )
        val systemText = executor.prompts.single().messages.first().textContent()
        assertTrue(systemText.contains("FORM, TABLE, KANBAN"), "Kosakata widget tertutup wajib disebut")
        val userText = executor.prompts.single().messages.last().textContent()
        assertTrue(userText.contains("antrean per poli"), "Narasi prospek wajib ikut ke prompt")
        assertTrue(userText.contains("Contoh kerangka dokumen yang sah"))
    }

    @Test
    fun `galat berpath dikirim kembali ke putaran koreksi berikutnya`() = runBlocking {
        val broken = withUnknownBlueprintModule(validKlinikJson())
        val executor = ScriptedPromptExecutor(listOf(broken, validKlinikJson()))
        val agent = KoogDiscoveryAgent(executor, model = model)

        val draft = agent.draft(narasiKlinik).getOrThrow()

        assertEquals(2, executor.calls)
        assertEquals("klinik", draft.pack.code.value)
        val feedback = executor.lastPromptText()
        assertTrue(feedback.contains("Putaran koreksi ke-2"), "Putaran kedua harus berupa koreksi")
        assertTrue(feedback.contains("$.blueprint"), "Path galat wajib diteruskan, bukan pesan umum")
        assertTrue(feedback.contains("tidak_ada_di_pack"), "Modul penyebab wajib disebut")
    }

    @Test
    fun `tiga putaran gagal menjadi kegagalan, bukan draf diam-diam`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf("bukan json", "masih bukan json", "tetap bukan json"))
        val agent = KoogDiscoveryAgent(executor, model = model)

        val result = agent.draft(narasiKlinik)

        assertEquals(3, executor.calls)
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("3 putaran"), "Pesan wajib menyebut batas putaran: $message")
        assertTrue(message.contains("jawaban agent tidak memuat objek JSON"))
    }

    @Test
    fun `fallback deterministik dipakai saat LLM gagal total`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf("bukan json", "bukan json", "bukan json"))
        val agent = KoogDiscoveryAgent(executor, model = model, fallback = DeterministicDiscoveryAgent())

        val draft = agent.draft(narasiKlinik).getOrThrow()

        assertEquals(DeterministicDiscoveryAgent().draft(narasiKlinik).getOrThrow(), draft)
        assertEquals(3, executor.calls)
    }

    @Test
    fun `jembatan pack bawaan mengganti useShipped dengan dokumen platform`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(garmentBridgeJson()))
        val agent = KoogDiscoveryAgent(executor, model = model)

        val draft = agent
            .draft(DiscoveryRequest("Kami konveksi makloon; kain disediakan buyer, cukup jahit saja."))
            .getOrThrow()

        assertEquals(1, executor.calls)
        assertEquals(GarmentDomainPack.pack, draft.pack)
        assertEquals(GarmentBlueprints.FOB_FULL_PACKAGE, draft.blueprint)
    }

    @Test
    fun `kode pack bawaan tak dikenal ditolak berpath, bukan diabaikan`() = runBlocking {
        val unknown = jsonObjectOf(
            "pack" to jsonObjectOf("useShipped" to jsonOf("tidak_ada")),
            "blueprint" to jsonObjectOf(),
            "screens" to JsonValue.Arr(emptyList())
        ).encode()
        val executor = ScriptedPromptExecutor(listOf(unknown, validKlinikJson()))
        val agent = KoogDiscoveryAgent(executor, model = model)

        agent.draft(narasiKlinik).getOrThrow()

        assertEquals(2, executor.calls)
        val feedback = executor.lastPromptText()
        assertTrue(feedback.contains("$.pack.useShipped"))
        assertTrue(feedback.contains("pilihan: garment"))
    }
}
