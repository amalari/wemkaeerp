package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.infrastructure.api.BuilderHttpException
import com.eventverse.app.infrastructure.api.ScenarioProposeOutcome
import com.eventverse.app.infrastructure.api.SessionTokenProvider
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.DiscoveryScreenUi
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pemilihan layar, rantai Alur Data, parse balasan server, dan notifikasi skenario (TRD-BLDR-002 Track C). */
class ScenarioClientModelTest {

    private fun scn(id: String, confirmed: Boolean, steps: String, source: String = "AI") =
        """{"id":"$id","title":"Judul $id","source":"$source","confirmed":$confirmed,"steps":$steps}"""

    private val garmentSteps = """[
      {"moduleId":"crm_sales","screenId":"scr-crm","label":"Lead","carries":[{"fromField":"nama","toField":"pelanggan"}]},
      {"moduleId":"invoicing","screenId":"scr-inv","label":"Invoice","carries":[]},
      {"moduleId":"sampling_order","screenId":null,"label":"Pesanan Sampel","carries":[]}]"""
    private val klinikSteps = """[
      {"moduleId":"pasien","screenId":"scr-pasien","label":"Daftar","carries":[]},
      {"moduleId":"kunjungan","screenId":null,"label":"Periksa","carries":[]}]"""

    private fun draft(scenariosJson: String?): DiscoveryDraftUi = DiscoveryDraftUi.fromJson(
        JsonParser.parse(
            """{"id":"d1","status":"DRAFT","modules":[
              {"id":"crm_sales","displayName":"Penjualan","section":"s","kind":"OPERATIONAL"},
              {"id":"pasien","displayName":"Pasien","section":"s","kind":"OPERATIONAL"}],
              "activeModuleCodes":["crm_sales","pasien"],"screens":[]
              ${scenariosJson?.let { ""","scenarios":$it""" }.orEmpty()}}"""
        ) as JsonValue.Obj
    )

    private fun screen(id: String, module: String) = DiscoveryScreenUi(id, module, "Layar $id", "FORM", emptyList())

