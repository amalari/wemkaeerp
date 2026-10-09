package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TableHints
import kotlin.test.Test
import kotlin.test.assertEquals

class InteractiveTableHeaderTest {

    private fun slugScreen(): InteractiveScreen {
        val entity = EntitySpec(
            "e", "Tugas",
            listOf(
                FieldSpec("judul", "Judul", FieldType.TEXT),
                FieldSpec("jumlah", "Jumlah Barang", FieldType.NUMBER)
            )
        )
        val spec = PrototypeSpec(
            listOf(entity),
            listOf(ScreenSpec("s", "Tugas", WidgetKind.TABLE, "e", table = TableConfig(listOf("judul", "jumlah"), null)))
        )
        val seed = mapOf("e" to listOf(PrototypeRow("r1", mapOf("judul" to "A", "jumlah" to "2"))))
        return InteractiveScreen(spec, seed).also { it.newStore() }
    }

    @Test
    fun columnLabel_keyBerbedaDariLabel_memakaiLabel() {
        val state = InteractiveTableState(slugScreen())
        assertEquals("Judul", state.columnLabel("judul"))
        assertEquals("Jumlah Barang", state.columnLabel("jumlah"))
    }

    @Test
    fun columnLabel_fieldTakDitemukan_fallbackKeyTanpaCrash() {
        assertEquals("tak_ada", InteractiveTableState(slugScreen()).columnLabel("tak_ada"))
    }

    @Test
    fun columnLabel_keySamaDenganLabelSepertiGarment_takBerubah() {
        val hints = TableHints(
            statusColumn = "Status",
            options = listOf("Tersedia", "Habis"),
            fields = listOf(
                FieldHint("Nama", FieldType.TEXT),
                FieldHint("Status", FieldType.ENUM, options = listOf("Tersedia", "Habis"))
            )
        )
        val rows = listOf(mapOf("Nama" to "Katun", "Status" to "Tersedia"))
        val screen = requireNotNull(InteractiveScreenFactory.table("s", "Material", rows, hints))
        val state = InteractiveTableState(screen)
        state.config.columns.forEach { assertEquals(it, state.columnLabel(it)) }
    }

    @Test
    fun toggleSort_tetapMemakaiKey_bukanLabel() {
        val state = InteractiveTableState(slugScreen())
        state.toggleSort("judul")
        assertEquals("judul", state.sortColumn)
    }
}
