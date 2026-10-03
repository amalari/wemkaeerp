package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Fixture sengaja non-garment (tiket servis) — Kontrak 6: bukan hanya tenant default. */
class PrototypeReducerTest {
    private val status = FieldSpec("status", "Status", FieldType.ENUM, listOf("Baru", "Diproses", "Selesai"))
    private val entity = EntitySpec(
        "tiket", "Tiket",
        listOf(FieldSpec("judul", "Judul", FieldType.TEXT), status),
        StateMachine("status", mapOf("Baru" to setOf("Diproses"), "Diproses" to setOf("Selesai", "Baru")))
    )
    private val spec = PrototypeSpec(
        listOf(entity),
        listOf(ScreenSpec("s1", "Papan", WidgetKind.KANBAN, "tiket", KanbanConfig("status", listOf("Baru", "Diproses", "Selesai"), "judul")))
    )
    private val store = PrototypeStore.seeded(spec, mapOf("tiket" to listOf(PrototypeRow("t1", mapOf("judul" to "AC mati", "status" to "Baru")))))

    @Test
    fun moveCard_allowedTransition_updatesRow() {
        val next = PrototypeReducer.moveCard(spec, store, "tiket", "t1", "status", "Diproses").getOrThrow()
        assertEquals("Diproses", next.rowsOf("tiket").single()["status"])
    }

    @Test
    fun moveCard_forbiddenTransition_isRejectedWithMessage() {
        val result = PrototypeReducer.moveCard(spec, store, "tiket", "t1", "status", "Selesai")
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("tidak boleh pindah"))
        assertEquals("Baru", store.rowsOf("tiket").single()["status"])
    }

    @Test
    fun moveCard_unknownColumnValue_isRejected() {
        assertTrue(PrototypeReducer.moveCard(spec, store, "tiket", "t1", "status", "Hantu").isFailure)
    }

    @Test
    fun create_duplicateId_isRejected() {
        val dup = PrototypeAction.Create("tiket", PrototypeRow("t1", mapOf("judul" to "x")))
        assertTrue(PrototypeReducer.reduce(spec, store, dup).isFailure)
    }

    @Test
    fun spec_kanbanColumnOutsideOptions_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            PrototypeSpec(listOf(entity), listOf(ScreenSpec("s", "x", WidgetKind.KANBAN, "tiket", KanbanConfig("status", listOf("Hantu"), "judul"))))
        }
    }

    @Test
    fun seed_invalidEnumValue_failsClosed() {
        assertFailsWith<IllegalArgumentException> {
            PrototypeStore.seeded(spec, mapOf("tiket" to listOf(PrototypeRow("t9", mapOf("status" to "Hantu")))))
        }
    }
}
