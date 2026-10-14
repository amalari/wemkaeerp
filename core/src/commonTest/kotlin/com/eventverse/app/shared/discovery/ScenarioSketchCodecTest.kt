package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.scenario.FieldCarry
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixture bentuk dokumen `scenarios` (TRD-BLDR-002 §4) — satu garment, satu non-garment (tenant-variability Kontrak 6). */
object ScenarioContractFixtures {
    const val GARMENT = """[{"id":"scn-lead-invoice-sampel","title":"Lead jadi pesanan sampel","source":"AI","confirmed":false,
      "steps":[
        {"moduleId":"crm_sales","screenId":"default-crm_sales","label":"Lead",
         "carries":[{"fromField":"nama","toField":"pelanggan"},{"fromField":"kebutuhan","toField":"catatan"}]},
        {"moduleId":"invoicing","screenId":"default-invoicing","label":"Invoice","carries":[{"fromField":"item","toField":"item_sampel"}]},
        {"moduleId":"sampling_order","screenId":null,"label":"Pesanan Sampel","carries":[]}]}]"""

    const val KLINIK = """[{"id":"scn-pasien-periksa-bayar","title":"Pasien sampai bayar","source":"DETERMINISTIC","confirmed":true,
      "steps":[
        {"moduleId":"klinik_pendaftaran","screenId":"s_daftar","label":"Daftar","carries":[{"fromField":"nama","toField":"pasien"}]},
        {"moduleId":"klinik_poli","screenId":"s_poli","label":"Periksa","carries":[{"fromField":"keluhan","toField":"tindakan"}]},
        {"moduleId":"klinik_kasir","screenId":"s_kasir","label":"Bayar","carries":[],"reworkTo":1}]}]"""
}

class ScenarioSketchCodecTest {
    private fun decode(raw: String) = ScenarioSketchCodec.decode(JsonParser.parse(raw), "$.scenarios")

    private val sample = ScenarioSketch(
        ScenarioId("scn-1"), "Lead jadi sampel",
        listOf(
            ScenarioStep(ModuleId("crm_sales"), "s_crm", "Lead", listOf(FieldCarry("nama", "pelanggan"))),
            ScenarioStep(ModuleId("sampling_order"), null, "Sampel", reworkTo = 0)
        ),
        ScenarioSource.USER, confirmed = true
    )

    @Test
    fun `scenario with skeleton step and rework should round trip`() {
        assertEquals(listOf(sample), decode(ScenarioSketchCodec.encode(listOf(sample)).encode()))
    }

    @Test
    fun `garment and klinik fixtures should decode`() {
        val g = decode(ScenarioContractFixtures.GARMENT).single()
        assertEquals(ScenarioSource.AI, g.source)
        assertFalse(g.confirmed)
        assertNull(g.steps[2].screenId, "langkah tanpa layar = kerangka")
        assertEquals(2, g.steps[0].carries.size)
        val k = decode(ScenarioContractFixtures.KLINIK).single()
        assertEquals(ScenarioSource.DETERMINISTIC, k.source)
        assertTrue(k.confirmed)
        assertEquals(1, k.steps[2].reworkTo)
    }

    @Test
    fun `empty list should decode to empty`() { assertEquals(emptyList(), decode("[]")) }

    @Test
    fun `unknown source should be rejected not defaulted`() {
        val e = assertFailsWith<DiscoveryDraftDecodeException> { decode(ScenarioContractFixtures.GARMENT.replace("\"AI\"", "\"HUMAN\"")) }
        assertEquals("$.scenarios[0].source", e.path)
    }

    @Test
    fun `unknown keys should be rejected at scenario step and carry level`() {
        assertFailsWith<DiscoveryDraftDecodeException> { decode(ScenarioContractFixtures.GARMENT.replace("\"confirmed\"", "\"extra\":1,\"confirmed\"")) }
        assertFailsWith<DiscoveryDraftDecodeException> { decode(ScenarioContractFixtures.GARMENT.replace("\"label\":\"Lead\"", "\"label\":\"Lead\",\"x\":1")) }
        assertFailsWith<DiscoveryDraftDecodeException> { decode(ScenarioContractFixtures.GARMENT.replace("\"toField\":\"pelanggan\"", "\"toField\":\"pelanggan\",\"y\":1")) }
    }

