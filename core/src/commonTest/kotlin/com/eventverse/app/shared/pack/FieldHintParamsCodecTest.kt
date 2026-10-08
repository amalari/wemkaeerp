package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.PackSuggestionMapping
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.discovery.proposal.applyEdits
import com.eventverse.app.domain.discovery.proposal.toInteractiveScreen
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.DateFieldValues
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.TableHints
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parameter field yang dideklarasikan **pack** (`FieldHint`: format, currencyCode, withTime, validation; Irisan 2 Track A)
 * dan pembacaan **ketat** `format`/`currencyCode` di kawat (D4: tipe JSON salah ditolak, bukan jatuh ke bawaan).
 * Fixture non-garment: pack klinik gigi dan pesanan bordir.
 */
class FieldHintParamsCodecTest {

    private val moduleId = ModuleId("klinik_pendaftaran")

    private val hints = listOf(
        FieldHint("status", FieldType.ENUM, options = listOf("Baru", "Selesai")),
        FieldHint("surel", FieldType.TEXT, validation = TextValidation.EMAIL),
        FieldHint("telepon", FieldType.TEXT, validation = TextValidation.PHONE),
        FieldHint("jadwal", FieldType.DATE, withTime = true),
        FieldHint("lahir", FieldType.DATE),
        FieldHint("tarif", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "IDR"),
        FieldHint("diskon", FieldType.NUMBER, format = NumberFormat.PERCENT)
    )

    private val sampleRow = mapOf(
        "status" to "Baru", "surel" to "pasien@klinik.id", "telepon" to "+628123456789",
        "jadwal" to "2026-10-08T14:30", "lahir" to "1990-05-17", "tarif" to "150000", "diskon" to "12.5"
    )

    private fun suggestion(fields: List<FieldHint> = hints, rows: List<Map<String, String>> = listOf(sampleRow)) = ScreenSuggestion(
        moduleId, "Pendaftaran pasien", WidgetKind.TABLE, rows,
        tableHints = TableHints("status", listOf("Baru", "Selesai"), fields = fields), rationale = "Pasien didaftarkan lebih dulu."
    )

    private fun roundTrip(s: List<ScreenSuggestion>) = ScreenSuggestionCodec.decode(JsonParser.parse(ScreenSuggestionCodec.encode(s).encode()))

    // ---- FieldHint ---------------------------------------------------------------------------------

    @Test
    fun fieldHint_invariants_matchFieldSpec_rejectedWhenPackIsBuilt() {
        fun fails(block: () -> FieldHint) = assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()
        assertTrue("withTime" in fails { FieldHint("k", FieldType.TEXT, withTime = true) })
        assertTrue("validation" in fails { FieldHint("k", FieldType.LONG_TEXT, validation = TextValidation.EMAIL) })
        assertTrue("validation" in fails { FieldHint("k", FieldType.DATE, validation = TextValidation.PHONE) })
        assertTrue("format" in fails { FieldHint("k", FieldType.TEXT, format = NumberFormat.PERCENT) })
        assertTrue("mata uang" in fails { FieldHint("k", FieldType.NUMBER, format = NumberFormat.CURRENCY) })
        assertTrue("mata uang" in fails { FieldHint("k", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "rupiah") })
        assertTrue("mata uang" in fails { FieldHint("k", FieldType.NUMBER, currencyCode = "IDR") })
    }

    @Test
    fun fieldHint_toFieldSpec_carriesEveryParameter_forEveryValidationAndDateShape() {
        TextValidation.entries.forEach { v ->
            assertEquals(v, FieldHint("k", FieldType.TEXT, validation = v).toFieldSpec().validation, "$v")
        }
        listOf(true, false).forEach { t -> assertEquals(t, FieldHint("k", FieldType.DATE, withTime = t).toFieldSpec().withTime) }
        val money = hints.first { it.key == "tarif" }.toFieldSpec()
        assertEquals(NumberFormat.CURRENCY to "IDR", money.format to money.currencyCode)
    }

    // ---- kawat usulan layar ---------------------------------------------------------------------

    @Test
    fun screenSuggestionCodec_roundTripsEveryParameter() {
        val back = roundTrip(listOf(suggestion()))
        assertEquals(listOf(suggestion()), back)
        assertEquals(hints, back.single().tableHints!!.fields)
    }

    @Test
    fun screenSuggestionCodec_defaults_areOmitted_soOldDocumentsStayByteIdentical() {
        val plain = suggestion(listOf(FieldHint("status", FieldType.ENUM, options = listOf("Baru", "Selesai")), FieldHint("nama", FieldType.TEXT)), listOf(mapOf("status" to "Baru", "nama" to "Budi")))
        val json = ScreenSuggestionCodec.encode(listOf(plain)).encode()
        listOf("\"format\"", "\"currencyCode\"", "\"withTime\"", "\"validation\"").forEach { assertFalse(it in json, "$it tidak boleh ditulis bila bawaan") }
        assertEquals(listOf(plain), ScreenSuggestionCodec.decode(JsonParser.parse(json)))
    }

