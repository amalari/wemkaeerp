package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InMemoryBlockDataPort
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.TableHints
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveTableTest {

    private fun createTestScreen(
        inlineCreate: Boolean = false,
        editableFields: List<String> = emptyList()
    ): InteractiveScreen {
        val hints = TableHints(
            statusColumn = "Status",
            options = listOf("Tersedia", "Habis"),
            inlineCreate = inlineCreate,
            editableFields = editableFields,
            fields = listOf(
                FieldHint("No", FieldType.TEXT, required = true),
                FieldHint("Nama", FieldType.TEXT, required = true),
                FieldHint("Jumlah", FieldType.NUMBER, required = false),
                FieldHint("Status", FieldType.ENUM, options = listOf("Tersedia", "Habis"))
            )
        )
        val rows = listOf(
            mapOf("No" to "M-01", "Nama" to "Katun", "Jumlah" to "50", "Status" to "Tersedia"),
            mapOf("No" to "M-02", "Nama" to "Linen", "Jumlah" to "20", "Status" to "Habis")
        )
        return requireNotNull(
            InteractiveScreenFactory.table("screen-mat-table", "Daftar Material", rows, hints)
        )
    }

    @Test
    fun testInlineCreateValidationAndSubmission() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val screen = createTestScreen(inlineCreate = true)
        val state = InteractiveTableState(screen = screen, scope = testScope)

        assertEquals(2, state.rows.size)
        assertFalse(state.isCreatingInline)

        // Buka form inline
        state.startInlineCreate()
        assertTrue(state.isCreatingInline)

        // Submit kosong -> field wajib (No, Nama) kosong -> validasi gagal
        state.submitInlineCreate()
        testScope.advanceUntilIdle()

        assertTrue(state.isCreatingInline, "Form inline harus tetap terbuka jika validasi gagal")
        assertNotNull(state.inlineErrorMessage, "Pesan galat validasi harus ada")

        // Isi field wajib
        state.setInlineValue("No", "M-03")
        state.setInlineValue("Nama", "Sutra")
        state.setInlineValue("Jumlah", "15")

        state.submitInlineCreate()
        testScope.advanceUntilIdle()

        assertFalse(state.isCreatingInline, "Form inline harus tertutup setelah sukses simpan")
        assertEquals(3, state.rows.size)
        val addedRow = state.rows.last()
        assertEquals("M-03", addedRow["No"])
        assertEquals("Sutra", addedRow["Nama"])
        assertEquals("15", addedRow["Jumlah"])
    }

    @Test
    fun testTableCellEditing() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val screen = createTestScreen(editableFields = listOf("Nama", "Jumlah"))
        val state = InteractiveTableState(screen = screen, scope = testScope)

        assertTrue(state.isCellEditable("Nama"))
        assertTrue(state.isCellEditable("Jumlah"))
        assertFalse(state.isCellEditable("Status"), "Kolom status tidak boleh diedit via sel biasa")
        assertFalse(state.isCellEditable("No"), "Kolom No tidak terdaftar di editableFields")

        val targetRow = state.rows.first()
        val targetId = targetRow.id

        // Mulai edit sel
        state.startCellEdit(targetId, "Nama", targetRow["Nama"])
        assertEquals(targetId to "Nama", state.editingCell)
        assertEquals("Katun", state.editingValue)

        // Ubah nilai dan simpan
        state.editingValue = "Katun Combed 30s"
        state.submitCellEdit(targetId, "Nama")
        testScope.advanceUntilIdle()

        assertEquals(null, state.editingCell, "Mode edit sel harus selesai setelah submit")
        val updatedRow = state.rows.first { it.id == targetId }
        assertEquals("Katun Combed 30s", updatedRow["Nama"])
    }

    @Test
    fun testCancelInlineCreateAndCancelCellEdit() {
        val screen = createTestScreen(inlineCreate = true, editableFields = listOf("Nama"))
        val state = InteractiveTableState(screen)

        state.startInlineCreate()
        assertTrue(state.isCreatingInline)
        state.cancelInlineCreate()
        assertFalse(state.isCreatingInline)

        val targetId = state.rows.first().id
        state.startCellEdit(targetId, "Nama", "Katun")
        assertEquals(targetId to "Nama", state.editingCell)
        state.cancelCellEdit()
        assertEquals(null, state.editingCell)
    }
}