    @Test
    fun `malformed shapes should be rejected with path`() {
        assertFailsWith<DiscoveryDraftDecodeException> { decode("""{"id":"x"}""") }
        assertFailsWith<DiscoveryDraftDecodeException> { decode("""[{"id":"x","title":"t","source":"AI","steps":"nope"}]""") }
        assertFailsWith<DiscoveryDraftDecodeException> { decode("""[{"id":"x","title":"t","source":"AI","confirmed":"ya","steps":[]}]""") }
        assertFailsWith<DiscoveryDraftDecodeException> { decode("""[{"id":" ","title":"t","source":"AI","steps":[]}]""") }
        assertFailsWith<DiscoveryDraftDecodeException> { decode("""[{"id":"x","title":" ","source":"AI","steps":[]}]""") }
    }

    @Test
    fun `step screenId must be present and non-empty or null`() {
        val noKey = """[{"id":"x","title":"t","source":"AI","steps":[{"moduleId":"m","label":"L","carries":[]}]}]"""
        val empty = """[{"id":"x","title":"t","source":"AI","steps":[{"moduleId":"m","screenId":"","label":"L","carries":[]}]}]"""
        assertFailsWith<DiscoveryDraftDecodeException> { decode(noKey) }
        assertFailsWith<DiscoveryDraftDecodeException> { decode(empty) }
    }

    @Test
    fun `invalid rework and carry should be rejected`() {
        fun step(extra: String) = """[{"id":"x","title":"t","source":"AI","steps":[{"moduleId":"m","screenId":null,"label":"L","carries":[$extra}]}]"""
        assertFailsWith<DiscoveryDraftDecodeException> { decode(step("]").replace("\"carries\":[]", "\"carries\":[],\"reworkTo\":\"a\"")) }
        assertFailsWith<DiscoveryDraftDecodeException> { decode(step("]").replace("\"carries\":[]", "\"carries\":[],\"reworkTo\":-1")) }
        assertFailsWith<DiscoveryDraftDecodeException> { decode(step("""{"fromField":"a"}]""")) }
        assertFailsWith<DiscoveryDraftDecodeException> { decode(step("""{"fromField":"","toField":"b"}]""")) }
    }

    @Test
    fun `scenario confirmed should default to false`() {
        val raw = ScenarioContractFixtures.GARMENT.replace("\"confirmed\":false,", "")
        assertFalse(decode(raw).single().confirmed)
    }

    // ── Integrasi dokumen draf ──────────────────────────────────────────────────

    private val klinikDraft = draftOf(InterviewFixtures.klinikPack, null)

    @Test
    fun `draft without scenarios should encode without the key and stay byte compatible`() {
        assertTrue(klinikDraft.scenarios.isEmpty())
        val encoded = DiscoveryDraftCodec.encodeToString(klinikDraft)
        assertFalse("\"scenarios\"" in encoded, "draf lama tidak boleh mendapat kunci baru")
        assertEquals(klinikDraft, DiscoveryDraftCodec.decode(encoded))
    }

    @Test
    fun `draft with scenarios should round trip through the draft codec`() {
        val withScenario = klinikDraft.copy(scenarios = listOf(sample))
        val encoded = DiscoveryDraftCodec.encodeToString(withScenario)
        assertEquals(withScenario, DiscoveryDraftCodec.decode(encoded))
    }

    @Test
    fun `draft scenarios key with wrong shape should be rejected with path`() {
        val obj = DiscoveryDraftCodec.encode(klinikDraft)
        val broken = com.eventverse.app.shared.json.JsonValue.Obj(obj.entries + ("scenarios" to com.eventverse.app.shared.json.jsonOf(5))).encode()
        val e = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(broken) }
        assertEquals("$.scenarios", e.path)
    }

    @Test
    fun `draft scenarios null should be read as empty`() {
        val obj = DiscoveryDraftCodec.encode(klinikDraft)
        val raw = com.eventverse.app.shared.json.JsonValue.Obj(obj.entries + ("scenarios" to com.eventverse.app.shared.json.JsonValue.Null)).encode()
        assertEquals(emptyList(), DiscoveryDraftCodec.decode(raw).scenarios)
    }
}
