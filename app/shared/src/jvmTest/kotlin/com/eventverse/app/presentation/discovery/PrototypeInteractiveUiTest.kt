package com.eventverse.app.presentation.discovery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.TableHints
import com.eventverse.app.presentation.designsystem.ClayKanbanDragState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrototypeInteractiveUiTest {

    @Test
    fun testFormStateSubmissionUpdatesSessionAndState() {
        val formHints = FormHints(
            fields = listOf("customer", "qty"),
            submitLabel = "Simpan",
            required = listOf("customer")
        )
        val formScreen = requireNotNull(
            InteractiveScreenFactory.form("screen-form", "Tambah Order", formHints)
        )

        var createdEntity: String? = null
        var createdRow: PrototypeRow? = null

        val formState = InteractiveFormState(formScreen) { entityId, newRow ->
            createdEntity = entityId
            createdRow = newRow
        }

        formState.setFieldValue("customer", "Budi")
        formState.setFieldValue("qty", "10")

        val success = formState.submit()
        assertTrue(success)
        assertEquals(InteractiveScreenFactory.ENTITY_ID, createdEntity)
        assertNotNull(createdRow)
        assertEquals("Budi", createdRow?.get("customer"))
        assertEquals("10", createdRow?.get("qty"))
        assertEquals("Data 'Tambah Order' berhasil ditambahkan!", formState.successMessage)
    }

    @Test
    fun testKanbanAndDeleteAction() {
        val rows = listOf(
            mapOf("Kolom" to "Draft", "Judul" to "Sample 1"),
            mapOf("Kolom" to "Selesai", "Judul" to "Sample 2")
        )
        val hints = KanbanHints(
            columns = listOf("Draft", "Proses", "Selesai"),
            groupLabel = "Status"
        )
        val screen = requireNotNull(
            InteractiveScreenFactory.kanban("screen-kanban", "Alur Sampling", rows, hints)
        )

        val kanbanState = InteractiveKanbanState(screen)
        assertEquals(1, kanbanState.cards("Draft").size)
        assertEquals(1, kanbanState.cards("Selesai").size)

        val rowToDelete = kanbanState.cards("Draft").first()
        kanbanState.delete(rowToDelete.id)

        assertEquals(0, kanbanState.cards("Draft").size)
        assertEquals(1, kanbanState.cards("Selesai").size)
    }

    @Test
    fun testTableDeleteRow() {
        val rows = listOf(
            mapOf("Nama" to "Kain Katun", "Status" to "Tersedia"),
            mapOf("Nama" to "Kain Linen", "Status" to "Habis")
        )
        val hints = TableHints(
            statusColumn = "Status",
            options = listOf("Tersedia", "Habis")
        )
        val screen = requireNotNull(
            InteractiveScreenFactory.table("screen-table", "Daftar Bahan", rows, hints)
        )

        val tableState = InteractiveTableState(screen)
        assertEquals(2, tableState.rows.size)

        val rowToDelete = tableState.rows.first()
        tableState.delete(rowToDelete.id)

        assertEquals(1, tableState.rows.size)
        assertEquals("Kain Linen", tableState.rows.first()["Nama"])
    }

    @Test
    fun testSessionUndoStack() {
        val rows = listOf(mapOf("Kolom" to "Draft", "Judul" to "Item 1"))
        val screen = requireNotNull(
            InteractiveScreenFactory.kanban("scr-1", "Awal", rows)
        )
        val screenUi = DiscoveryScreenUi(
            screenId = "scr-1",
            moduleId = "mod-1",
            title = "Awal",
            widget = WidgetKind.KANBAN.name,
            sampleRows = rows,
            interactive = screen
        )
        val session = PrototypeSession(listOf(screenUi))

        val updatedScreen = requireNotNull(
            InteractiveScreenFactory.kanban("scr-1", "Diubah", rows)
        )

        session.updateScreenSpec("scr-1", screen, updatedScreen)
        assertTrue(session.canUndo("scr-1"))

        val restored = session.undo("scr-1")
        assertNotNull(restored)
        assertEquals(screen, restored)
        assertFalse(session.canUndo("scr-1"))
    }

    @Test
    fun testClayKanbanDragStateCoordination() {
        val dragState = ClayKanbanDragState<String, String>()
        dragState.registerColumn("col-1", Rect(0f, 0f, 100f, 200f))
        dragState.registerColumn("col-2", Rect(100f, 0f, 200f, 200f))

        dragState.onDragStart("item-A", Offset(10f, 10f), Size(80f, 40f), Offset(5f, 5f))
        assertTrue(dragState.isDragging)
        assertEquals("col-1", dragState.hoveredColumnId)

        dragState.onDrag(Offset(110f, 0f))
        assertEquals("col-2", dragState.hoveredColumnId)

        dragState.canMoveCheck = { _, colId -> colId != "col-2" }
        dragState.onDrag(Offset(1f, 0f))
        assertNull(dragState.hoveredColumnId)

        var committedCol: String? = null
        dragState.canMoveCheck = { _, _ -> true }
        dragState.onDrag(Offset(0f, 0f))
        dragState.onDragEnd { toCol -> committedCol = toCol }

        assertEquals("col-2", committedCol)
        assertFalse(dragState.isDragging)
        assertNull(dragState.draggedItem)
    }
}
