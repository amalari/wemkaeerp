package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.draft
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.screenOf
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.PrototypeFieldTypeSampleFields.allFields
import com.eventverse.app.domain.prototype.PrototypeFieldTypeSampleFields.validValue
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.SpecOp
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TableHints
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Paritas codec kosakata **prototype**: tiap [FieldType] round-trip di keempat kawat (layar interaktif, `SpecOp`,
 * usulan pack `ScreenSuggestion`, draf usulan layar), dan nilai tipe tak dikenal **ditolak**, tidak jatuh ke `TEXT`
 * (Kontrak 4 variability, field-component-rules Kontrak 8). Fixture non-garment (bordir, klinik).
 */
class PrototypeFieldTypeCodecParityTest {

    /** Nama tipe yang bukan kosakata: mata uang (parameter, bukan tipe), tipe yang masih ditunda
     *  (plan C4-C8), salah huruf, dan kosong. `LONG_TEXT` lulusan plan C3 (Irisan 2), `RELATION`
     *  lulusan C7 (TRD-FIELD-001), dan `FILE` lulusan C8 (TRD-FIELD-002) — keduanya kini anggota
     *  kosakata dan diuji round-trip-nya, bukan lagi di daftar penolakan ini. */
    private val unknownNames = listOf("CURRENCY", "MULTI_SELECT", "text", "Text", "UANG", "")

    // ---- InteractiveScreenCodec ----------------------------------------------------------------

    private fun interactive(): InteractiveScreen {
        val fields = allFields()
        val row = PrototypeRow("r1", fields.associate { it.key to validValue(it.type) })
        return InteractiveScreen(
            PrototypeSpec(
                listOf(EntitySpec("pesanan_bordir", "Pesanan bordir", fields)),
                listOf(ScreenSpec("tabel", "Tabel", WidgetKind.TABLE, "pesanan_bordir", table = TableConfig(fields.map { it.key })))
            ),
            mapOf("pesanan_bordir" to listOf(row))
        )
    }

    @Test
    fun interactiveScreenCodec_everyFieldType_roundTrips() {
        val screen = interactive()
        assertEquals(screen, InteractiveScreenCodec.decode(JsonParser.parseObject(InteractiveScreenCodec.encode(screen).encode())))
        val decodedTypes = InteractiveScreenCodec.decode(InteractiveScreenCodec.encode(screen)).spec.entities.single().fields.map { it.type }
        assertEquals(FieldType.entries, decodedTypes)
    }

    @Test
    fun interactiveScreenCodec_unknownTypeName_isRejectedNotText() {
        val raw = InteractiveScreenCodec.encode(interactive()).encode()
        unknownNames.forEach { name ->
            val tampered = raw.replace("\"type\":\"TEXT\"", "\"type\":\"$name\"")
            val error = assertFailsWith<IllegalArgumentException>("'$name' harus ditolak") {
                InteractiveScreenCodec.decode(JsonParser.parseObject(tampered))
            }
            assertTrue("tak dikenal" in (error.message ?: ""), error.message)
        }
    }

    @Test
    fun interactiveScreenCodec_missingTypeKey_isRejectedNotText() {
        val raw = InteractiveScreenCodec.encode(interactive()).encode().replace("\"type\":\"TEXT\",", "")
        assertFailsWith<IllegalArgumentException> { InteractiveScreenCodec.decode(JsonParser.parseObject(raw)) }
    }

    // ---- SpecOpCodec ---------------------------------------------------------------------------

    @Test
    fun specOpCodec_addFieldOfEveryType_roundTrips() {
        allFields().forEach { f ->
            val op = SpecOp.AddField("pesanan_bordir", f)
            val decoded = SpecOpCodec.decode(JsonParser.parseObject(SpecOpCodec.encode(op).encode()))
            assertEquals(op, decoded.getOrNull(), "AddField ${f.type}")
        }
    }

    @Test
    fun specOpCodec_addFieldWithUnknownType_failsAndNeverBecomesText() {
        val raw = SpecOpCodec.encode(SpecOp.AddField("pesanan_bordir", allFields().first())).encode()
        unknownNames.forEach { name ->
            val tampered = raw.replace("\"fieldType\":\"TEXT\"", "\"fieldType\":\"$name\"")
            val result = SpecOpCodec.decode(JsonParser.parseObject(tampered))
            assertTrue(result.isFailure, "'$name' harus ditolak, dapat ${result.getOrNull()}")
            assertTrue("tidak dikenal" in (result.exceptionOrNull()?.message ?: ""))
        }
    }

    // ---- ScreenSuggestionCodec (usulan layar bawaan pack) --------------------------------------

