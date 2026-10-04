package com.eventverse.app.presentation.discovery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.PrototypeContractSamples
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.SpecOp
import com.eventverse.app.domain.prototype.SpecOpApplier
import com.eventverse.app.domain.prototype.TableHints
import com.eventverse.app.presentation.designsystem.ClayKanbanDragState
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.InteractiveScreenCodec
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

    @Test
    fun testChatEditShowFieldOnCardAndSetFieldRequired() {
        val initialScreen = PrototypeContractSamples.orderScreen
        val screenUi = DiscoveryScreenUi(
            screenId = "antrian",
            moduleId = "servis",
            title = "Antrian Servis",
            widget = WidgetKind.KANBAN.name,
            sampleRows = initialScreen.seed["order"].orEmpty().map { it.values },
            interactive = initialScreen
        )
        val session = PrototypeSession(listOf(screenUi))

        // Terapkan SpecOp.ShowFieldOnCard (ubah Target menjadi NUMBER di posisi semula)
        val op1 = SpecOp.ShowFieldOnCard("order", "Target", CardStyle.NUMBER)
        val op2 = SpecOp.SetFieldRequired("order", "Total", true)
        val applied = SpecOpApplier.applyAll(initialScreen, listOf(op1, op2), "2026-10-04T10:00:00Z")

        assertEquals(2, applied.log.count { it.ok })
        val updatedKanban = applied.screen.spec.screens.first { it.screenId == "antrian" }.kanban!!
        val targetElem = updatedKanban.card.firstOrNull { it.field == "Target" }
        assertNotNull(targetElem)
        assertEquals(CardStyle.NUMBER, targetElem.style)
        assertTrue(applied.screen.spec.entity("order")!!.field("Total")!!.required)

        session.updateScreenSpec("antrian", initialScreen, applied.screen)
        assertTrue(session.canUndo("antrian"))
        val restored = session.undo("antrian")
        assertNotNull(restored)
        val restoredKanban = restored.spec.screens.first { it.screenId == "antrian" }.kanban!!
        assertEquals(CardStyle.DATE, restoredKanban.card.first { it.field == "Target" }.style)
    }

    @Test
    fun testDiscoveryUiModelParsesBinding() {
        val memScreen = PrototypeContractSamples.orderScreen
        val apiScreen = PrototypeContractSamples.orderScreenApi

        val draftJson = JsonValue.Obj(
            mapOf(
                "id" to JsonValue.Str("draft-1"),
                "status" to JsonValue.Str("draft"),
                "packCode" to JsonValue.Str("layanan"),
                "modules" to JsonValue.Arr(emptyList()),
                "screens" to JsonValue.Arr(
                    listOf(
                        JsonValue.Obj(
                            mapOf(
                                "screenId" to JsonValue.Str("s-mem"),
                                "moduleId" to JsonValue.Str("m1"),
                                "title" to JsonValue.Str("Memori"),
                                "widget" to JsonValue.Str(WidgetKind.KANBAN.name),
                                "sampleRows" to JsonValue.Arr(emptyList()),
                                "interactive" to InteractiveScreenCodec.encode(memScreen)
                            )
                        ),
                        JsonValue.Obj(
                            mapOf(
                                "screenId" to JsonValue.Str("s-api"),
                                "moduleId" to JsonValue.Str("m1"),
                                "title" to JsonValue.Str("API"),
                                "widget" to JsonValue.Str(WidgetKind.KANBAN.name),
                                "sampleRows" to JsonValue.Arr(emptyList()),
                                "interactive" to InteractiveScreenCodec.encode(apiScreen)
                            )
                        )
                    )
                )
            )
        )

        val parsed = DiscoveryDraftUi.fromJson(draftJson)
        assertEquals(2, parsed.screens.size)
        val memParsed = parsed.screens.first { it.screenId == "s-mem" }.interactive
        assertNotNull(memParsed)
        assertTrue(memParsed.binding is DataBinding.Memory)

        val apiParsed = parsed.screens.first { it.screenId == "s-api" }.interactive
        assertNotNull(apiParsed)
        assertTrue(apiParsed.binding is DataBinding.Api)
        assertEquals("/api/tenant/modules/servis/service_orders", (apiParsed.binding as DataBinding.Api).basePath)
    }
}