    private fun client(status: HttpStatusCode, body: String) = BuilderApiClient(
        HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }),
        tokenProvider = object : SessionTokenProvider { override fun currentToken() = "t" }
    )

    // ---- pemilihan layar menurut urutan skenario ----

    @Test
    fun `layar skenario mengikuti urutan skenario bukan urutan daftar layar`() {
        val sc = draft("[${scn("a", true, garmentSteps)}]").scenarios.single()
        val listOrder = listOf(screen("scr-inv", "invoicing"), screen("scr-crm", "crm_sales"))
        val result = scenarioScreens(sc, listOrder)
        assertEquals(listOf("Lead", "Invoice", "Pesanan Sampel"), result.map { it.title })
        assertEquals(listOf("scr-crm", "scr-inv"), result.take(2).map { it.screenId })
    }

    @Test
    fun `langkah tanpa layar menjadi kerangka CUSTOM_SCREEN bukan layar teks`() {
        val sc = draft("[${scn("a", true, garmentSteps)}]").scenarios.single()
        val last = scenarioScreens(sc, listOf(screen("scr-crm", "crm_sales"), screen("scr-inv", "invoicing"))).last()
        assertTrue(isSkeletonScreen(last))
        assertEquals("CUSTOM_SCREEN", last.widget)
        assertTrue(last.rationale.orEmpty().contains("kerangka"))
    }

    @Test
    fun `screenId yang tak ada di draf juga kerangka bukan layar modul lain`() {
        val sc = draft("[${scn("a", true, klinikSteps)}]").scenarios.single()
        assertTrue(scenarioScreens(sc, listOf(screen("lain", "pasien"))).all(::isSkeletonScreen))
    }

    // ---- rantai Alur Data: hanya terkonfirmasi ----

    @Test
    fun `rantai alur data hanya skenario terkonfirmasi garment dan klinik`() {
        val d = draft("[${scn("g", true, garmentSteps)},${scn("k", true, klinikSteps, "USER")},${scn("x", false, garmentSteps)}]")
        val chains = scenarioChains(d)
        assertEquals(listOf("g", "k"), chains.map { it.id })
        assertEquals(listOf("Penjualan", "invoicing", "sampling_order"), chains[0].steps.map { it.moduleName }) // nama dari draf, jatuh ke kode
        assertEquals(listOf(false, false, true), chains[0].steps.map { it.skeleton })
        assertEquals(ScenarioSource.USER, chains[1].source)
        assertEquals(listOf("Pasien", "kunjungan"), chains[1].steps.map { it.moduleName })
    }

    // ---- draf: kunci opsional, rusak ditolak ----

    @Test
    fun `draf lama tanpa kunci scenarios kosong tanpa galat`() {
        val d = draft(null)
        assertTrue(d.scenarios.isEmpty())
        assertNull(d.scenariosError)
    }

    @Test
    fun `scenarios rusak menghasilkan galat bukan daftar kosong senyap`() {
        val d = draft("""[{"id":"a","title":"t","source":"ROBOT","steps":[]}]""")
        assertTrue(d.scenarios.isEmpty())
        assertNotNull(d.scenariosError)
    }

    // ---- parse balasan propose/confirm lewat klien ----

    @Test
    fun `propose valid kosong dan rusak`() = runTest {
        val ok = client(HttpStatusCode.OK, """{"scenarios":[${scn("a", false, garmentSteps)}]}""").proposeScenarios().getOrThrow()
        assertTrue(ok.featureEnabled)
        assertEquals(1, ok.scenarios.size)
        assertFalse(ok.scenarios.single().confirmed)
        val empty = client(HttpStatusCode.OK, """{"scenarios":[]}""").proposeScenarios().getOrThrow()
        assertTrue(empty.featureEnabled && empty.scenarios.isEmpty())
        assertTrue(client(HttpStatusCode.OK, """{"foo":1}""").proposeScenarios().isFailure)
        assertTrue(client(HttpStatusCode.OK, """{"scenarios":[{"id":"a","title":"t","source":"ROBOT","steps":[]}]}""").proposeScenarios().isFailure)
        assertTrue(client(HttpStatusCode.OK, """{"scenarios":"x"}""").proposeScenarios().isFailure)
    }

    @Test
    fun `propose 404 berarti fitur belum aktif`() = runTest {
        val o = client(HttpStatusCode.NotFound, "nf").proposeScenarios().getOrThrow()
        assertFalse(o.featureEnabled)
        assertEquals("Fitur skenario belum aktif di server ini.", scenarioProposeNotice(o))
    }

    @Test
    fun `confirm valid dan rusak`() = runTest {
        val sc = client(HttpStatusCode.OK, """{"scenario":${scn("a", true, garmentSteps)}}""").confirmScenario("a").getOrThrow()
        assertTrue(sc.confirmed)
        assertTrue(client(HttpStatusCode.OK, """{"x":1}""").confirmScenario("a").isFailure)
        assertTrue(client(HttpStatusCode.OK, """{"scenario":[]}""").confirmScenario("a").isFailure)
    }

    @Test
    fun `confirm mengirim id di body`() = runTest {
        var sent = ""
        val c = BuilderApiClient(
            HttpClient(MockEngine { req -> sent = req.body.toString(); respond("""{"scenario":${scn("a", true, garmentSteps)}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }),
            tokenProvider = object : SessionTokenProvider { override fun currentToken() = "t" }
        )
        c.confirmScenario("a").getOrThrow()
        assertTrue(sent.contains("\"id\":\"a\""), sent)
    }

    // ---- notifikasi ----

    @Test
    fun `notifikasi propose`() {
        assertTrue(scenarioProposeNotice(ScenarioProposeOutcome(true, emptyList())).startsWith("Belum ada skenario"))
        val one = draft("[${scn("a", false, garmentSteps)}]").scenarios
        assertEquals("1 skenario diusulkan. Periksa lalu konfirmasi.", scenarioProposeNotice(ScenarioProposeOutcome(true, one)))
    }

    @Test
    fun `notifikasi galat confirm 404 422 403`() {
        assertEquals("Fitur skenario belum aktif di server ini.", scenarioConfirmFailureNotice(BuilderHttpException(404, "HTTP 404")))
        assertTrue(scenarioConfirmFailureNotice(BuilderHttpException(404, """{"error":"tidak ada"}""")).startsWith("Skenario tidak ditemukan"))
        val m = scenarioConfirmFailureNotice(BuilderHttpException(422, """{"error":"invalid","issues":[{"path":"steps[1]","message":"layar bukan milik modul"}]}"""))
        assertEquals("Skenario tidak lolos validasi: steps[1]: layar bukan milik modul", m)
        assertTrue(scenarioConfirmFailureNotice(BuilderHttpException(403, "x")).contains("tidak berwenang"))
        assertEquals("boom", scenarioConfirmFailureNotice(IllegalStateException("boom")))
    }
}
