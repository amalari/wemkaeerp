package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.KanbanHints
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveKanbanTest {

    private fun createRichKanbanScreen() = requireNotNull(
        InteractiveScreenFactory.kanban(
            screenId = "screen-sample-kanban",
            title = "Alur Sampling",
            rows = listOf(
                mapOf(
                    "Kolom" to "Draft",
                    "Judul" to "Sample Kemeja",
                    "Deskripsi" to "Bahan katun combed",
                    "Tipe" to "FOB",
                    "Target" to "2026-10-20",
                    "Qty" to "10",
                    "Mendesak" to "ya"
                ),
                mapOf(
                    "Kolom" to "Draft",
                    "Judul" to "Sample Celana",
                    "Deskripsi" to "Bahan twill",
                    "Tipe" to "CMT",
                    "Target" to "2026-10-25",
                    "Qty" to "5",
                    "Mendesak" to "tidak"
                ),
                mapOf(
                    "Kolom" to "Selesai",
                    "Judul" to "Sample Jaket",
                    "Deskripsi" to "Bahan parasut",
                    "Tipe" to "FOB",
                    "Target" to "2026-10-10",
                    "Qty" to "2",
                    "Mendesak" to "tidak"
                )
            ),
            hints = KanbanHints(
                groupField = "Kolom",
                columns = listOf("Draft", "Proses", "Selesai"),
                groupLabel = "Status",
                transitions = mapOf("Draft" to setOf("Proses"), "Proses" to setOf("Selesai")),
                card = listOf(
                    CardElement("Judul", CardStyle.TITLE),
                    CardElement("Deskripsi", CardStyle.TEXT),
                    CardElement("Tipe", CardStyle.BADGE),
                    CardElement("Target", CardStyle.DATE),
                    CardElement("Qty", CardStyle.NUMBER),
                    CardElement("Mendesak", CardStyle.FLAG)
                ),
                columnMeta = mapOf(
                    "Draft" to ColumnMeta(tintHex = 0xFF2563EB, wipLimit = 1),
                    "Proses" to ColumnMeta(tintHex = 0xFFEA580C, wipLimit = 5),
                    "Selesai" to ColumnMeta(tintHex = 0xFF16A34A)
                ),
                detailForm = FormConfig(
                    fields = listOf("Judul", "Deskripsi", "Tipe", "Target", "Qty", "Mendesak"),
                    submitLabel = "Simpan Perubahan"
                ),
                fields = listOf(
                    FieldHint("Judul", FieldType.TEXT, required = true),
                    FieldHint("Deskripsi", FieldType.TEXT),
                    FieldHint("Tipe", FieldType.ENUM, options = listOf("FOB", "CMT")),
                    FieldHint("Target", FieldType.DATE),
                    FieldHint("Qty", FieldType.NUMBER),
                    FieldHint("Mendesak", FieldType.BOOL)
                )
            )
        )
    )

    @Test
    fun testRichKanbanConfigAndTransitions() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val screen = createRichKanbanScreen()
        val state = InteractiveKanbanState(screen = screen, scope = testScope)

        assertEquals(3, state.config.columns.size)
        assertEquals(6, state.config.card.size)
        assertEquals(3, state.config.columnMeta.size)
        assertNotNull(state.config.detailForm)

        // Verifikasi WIP limit di kolom Draft (ada 2 kartu, limit 1 -> melebihi limit)
        val draftCards = state.cards("Draft")
        assertEquals(2, draftCards.size)
        val draftMeta = state.config.columnMeta["Draft"]
        assertNotNull(draftMeta)
        assertEquals(1, draftMeta.wipLimit)
        assertTrue(draftCards.size > draftMeta.wipLimit!!)

        // Verifikasi aturan transisi mesin status:
        // Dari "Draft", hanya boleh ke "Proses" (bukan langsung "Selesai")
        val card1 = draftCards.first()
        val targets = state.targetsFor(card1)
        assertEquals(listOf("Proses"), targets)

        // Coba pindah ke target yang dilarang ("Selesai")
        state.move(card1.id, "Selesai")
        testScope.advanceUntilIdle()

        // Harus ditolak dengan pesan galat
        assertNotNull(state.message)
        assertEquals(2, state.cards("Draft").size)
        assertEquals(1, state.cards("Selesai").size)

        // Pindah ke target yang sah ("Proses")
        state.move(card1.id, "Proses")
        testScope.advanceUntilIdle()

        assertNull(state.message)
        assertEquals(1, state.cards("Draft").size)
        assertEquals(1, state.cards("Proses").size)
    }

    @Test
    fun testCardDetailSelectionAndEdit() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val screen = createRichKanbanScreen()
        val state = InteractiveKanbanState(screen = screen, scope = testScope)

        val card = state.cards("Draft").first()
        assertNull(state.selectedCardForDetail)

        // Pilih kartu untuk detail
        state.selectedCardForDetail = card
        assertEquals(card.id, state.selectedCardForDetail?.id)

        // Perbarui field kartu
        state.updateRow(card.id, mapOf("Deskripsi" to "Deskripsi Diperbarui"))
        testScope.advanceUntilIdle()

        val updated = state.cards("Draft").first { it.id == card.id }
        assertEquals("Deskripsi Diperbarui", updated["Deskripsi"])

        // Tutup detail
        state.selectedCardForDetail = null
        assertNull(state.selectedCardForDetail)
    }
}
