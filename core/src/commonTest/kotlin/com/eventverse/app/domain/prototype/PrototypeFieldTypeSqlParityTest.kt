package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.handoff.SpecPostgresWriter
import com.eventverse.app.domain.discovery.handoff.SpecTable
import com.eventverse.app.domain.discovery.handoff.sqlDefinition
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.prototype.PrototypeFieldTypeSampleFields.allFields
import com.eventverse.app.domain.prototype.PrototypeFieldTypeSampleFields.fieldFor
import com.eventverse.app.domain.prototype.PrototypeFieldTypeSampleFields.validValue
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paritas kosakata **prototype** (`enum FieldType`): tiap entri wajib punya pemetaan SQL, penulis Exposed,
 * dan aturan nilai (Kontrak 7). Entitas uji = antrean bordir (non-garment-default, Kontrak 6 variability).
 */
class PrototypeFieldTypeSqlParityTest {

    private fun table(vararg fields: FieldSpec) = SpecTable.of("bordir_uji", EntitySpec("pesanan_bordir", "Pesanan bordir", fields.toList()))

    /** Awalan SQL yang diharapkan; `when` tanpa `else` = pagar kompilator. */
    private fun expectedSql(type: FieldType): String = when (type) {
        FieldType.TEXT, FieldType.LONG_TEXT -> "TEXT"
        FieldType.NUMBER -> "NUMERIC(18,4)"
        FieldType.DATE -> "DATE"
        FieldType.ENUM -> "VARCHAR(120)"
        FieldType.BOOL -> "BOOLEAN"
    }

    private fun expectedExposed(type: FieldType): String = when (type) {
        FieldType.TEXT, FieldType.LONG_TEXT -> "text("
        FieldType.NUMBER -> "decimal("
        FieldType.DATE -> "date("
        FieldType.ENUM -> "varchar("
        FieldType.BOOL -> "bool("
    }

    @Test
    fun sqlDefinition_everyFieldType_mapsToExpectedColumnType() {
        FieldType.entries.forEach { type ->
            val column = table(fieldFor(type)).columns.single()
            assertTrue(column.sqlDefinition().startsWith(expectedSql(type)), "$type -> ${column.sqlDefinition()}")
        }
    }

    @Test
    fun sqlDefinition_requiredEnum_carriesNotNullAndCheckOverOptions() {
        val sql = table(fieldFor(FieldType.ENUM, required = true)).columns.single().sqlDefinition()
        assertTrue("NOT NULL" in sql && "IN ('Digitizing', 'Hooping', 'Selesai')" in sql, sql)
    }

    @Test
    fun sqlDefinition_requiredFieldsOfEveryType_neverLoseNotNullExceptBoolDefault() {
        FieldType.entries.forEach { type ->
            val sql = table(fieldFor(type, required = true)).columns.single().sqlDefinition()
            assertTrue("NOT NULL" in sql, "$type wajib harus NOT NULL: $sql")
        }
    }

    @Test
    fun exposedTable_everyFieldType_hasWriterColumn() {
        val file = SpecPostgresWriter.tableFile(table(*allFields().toTypedArray()))
        FieldType.entries.forEach { type ->
            assertTrue(expectedExposed(type) in file, "$type tak punya kolom Exposed ${expectedExposed(type)}")
        }
    }

    @Test
    fun repository_everyFieldType_hasWriteAndReadExpression() {
        val repo = SpecPostgresWriter.repositoryFile(table(*allFields().toTypedArray()))
        assertTrue("LocalDate.parse" in repo, "DATE tulis")
        assertTrue("toBigDecimal" in repo, "NUMBER tulis")
        assertTrue("== \"ya\"" in repo, "BOOL tulis")
        assertTrue("stripTrailingZeros" in repo, "NUMBER baca")
    }