    private fun suggestionWithEveryType(): ScreenSuggestion {
        val hints = FieldType.entries.map { type ->
            val f = allFields().single { it.type == type }
            FieldHint(f.key, type, options = f.options, target = f.target)
        }
        return ScreenSuggestion(
            ModuleId("bordir_antrean"), "Antrean bordir", WidgetKind.TABLE,
            sampleRows = listOf(allFields().associate { it.key to validValue(it.type) }),
            tableHints = TableHints(statusColumn = "tahap", options = listOf("Digitizing", "Hooping", "Selesai"), fields = hints),
            rationale = "Dipilih karena bordir berurutan."
        )
    }

    @Test
    fun screenSuggestionCodec_fieldHintsOfEveryType_roundTrip() {
        val original = listOf(suggestionWithEveryType())
        val decoded = ScreenSuggestionCodec.decode(JsonParser.parse(ScreenSuggestionCodec.encode(original).encode()))
        assertEquals(original, decoded)
        assertEquals(FieldType.entries.toSet(), decoded.single().tableHints?.fields?.map { it.type }?.toSet())
    }

    @Test
    fun screenSuggestionCodec_unknownTypeName_isRejectedWithPath() {
        val raw = ScreenSuggestionCodec.encode(listOf(suggestionWithEveryType())).encode()
        unknownNames.forEach { name ->
            val tampered = raw.replace("\"type\":\"TEXT\"", "\"type\":\"$name\"")
            val error = assertFailsWith<DomainPackDecodeException>("'$name' harus ditolak") {
                ScreenSuggestionCodec.decode(jsonArrayOf((JsonParser.parse(tampered) as JsonValue.Arr).items))
            }
            assertTrue(error.path.endsWith(".type"), error.path)
        }
    }

    // ---- ScreenProposalCodec (lewat dokumen draf) -----------------------------------------------

    private fun proposalWithEveryType(): String {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val extras = FieldType.entries.map { type ->
            val f = allFields().single { it.type == type }
            // C7: `target` RELATION ikut dokumen draf (null untuk tipe lain).
            FieldProposal("paritas_${type.name.lowercase()}", "Paritas ${type.name}", type, options = f.options, target = f.target)
        }
        val entity = ScreenProposalFixtures.pasien
        val proposal = base.copy(entity = entity.copy(fields = entity.fields + extras))
        return DiscoveryDraftCodec.encodeToString(draft(screenOf(proposal)))
    }

    @Test
    fun screenProposalCodec_everyFieldType_roundTripsThroughDraftDocument() {
        val raw = proposalWithEveryType()
        val decoded = DiscoveryDraftCodec.decode(raw)
        assertEquals(raw, DiscoveryDraftCodec.encodeToString(decoded))
        val types = decoded.screens.single().proposal?.entity?.fields?.map { it.type }.orEmpty().toSet()
        assertEquals(FieldType.entries.toSet(), types)
        // C7: target RELATION ikut kawat usulan (bukan hilang saat encode/decode).
        val relation = decoded.screens.single().proposal?.entity?.fields?.single { it.type == FieldType.RELATION }
        assertEquals(allFields().single { it.type == FieldType.RELATION }.target, relation?.target)
        assertEquals(null, decoded.screens.single().proposal?.entity?.fields?.first { it.type == FieldType.TEXT }?.target)
    }

    @Test
    fun screenProposalCodec_unknownTypeName_isRejectedAndListsClosedVocabulary() {
        val raw = proposalWithEveryType()
        unknownNames.forEach { name ->
            val tampered = raw.replace("\"type\":\"TEXT\"", "\"type\":\"$name\"")
            val error = assertFailsWith<DiscoveryDraftDecodeException>("'$name' harus ditolak") { DiscoveryDraftCodec.decode(tampered) }
            assertTrue(error.path.endsWith(".type"), error.path)
            assertTrue(FieldType.entries.all { it.name in (error.message ?: "") }, "pesan harus menyebut kosakata tertutup: ${error.message}")
        }
    }

    // ---- C4 Irisan 2: NumberFormat sebagai parameter NUMBER ------------------------------------

