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

    /** C3 Irisan 2: LONG_TEXT menerima isi multibaris lewat reducer — tidak ada pemangkasan diam-diam. */
    @Test
    fun setField_longText_menerimaMultiline() {
        val catatan = FieldSpec("catatan", "Catatan", FieldType.LONG_TEXT)
        val specCatatan = PrototypeSpec(
            listOf(EntitySpec("tiket", "Tiket", listOf(FieldSpec("judul", "Judul", FieldType.TEXT), catatan))),
            emptyList()
        )
        val storeCatatan = PrototypeStore.seeded(specCatatan, mapOf("tiket" to listOf(PrototypeRow("t1", mapOf("judul" to "AC mati")))))
        val multiline = "Lapis 1: kain diperiksa.\nLapis 2: jahit manual, benang polyester."
        val next = PrototypeReducer.reduce(specCatatan, storeCatatan, PrototypeAction.SetField("tiket", "t1", "catatan", multiline)).getOrThrow()
        assertEquals(multiline, next.rowsOf("tiket").single()["catatan"])
    }

    /** C3 Irisan 2: kewajiban isi LONG_TEXT ditegakkan `Create` sama seperti tipe lain. */
    @Test
    fun create_requiredLongTextKosong_ditolak() {
        val wajib = FieldSpec("catatan", "Catatan", FieldType.LONG_TEXT, required = true)
        val specWajib = PrototypeSpec(listOf(EntitySpec("tiket", "Tiket", listOf(wajib))), emptyList())
        val result = PrototypeReducer.reduce(
            specWajib,
            PrototypeStore(mapOf("tiket" to emptyList())),
            PrototypeAction.Create("tiket", PrototypeRow("t2", mapOf("catatan" to "")))
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("wajib diisi"))
    }
}
