package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.handoff.SpecPostgresWriter
import com.eventverse.app.domain.discovery.handoff.SpecTable
import com.eventverse.app.domain.discovery.handoff.sqlDefinition
import com.eventverse.app.domain.discovery.proposal.DeterministicScreenProposer
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.discovery.proposal.applyEdits
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.shared.discovery.ScreenProposalCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import com.eventverse.app.shared.pack.SpecOpCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paritas varian angka (C4 Irisan 2, `NumberFormat`): tiap entri enum punya perilaku terdefinisi — kolom SQL identik
 * `NUMERIC(18,4)` tanpa kolom kode mata uang, round-trip di tiga kawat, penolakan nilai tak dikenal, suntingan lewat
 * `SetFieldFormat`, dan bawaan mata uang pack yang mengalir ke usulan deterministik. Pack uji non-garment (klinik, USD).
 */
class NumberFormatParityTest {

    private fun currencyFor(format: NumberFormat): String? = if (format == NumberFormat.CURRENCY) "USD" else null

    private fun numberField(format: NumberFormat, key: String = "tarif") =
        FieldSpec(key, "Tarif", FieldType.NUMBER, format = format, currencyCode = currencyFor(format))

    private fun entity(vararg fields: FieldSpec) = EntitySpec("pesanan_bordir", "Pesanan bordir", fields.toList())

    private fun screenWith(field: FieldSpec, value: String = "12.5") = InteractiveScreen(
        PrototypeSpec(
            listOf(entity(FieldSpec("nama", "Nama", FieldType.TEXT, required = true), field)),
            listOf(ScreenSpec("tabel", "Tabel", WidgetKind.TABLE, "pesanan_bordir", table = TableConfig(listOf("nama", field.key))))
        ),
        mapOf("pesanan_bordir" to listOf(PrototypeRow("r1", mapOf("nama" to "Seragam", field.key to value))))
    )

    // ---- penyimpanan: identik untuk semua format ----------------------------------------------

    @Test
    fun sqlColumn_everyFormat_isIdenticalNumeric_withoutCurrencyColumn() {
        val plain = SpecTable.of("bordir_uji", entity(numberField(NumberFormat.PLAIN)))
        val plainSql = plain.columns.single().sqlDefinition()
        NumberFormat.entries.forEach { format ->
            val table = SpecTable.of("bordir_uji", entity(numberField(format)))
            val column = table.columns.single() // tepat satu kolom: kode mata uang TIDAK jadi kolom
            assertEquals(plainSql, column.sqlDefinition(), "$format")
            assertTrue(column.sqlDefinition().startsWith("NUMERIC(18,4)"), "$format")
            val file = SpecPostgresWriter.tableFile(table)
            assertFalse("USD" in file, "kode mata uang tidak boleh bocor ke tabel Exposed: $format")
            assertTrue("decimal(" in file, "$format")
        }
    }

    @Test
    fun accepts_everyFormat_takesPlainNumbersAndRejectsText() {
        NumberFormat.entries.forEach { format ->
            val f = numberField(format)
            assertTrue(f.accepts("12.5") && f.accepts("0") && f.accepts(""), "$format")
            assertFalse(f.accepts("12,5%") || f.accepts("USD 5"), "$format menyimpan angka polos")
        }
    }

    // ---- kawat: round-trip & penolakan ---------------------------------------------------------

