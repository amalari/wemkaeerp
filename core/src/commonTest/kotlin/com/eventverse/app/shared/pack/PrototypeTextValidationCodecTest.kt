package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.draft
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.screenOf
import com.eventverse.app.domain.discovery.proposal.applyEdits
import com.eventverse.app.domain.discovery.proposal.toInteractiveScreen
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.SpecOp
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.domain.prototype.TextValidations
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A0(C9) Irisan 2 — `validation` di tiga kawat + validator/edit usulan. Nama tak dikenal ditolak, tidak jatuh ke NONE. Fixture klinik/bordir. */
class PrototypeTextValidationCodecTest {

    private val unknownNames = listOf("email", "Email", "URL", "UUID", "")

    private fun screen(): InteractiveScreen {
        val fields = listOf(
            FieldSpec("surel", "Surel", FieldType.TEXT, validation = TextValidation.EMAIL),
            FieldSpec("telepon", "Telepon", FieldType.TEXT, validation = TextValidation.PHONE),
            FieldSpec("catatan", "Catatan", FieldType.TEXT)
        )
        return InteractiveScreen(
            PrototypeSpec(
                listOf(EntitySpec("pesanan_bordir", "Pesanan bordir", fields)),
                listOf(ScreenSpec("t", "T", WidgetKind.TABLE, "pesanan_bordir", table = TableConfig(fields.map { it.key })))
            ),
            mapOf("pesanan_bordir" to listOf(PrototypeRow("r1", mapOf("surel" to "a@b.id", "telepon" to "+628123456789"))))
        )
    }

    // ---- InteractiveScreenCodec ----------------------------------------------------------------

    @Test
    fun interactiveScreenCodec_validation_roundTrips_andOldDocumentsDefaultToNone() {
        val encoded = InteractiveScreenCodec.encode(screen())
        val fields = InteractiveScreenCodec.decode(encoded).spec.entities.single().fields
        assertEquals(listOf(TextValidation.EMAIL, TextValidation.PHONE, TextValidation.NONE), fields.map { it.validation })
        val old = TextValidation.entries.fold(encoded.encode()) { acc, v -> acc.replace(",\"validation\":\"${v.name}\"", "") }
        assertTrue("validation" !in old)
        assertTrue(InteractiveScreenCodec.decode(JsonParser.parse(old) as JsonValue.Obj).spec.entities.single().fields.all { it.validation == TextValidation.NONE })
    }