    @Test
    fun screenSuggestionCodec_unknownOrWrongTypedValues_areRejectedWithPath_neverDefaulted() {
        val json = ScreenSuggestionCodec.encode(listOf(suggestion())).encode()
        fun decodeError(from: String, to: String): String {
            assertTrue(from in json, "fixture memuat $from")
            return assertFailsWith<DomainPackDecodeException>("$to harus ditolak") {
                ScreenSuggestionCodec.decode(JsonParser.parse(json.replace(from, to)))
            }.message.orEmpty()
        }
        listOf("\"email\"", "\"URL\"", "\"\"", "5", "true").forEach { bad ->
            assertTrue(decodeError("\"validation\":\"EMAIL\"", "\"validation\":$bad").let { "validation" in it || "Validasi" in it }, bad)
        }
        listOf("\"rupiah\"", "\"currency\"", "5", "[]").forEach { bad ->
            assertTrue(decodeError("\"format\":\"CURRENCY\"", "\"format\":$bad").let { "format" in it || "Format" in it }, bad)
        }
        listOf("\"true\"", "1", "\"ya\"").forEach { bad ->
            assertTrue("withTime" in decodeError("\"withTime\":true", "\"withTime\":$bad"), bad)
        }
        assertTrue("currencyCode" in decodeError("\"currencyCode\":\"IDR\"", "\"currencyCode\":5"))
        // bentuk salah tapi bertipe benar: invarian FieldHint menolaknya saat decode
        assertTrue("withTime" in decodeError("{\"key\":\"surel\"", "{\"withTime\":true,\"key\":\"surel\""))
    }

    // ---- hint pack -> usulan -> layar ---------------------------------------------------------------

    @Test
    fun packSuggestion_carriesParameters_intoProposalEntity_andValidatorAcceptsTheSeed() {
        val proposal = PackSuggestionMapping.map(suggestion()).single()
        val fields = proposal.entity!!.fields.associateBy { it.key }
        assertEquals(TextValidation.EMAIL, fields.getValue("surel").validation)
        assertEquals(TextValidation.PHONE, fields.getValue("telepon").validation)
        assertTrue(fields.getValue("jadwal").withTime && !fields.getValue("lahir").withTime)
        assertEquals(NumberFormat.CURRENCY to "IDR", fields.getValue("tarif").let { it.format to it.currencyCode })
        assertEquals(emptyList(), ScreenProposalValidator.validate(proposal, "$.proposal", null, null))
        val spec = proposal.toInteractiveScreen().getOrThrow().spec.entities.single()
        assertEquals(TextValidation.EMAIL, spec.field("surel")!!.validation)
        assertTrue(spec.field("jadwal")!!.withTime)
    }

    @Test
    fun packSuggestion_seedViolatingDeclaredParameter_isRejected_notCoerced() {
        val bad = sampleRow + ("surel" to "bukan-email")
        val rejected = PackSuggestionMapping.map(suggestion(rows = listOf(bad))).single()
        assertTrue(ScreenProposalValidator.validate(rejected, "$.proposal", null, null).isNotEmpty(), "validator menolak seed tak sah")
        val dateOnly = sampleRow + ("jadwal" to "2026-10-08")
        val wrongShape = PackSuggestionMapping.map(suggestion(rows = listOf(dateOnly))).single()
        assertTrue(ScreenProposalValidator.validate(wrongShape, "$.proposal", null, null).isNotEmpty(), "tanggal-saja pada field withTime ditolak")
    }

    @Test
    fun interactiveScreenFactory_table_usesHintParameters_andFallsBackToStaticWhenSeedIncoherent() {
        val rows = listOf(sampleRow)
        val screen = InteractiveScreenFactory.table("t", "Pendaftaran", rows, TableHints("status", listOf("Baru", "Selesai"), fields = hints))
        val entity = screen!!.spec.entities.single()
        assertEquals(TextValidation.PHONE, entity.field("telepon")!!.validation)
        assertTrue(entity.field("jadwal")!!.withTime)
        val incoherent = InteractiveScreenFactory.table("t", "P", listOf(sampleRow + ("telepon" to "abc")), TableHints("status", listOf("Baru", "Selesai"), fields = hints))
        assertEquals(null, incoherent, "seed tak lolos parameter -> gambar statis, bukan diabaikan")
    }

