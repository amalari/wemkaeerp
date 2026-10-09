package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.handoff.SpecPostgresWriter
import com.eventverse.app.domain.discovery.handoff.SpecRoutesWriter
import com.eventverse.app.domain.discovery.handoff.SpecTable
import com.eventverse.app.domain.discovery.handoff.sqlDefinition
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.PrototypeFieldTypeSampleFields.fieldFor
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.strictOptInt
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A0 (TRD-FIELD-003) — nilai & kolom tipe [FieldType.MULTI_SELECT]:
 * aturan kanonik [MultiSelectValues], invarian [FieldSpec], SQL `TEXT[]` + CHECK, penulis Exposed,
 * dan penolakan `statusField` (FR-5, aturan `checkStatus` yang sudah ada).
 */
class PrototypeMultiSelectTest {

    private val options = listOf("Digitizing", "Hooping", "Selesai")

    private fun table(vararg fields: FieldSpec) = SpecTable.of("bordir_uji", EntitySpec("pesanan_bordir", "Pesanan bordir", fields.toList()))

    // ---- MultiSelectValues.isValid ------------------------------------------------------------

    @Test
    fun isValid_emptyString_isValid_butEmptyArrayIsRejected() {
        assertTrue(MultiSelectValues.isValid("", options, null), "belum diisi selalu sah")
        assertFalse(MultiSelectValues.isValid("[]", options, null), "'[]' ditolak (satu bentuk kosong)")
    }

    @Test
    fun isValid_rejectsOutOfOptionsDuplicateAndExceedingMax() {
        assertFalse(MultiSelectValues.isValid("[\"Cuci\"]", options, null), "elemen di luar options ditolak")
        assertFalse(MultiSelectValues.isValid("[\"Digitizing\",\"Digitizing\"]", options, null), "duplikat ditolak")
        assertFalse(MultiSelectValues.isValid("[\"Digitizing\",\"Hooping\"]", options, 1), "melebihi maxSelections ditolak")
        assertTrue(MultiSelectValues.isValid("[\"Digitizing\",\"Hooping\"]", options, 2), "tepat batas sah")
        assertTrue(MultiSelectValues.isValid("[\"Digitizing\"]", options, null), "tanpa batas sah")
    }

    @Test
    fun isValid_rejectsNonJsonArrayOfStrings() {
        listOf("banyak", "Digitizing", "[Digitizing]", "[1,2]", "{\"a\":\"b\"}", "[\"a\"", "\"a\"").forEach { raw ->
            assertFalse(MultiSelectValues.isValid(raw, options, null), "'$raw' bukan array JSON string sah")
        }
    }

    @Test
    fun parse_returnsNullWhenNotAStringArray() {
        assertNull(MultiSelectValues.parse(""))
        assertNull(MultiSelectValues.parse("[1]"), "angka bukan string")
        assertNull(MultiSelectValues.parse("nope"))
        assertEquals(listOf("a", "b"), MultiSelectValues.parse("[\"a\",\"b\"]"))
        assertEquals(emptyList(), MultiSelectValues.parse("[]"))
    }

    // ---- MultiSelectValues.encode (kanonik, byte-stabil) ---------------------------------------

    @Test
    fun encode_ordersByOptions_dropsDuplicatesAndUnknowns() {
        assertEquals("[\"Digitizing\",\"Hooping\"]", MultiSelectValues.encode(listOf("Hooping", "Digitizing"), options), "urut menurut options, bukan urutan klik")
        assertEquals("[\"Digitizing\"]", MultiSelectValues.encode(listOf("Digitizing", "Digitizing", "Cuci"), options), "duplikat & tak dikenal dibuang")
        assertEquals("", MultiSelectValues.encode(emptyList(), options), "pilihan kosong = '' (bukan '[]')")
    }

    @Test
    fun encode_thenParse_roundTripsToCanonicalString() {
        val raw = "[\"Hooping\",\"Digitizing\"]"
        val parsed = requireNotNull(MultiSelectValues.parse(raw))
        val canonical = MultiSelectValues.encode(parsed, options)
        assertEquals("[\"Digitizing\",\"Hooping\"]", canonical)
        // Kanonik: parse ulang lalu encode lagi harus byte-identik (stabil).
        assertEquals(canonical, MultiSelectValues.encode(requireNotNull(MultiSelectValues.parse(canonical)), options))
    }

    // ---- FieldSpec invariant ------------------------------------------------------------------