    @Test
    fun interactiveScreenCodec_unknownOrNonStringValidation_isRejected() {
        val encoded = InteractiveScreenCodec.encode(screen()).encode()
        unknownNames.forEach { name ->
            val tampered = encoded.replace("\"validation\":\"EMAIL\"", "\"validation\":\"$name\"")
            assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(tampered) as JsonValue.Obj) }.isFailure, "'$name' harus ditolak")
        }
        val numeric = encoded.replace("\"validation\":\"EMAIL\"", "\"validation\":7")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(numeric) as JsonValue.Obj) }.isFailure, "bukan string ditolak")
        val onLong = encoded.replace("\"type\":\"TEXT\"", "\"type\":\"LONG_TEXT\"")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(onLong) as JsonValue.Obj) }.isFailure, "validation pada LONG_TEXT ditolak invarian")
    }

    // ---- SpecOpCodec ---------------------------------------------------------------------------

    @Test
    fun specOpCodec_addFieldValidation_roundTripsForEveryEntry_andUnknownIsRejected() {
        TextValidation.entries.forEach { v ->
            val op = SpecOp.AddField("e", FieldSpec("k", "K", FieldType.TEXT, validation = v))
            assertEquals(op, SpecOpCodec.decode(SpecOpCodec.encode(op)).getOrThrow(), "$v round-trip")
        }
        val encoded = SpecOpCodec.encode(SpecOp.AddField("e", FieldSpec("k", "K", FieldType.TEXT, validation = TextValidation.PHONE))).encode()
        unknownNames.forEach { name ->
            val tampered = encoded.replace("\"validation\":\"PHONE\"", "\"validation\":\"$name\"")
            val result = SpecOpCodec.decode(JsonParser.parse(tampered) as JsonValue.Obj)
            assertTrue(result.isFailure, "'$name' harus ditolak")
            assertTrue(result.exceptionOrNull()!!.message!!.contains("NONE, EMAIL, PHONE"), result.exceptionOrNull()?.message)
        }
        val onNumber = encoded.replace("\"fieldType\":\"TEXT\"", "\"fieldType\":\"NUMBER\"")
        assertTrue(SpecOpCodec.decode(JsonParser.parse(onNumber) as JsonValue.Obj).isFailure, "validation pada NUMBER ditolak")
    }

    // ---- ScreenProposalCodec (draf) + validator ------------------------------------------------

    private fun proposalWith(vararg extras: FieldProposal) = ScreenProposalFixtures.kanbanAntrean().let { base ->
        base.copy(entity = base.entity!!.let { it.copy(fields = it.fields + extras) })
    }

    @Test
    fun screenProposalCodec_validation_roundTripsThroughDraft_byteStable_andUnknownIsRejectedWithPath() {
        val raw = DiscoveryDraftCodec.encodeToString(draft(screenOf(proposalWith(FieldProposal("surel", "Surel", FieldType.TEXT, validation = TextValidation.EMAIL)))))
        val fields = DiscoveryDraftCodec.decode(raw).screens.single().proposal?.entity?.fields.orEmpty()
        assertEquals(TextValidation.EMAIL, fields.first { it.key == "surel" }.validation)
        assertEquals(TextValidation.NONE, fields.first { it.key == "nama" }.validation)
        assertEquals(raw, DiscoveryDraftCodec.encodeToString(DiscoveryDraftCodec.decode(raw)), "dokumen draf byte-stabil")
        unknownNames.forEach { name ->
            val error = assertFailsWith<DiscoveryDraftDecodeException>("'$name' harus ditolak") {
                DiscoveryDraftCodec.decode(raw.replace("\"validation\":\"EMAIL\"", "\"validation\":\"$name\""))
            }
            assertTrue(error.path.endsWith(".validation"), error.path)
        }
    }

    @Test
    fun proposalValidator_validationOnlyOnText_andSeedMustMatch() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val onDate = base.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("jadwal", "Jadwal", FieldType.DATE, validation = TextValidation.EMAIL))))
        assertTrue(onDate.exceptionOrNull()!!.message!!.contains("bukan TEXT"), onDate.exceptionOrNull()?.message)
        val onLong = base.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("uraian", "Uraian", FieldType.LONG_TEXT, validation = TextValidation.PHONE))))
        assertTrue(onLong.exceptionOrNull()!!.message!!.contains("bukan TEXT"), "LONG_TEXT juga ditolak")
        val ok = base.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("surel", "Surel", FieldType.TEXT, validation = TextValidation.EMAIL)))).getOrThrow()
        assertEquals(TextValidation.EMAIL, ok.toInteractiveScreen().getOrThrow().spec.entities.single().field("surel")!!.validation, "konversi meneruskan validation")
        val badSeed = ok.copy(seed = ok.seed.map { it + ("surel" to "bukan-email") })
        val rejected = badSeed.applyEdits(emptyList())
        assertTrue(rejected.isFailure && rejected.exceptionOrNull()!!.message!!.contains("email"), "seed tak sah ditolak: ${rejected.exceptionOrNull()?.message}")
    }

    @Test
    fun proposalEdit_replaceTextWithValidation_dropsOrSamplesSeedValueByRequired() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val seeded = base.copy(seed = base.seed.map { it + ("keluhan" to "ngilu") })
        val optional = seeded.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", FieldProposal("keluhan", "Keluhan", FieldType.TEXT, validation = TextValidation.PHONE)))).getOrThrow()
        assertTrue(optional.seed.all { "keluhan" !in it }, "nilai bukan telepon dibuang")
        val required = seeded.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", FieldProposal("keluhan", "Keluhan", FieldType.TEXT, required = true, validation = TextValidation.EMAIL)))).getOrThrow()
        assertTrue(required.seed.all { it["keluhan"] == TextValidations.sample(TextValidation.EMAIL) }, "wajib: diganti contoh email")
        assertEquals("contoh@contoh.id", TextValidations.sample(TextValidation.EMAIL))
        assertEquals("+628123456789", TextValidations.sample(TextValidation.PHONE))
    }
}