    @Test
    fun interactiveScreenCodec_everyFormat_roundTrips() {
        NumberFormat.entries.forEach { format ->
            val screen = screenWith(numberField(format))
            val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(screen).encode()))
            assertEquals(screen, decoded, "$format")
            assertEquals(format, decoded.spec.entities.single().field("tarif")!!.format)
        }
    }

    @Test
    fun specOpCodec_everyFormat_roundTrips_forAddFieldAndSetFieldFormat() {
        NumberFormat.entries.forEach { format ->
            val add = SpecOp.AddField("pesanan_bordir", numberField(format))
            val set = SpecOp.SetFieldFormat("pesanan_bordir", "tarif", format, currencyFor(format))
            listOf(add, set).forEach { op ->
                assertEquals(op, SpecOpCodec.decode(JsonParser.parseObject(SpecOpCodec.encode(op).encode())).getOrThrow(), "$format")
            }
        }
    }

    @Test
    fun specOpCodec_setFieldFormat_unknownMissingOrLowercaseFormat_isRejected() {
        listOf("RUPIAH", "currency", "").forEach { name ->
            val raw = """{"type":"SetFieldFormat","entityId":"e","field":"f","format":"$name"}"""
            val error = SpecOpCodec.decode(JsonParser.parseObject(raw)).exceptionOrNull()
            assertTrue(error?.message?.contains("bukan kosakata tertutup") == true, "'$name' ditolak: ${error?.message}")
        }
        // Berbeda dari AddField (dokumen lama tanpa format = PLAIN): mengubah format tanpa menyebutnya tidak bermakna.
        val missing = SpecOpCodec.decode(JsonParser.parseObject("""{"type":"SetFieldFormat","entityId":"e","field":"f"}"""))
        assertTrue(missing.isFailure)
    }

    @Test
    fun screenProposalCodec_everyFormat_roundTrips() = runTest {
        val pack = klinikPack()
        NumberFormat.entries.forEach { format ->
            val base = DeterministicScreenProposer.proposalsFor(pack, pack.modules.first { it.id.value.endsWith("_tagihan") }).getOrThrow().single()
            val field = FieldProposal("tarif", "Tarif", FieldType.NUMBER, format = format, currencyCode = currencyFor(format))
            val edited = base.applyEdits(listOf(ProposalEdit.AddField(field))).getOrThrow()
            val back = ScreenProposalCodec.decode(JsonParser.parseObject(ScreenProposalCodec.encode(edited).encode()), "$")
            assertEquals(edited, back, "$format")
        }
    }

    // ---- SetFieldFormat -----------------------------------------------------------------------

    @Test
    fun setFieldFormat_onNumberField_changesFormatKeepsRows_andCanRevert() {
        val screen = screenWith(numberField(NumberFormat.PLAIN))
        val currency = SpecOpApplier.apply(screen, SpecOp.SetFieldFormat("pesanan_bordir", "tarif", NumberFormat.CURRENCY, "EUR")).getOrThrow()
        val f = currency.spec.entity("pesanan_bordir")!!.field("tarif")!!
        assertEquals(NumberFormat.CURRENCY to "EUR", f.format to f.currencyCode)
        assertEquals(screen.seed, currency.seed, "isi baris tidak berubah")
        val percent = SpecOpApplier.apply(currency, SpecOp.SetFieldFormat("pesanan_bordir", "tarif", NumberFormat.PERCENT)).getOrThrow()
        assertEquals(null, percent.spec.entity("pesanan_bordir")!!.field("tarif")!!.currencyCode, "kode dilepas saat bukan CURRENCY")
        val plain = SpecOpApplier.apply(percent, SpecOp.SetFieldFormat("pesanan_bordir", "tarif", NumberFormat.PLAIN)).getOrThrow()
        assertEquals(screen, plain)
    }

    @Test
    fun setFieldFormat_invalidRequests_areRejectedWithMessage() {
        val screen = screenWith(numberField(NumberFormat.PLAIN))
        fun fails(op: SpecOp.SetFieldFormat) = SpecOpApplier.apply(screen, op).exceptionOrNull()?.message.orEmpty()
        assertTrue("hanya untuk field NUMBER" in fails(SpecOp.SetFieldFormat("pesanan_bordir", "nama", NumberFormat.CURRENCY, "IDR")))
        assertTrue("kode mata uang" in fails(SpecOp.SetFieldFormat("pesanan_bordir", "tarif", NumberFormat.CURRENCY)))
        assertTrue("kode mata uang" in fails(SpecOp.SetFieldFormat("pesanan_bordir", "tarif", NumberFormat.CURRENCY, "usd")))
        assertTrue("kode mata uang" in fails(SpecOp.SetFieldFormat("pesanan_bordir", "tarif", NumberFormat.PERCENT, "USD")))
        assertTrue("tidak ada" in fails(SpecOp.SetFieldFormat("pesanan_bordir", "hantu", NumberFormat.PERCENT)))
    }

    // ---- sunting usulan: baris contoh tetap sah -------------------------------------------------

    @Test
    fun proposalEdit_requiredCurrencyAndPercentFields_getNumericSeedAndPassValidator() = runTest {
        val pack = klinikPack()
        val base = DeterministicScreenProposer.proposalsFor(pack, pack.modules.first { it.id.value.endsWith("_tagihan") }).getOrThrow().single()
        listOf(NumberFormat.CURRENCY, NumberFormat.PERCENT).forEach { format ->
            val field = FieldProposal("diskon", "Diskon", FieldType.NUMBER, required = true, format = format, currencyCode = currencyFor(format))
            val out = base.applyEdits(listOf(ProposalEdit.AddField(field))).getOrThrow()
            assertEquals("0", out.seed.first()["diskon"], "$format")
            assertEquals(emptyList(), ScreenProposalValidator.validate(out, "$.proposal", null, null), "$format")
        }
        // Mengganti TEXT berisi teks → NUMBER berformat: nilai bukan angka diganti contoh karena wajib.
        val replaced = base.applyEdits(listOf(ProposalEdit.ReplaceField("catatan", FieldProposal("catatan", "Catatan", FieldType.NUMBER, required = true, format = NumberFormat.PERCENT)))).getOrThrow()
        assertTrue(replaced.seed.all { it["catatan"]?.toDoubleOrNull() != null })
    }

    // ---- bawaan mata uang pack ------------------------------------------------------------------

    private suspend fun klinikPack(): DomainPack =
        DeterministicDiscoveryAgent().draft(DiscoveryRequest("Kami klinik gigi: antrean per poli, tagihan pembayaran kasir.", industryHint = "klinik")).getOrThrow().pack

    private fun ScreenProposal.jumlah() = entity!!.fields.first { it.key == "jumlah" }

    @Test
    fun packDefault_idrByDefault_flowsToTagihanProposal() = runTest {
        val pack = klinikPack()
        assertEquals("IDR", pack.defaultCurrencyCode)
        val tagihan = DeterministicScreenProposer.proposalsForAll(pack).getOrThrow().first { it.moduleId.value.endsWith("_tagihan") }
        assertEquals(Triple(FieldType.NUMBER, NumberFormat.CURRENCY, "IDR"), tagihan.jumlah().let { Triple(it.type, it.format, it.currencyCode) })
        assertEquals(emptyList(), ScreenProposalValidator.validate(tagihan, "$.proposal", null, null))
    }

    @Test
    fun packDefault_usd_flowsToProposal_andOtherRolesStayPlain() = runTest {
        val pack = klinikPack().copy(defaultCurrencyCode = "USD")
        val all = DeterministicScreenProposer.proposalsForAll(pack).getOrThrow()
        val tagihan = all.first { it.moduleId.value.endsWith("_tagihan") }
        assertEquals("USD", tagihan.jumlah().currencyCode)
        val stok = all.firstOrNull { it.moduleId.value.endsWith("_stok") }
        stok?.let { assertEquals(NumberFormat.PLAIN, it.jumlah().format, "peran stok = jumlah barang, bukan uang") }
        all.forEach { assertEquals(emptyList(), ScreenProposalValidator.validate(it, "$.proposal", null, null)) }
    }

    @Test
    fun packDefault_malformedCode_failsWhenPackIsBuilt_notFallbackToIdr() = runTest {
        val pack = klinikPack()
        listOf("usd", "US", "USDX", "", "R P").forEach { bad ->
            val error = assertFailsWith<IllegalArgumentException>("'$bad'") { pack.copy(defaultCurrencyCode = bad) }
            assertTrue("defaultCurrencyCode" in (error.message ?: ""), error.message)
        }
    }

    @Test
    fun packCodec_defaultCurrency_roundTrips_omittedWhenIdr_andRejectsMalformed() = runTest {
        val pack = klinikPack()
        assertFalse("defaultCurrencyCode" in DomainPackCodec.encode(pack).entries, "IDR tidak ditulis: dokumen lama byte-per-byte sama")
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encode(pack)))
        val usd = pack.copy(defaultCurrencyCode = "USD")
        assertEquals(usd, DomainPackCodec.decode(DomainPackCodec.encodeToString(usd)))
        val tampered = JsonValue.Obj(DomainPackCodec.encode(usd).entries + ("defaultCurrencyCode" to JsonValue.Str("dolar")))
        val error = assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(tampered) }
        assertTrue("defaultCurrencyCode" in (error.message ?: ""), error.message)
    }
}
