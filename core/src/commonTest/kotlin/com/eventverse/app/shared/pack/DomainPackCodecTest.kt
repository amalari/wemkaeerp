package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** B7 AC: codec pack ketat, round-trip garment identik, dan aturan identitas global registry. */
class DomainPackCodecTest {

    @Test
    fun screenSuggestions_sampleRows_optional_roundTrips_andRejectsNonString() {
        val withRows = KLINIK_JSON.replace(
            "\"modules\":[",
            "\"screenSuggestions\":[{\"moduleId\":\"klinik_antrean\",\"title\":\"Antrean Harian\",\"widget\":\"KANBAN\"," +
                "\"sampleRows\":[{\"Kolom\":\"Baru\",\"Kartu\":\"Q-001 · pasien baru\"}]}],\"modules\":["
        )
        val pack = DomainPackCodec.decode(withRows)
        assertEquals(
            listOf(mapOf("Kolom" to "Baru", "Kartu" to "Q-001 · pasien baru")),
            pack.screenSuggestions.single().sampleRows
        )
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)))

        // Pack lama tanpa sampleRows tetap ter-decode — kosong, bukan fallback ke pack lain.
        val withoutRows = withRows.replace(",\"sampleRows\":[{\"Kolom\":\"Baru\",\"Kartu\":\"Q-001 · pasien baru\"}]", "")
        assertEquals(emptyList(), DomainPackCodec.decode(withoutRows).screenSuggestions.single().sampleRows)

        // Nilai bukan string ditolak dengan path yang menunjuk baris & kuncinya.
        assertPathRejected("$.screenSuggestions[0].sampleRows[0].Kartu", withRows.replace("\"Q-001 · pasien baru\"", "7"))
    }

    @Test
    fun garmentPack_roundTripsIdentically() {
        val pack = GarmentDomainPack.pack
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)))
    }

    @Test
    fun dataPack_decodes_registers_andResolvesModulesGlobally() {
        val pack = DomainPackCodec.decode(KLINIK_JSON)
        assertEquals(listOf("org_chart", "klinik_antrean"), pack.modules.map { it.id.value })
        assertEquals(emptyList(), DomainPackRegistry.violations(pack))
        DomainPackRegistry.register(pack)
        try {
            assertEquals("Antrean Pasien", DomainPackRegistry.moduleDefinition(pack.modules[1].id)?.displayName)
            assertEquals(pack, DomainPackRegistry.find(DomainPackCode("klinik")))
        } finally {
            DomainPackRegistry.unregister(pack.code)
        }
    }

    @Test
    fun screenSuggestions_optionalField_roundTrips_andIsRejectedWhenInvalid() {
        // Pack lama tanpa field ini tetap ter-decode — kosong, tanpa fallback ke usulan pack lain.
        assertEquals(emptyList(), DomainPackCodec.decode(KLINIK_JSON).screenSuggestions)

        val withScreens = KLINIK_JSON.replace(
            "\"modules\":[",
            "\"screenSuggestions\":[{\"moduleId\":\"klinik_antrean\",\"title\":\"Antrean Harian\",\"widget\":\"KANBAN\"}],\"modules\":["
        )
        val pack = DomainPackCodec.decode(withScreens)
        assertEquals(
            listOf(ScreenSuggestion(ModuleId("klinik_antrean"), "Antrean Harian", WidgetKind.KANBAN)),
            pack.screenSuggestions
        )
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)))

        // Widget di luar kosakata tertutup ditolak dengan path yang bisa dikembalikan ke AI/penyunting.
        val badWidget = withScreens.replace("\"widget\":\"KANBAN\"", "\"widget\":\"MAGIC\"")
        assertPathRejected("$.screenSuggestions[0].widget", badWidget)

        // Usulan layar ke modul yang tidak ada = pack rusak — invarian DomainPack menolaknya.
        val badModule = withScreens.replace("\"moduleId\":\"klinik_antrean\"", "\"moduleId\":\"hantu\"")
        assertPathRejected("$", badModule, prefixOnly = true)
    }

    @Test
    fun missingField_unknownEnum_andBadColor_areRejectedWithPath() {
        assertPathRejected("$.modules[1].kind", KLINIK_JSON.replace("\"OPERATIONAL\"", "\"MAGIC\""))
        assertPathRejected("$.phases[0].color", KLINIK_JSON.replace("#FF2563EB", "blue"))
        assertPathRejected("$.modules[1].iconKey", KLINIK_JSON.replace("\"iconKey\":\"clipboard\",", ""))
        // Invarian struktur DomainPack (slot menunjuk fase tak dikenal) juga ditolak, bukan dilewatkan.
        assertPathRejected("$.slots[0]", KLINIK_JSON.replace("\"phase\":\"LAYANAN\"", "\"phase\":\"GHOST\""), prefixOnly = true)
    }

    @Test
    fun registry_rejectsHijackedIds_unprefixedNewIds_andShippedCode() {
        val garmentCrm = DomainPackCodec.encodeToString(GarmentDomainPack.pack)
        val crm = JsonParser.parseObject(garmentCrm).array("modules").filterIsInstance<JsonValue.Obj>()
            .first { it.string("id") == "crm_sales" }.encode()

        // crm_sales dengan nama lain = merebut identitas modul garment.
        val hijack = KLINIK_JSON.replace(ANTREAN_MODULE, crm.replace("Pelanggan & Prospek Sales", "CRM Klinik"))
            .replace("\"section\":\"SALES\"", "\"section\":\"LAYANAN\"")
            .replace("\"slot\":\"order_ingestion\"", "\"slot\":\"klinik_layanan\"")
        assertTrue(DomainPackRegistry.violations(DomainPackCodec.decode(hijack)).any { "crm_sales" in it })

        val unprefixed = KLINIK_JSON.replace("klinik_antrean", "antrean")
        assertTrue(DomainPackRegistry.violations(DomainPackCodec.decode(unprefixed)).any { "wajib berprefiks" in it })

        val shipped = KLINIK_JSON.replace("\"code\":\"klinik\"", "\"code\":\"garment\"")
        assertFailsWith<IllegalArgumentException> { DomainPackRegistry.register(DomainPackCodec.decode(shipped)) }
    }

    private fun assertPathRejected(path: String, json: String, prefixOnly: Boolean = false) {
        val e = assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json) }
        if (prefixOnly) assertTrue(e.path.startsWith("$"), e.message) else assertEquals(path, e.path, e.message)
    }

    companion object {
        private const val ANTREAN_MODULE =
            """{"id":"klinik_antrean","displayName":"Antrean Pasien","description":"Antrean pendaftaran & poli","section":"LAYANAN","kind":"OPERATIONAL","iconKey":"clipboard","scopeCapability":"HIERARCHICAL","supportedScopes":["OWN_DATA_ONLY","ALL_TENANT_DATA"],"slot":"klinik_layanan"}"""

        private val ORG_CHART_MODULE: String = JsonParser.parseObject(DomainPackCodec.encodeToString(GarmentDomainPack.pack))
            .array("modules").filterIsInstance<JsonValue.Obj>().first { it.string("id") == "org_chart" }.encode()

        private val GOVERNANCE_SECTION: String = JsonParser.parseObject(DomainPackCodec.encodeToString(GarmentDomainPack.pack))
            .array("sections").filterIsInstance<JsonValue.Obj>().first { it.string("code") == "GOVERNANCE" }.encode()

        /** Pack klinik minimal: satu fase, satu slot, modul platform bersama + satu modul baru berprefiks. */
        val KLINIK_JSON: String = """
            {"code":"klinik","displayName":"Klinik & Layanan Kesehatan",
             "phases":[{"code":"LAYANAN","order":1,"displayName":"1. Layanan","subtitle":"Pasien datang","color":"#FF2563EB"}],
             "slots":[{"code":"klinik_layanan","displayName":"Layanan","phase":"LAYANAN","defaultInput":"Kunjungan","defaultOutput":"Rekam"}],
             "portTypes":["Kunjungan","Rekam"],"wiredPortTypes":["Kunjungan","Rekam"],
             "sections":[$GOVERNANCE_SECTION,{"code":"LAYANAN","displayName":"Layanan","order":2,"color":"#FF16A34A","tint":"#FFF0FDF4"}],
             "modules":[$ORG_CHART_MODULE,$ANTREAN_MODULE]}
        """.trimIndent().replace("\n", "")
    }
}