    @Test
    fun scaffold_specWithEveryFieldType_emitsAllColumnsInMigration() {
        val module = ModuleDefinition(
            ModuleId("bordir_uji"), "Uji", "Modul uji", ModuleSectionCode("UTAMA"), ModuleKind.OPERATIONAL, "clipboard",
            ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), slot = SlotCode("uji_slot")
        )
        val fields = allFields()
        val spec = PrototypeSpec(
            listOf(EntitySpec("pesanan_bordir", "Pesanan bordir", fields)),
            listOf(ScreenSpec("s", "S", WidgetKind.TABLE, "pesanan_bordir", table = TableConfig(fields.map { it.key })))
        )
        val sql = HandoffScaffoldGenerator().generateFromSpec(spec, module, 91, "com.example.Pack.pack").files.single { it.path.endsWith("_module.sql") }.content
        FieldType.entries.forEach { type ->
            val column = SpecTable.of("bordir_uji", spec.entities.single()).columns.single { it.field.type == type }
            assertTrue(column.sqlDefinition() in sql, "$type: kolom '${column.name}' tak ada di migrasi")
        }
    }

    // ---- aturan nilai per tipe (FieldSpec.accepts) --------------------------------------------

    @Test
    fun accepts_everyFieldType_acceptsItsValidValueAndEmpty() {
        FieldType.entries.forEach { type ->
            val f = fieldFor(type)
            assertTrue(f.accepts(validValue(type)), "$type menolak nilai sah '${validValue(type)}'")
            assertTrue(f.accepts(""), "$type menolak kosong")
        }
    }

    @Test
    fun accepts_typedFields_rejectValuesOfTheWrongShape() {
        assertFalse(fieldFor(FieldType.NUMBER).accepts("banyak"))
        assertFalse(fieldFor(FieldType.BOOL).accepts("mungkin"))
        assertFalse(fieldFor(FieldType.ENUM).accepts("Tidak Ada"))
    }

    /** DATE wajib tanggal kalender ISO — sama dengan `ProposalEntityRules`, bukan teks bebas. */
    @Test
    fun accepts_dateField_rejectsNonIsoText() {
        val date = fieldFor(FieldType.DATE)
        assertFalse(date.accepts("besok pagi"))
        assertFalse(date.accepts("2026-13-40"))
        assertFalse(date.accepts("08/10/2026"))
        assertTrue(date.accepts("2026-10-08"))
    }

    /** C3 Irisan 2: LONG_TEXT sebebas TEXT — multibaris dan isi panjang sah, kosong tetap sah. */
    @Test
    fun accepts_longTextField_allowsMultilineAndLongContent() {
        val f = fieldFor(FieldType.LONG_TEXT)
        assertTrue(f.accepts("Lapis 1\nLapis 2\nLapis 3"), "multibaris sah")
        assertTrue(f.accepts("instruksi ".repeat(400)), "isi panjang sah")
        assertTrue(f.accepts(""), "kosong tetap sah (belum diisi)")
    }

    /** C3 Irisan 2: kolom LONG_TEXT = TEXT (tanda tangan simpan A0); required tetap NOT NULL + trim. */
    @Test
    fun sqlDefinition_requiredLongText_keepsNotNullAndTrimCheck() {
        val sql = table(fieldFor(FieldType.LONG_TEXT, required = true)).columns.single().sqlDefinition()
        assertTrue("NOT NULL" in sql && "btrim" in sql, sql)
    }

    // ---- invarian konstruksi ----------------------------------------------------------------

    @Test
    fun fieldSpec_nonEnumTypeWithOptions_isRejected() {
        FieldType.entries.filter { it != FieldType.ENUM }.forEach { type ->
            val result = runCatching { FieldSpec("k", "K", type, listOf("a")) }
            assertTrue(result.isFailure, "$type dengan opsi harus ditolak")
        }
    }

    /** C4 Irisan 2 (D3): format adalah varian NUMBER — ditolak pada tipe lain, sah pada NUMBER. */
    @Test
    fun fieldSpec_formatOnNonNumber_isRejected_andOnNumberIsAccepted() {
        FieldType.entries.filter { it != FieldType.NUMBER }.forEach { type ->
            val result = runCatching { FieldSpec("k", "K", type, format = NumberFormat.CURRENCY) }
            assertTrue(result.isFailure, "$type dengan format harus ditolak")
        }
        val number = FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.CURRENCY)
        assertEquals(NumberFormat.CURRENCY, number.format)
        assertEquals(NumberFormat.PLAIN, FieldSpec("x", "X", FieldType.NUMBER).format, "default PLAIN")
    }

    @Test
    fun fieldType_entries_matchSampleVocabularySize() {
        assertEquals(FieldType.entries, allFields().map { it.type })
    }
}