    /** C4 Irisan 2: `format` NUMBER ikut kawat layar interaktif; yang tak dikenal ditolak, bukan jadi PLAIN. */
    @Test
    fun interactiveScreenCodec_numberFormat_roundTrips_andUnknownIsRejected() {
        val spec = PrototypeSpec(
            listOf(EntitySpec("e", "E", listOf(FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "IDR")))),
            listOf(ScreenSpec("t", "T", WidgetKind.TABLE, "e", table = TableConfig(listOf("harga"))))
        )
        val screen = InteractiveScreen(spec, mapOf("e" to listOf(PrototypeRow("r1", mapOf("harga" to "12000")))))
        val decoded = InteractiveScreenCodec.decode(InteractiveScreenCodec.encode(screen))
        assertEquals(NumberFormat.CURRENCY, decoded.spec.entities.single().fields.single().format)
        assertEquals("IDR", decoded.spec.entities.single().fields.single().currencyCode)
        val noCode = InteractiveScreenCodec.encode(screen).encode().replace("\"currencyCode\":\"IDR\"", "\"currencyCode\":null")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(noCode) as JsonValue.Obj) }.isFailure, "CURRENCY tanpa kode ditolak, tidak jadi IDR")
        val tampered = InteractiveScreenCodec.encode(screen).encode().replace("\"format\":\"CURRENCY\"", "\"format\":\"RUPIAH\"")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(tampered) as JsonValue.Obj) }.isFailure, "format 'RUPIAH' harus ditolak, tidak diam-diam jadi PLAIN")
    }

    /** C4 Irisan 2: AddField membawa `format` utuh lewat kawat SpecOp. */
    @Test
    fun specOpCodec_addFieldWithCurrency_keepsCode() {
        val op = SpecOp.AddField("e", FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "EUR"))
        assertEquals(op, SpecOpCodec.decode(SpecOpCodec.encode(op)).getOrThrow())
    }

    /** C7 (TRD-FIELD-001): `target` RELATION ikut kawat layar interaktif — hilang = rujukan rusak. */
    @Test
    fun interactiveScreenCodec_relationTarget_roundTrips_andMissingTargetIsRejected() {
        val spec = PrototypeSpec(
            listOf(EntitySpec("e", "E", listOf(FieldSpec("rujukan", "Rujukan", FieldType.RELATION, target = "crm:lead")))),
            listOf(ScreenSpec("t", "T", WidgetKind.TABLE, "e", table = TableConfig(listOf("rujukan"))))
        )
        val screen = InteractiveScreen(spec, mapOf("e" to listOf(PrototypeRow("r1", mapOf("rujukan" to "lead-1")))))
        val decoded = InteractiveScreenCodec.decode(InteractiveScreenCodec.encode(screen))
        assertEquals("crm:lead", decoded.spec.entities.single().fields.single().target)
        // RELATION tanpa target di kawat = korupsi bentuk: FieldSpec menolak, bukan fallback.
        val raw = InteractiveScreenCodec.encode(screen).encode().replace("\"target\":\"crm:lead\"", "\"target\":null")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(raw) as JsonValue.Obj) }.isFailure, "RELATION tanpa target ditolak")
    }

    /** C7: AddField membawa `target` utuh lewat kawat SpecOp. */
    @Test
    fun specOpCodec_addFieldWithRelationTarget_keepsTarget() {
        val op = SpecOp.AddField("e", FieldSpec("rujukan", "Rujukan", FieldType.RELATION, target = "pesanan"))
        val decoded = SpecOpCodec.decode(SpecOpCodec.encode(op)).getOrThrow()
        assertEquals(op, decoded)
        assertEquals("pesanan", (decoded as SpecOp.AddField).field.target)
    }

    @Test
    fun specOpCodec_addFieldWithNumberFormat_roundTrips() {
        val op = SpecOp.AddField("e", FieldSpec("harga", "Harga", FieldType.NUMBER, format = NumberFormat.PERCENT))
        val decoded = SpecOpCodec.decode(SpecOpCodec.encode(op)).getOrThrow()
        assertEquals(op, decoded)
        assertEquals(NumberFormat.PERCENT, (decoded as SpecOp.AddField).field.format)
    }

    /** C4 Irisan 2: `format` ikut kawat dokumen draf, byte-stabil, dan format tak dikenal ditolak berpath. */
    @Test
    fun screenProposalCodec_numberFormat_roundTripsThroughDraftDocument_andUnknownIsRejected() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val extras = listOf(
            FieldProposal("tarif", "Tarif", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = "USD"),
            FieldProposal("diskon", "Diskon", FieldType.NUMBER, format = NumberFormat.PERCENT)
        )
        val entity = ScreenProposalFixtures.pasien
        val proposal = base.copy(entity = entity.copy(fields = entity.fields + extras))
        val raw = DiscoveryDraftCodec.encodeToString(draft(screenOf(proposal)))
        val fields = DiscoveryDraftCodec.decode(raw).screens.single().proposal?.entity?.fields.orEmpty()
        assertEquals(NumberFormat.CURRENCY, fields.first { it.key == "tarif" }.format)
        assertEquals("USD", fields.first { it.key == "tarif" }.currencyCode)
        assertEquals(null, fields.first { it.key == "diskon" }.currencyCode)
        assertEquals(NumberFormat.PERCENT, fields.first { it.key == "diskon" }.format)
        assertEquals(raw, DiscoveryDraftCodec.encodeToString(DiscoveryDraftCodec.decode(raw)), "dokumen draf byte-stabil")
        val tampered = raw.replace("\"format\":\"CURRENCY\"", "\"format\":\"RUPIAH\"")
        val error = assertFailsWith<DiscoveryDraftDecodeException>("format tak dikenal harus ditolak") { DiscoveryDraftCodec.decode(tampered) }
        assertTrue(error.path.endsWith(".format"), error.path)
    }
}
