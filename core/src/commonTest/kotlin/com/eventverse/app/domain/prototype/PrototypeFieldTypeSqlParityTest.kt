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
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.FILE -> "TEXT"
        FieldType.NUMBER -> "NUMERIC(18,4)"
        FieldType.DATE -> "DATE"
        FieldType.TIME -> "TIME"
        FieldType.ENUM -> "VARCHAR(120)"
        FieldType.MULTI_SELECT -> "TEXT[]"
        FieldType.BOOL -> "BOOLEAN"
        FieldType.RELATION -> "VARCHAR(64)"
        FieldType.USER_REF -> "VARCHAR(120)"
    }

    private fun expectedExposed(type: FieldType): String = when (type) {
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.FILE -> "text("
        FieldType.NUMBER -> "decimal("
        FieldType.DATE -> "date("
        FieldType.TIME -> "time("
        FieldType.ENUM -> "varchar("
        FieldType.MULTI_SELECT -> "array<"
        FieldType.BOOL -> "bool("
        FieldType.RELATION -> "varchar("
        FieldType.USER_REF -> "varchar("
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
        assertTrue("LocalTime.parse" in repo, "TIME tulis (C6)")
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

    /** C6: TIME wajib `JJ:MM` 24-jam tepat menit — bentuk lain ditolak, tanpa koersi; kosong = belum diisi. */
    @Test
    fun accepts_timeField_acceptsOnlyWallClockMinuteShape() {
        val time = fieldFor(FieldType.TIME)
        assertTrue(time.accepts("00:00"), "tengah malam sah")
        assertTrue(time.accepts("23:59"), "tepi atas sah")
        assertTrue(time.accepts(TimeFieldValues.sample()), "contoh satu sumber sah")
        assertTrue(time.accepts(""), "kosong = belum diisi")
        listOf("9:30", "24:00", "09:60", "09:30:15", "0930", "09-30", "9:30 pagi", "ab:cd", " 9:30").forEach { broken ->
            assertFalse(time.accepts(broken), "'$broken' harus ditolak (tanpa fallback senyap)")
        }
    }

    /** C6: kolom TIME tidak pernah menerima detik/zona — bentuk simpan tepat menit, pola DATE/TIMESTAMP. */
    @Test
    fun sqlDefinition_timeColumn_mapsToPlainTime() {
        val sql = table(fieldFor(FieldType.TIME)).columns.single().sqlDefinition()
        assertEquals("TIME", sql, "TIME opsional tanpa NOT NULL/CHECK")
        val required = table(fieldFor(FieldType.TIME, required = true)).columns.single().sqlDefinition()
        assertEquals("TIME NOT NULL", required, "TIME wajib = TIME NOT NULL")
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

    /** C7 (TRD-FIELD-001 FR-1, kriteria terima #1): RELATION TIDAK PERNAH memancarkan `REFERENCES` —
     *  rujukan logis, pagar J3; integritas dijaga validasi saat tulis, bukan FK lintas schema. */
    @Test
    fun sqlDefinition_relationColumn_neverEmitsReferences() {
        val sql = table(fieldFor(FieldType.RELATION, required = true)).columns.single().sqlDefinition()
        assertTrue(sql.startsWith("VARCHAR(64)") && "NOT NULL" in sql, sql)
        assertFalse("REFERENCES" in sql.uppercase(), "RELATION tidak boleh punya FK fisik: $sql")
        val optional = table(fieldFor(FieldType.RELATION)).columns.single().sqlDefinition()
        assertFalse("REFERENCES" in optional.uppercase(), optional)
    }

    // ---- invarian konstruksi ----------------------------------------------------------------

    @Test
    fun fieldSpec_nonEnumTypeWithOptions_isRejected() {
        // A0 (TRD-FIELD-003): MULTI_SELECT juga sah beropsi; sisanya ditolak.
        FieldType.entries.filter { it != FieldType.ENUM && it != FieldType.MULTI_SELECT }.forEach { type ->
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
        val number = FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "IDR")
        assertEquals(NumberFormat.CURRENCY, number.format)
        assertEquals(NumberFormat.PLAIN, FieldSpec("x", "X", FieldType.NUMBER).format, "default PLAIN")
    }

    /** Keputusan mata uang per field: CURRENCY wajib berkode tiga huruf besar; format lain wajib tanpa kode. */
    @Test
    fun fieldSpec_currencyCode_isRequiredExactlyForCurrency() {
        listOf(null, "", "idr", "RP", "RUPIAH", "ID1").forEach { bad ->
            assertTrue(
                runCatching { FieldSpec("k", "K", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = bad) }.isFailure,
                "kode '$bad' harus ditolak, tidak diam-diam jadi IDR"
            )
        }
        assertEquals("USD", FieldSpec("k", "K", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "USD").currencyCode)
        NumberFormat.entries.filter { it != NumberFormat.CURRENCY }.forEach { format ->
            assertTrue(
                runCatching { FieldSpec("k", "K", FieldType.NUMBER, format = format, currencyCode = "IDR") }.isFailure,
                "$format tidak boleh membawa kode mata uang"
            )
        }
    }

    @Test
    fun fieldType_entries_matchSampleVocabularySize() {
        assertEquals(FieldType.entries, allFields().map { it.type })
    }

    // ---- C7 (TRD-FIELD-001 §4.3): kontrak target RELATION --------------------------------------

    /** `target` wajib TEPAT untuk RELATION: RELATION tanpa target ditolak, tipe lain dengan target ditolak. */
    @Test
    fun fieldSpec_target_isRequiredExactlyForRelation() {
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.RELATION) }.isFailure, "RELATION tanpa target ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.RELATION, target = "  ") }.isFailure, "target kosong ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.TEXT, target = "pesanan") }.isFailure, "TEXT dengan target ditolak")
        assertEquals("pesanan", FieldSpec("k", "K", FieldType.RELATION, target = "pesanan").target)
    }

    /** Bentuk target: satu string tanpa spasi, maksimum satu ':' (format "entityId" / "moduleId:entityId"). */
    @Test
    fun fieldSpec_relationTarget_shapeIsValidated() {
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.RELATION, target = "dua modul") }.isFailure, "spasi ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.RELATION, target = "a:b:c") }.isFailure, "dua ':' ditolak")
        // C7: bentuk = satu sumber aturan (`relationTargetFormatError`) — bagian kosong di sekitar ':' juga ditolak.
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.RELATION, target = "a:") }.isFailure, "sisi kanan kosong ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.RELATION, target = ":a") }.isFailure, "sisi kiri kosong ditolak")
        assertEquals("crm:lead", FieldSpec("k", "K", FieldType.RELATION, target = "crm:lead").target, "lintas modul sah")
    }

    /** accepts RELATION: kosong sah (belum diisi); id non-blank tanpa ".." sah; sisanya ditolak. */
    @Test
    fun accepts_relationField_acceptsTargetIdShapeOnly() {
        val f = fieldFor(FieldType.RELATION)
        assertTrue(f.accepts(""), "kosong = belum diisi")
        assertTrue(f.accepts("po-001"), "id target sah")
        assertTrue(f.accepts("crm:lead-9"), "id lintas modul sah")
        assertFalse(f.accepts("   "), "blank bukan id")
        assertFalse(f.accepts("../po"), "path traversal ditolak")
        assertFalse(f.accepts("po..001"), "..' ditolak di mana pun")
    }

    /** C8 (TRD-FIELD-002 FR-5): accepts FILE = bentuk FileRef; referensi rusak/buatan ditolak, kosong sah. */
    @Test
    fun accepts_fileField_acceptsOnlyValidFileRefShape() {
        val f = fieldFor(FieldType.FILE)
        assertTrue(f.accepts(""), "kosong = belum diisi")
        assertTrue(f.accepts("fields/ten/pesanan/r-1/scan-a1b2c3-scan.pdf"), "FileRef sah")
        assertFalse(f.accepts("fields/../rahasia"), "path traversal ditolak")
        assertFalse(f.accepts("/absolut/fields/scan.pdf"), "awalan absolut ditolak")
        assertFalse(f.accepts("scan.pdf"), "tanpa namespace fields/ ditolak")
        assertFalse(f.accepts("fields/ten/r-1/baris\nbaru.pdf"), "kontrol/baris baru ditolak")
    }

    // ---- A0 (penyatuan kosakata, PLAN-unify §3): parameter decimals NUMBER + tipe USER_REF ------

    /** decimals: hanya NUMBER; `null` atau `0..6` (0, 2, 6 sah; -1 dan 7 ditolak, bukan dibulatkan). */
    @Test
    fun fieldSpec_decimals_onlyForNumber_withinZeroToSix() {
        listOf(0, 2, 6).forEach { d ->
            assertEquals(d, FieldSpec("k", "K", FieldType.NUMBER, decimals = d).decimals, "decimals=$d sah")
        }
        listOf(-1, 7).forEach { d ->
            assertTrue(runCatching { FieldSpec("k", "K", FieldType.NUMBER, decimals = d) }.isFailure, "decimals=$d harus ditolak")
        }
        FieldType.entries.filter { it != FieldType.NUMBER }.forEach { type ->
            assertTrue(runCatching { FieldSpec("k", "K", type, decimals = 2) }.isFailure, "$type dengan decimals harus ditolak")
        }
    }

    /** decimals = petunjuk pembulatan masukan/tampilan, BUKAN skala kolom — kolom tetap NUMERIC(18,4). */
    @Test
    fun sqlDefinition_numberColumn_ignoresDecimals_staysNumeric184() {
        val whole = table(FieldSpec("tarif", "Tarif", FieldType.NUMBER, decimals = 0)).columns.single().sqlDefinition()
        assertEquals("NUMERIC(18,4)", whole, "decimals=0 tidak mengubah skala kolom")
        val required = table(FieldSpec("tarif", "Tarif", FieldType.NUMBER, decimals = 6, required = true)).columns.single().sqlDefinition()
        assertEquals("NUMERIC(18,4) NOT NULL", required, "decimals=6 wajib tetap NUMERIC(18,4)")
    }

    /** USER_REF (A0): id pengguna — non-blank tanpa ".."; kosong = belum diisi; bentuk lain ditolak. */
    @Test
    fun accepts_userRefField_acceptsSingleUserIdShape() {
        val f = fieldFor(FieldType.USER_REF)
        assertTrue(f.accepts(""), "kosong = belum diisi")
        assertTrue(f.accepts("usr-1"), "id pengguna sah")
        assertFalse(f.accepts("   "), "blank bukan id")
        assertFalse(f.accepts("../usr"), "path traversal ditolak")
        assertFalse(f.accepts("usr..1"), "..' ditolak di mana pun")
    }

    /** USER_REF (A0): kolom VARCHAR(120) TANPA `REFERENCES` — pola RELATION (rujukan logis, pagar J3). */
    @Test
    fun sqlDefinition_userRefColumn_plainVarchar120_neverEmitsReferences() {
        val sql = table(fieldFor(FieldType.USER_REF, required = true)).columns.single().sqlDefinition()
        assertTrue(sql.startsWith("VARCHAR(120)") && "NOT NULL" in sql, sql)
        assertFalse("REFERENCES" in sql.uppercase(), "USER_REF tidak boleh punya FK fisik: $sql")
        val optional = table(fieldFor(FieldType.USER_REF)).columns.single().sqlDefinition()
        assertFalse("REFERENCES" in optional.uppercase(), optional)
    }
}