    // ---- ProposalEdit: seed tetap sah setelah tambah/ganti field berparameter ----------------------------

    @Test
    fun proposalEdit_withTimeAndValidation_keepSeedValid_forRequiredAndOptional() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        listOf(true, false).forEach { time ->
            val field = FieldProposal("jadwal", "Jadwal", FieldType.DATE, required = true, withTime = time)
            val out = base.applyEdits(listOf(ProposalEdit.AddField(field))).getOrThrow()
            assertTrue(out.seed.all { DateFieldValues.isValid(it.getValue("jadwal"), time) }, "withTime=$time")
            assertEquals(emptyList(), ScreenProposalValidator.validate(out, "$.proposal", null, null))
        }
        // mengganti DATE tanggal-saja -> withTime: nilai lama (bukan bentuk baru) opsional dibuang, wajib diganti contoh
        val seeded = base.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("jadwal", "Jadwal", FieldType.DATE)))).getOrThrow()
            .let { it.copy(seed = it.seed.map { r -> r + ("jadwal" to "2026-10-08") }) }
        val optional = seeded.applyEdits(listOf(ProposalEdit.ReplaceField("jadwal", FieldProposal("jadwal", "Jadwal", FieldType.DATE, withTime = true)))).getOrThrow()
        assertTrue(optional.seed.all { "jadwal" !in it })
        val required = seeded.applyEdits(listOf(ProposalEdit.ReplaceField("jadwal", FieldProposal("jadwal", "Jadwal", FieldType.DATE, required = true, withTime = true)))).getOrThrow()
        assertTrue(required.seed.all { it["jadwal"] == DateFieldValues.sample(true) })
    }

    // ---- D4: format & currencyCode ketat di InteractiveScreenCodec dan SpecOpCodec ---------------------------

    private val specOpAdd = """{"type":"AddField","entityId":"e","field":{"key":"k","label":"K","fieldType":"NUMBER","format":"CURRENCY","currencyCode":"IDR"}}"""

    @Test
    fun specOpCodec_formatAndCurrency_wrongJsonType_isRejected_notDefaultedToPlain() {
        fun error(raw: String) = SpecOpCodec.decode(JsonParser.parse(raw) as JsonValue.Obj).exceptionOrNull()?.message.orEmpty()
        assertTrue("format" in error(specOpAdd.replace("\"format\":\"CURRENCY\"", "\"format\":5")))
        assertTrue("format" in error(specOpAdd.replace("\"format\":\"CURRENCY\"", "\"format\":true")))
        assertTrue("currencyCode" in error(specOpAdd.replace("\"currencyCode\":\"IDR\"", "\"currencyCode\":5")))
        assertTrue("format" in error("""{"type":"SetFieldFormat","entityId":"e","field":"f","format":5}"""))
        assertTrue("currencyCode" in error("""{"type":"SetFieldFormat","entityId":"e","field":"f","format":"CURRENCY","currencyCode":["IDR"]}"""))
        assertTrue(SpecOpCodec.decode(JsonParser.parse(specOpAdd) as JsonValue.Obj).isSuccess, "kontrol: dokumen sah tetap terbaca")
        // kunci absen / null tetap bawaan (dokumen lama)
        val old = """{"type":"AddField","entityId":"e","field":{"key":"k","label":"K","fieldType":"NUMBER","format":null,"currencyCode":null}}"""
        assertTrue(SpecOpCodec.decode(JsonParser.parse(old) as JsonValue.Obj).isSuccess)
    }

    @Test
    fun interactiveScreenCodec_formatAndCurrency_wrongJsonType_isRejected() {
        val screen = ScreenProposalFixtures.tabelTagihan().toInteractiveScreen().getOrThrow()
        val withMoney = screen.copy(spec = screen.spec.copy(entities = screen.spec.entities.map { e ->
            e.copy(fields = e.fields + com.eventverse.app.domain.prototype.FieldSpec("tarif", "Tarif", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "IDR"))
        }))
        val json = InteractiveScreenCodec.encode(withMoney).encode()
        assertEquals(withMoney, InteractiveScreenCodec.decode(JsonParser.parse(json) as JsonValue.Obj), "kontrol round-trip")
        listOf("5", "true", "[]").forEach { bad ->
            assertFailsWith<IllegalArgumentException>("format=$bad") {
                InteractiveScreenCodec.decode(JsonParser.parse(json.replace("\"format\":\"CURRENCY\"", "\"format\":$bad")) as JsonValue.Obj)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            InteractiveScreenCodec.decode(JsonParser.parse(json.replace("\"currencyCode\":\"IDR\"", "\"currencyCode\":5")) as JsonValue.Obj)
        }
    }
}
