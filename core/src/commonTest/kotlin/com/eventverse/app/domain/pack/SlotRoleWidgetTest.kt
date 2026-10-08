package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.rbac.code
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B2 — pemetaan peran → tampilan sebagai data pack (`SlotDefinition.defaultWidget`/`defaultStatuses`). */
class SlotRoleWidgetTest {

    private val phase = PhaseCode("OPERASI")
    private fun slot(widget: WidgetKind? = null, statuses: List<String> = emptyList()) =
        SlotDefinition(SlotCode("x_antrean"), "Antrean", phase, PortType("A"), PortType("B"), widget, statuses)

    // --- aturan SlotDefinition ------------------------------------------------------------------
    @Test
    fun `slot tanpa widget dan status tetap sah dan tidak berpendapat`() {
        val s = SlotDefinition(SlotCode("x_antrean"), "Antrean", phase, PortType("A"), PortType("B"))
        assertNull(s.defaultWidget)
        assertEquals(emptyList(), s.defaultStatuses)
    }

    @Test
    fun `status hanya untuk kanban atau tabel dan berbatas 2 sampai 8 tanpa kembar`() {
        assertEquals(listOf("Baru", "Selesai"), slot(WidgetKind.KANBAN, listOf("Baru", "Selesai")).defaultStatuses)
        listOf<() -> SlotDefinition>(
            { slot(WidgetKind.DASHBOARD, listOf("a", "b")) }, { slot(null, listOf("a", "b")) },
            { slot(WidgetKind.KANBAN, listOf("sendiri")) }, { slot(WidgetKind.KANBAN, (1..9).map { "s$it" }) },
            { slot(WidgetKind.KANBAN, listOf("a", "a")) }, { slot(WidgetKind.KANBAN, listOf("a", " ")) }
        ).forEach { make -> assertFailsWith<IllegalArgumentException> { make() } }
    }

    // --- codec ----------------------------------------------------------------------------------
    private val json = DomainPackCodec.encodeToString(GarmentDomainPack.pack)

    @Test
    fun `round-trip garment membawa widget dan status slot`() {
        val decoded = DomainPackCodec.decode(json)
        assertEquals(GarmentDomainPack.pack.slots, decoded.slots)
        assertEquals(WidgetKind.KANBAN, decoded.slots.first { it.code == GarmentSlots.CUTTING }.defaultWidget)
    }

    @Test
    fun `pack lama tanpa kunci baru terbaca dengan null dan kosong`() {
        val old = json.replace(Regex(",\"defaultWidget\":(\"[A-Z_]+\"|null),\"defaultStatuses\":\\[[^\\]]*\\]"), "")
        assertTrue(!old.contains("defaultWidget"))
        val decoded = DomainPackCodec.decode(old)
        assertTrue(decoded.slots.all { it.defaultWidget == null && it.defaultStatuses.isEmpty() })
    }

    @Test
    fun `kunci rusak ditolak dengan path bukan diabaikan`() {
        fun failPath(edit: (String) -> String) = assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(edit(json)) }.path
        assertTrue(failPath { it.replaceFirst("\"defaultWidget\":\"TABLE\"", "\"defaultWidget\":\"PETA\"") }.endsWith(".defaultWidget"))
        assertTrue(failPath { it.replaceFirst("\"defaultStatuses\":[\"Prospek\"", "\"defaultStatuses\":[7") }.contains("defaultStatuses[0]"))
        // kombinasi tak sah (status dengan widget DASHBOARD) ditolak konstruktor domain, dibungkus path slotnya
        assertTrue(failPath { it.replaceFirst("\"defaultWidget\":\"TABLE\",\"defaultStatuses\":[\"Prospek\"", "\"defaultWidget\":\"DASHBOARD\",\"defaultStatuses\":[\"Prospek\"") }.startsWith("$.slots["))
    }

    // --- cermin garment (paritas dengan acuan B1) -----------------------------------------------
    private val unmirrored = setOf(GarmentSlots.FINISHING, GarmentSlots.CUSTOM_EXTENSION)

    @Test
    fun `slot garment mencerminkan usulan layar modul wakilnya`() {
        GarmentSlots.meta.forEach { m ->
            if (m.slot in unmirrored) return@forEach
            val suggestion = GarmentScreenSuggestions.all.first { it.moduleId.value == m.representative.code }
            assertEquals(suggestion.widget, m.widget, m.slot.value)
            val expected = suggestion.kanbanHints?.columns ?: suggestion.tableHints?.options.orEmpty()
            assertEquals(expected, m.statuses, m.slot.value)
        }
    }

    @Test
    fun `slot garment tanpa pendapat tetap null — tidak ada tebakan`() {
        val slots = GarmentDomainPack.pack.slots.associateBy { it.code }
        unmirrored.forEach { assertNull(slots.getValue(it).defaultWidget, it.value) }
        assertEquals(GarmentSlots.all.size - unmirrored.size, slots.values.count { it.defaultWidget != null })
    }

    @Test
    fun `pack tanpa pemetaan peran tidak meminjam dari garment`() {
        assertTrue(LayananPilotPack.pack.slots.all { it.defaultWidget == null && it.defaultStatuses.isEmpty() })
    }

    // --- agent deterministik (pack non-garment) -------------------------------------------------
    @Test
    fun `pack hasil agent deterministik memberi widget dari kemampuan bawaannya`() = runTest {
        val pack = DeterministicDiscoveryAgent().draft(DiscoveryRequest("Klinik gigi: pendaftaran pasien, antrean poli, stok obat, tagihan, laporan harian")).getOrThrow().pack
        val bySuffix = pack.slots.associateBy { it.code.value.substringAfter('_') }
        assertEquals(WidgetKind.TABLE to listOf("Baru", "Diproses", "Selesai"), bySuffix.getValue("pesanan").let { it.defaultWidget to it.defaultStatuses })
        assertEquals(WidgetKind.KANBAN, bySuffix.getValue("antrean").defaultWidget)
        assertEquals(WidgetKind.TABLE, bySuffix.getValue("stok").defaultWidget)
        assertEquals(listOf("Belum bayar", "Lunas"), bySuffix.getValue("tagihan").defaultStatuses)
        assertEquals(WidgetKind.DASHBOARD, bySuffix.getValue("laporan").defaultWidget)
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)))
    }

    @Test
    fun `kemampuan cadangan tanpa kata kunci tidak berpendapat`() = runTest {
        val pack = DeterministicDiscoveryAgent().draft(DiscoveryRequest("Usaha jasa serba guna")).getOrThrow().pack
        assertTrue(pack.slots.all { it.defaultWidget == null })
    }
}
