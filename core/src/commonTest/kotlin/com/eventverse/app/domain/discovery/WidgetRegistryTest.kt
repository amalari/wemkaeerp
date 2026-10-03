package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan §4 Fase C: kosakata widget tertutup + sample data berupa data. Sample deterministik —
 * renderer hanya menggambar, test membandingkan keluarannya persis.
 */
class WidgetRegistryTest {

    private suspend fun klinikDraft(): DiscoveryDraft = DeterministicDiscoveryAgent().draft(
        DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik")
    ).getOrThrow()

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }

    @Test
    fun `kosakata widget tertutup sesuai plan`() {
        assertEquals(7, WidgetKind.entries.size)
        assertEquals("TABLE", WidgetKind.TABLE.code)
        assertNull(WidgetKind.fromCode("SPREADSHEET"))
    }

    @Test
    fun `sample data deterministik untuk setiap kind dan memakai kosakata pack`() = runTest {
        val draft = klinikDraft()
        val module = draft.pack.modules.first()
        val screen = PrototypeScreen("scr-1", module.id, "Antrean Harian", "TABLE")

        val rows = WidgetRegistry.sampleRowsFor(screen, draft.pack)
        val again = WidgetRegistry.sampleRowsFor(screen, draft.pack)
        assertEquals(rows, again)
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.containsValue("Baru") || it.containsValue("Proses") || it.containsValue("Selesai") })
        // Kosakata pack, bukan garment: tidak ada kata SPK/pabrik.
        assertTrue(rows.joinToString().contains(module.displayName))
        assertTrue(!rows.joinToString().uppercase().contains("SPK"))

        // Kind lain menghasilkan data non-kosong & deterministik juga — **termasuk CUSTOM_SCREEN**,
        // yang samplenya berupa kerangka blok. Mengeluarkannya dari loop ini pernah menyembunyikan
        // kartu kosong di renderer: test hijau, layar tetap tanpa penjelasan apa pun.
        WidgetKind.entries.forEach { kind ->
            val s = PrototypeScreen("scr-${kind.code}", module.id, module.displayName, kind.code)
            assertTrue(WidgetRegistry.sampleRowsFor(s, draft.pack).isNotEmpty(), kind.code)
        }
        // Layar kustom memakai kosakata struktural (blok + lebar), bukan kolom data.
        val custom = WidgetRegistry.sampleRowsFor(
            PrototypeScreen("scr-custom", module.id, module.displayName, WidgetKind.CUSTOM_SCREEN.code),
            draft.pack
        )
        assertEquals(listOf("penuh", "separuh", "separuh"), custom.map { it["Lebar"] })
        // Layar untuk modul asing → kosong, bukan data karangan.
        assertTrue(WidgetRegistry.sampleRowsFor(screen.copy(moduleId = ModuleId("modul_hantu")), draft.pack).isEmpty())
    }

    @Test
    fun `baris contoh pack garment dipakai apa adanya, widget lain tetap generik`() = runTest {
        val pack = GarmentDomainPack.pack
        val suggestion = pack.screenSuggestions.first()
        val screen = PrototypeScreen(
            "default-${suggestion.moduleId.value}",
            suggestion.moduleId,
            suggestion.title,
            suggestion.widget.code
        )
        // Layar bawaan pack (widget cocok dengan usulan) memakai data vertikal pack — bukan karangan.
        assertEquals(suggestion.sampleRows, WidgetRegistry.sampleRowsFor(screen, pack))

        // Widget berbeda dari usulan → penanda struktural generik, bukan baris yang salah bentuk.
        val generic = WidgetRegistry.sampleRowsFor(screen.copy(widget = WidgetKind.TABLE.code), pack)
        assertTrue(generic.isNotEmpty())
        assertTrue(generic != suggestion.sampleRows)
    }

    @Test
    fun `validator menolak widget di luar kosakata dengan path`() = runTest {
        val draft = klinikDraft()
        val module = draft.pack.modules.first()
        val bad = draft.copy(screens = listOf(PrototypeScreen("scr-1", module.id, "Antrean", "SPREADSHEET")))
        val issues = DiscoveryDraftValidator.validate(bad)
        assertTrue(issues.any { it.path == "$.screens[0].widget" && it.message.contains("SPREADSHEET") })
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draft))
    }

    @Test
    fun `simpan pola menolak widget asing dan pack hantu, menerima pola sah`() = runTest {
        val repo = InMemoryPatternRepo()
        val save = SavePrototypePatternUseCase(repo)
        val owner = UserId("usr-staff")

        assertTrue(save("p1", "Tabel antrean", "SPREADSHEET", "{}", owner).isFailure)
        assertTrue(save("p1", "Tabel antrean", "TABLE", "bukan json", owner).isFailure)
        assertTrue(save("p1", "Tabel antrean", "TABLE", "{}", owner, packCode = "pack_hantu").isFailure)

        val saved = save("p1", "Tabel antrean", "TABLE", """{"kolom":["a","b"]}""", owner, packCode = "garment").getOrThrow()
        assertEquals("TABLE", saved.widget.code)
        assertEquals(1, repo.rows.size)

        // Nama duplikat untuk pola berbeda ditolak; pembaruan pola yang sama boleh.
        assertTrue(save("p2", "Tabel antrean", "KANBAN", "{}", owner).isFailure)
        save("p1", "Tabel antrean", "KANBAN", """{"kolom":["c"]}""", owner).getOrThrow()
        assertEquals("KANBAN", repo.findById("p1")!!.widget.code)
    }

    private class InMemoryPatternRepo : PrototypePatternRepository {
        val rows = linkedMapOf<String, PrototypePattern>()
        override suspend fun findById(id: String) = rows[id]
        override suspend fun findByName(name: String) = rows.values.firstOrNull { it.name == name }
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(pattern: PrototypePattern) { rows[pattern.id] = pattern }
    }
}
