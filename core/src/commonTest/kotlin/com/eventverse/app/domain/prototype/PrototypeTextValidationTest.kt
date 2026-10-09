package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.handoff.SpecPostgresWriter
import com.eventverse.app.domain.discovery.handoff.SpecRoutesWriter
import com.eventverse.app.domain.discovery.handoff.SpecTable
import com.eventverse.app.domain.discovery.handoff.sqlDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A0(C9) Irisan 2 — `TEXT` + `validation`: aturan bentuk per [TextValidation] (iterasi `entries`), invarian tipe,
 * penyimpanan tetap `TEXT`, dan route hasil generate. Entitas uji = antrean bordir.
 */
class PrototypeTextValidationTest {

    /** `when` tanpa `else`: entri baru tanpa contoh = kompilasi gagal (Kontrak 6). */
    private fun valid(v: TextValidation): List<String> = when (v) {
        TextValidation.NONE -> listOf("apa saja", "a@b", "!!!")
        TextValidation.EMAIL -> listOf("contoh@contoh.id", "a.b+c@sub.domain.co.id", "x@y.z")
        TextValidation.PHONE -> listOf("+628123456789", "0812-3456-7890", "(021) 555 1234", "+62 812 3456 7890", "12345678", "123456789012345")
    }

    private fun invalid(v: TextValidation): List<String> = when (v) {
        TextValidation.NONE -> emptyList()
        TextValidation.EMAIL -> listOf(
            "tanpa-at", "@contoh.id", "a@", "a@@b.id", "a@b@c.id", "a b@contoh.id", "a@contoh", "a@.id", "a@contoh.", "a@contoh..id",
            " a@contoh.id", "a@contoh.id ", "a@" + "x".repeat(250) + ".id"
        )
        TextValidation.PHONE -> listOf(
            "abc", "1234567", "1234567890123456", "+", "++628123456789", "62+8123456789", "08123x56789", "+62 812 abc 7890",
            "٠٨١٢٣٤٥٦٧٨٩٠", "08.123.456.789"
        )
    }

    private fun field(v: TextValidation, type: FieldType = FieldType.TEXT) = FieldSpec("kontak", "Kontak", type, validation = v)
    private fun table(vararg f: FieldSpec) = SpecTable.of("bordir_uji", EntitySpec("pesanan_bordir", "Pesanan bordir", f.toList()))

    @Test
    fun everyTextValidation_acceptsItsValidExamples_rejectsItsInvalidOnes_andEmptyIsAlwaysValid() {
        TextValidation.entries.forEach { v ->
            val f = field(v)
            valid(v).forEach { assertTrue(TextValidations.isValid(v, it), "$v harus menerima '$it'"); assertTrue(f.accepts(it), "FieldSpec $v menerima '$it'") }
            invalid(v).forEach { assertFalse(TextValidations.isValid(v, it), "$v harus menolak '$it'"); assertFalse(f.accepts(it), "FieldSpec $v menolak '$it'") }
            assertTrue(f.accepts(""), "$v: kosong tetap sah (belum diisi)")
            assertTrue(f.accepts(TextValidations.sample(v)), "$v: sample() sah")
        }
    }

    @Test
    fun noneValidation_changesNothing_comparedToPlainText() {
        assertEquals(TextValidation.NONE, FieldSpec("k", "K", FieldType.TEXT).validation)
        assertTrue(FieldSpec("k", "K", FieldType.TEXT).accepts("bebas \n apa saja"))
    }

    @Test
    fun fieldSpec_validationOnNonText_isRejected_includingLongText() {
        FieldType.entries.filter { it != FieldType.TEXT }.forEach { type ->
            val options = if (type == FieldType.ENUM) listOf("a") else emptyList()
            TextValidation.entries.filter { it != TextValidation.NONE }.forEach { v ->
                val r = runCatching { FieldSpec("k", "K", type, options, validation = v) }
                assertTrue(r.isFailure, "$type dengan validation $v harus ditolak")
                assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("bukan TEXT"), r.exceptionOrNull()?.message)
            }
            assertTrue(runCatching {
                // C7: RELATION wajib bawa target — konstruksi sah-nya beda satu parameter itu saja.
                if (type == FieldType.RELATION) FieldSpec("k", "K", type, options, validation = TextValidation.NONE, target = "pesanan")
                else FieldSpec("k", "K", type, options, validation = TextValidation.NONE)
            }.isSuccess, "$type + NONE sah")
        }
    }

    @Test
    fun sql_everyValidation_staysPlainText_andExposedTextColumn() {
        TextValidation.entries.forEach { v ->
            val column = table(field(v)).columns.single()
            assertEquals("TEXT", column.sqlDefinition(), "$v: kolom tetap TEXT")
            val file = SpecPostgresWriter.tableFile(table(field(v)))
            assertTrue("val kontak = text(\"kontak\").nullable()" in file, "$v: Exposed tetap text()")
        }
        assertEquals("TEXT NOT NULL CHECK (btrim(kontak) <> '')", table(FieldSpec("kontak", "Kontak", FieldType.TEXT, required = true, validation = TextValidation.EMAIL)).columns.single().sqlDefinition())
        val repo = SpecPostgresWriter.repositoryFile(table(field(TextValidation.EMAIL)))
        assertTrue("it[T.kontak] = row[\"kontak\"].ifBlank { null }" in repo, "tulis apa adanya (tanpa normalisasi)")
    }

    @Test
    fun routes_validatedText_carriesLiteralAndTextProblemGate() {
        val t = table(field(TextValidation.EMAIL), FieldSpec("catatan", "Catatan", FieldType.TEXT))
        val routes = SpecRoutesWriter.routesFile("bordir_uji", t)
        assertTrue("FieldSpec(\"kontak\", \"Kontak\", FieldType.TEXT, listOf(), false, validation = TextValidation.EMAIL)" in routes, "entityLiteral membawa validation")
        assertTrue("TEXT_FIELDS = listOf<FieldSpec>(FieldSpec(\"kontak\", \"Kontak\", FieldType.TEXT, validation = TextValidation.EMAIL))" in routes, routes)
        assertFalse("\"catatan\", \"Catatan\", FieldType.TEXT, validation" in routes, "field tanpa validasi tidak masuk TEXT_FIELDS")
        assertTrue("private fun textProblem(" in routes && "dateProblem(values) ?: textProblem(values)" in routes)
        assertTrue("import com.eventverse.app.domain.prototype.TextValidation" in routes)
        val none = SpecRoutesWriter.routesFile("bordir_uji", table(FieldSpec("catatan", "Catatan", FieldType.TEXT)))
        assertTrue("TEXT_FIELDS = listOf<FieldSpec>()" in none)
    }

    @Test
    fun entityLiteral_noneValidation_isOmitted() {
        val literal = SpecRoutesWriter.entityLiteral(EntitySpec("e", "E", listOf(field(TextValidation.NONE))))
        assertFalse("validation" in literal, literal)
    }
}
