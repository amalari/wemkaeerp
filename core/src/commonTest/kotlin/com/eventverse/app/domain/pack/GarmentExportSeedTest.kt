package com.eventverse.app.domain.pack

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
