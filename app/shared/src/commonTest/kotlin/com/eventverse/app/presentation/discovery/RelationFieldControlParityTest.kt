package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.TableHints
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Paritas + perilaku kontrol rujukan (C7, TRD-FIELD-001 Track C) untuk kosakata prototype:
 * kolom `RELATION` punya jalur pemilih yang dihasilkan dari binding, dan **bukan** dipalsukan
 * jadi kolom teks. Di layar berbinding memori (demo tanpa server) tidak ada kontroler — UI
 * jatuh ke tampilan baca-saja label/id, bukan input teks bebas (field-component Kontrak 8).
 */
class RelationFieldControlParityTest {

    @Test
    fun relationColumn_memoryBinding_hasNoPickerController_fallsBackReadOnly() {
        val hints = TableHints(
            statusColumn = "status",
            options = listOf("Aktif"),
            fields = listOf(
                FieldHint("ref", FieldType.RELATION, required = true, target = "pesanan")
            ),
            editableFields = listOf("ref")
        )
        val screen = requireNotNull(
            InteractiveScreenFactory.table(
                "scr-rel",
                "Rujukan",
                listOf(mapOf("ref" to "p-1", "status" to "Aktif")),
                hints
            )
        )
        val state = InteractiveTableState(screen)

        // Demo memori: tidak ada server untuk memuat opsi → tidak ada kontroler pemilih.
        assertNull(state.relationField("ref"))
        assertTrue(state.isCellEditable("ref"))
    }

    @Test
    fun nonRelationColumn_neverYieldsRelationController() {
        val hints = TableHints(
            statusColumn = "status",
            options = listOf("Aktif"),
            fields = listOf(
                FieldHint("nama", FieldType.TEXT, required = true),
                FieldHint("ref", FieldType.RELATION, target = "pesanan")
            )
        )
        val screen = requireNotNull(
            InteractiveScreenFactory.table(
                "scr-2",
                "Campur",
                listOf(mapOf("nama" to "SPK-1", "ref" to "p-1", "status" to "Aktif")),
                hints
            )
        )
        val state = InteractiveTableState(screen)

        assertNull(state.relationField("nama"))
    }
}
