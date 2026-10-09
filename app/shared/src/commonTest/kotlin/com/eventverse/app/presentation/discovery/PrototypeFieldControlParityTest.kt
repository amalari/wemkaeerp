package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.TableHints
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Paritas kontrol UI untuk kosakata prototype: tiap [FieldType] (termasuk [FieldType.LONG_TEXT] dari Irisan 2)
 * terintegrasi di state formulir ([InteractiveFormState]) dan tabel ([InteractiveTableState]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrototypeFieldControlParityTest {

    @Test
    fun allFieldTypes_handledInFormResetAndTableInline() {
        val allFields = listOf(
            FieldSpec("f_text", "Nama", FieldType.TEXT),
            FieldSpec("f_long", "Catatan", FieldType.LONG_TEXT),
            FieldSpec("f_num", "Jumlah", FieldType.NUMBER),
            FieldSpec("f_date", "Tanggal", FieldType.DATE),
            FieldSpec("f_enum", "Status", FieldType.ENUM, options = listOf("Draft", "Rilis")),
            FieldSpec("f_multi", "Label Ganda", FieldType.MULTI_SELECT, options = listOf("A", "B", "C")),
            FieldSpec("f_bool", "Aktif", FieldType.BOOL),
            FieldSpec("f_rel", "Rujukan", FieldType.RELATION, target = "pesanan"),
            FieldSpec("f_file", "Lampiran", FieldType.FILE)
        )

        // Verifikasi semua entri FieldType tercakup dalam daftar uji
        val testedTypes = allFields.map { it.type }.toSet()
        assertEquals(FieldType.entries.toSet(), testedTypes, "Semua FieldType wajib diuji kontrolnya")

        // 1. Form State resetForm menghasilkan nilai awal terdefinisi
        val entity = EntitySpec("item", "Uji Form", allFields)
        val formSpec = PrototypeSpec(
            listOf(entity),
            listOf(
                ScreenSpec(
                    "scr-form-parity", "Uji Form", WidgetKind.FORM, "item",
                    form = FormConfig(allFields.map { it.key }, "Simpan")
                )
            )
        )
        val formScreen = InteractiveScreen(formSpec, emptyMap())
        val formState = InteractiveFormState(formScreen)
        assertEquals("", formState.formValues["f_text"])
        assertEquals("", formState.formValues["f_long"], "LONG_TEXT harus memiliki nilai awal string kosong")
        assertEquals("", formState.formValues["f_num"])
        assertEquals("", formState.formValues["f_date"])
        assertEquals("Draft", formState.formValues["f_enum"])
        assertEquals("", formState.formValues["f_multi"], "MULTI_SELECT harus memiliki nilai awal kosong (belum ada pilihan)")
        assertEquals("tidak", formState.formValues["f_bool"])
        assertEquals("", formState.formValues["f_rel"], "RELATION harus memiliki nilai awal string kosong")
        assertEquals("", formState.formValues["f_file"], "FILE harus memiliki nilai awal string kosong")

        // 2. Table State startInlineCreate menghasilkan nilai inline terdefinisi
        val tableHints = TableHints(
            statusColumn = "f_enum",
            options = listOf("Draft", "Rilis"),
            fields = allFields.filter { it.key != "f_enum" }.map { FieldHint(it.key, it.type, options = it.options, target = it.target) },
            inlineCreate = true
        )
        val sampleRow = mapOf(
            "f_text" to "Teks",
            "f_long" to "Catatan",
            "f_num" to "10",
            "f_date" to "2026-10-08",
            "f_enum" to "Draft",
            "f_multi" to """["A","C"]""",
            "f_bool" to "ya",
            "f_rel" to "pesanan-1",
            "f_file" to "fields/ten/item/i-1/lampiran-a1b2c3-scan.pdf"
        )
        val tableScreen = requireNotNull(
            InteractiveScreenFactory.table("scr-table-parity", "Uji Tabel", listOf(sampleRow), tableHints)
        )
        val tableState = InteractiveTableState(tableScreen)
        tableState.startInlineCreate()
        assertTrue(tableState.isCreatingInline)
        assertEquals("", tableState.inlineValues["f_text"])
        assertEquals("", tableState.inlineValues["f_long"], "LONG_TEXT inline harus kosong")
        assertEquals("Draft", tableState.inlineValues["f_enum"])
        assertEquals("", tableState.inlineValues["f_multi"], "MULTI_SELECT inline harus kosong")
        assertEquals("tidak", tableState.inlineValues["f_bool"])
        assertEquals("", tableState.inlineValues["f_rel"], "RELATION inline harus kosong")
        assertEquals("", tableState.inlineValues["f_file"], "FILE inline harus kosong")
    }

    @Test
    fun longTextField_acceptsMultilineContentInForm() {
        val allFields = listOf(
            FieldSpec("judul", "Judul", FieldType.TEXT, required = true),
            FieldSpec("deskripsi", "Deskripsi", FieldType.LONG_TEXT, required = false)
        )
        val entity = EntitySpec("item", "Form Multiline", allFields)
        val formSpec = PrototypeSpec(
            listOf(entity),
            listOf(
                ScreenSpec(
                    "scr-multiline", "Form Multiline", WidgetKind.FORM, "item",
                    form = FormConfig(listOf("judul", "deskripsi"), "Kirim")
                )
            )
        )
        val formScreen = InteractiveScreen(formSpec, emptyMap())

        var createdRow: PrototypeRow? = null
        val formState = InteractiveFormState(formScreen) { _, row ->
            createdRow = row
        }

        val multilineText = "Baris pertama catatan\nBaris kedua catatan spesifikasi\nBaris ketiga penutup."
        formState.setFieldValue("judul", "Fitur Baru")
        formState.setFieldValue("deskripsi", multilineText)

        val success = formState.submit()
        assertTrue(success, "Submit form dengan LONG_TEXT multiline harus berhasil")
        assertNotNull(createdRow)
        assertEquals(multilineText, createdRow?.get("deskripsi"))
    }

    @Test
    fun tableState_withLongTextCellEditing() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val hints = TableHints(
            statusColumn = "status",
            options = listOf("Aktif"),
            fields = listOf(
                FieldHint("kode", FieldType.TEXT, required = true),
                FieldHint("catatan", FieldType.LONG_TEXT, required = false)
            ),
            editableFields = listOf("catatan")
        )
        val rows = listOf(
            mapOf("kode" to "K-01", "status" to "Aktif", "catatan" to "Catatan singkat")
        )
        val screen = requireNotNull(
            InteractiveScreenFactory.table("scr-table-edit", "Tabel Edit", rows, hints)
        )
        val state = InteractiveTableState(screen = screen, scope = testScope)

        assertTrue(state.isCellEditable("catatan"))
        state.startCellEdit("scr-table-edit-1", "catatan", "Catatan lama")
        assertEquals("Catatan lama", state.editingValue)

        val updatedMultiline = "Catatan revisi\nTambahan catatan"
        state.editingValue = updatedMultiline
        state.submitCellEdit("scr-table-edit-1", "catatan")
        testScope.advanceUntilIdle()

        // Cell edit selesai
        assertEquals(null, state.editingCell)
    }
}
