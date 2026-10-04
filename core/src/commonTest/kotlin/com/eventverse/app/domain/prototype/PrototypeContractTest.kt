package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.brief.BriefCoverage
import com.eventverse.app.domain.discovery.brief.BriefRenderer
import com.eventverse.app.domain.discovery.brief.RequirementsBrief
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.BriefCodec
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import com.eventverse.app.shared.pack.SpecOpCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Kontrak v1 (B0) — yang dikunci di sini adalah **bentuk** yang A dan C andalkan: spec berformulir,
 * `required`, `Delete`, kodek `SpecOp`/`CaptureEntry`, batas operasi per giliran, dan keluaran brief
 * yang deterministik. Fixture non-garment (`PrototypeContractSamples`), sesuai Kontrak 6.
 */
class PrototypeContractTest {
    private val screen = PrototypeContractSamples.ticketScreen
    private val spec = screen.spec
    private val store = screen.newStore()

    // ---- spec & codec ----------------------------------------------------------------------

    @Test
    fun samples_areValid_andFormSharesTheKanbanEntity() {
        assertEquals(2, store.rowsOf("tiket").size)
        val ids = spec.screens.map { it.entityId }.toSet()
        assertEquals(setOf("tiket"), ids, "form dan papan terikat ke entitas yang sama")
    }

