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
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreen
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

    /** Nama tipe yang bukan kosakata: mata uang, tipe yang ditunda (plan C3-C8), salah huruf, dan kosong. */
    private val unknownNames = listOf("CURRENCY", "LONG_TEXT", "MULTI_SELECT", "FILE", "RELATION", "text", "Text", "UANG", "")

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
            FieldHint(f.key, type, options = f.options)
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
            FieldProposal("paritas_${type.name.lowercase()}", "Paritas ${type.name}", type, options = f.options)
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
}
