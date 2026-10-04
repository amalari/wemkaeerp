package com.eventverse.app.domain.pack

import com.eventverse.app.domain.prototype.CardStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GarmentExportSeedTest {
    private val poPattern = Regex("[A-Z]{2}-26-\\d{4}")
    private val allRows get() = GarmentScreenSuggestions.all.flatMap { s -> s.sampleRows.map { s.moduleId.value to it } }

    @Test
    fun everyPoMentionedOnAnyScreen_existsInSeed() {
        val known = GarmentExportSeed.orders.map { it.po }.toSet()
        allRows.forEach { (module, row) ->
            row.values.flatMap { poPattern.findAll(it).map { m -> m.value }.toList() }.forEach { po ->
                assertTrue(po in known, "PO $po di layar $module tidak ada di seed")
            }
        }
    }

    @Test
    fun crmListsEveryOrder_andMrpListsEveryOrderInProduction() {
        val crm = GarmentExportSeed.crmRows().map { it["No. PO"] }
        assertTrue(GarmentExportSeed.orders.all { it.po in crm })
        val mrp = GarmentExportSeed.mrpRows().joinToString { it["Kartu"].orEmpty() }
        GarmentExportSeed.orders.filter { it.stage != GarmentExportSeed.Stage.SAMPLING }.forEach { assertTrue(it.po in mrp) }
        assertTrue(GarmentExportSeed.orders.first { it.stage == GarmentExportSeed.Stage.SAMPLING }.po !in mrp)
    }

    @Test
    fun statusesAndColumnsStayInsidePackHints() {
        val pack = GarmentScreenSuggestions.all
        pack.forEach { s ->
            s.tableHints?.let { h -> s.sampleRows.forEach { assertTrue(it[h.statusColumn] in h.options, "${s.moduleId.value}: status ${it[h.statusColumn]}") } }
            s.kanbanHints?.let { h -> s.sampleRows.forEach { assertTrue(it["Kolom"] in h.columns, "${s.moduleId.value}: kolom ${it["Kolom"]}") } }
        }
    }

    @Test
    fun valuesAreComputedNotTyped() {
        assertEquals("USD 82.200", GarmentExportSeed.activeValueText())
        assertEquals("6.000 pcs", GarmentExportSeed.order("NW-26-0412").qtyText)
    }

    @Test
    fun noDomesticLeftovers() {
        val text = allRows.joinToString { it.second.values.joinToString() }
        listOf("Sinar Jaya", "Truk rental", "PO-2026").forEach { assertTrue(it !in text, "sisa data lama: $it") }
    }

    // ---- B3: petunjuk kaya CRM & papan sampling ---------------------------------------------

    @Test
    fun crmTableHints_typedFieldsInlineCreateAndEditable_perPlan() {
        val crm = GarmentScreenSuggestions.all.first { it.moduleId == GarmentModules.CRM_SALES }
        val h = assertNotNull(crm.tableHints)
        assertTrue(h.inlineCreate, "tambah baris langsung dari tabel")
        assertEquals(listOf("Pembeli", "Produk", "Target Kirim"), h.editableFields)
        assertEquals(listOf("No. PO", "Pembeli"), h.fields.filter { it.required }.map { it.key }, "No. PO & Pembeli wajib")
        assertTrue(h.fields.none { it.key == h.statusColumn }, "status tak dideklarasikan ulang — ENUM otomatis dari kolom status")
        // Setiap field deklarasi benar-benar jadi kolom baris seed (satu sumber di GarmentExportSeed).
        h.fields.forEach { f -> assertTrue(crm.sampleRows.all { f.key in it }, "kolom ${f.key} hilang di baris CRM") }
    }

    @Test
    fun samplingKanban_richHints_coherentWithSeedRows() {
        val spk = GarmentScreenSuggestions.all.first { it.moduleId == GarmentModules.SAMPLING_ORDER }
        val h = assertNotNull(spk.kanbanHints)
        val keys = spk.sampleRows.first().keys
        assertTrue(spk.sampleRows.all { it.keys == keys }, "kunci baris seragam lintas SPK")
        h.card.forEach { el -> assertTrue(el.field in keys, "elemen kartu '${el.field}' harus ada di baris seed") }
        assertEquals(CardStyle.TITLE, h.card.first().style, "nomor SPK jadi judul kartu")
        val wip = assertNotNull(h.columnMeta["Dikerjakan"]?.wipLimit, "kolom Dikerjakan berbatas WIP")
        assertTrue(wip > 0)
        h.columnMeta.forEach { (col, _) -> assertTrue(col in h.columns, "metadata kolom '$col' harus kolom papan") }
        val form = assertNotNull(h.detailForm)
        form.fields.forEach { assertTrue(it in keys, "field form '$it' harus ada di baris seed") }
        // Paritas pabrik: hints kaya harus benar-benar membentuk layar interaktif, bukan sekadar data.
        val built = com.eventverse.app.domain.prototype.InteractiveScreenFactory.kanban("spk", "Papan SPK", spk.sampleRows, h)
        assertNotNull(built, "hints kaya sampling harus koheren bagi factory")
        assertEquals("Nomor", built.spec.screens.single().kanban!!.titleField, "field teks pertama baris jadi judul")
    }

    @Test
    fun samplingRows_singleSourcedTypedAndFlagExercised() {
        val rows = GarmentExportSeed.samplingRows()
        assertEquals(5, rows.size)
        assertEquals(listOf("Kolom", "Nomor", "Artikel", "Jenis", "Pembeli", "Jumlah", "Due", "Mendesak"), rows.first().keys.toList())
        assertTrue(rows.any { it["Mendesak"] == "ya" }, "ada SPK mendesak agar elemen FLAG terlatih")
    }
}