    @Test
    fun fieldSpec_multiSelect_requiresOptionsAndHonoursMaxRange() {
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.MULTI_SELECT) }.isFailure, "MULTI_SELECT tanpa options ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "a")) }.isFailure, "options kembar ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "b"), maxSelections = 0) }.isFailure, "maxSelections < 1 ditolak")
        assertTrue(runCatching { FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "b"), maxSelections = 3) }.isFailure, "maxSelections > options ditolak")
        assertEquals(1, FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "b"), maxSelections = 1).maxSelections)
        assertNull(FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "b")).maxSelections)
    }

    @Test
    fun fieldSpec_maxSelections_onOtherType_isRejected() {
        FieldType.entries.filter { it != FieldType.MULTI_SELECT }.forEach { type ->
            val opts = if (type == FieldType.ENUM) listOf("a", "b") else emptyList()
            val result = runCatching { FieldSpec("k", "K", type, opts, maxSelections = 1) }
            assertTrue(result.isFailure, "$type dengan maxSelections harus ditolak")
        }
    }

    @Test
    fun accepts_multiSelect_matchesMultiSelectValues() {
        val f = FieldSpec("k", "K", FieldType.MULTI_SELECT, options, maxSelections = 2)
        assertTrue(f.accepts(""), "kosong sah")
        assertTrue(f.accepts("[\"Digitizing\",\"Hooping\"]"))
        assertFalse(f.accepts("[]"))
        assertFalse(f.accepts("[\"Cuci\"]"), "di luar options")
        assertFalse(f.accepts("[\"Digitizing\",\"Digitizing\"]"), "duplikat")
        assertFalse(f.accepts("[\"Digitizing\",\"Hooping\",\"Selesai\"]"), "melebihi maxSelections")
    }

    // ---- SQL ----------------------------------------------------------------------------------

    @Test
    fun sqlDefinition_multiSelect_emitsTextArrayWithChecks() {
        val sql = table(fieldFor(FieldType.MULTI_SELECT, required = true)).columns.single().sqlDefinition()
        assertTrue(sql.startsWith("TEXT[]"), sql)
        assertTrue("NOT NULL" in sql, sql)
        assertTrue("<@ ARRAY['Digitizing', 'Hooping', 'Selesai']::text[]" in sql, sql)
        assertTrue("cardinality(layanan_dibeli) > 0" in sql, "required menambah cardinality > 0: $sql")
        assertTrue("cardinality(layanan_dibeli) <= 2" in sql, "maxSelections menambah batas atas: $sql")
    }

    @Test
    fun sqlDefinition_optionalMultiSelect_hasNoNotNullAndNoCardinalityMin() {
        val sql = table(FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "b"))).columns.single().sqlDefinition()
        assertTrue(sql.startsWith("TEXT[]"), sql)
        assertFalse("NOT NULL" in sql, sql)
        assertFalse("> 0" in sql, sql)
        assertFalse("<=" in sql, "tanpa maxSelections tak ada batas atas: $sql")
    }

    // ---- Exposed writer -----------------------------------------------------------------------

    @Test
    fun exposedWriter_multiSelect_usesArrayColumnAndCanonicalisers() {
        val t = table(fieldFor(FieldType.MULTI_SELECT, required = true))
        val tableFile = SpecPostgresWriter.tableFile(t)
        assertTrue("array<String>(\"layanan_dibeli\")" in tableFile, tableFile)
        val repo = SpecPostgresWriter.repositoryFile(t)
        assertTrue("MultiSelectValues.parse" in repo, "tulis = parse array")
        assertTrue("MultiSelectValues.encode" in repo, "baca = encode kanonik")
        assertTrue("import com.eventverse.app.domain.prototype.MultiSelectValues" in repo, repo)
    }

    @Test
    fun exposedWriter_optionalMultiSelect_isNullable() {
        val t = table(FieldSpec("k", "K", FieldType.MULTI_SELECT, listOf("a", "b")))
        val tableFile = SpecPostgresWriter.tableFile(t)
        assertTrue("array<String>(\"k\").nullable()" in tableFile, tableFile)
    }

    // ---- Route scaffold -----------------------------------------------------------------------

    @Test
    fun routes_multiSelect_carriesFieldListAndProblemCheckInPostAndPut() {
        val routes = SpecRoutesWriter.routesFile("bordir_uji", table(fieldFor(FieldType.MULTI_SELECT, required = true)))
        assertTrue(
            "private val MULTI_FIELDS = listOf<FieldSpec>(FieldSpec(\"layanan_dibeli\", \"Layanan dibeli\", FieldType.MULTI_SELECT, listOf(\"Digitizing\", \"Hooping\", \"Selesai\"), true, maxSelections = 2))" in routes,
            "MULTI_FIELDS dengan maxSelections harus ikut tercetak"
        )
        assertTrue("private fun multiProblem" in routes, "fungsi multiProblem harus ada")
        assertEquals(2, Regex("multiProblem\\(values\\)").findAll(routes).count(), "multiProblem dipanggil di POST dan PUT")
        assertTrue("FieldType.MULTI_SELECT" in routes, "entityLiteral membawa tipe MULTI_SELECT")
    }

    // ---- kawat ketat: maxSelections harus bilangan bulat ---------------------------------------

    @Test
    fun codec_strictMaxSelections_rejectsFractionalAndWrongType() {
        val spec = PrototypeSpec(
            listOf(EntitySpec("e", "E", listOf(fieldFor(FieldType.MULTI_SELECT)))),
            listOf(ScreenSpec("t", "T", WidgetKind.TABLE, "e", table = TableConfig(listOf("layanan_dibeli"))))
        )
        val encoded = InteractiveScreenCodec.encode(InteractiveScreen(spec, emptyMap())).encode()
        assertTrue("\"maxSelections\":2" in encoded, encoded)
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(encoded) as JsonValue.Obj) }.isSuccess, "2 sah")
        // Bilangan pecahan/overflow/string = tipe salah: DITOLAK, bukan dipotong diam-diam jadi 2.
        val fractional = encoded.replace("\"maxSelections\":2", "\"maxSelections\":2.5")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(fractional) as JsonValue.Obj) }.isFailure, "maxSelections 2.5 harus ditolak")
        val stringly = encoded.replace("\"maxSelections\":2", "\"maxSelections\":\"2\"")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(stringly) as JsonValue.Obj) }.isFailure, "maxSelections string harus ditolak")
    }

    @Test
    fun strictOptInt_acceptsIntegersOnly() {
        assertEquals(2, JsonParser.parseObject("{\"m\":2}").strictOptInt("m"))
        assertNull(JsonParser.parseObject("{}").strictOptInt("m"))
        assertNull(JsonParser.parseObject("{\"m\":null}").strictOptInt("m"))
        listOf("{\"m\":2.5}", "{\"m\":2.0}", "{\"m\":\"2\"}", "{\"m\":99999999999}").forEach { raw ->
            assertTrue(runCatching { JsonParser.parseObject(raw).strictOptInt("m") }.isFailure, "'$raw' harus ditolak")
        }
    }

    // ---- FR-5: MULTI_SELECT bukan statusField --------------------------------------------------

    @Test
    fun proposalValidator_multiSelectAsStatusField_isRejected() {
        val multi = FieldProposal("layanan", "Layanan", FieldType.MULTI_SELECT, options = listOf("a", "b"), maxSelections = 1)
        val entity = EntityProposal("e", "E", listOf(FieldProposal("judul", "Judul", FieldType.TEXT), multi), statusField = "layanan")
        val proposal = ScreenProposal(
            "s", ModuleId("m"), "T", WidgetKind.TABLE, "alasan jelas",
            entity, ViewProposal.Table(listOf("judul"), inlineCreate = false, editableFields = listOf("judul"))
        )
        val issues = ScreenProposalValidator.validate(proposal, "$.proposal")
        assertTrue(
            issues.any { it.path == "$.proposal.entity.statusField" && "ENUM" in it.message },
            "MULTI_SELECT sebagai statusField harus ditolak dengan pesan ENUM: ${issues.map { it.path + ": " + it.message }}"
        )
    }

    @Test
    fun proposalValidator_rejectsMaxSelectionsOnNonMultiAndOutOfRange() {
        val textWithMax = EntityProposal("e", "E", listOf(FieldProposal("judul", "Judul", FieldType.TEXT, maxSelections = 1)))
        val p1 = ScreenProposal("s", ModuleId("m"), "T", WidgetKind.FORM, "alasan jelas", textWithMax, ViewProposal.Form(listOf("judul"), "Simpan"))
        assertTrue(ScreenProposalValidator.validate(p1, "$.proposal").any { it.path.endsWith(".maxSelections") }, "maxSelections pada TEXT harus ditolak")

        val multiOut = EntityProposal("e", "E", listOf(FieldProposal("layanan", "Layanan", FieldType.MULTI_SELECT, options = listOf("a", "b"), maxSelections = 5)))
        val p2 = ScreenProposal("s", ModuleId("m"), "T", WidgetKind.FORM, "alasan jelas", multiOut, ViewProposal.Form(listOf("layanan"), "Simpan"))
        assertTrue(ScreenProposalValidator.validate(p2, "$.proposal").any { it.path.endsWith(".maxSelections") }, "maxSelections di luar 1..options harus ditolak")
    }
}