    @Test
    fun codec_roundTrip_preservesFormAndRequired() {
        val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(screen).encode()))
        assertEquals(screen, decoded)
        assertTrue(decoded.spec.entity("tiket")!!.field("Judul")!!.required)
    }

    @Test
    fun codec_legacySpecWithoutFormOrRequired_stillDecodes() {
        val legacy = """{"entities":[{"id":"item","label":"Papan","fields":[
            {"key":"Kolom","label":"Kolom","type":"ENUM","options":["A","B"]},{"key":"Judul","label":"Judul","type":"TEXT","options":[]}],
            "stateMachine":null}],
          "screens":[{"screenId":"s","title":"Papan","widget":"KANBAN","entityId":"item",
            "kanban":{"groupField":"Kolom","columns":["A","B"],"titleField":"Judul","detailFields":[]},"table":null}],
          "seed":{"item":[{"id":"s-1","values":{"Kolom":"A","Judul":"x"}}]}}"""
        val decoded = InteractiveScreenCodec.decode(JsonParser.parseObject(legacy))
        assertTrue(decoded.spec.entity("item")!!.fields.none { it.required })
        assertEquals(null, decoded.spec.screens.single().form)
    }

    @Test
    fun screenSpec_formWidgetWithoutFormConfig_isRejected() {
        assertFailsWith<IllegalArgumentException> { ScreenSpec("f", "F", WidgetKind.FORM, "tiket") }
        assertFailsWith<IllegalArgumentException> { ScreenSpec("k", "K", WidgetKind.TABLE, "tiket", form = FormConfig(listOf("Judul"))) }
    }

    @Test
    fun spec_formFieldOutsideEntity_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            PrototypeSpec(listOf(PrototypeContractSamples.ticketEntity), listOf(ScreenSpec("f", "F", WidgetKind.FORM, "tiket", form = FormConfig(listOf("Hantu")))))
        }
    }

    // ---- reducer: required + Delete --------------------------------------------------------

    @Test
    fun create_missingRequiredField_isRejectedWithUserMessage() {
        val result = PrototypeReducer.reduce(spec, store, PrototypeAction.Create("tiket", PrototypeRow("t-9", mapOf("Peminta" to "Rina"))))
        assertEquals("'Judul' wajib diisi", result.exceptionOrNull()?.message)
        assertEquals(2, store.rowsOf("tiket").size)
    }

    @Test
    fun setField_blankingRequiredField_isRejected() {
        val result = PrototypeReducer.reduce(spec, store, PrototypeAction.SetField("tiket", "t-1", "Judul", ""))
        assertTrue(result.isFailure)
        assertEquals("'Judul' wajib diisi", result.exceptionOrNull()?.message)
    }

    @Test
    fun create_withRequiredField_appearsInStore() {
        val next = PrototypeReducer.reduce(spec, store, PrototypeAction.Create("tiket", PrototypeRow("t-3", mapOf("Judul" to "Lampu mati", "Status" to "Baru")))).getOrThrow()
        assertEquals(3, next.rowsOf("tiket").size)
    }

    @Test
    fun delete_removesOnlyThatRow_andUnknownRowOrEntityFailsWithMessage() {
        val next = PrototypeReducer.reduce(spec, store, PrototypeAction.Delete("tiket", "t-1")).getOrThrow()
        assertEquals(listOf("t-2"), next.rowsOf("tiket").map { it.id })
        assertTrue(PrototypeReducer.reduce(spec, store, PrototypeAction.Delete("tiket", "hantu")).exceptionOrNull()!!.message!!.contains("tidak ada"))
        assertTrue(PrototypeReducer.reduce(spec, store, PrototypeAction.Delete("hantu", "t-1")).isFailure)
    }

    // ---- SpecOp & CaptureEntry codec -------------------------------------------------------

    @Test
    fun specOpCodec_roundTrip_allFiveOperationTypes() {
        assertEquals(5, PrototypeContractSamples.sampleOps.map { it::class }.toSet().size, "sampel harus mencakup kelima jenis")
        PrototypeContractSamples.sampleOps.forEach { op ->
            assertEquals(op, SpecOpCodec.decode(JsonParser.parseObject(SpecOpCodec.encode(op).encode())).getOrThrow())
        }
    }

    @Test
    fun specOpCodec_unknownTypeOrMissingField_failsWithMessage_notGuess() {
        val unknown = SpecOpCodec.decode(JsonParser.parseObject("""{"type":"HapusSemua","entityId":"tiket"}"""))
        assertEquals("Jenis operasi 'HapusSemua' tidak dikenal.", unknown.exceptionOrNull()?.message)
        val missing = SpecOpCodec.decode(JsonParser.parseObject("""{"type":"AddEnumOption","entityId":"tiket","field":"Status"}"""))
        assertEquals("Bidang 'option' wajib diisi.", missing.exceptionOrNull()?.message)
    }

    @Test
    fun captureEntry_roundTrip_includingRejectedEntryWithMessage() {
        PrototypeContractSamples.sampleLog.forEach { e ->
            assertEquals(e, SpecOpCodec.decodeEntry(JsonParser.parseObject(SpecOpCodec.encode(e).encode())).getOrThrow())
        }
        assertTrue(SpecOpCodec.decodeEntry(JsonParser.parseObject("""{"op":{"type":"AddTransition","entityId":"a","field":"b","from":"c","to":"d"}}""")).isFailure, "waktu wajib")
    }

    // ---- applyAll: batas & log (berlaku sejak B0, tidak berubah di B3) ----------------------

    @Test
    fun applyAll_logsEveryOp_inOrder_andLimitsToFivePerTurn() {
        val ops = PrototypeContractSamples.sampleOps + listOf(
            SpecOp.RenameFieldLabel("tiket", "Judul", "Subjek"), SpecOp.RenameFieldLabel("tiket", "Judul", "Perihal")
        )
        val applied = SpecOpApplier.applyAll(screen, ops, "2026-10-04T10:00:00Z")
        assertEquals(ops, applied.log.map { it.op }, "urutan operasi terjaga")
        applied.log.drop(SpecOpApplier.MAX_OPS_PER_TURN).forEach {
            assertTrue(!it.ok && it.message!!.contains("Dibatasi ${SpecOpApplier.MAX_OPS_PER_TURN}"))
        }
        assertTrue(applied.log.all { it.at == "2026-10-04T10:00:00Z" })
    }

    // ---- proposer (kerangka) ---------------------------------------------------------------

    @Test
    fun deterministicProposer_unrecognisedSentence_failsWithExample_notGuess() = runTest {
        val result = DeterministicSpecOpProposer().propose("buatkan aplikasi kasir", screen)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Contoh:"))
    }

    // ---- brief -----------------------------------------------------------------------------

    private val brief = RequirementsBrief(
        packCode = "layanan",
        modules = emptyList(),
        changes = PrototypeContractSamples.sampleLog,
        coverage = listOf(BriefCoverage("tiket", "Tiket", covered = false, monthlyIdr = null, gapLowIdr = 100_000, gapHighIdr = 250_000)),
        customNeeds = listOf("Integrasi mesin absensi (CUSTOM_EXTENSION)")
    )

    @Test
    fun briefRenderer_isDeterministic_andKeepsAllFiveSections() {
        val md = BriefRenderer.markdown(brief)
        assertEquals(md, BriefRenderer.markdown(brief))
        listOf("# Brief Kebutuhan — layanan", "## Modul & layar", "## Perubahan dari klien", "## Cakupan katalog", "## Kebutuhan kustom")
            .forEach { assertTrue(it in md, "bagian hilang: $it") }
        assertTrue("ditolak (Status 'Hantu' tidak ada.)" in md)
    }

    @Test
    fun briefCodec_encode_carriesChangesCoverageAndCustomNeeds() {
        val json = BriefCodec.encode(brief)
        assertEquals(2, json.objectArray("changes").size)
        assertEquals(250_000L, json.objectArray("coverage").single().long("gapHighIdr"))
        assertEquals(listOf("Integrasi mesin absensi (CUSTOM_EXTENSION)"), json.stringArray("customNeeds"))
    }
}
