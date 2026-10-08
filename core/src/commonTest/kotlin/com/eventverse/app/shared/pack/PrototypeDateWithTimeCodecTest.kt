package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.draft
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.screenOf
import com.eventverse.app.domain.discovery.proposal.ViewProposal
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
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A0(C6) Irisan 2 — `withTime` di tiga kawat (layar interaktif, SpecOp, draf usulan) + validator/edit usulan. Fixture klinik/bordir. */
class PrototypeDateWithTimeCodecTest {

    private fun screen(): InteractiveScreen {
        val fields = listOf(
            FieldSpec("jadwal", "Jadwal", FieldType.DATE, withTime = true),
            FieldSpec("tanggal", "Tanggal", FieldType.DATE)
        )
        return InteractiveScreen(
            PrototypeSpec(
                listOf(EntitySpec("pesanan_bordir", "Pesanan bordir", fields)),
                listOf(ScreenSpec("t", "T", WidgetKind.TABLE, "pesanan_bordir", table = TableConfig(listOf("jadwal", "tanggal"))))
            ),
            mapOf("pesanan_bordir" to listOf(PrototypeRow("r1", mapOf("jadwal" to "2026-10-08T14:30", "tanggal" to "2026-10-08"))))
        )
    }

    // ---- InteractiveScreenCodec ----------------------------------------------------------------

    @Test
    fun interactiveScreenCodec_withTime_roundTrips_andOldDocumentsDefaultToFalse() {
        val encoded = InteractiveScreenCodec.encode(screen())
        val fields = InteractiveScreenCodec.decode(encoded).spec.entities.single().fields
        assertTrue(fields.first { it.key == "jadwal" }.withTime)
        assertFalse(fields.first { it.key == "tanggal" }.withTime)
        val old = encoded.encode().replace(",\"withTime\":true", "").replace(",\"withTime\":false", "")
        assertFalse("withTime" in old)
        assertTrue(InteractiveScreenCodec.decode(JsonParser.parse(old) as JsonValue.Obj).spec.entities.single().fields.none { it.withTime })
    }

    @Test
    fun interactiveScreenCodec_nonBooleanWithTime_isRejected() {
        val tampered = InteractiveScreenCodec.encode(screen()).encode().replace("\"withTime\":true", "\"withTime\":\"ya\"")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(tampered) as JsonValue.Obj) }.isFailure)
    }

    @Test
    fun interactiveScreenCodec_withTimeOnNonDate_isRejectedByInvariant() {
        val tampered = InteractiveScreenCodec.encode(screen()).encode().replace("\"type\":\"DATE\"", "\"type\":\"TEXT\"")
        assertTrue(runCatching { InteractiveScreenCodec.decode(JsonParser.parse(tampered) as JsonValue.Obj) }.isFailure, "TEXT + withTime=true harus ditolak")
    }

    // ---- SpecOpCodec ---------------------------------------------------------------------------

    @Test
    fun specOpCodec_addFieldWithTime_roundTrips_andNonBooleanIsRejected() {
        val op = SpecOp.AddField("e", FieldSpec("jadwal", "Jadwal", FieldType.DATE, withTime = true))
        val encoded = SpecOpCodec.encode(op)
        val decoded = SpecOpCodec.decode(encoded).getOrThrow()
        assertEquals(op, decoded)
        assertTrue((decoded as SpecOp.AddField).field.withTime)
        val tampered = encoded.encode().replace("\"withTime\":true", "\"withTime\":1")
        assertTrue(SpecOpCodec.decode(JsonParser.parse(tampered) as JsonValue.Obj).isFailure)
        val onText = encoded.encode().replace("\"fieldType\":\"DATE\"", "\"fieldType\":\"TEXT\"")
        assertTrue(SpecOpCodec.decode(JsonParser.parse(onText) as JsonValue.Obj).isFailure, "withTime pada TEXT ditolak")
    }

    // ---- ScreenProposalCodec (draf) + validator ------------------------------------------------

    private fun proposalWith(vararg extras: FieldProposal) = ScreenProposalFixtures.kanbanAntrean().let { base ->
        base.copy(entity = base.entity!!.let { it.copy(fields = it.fields + extras) })
    }

    @Test
    fun screenProposalCodec_withTime_roundTripsThroughDraft_byteStable_andNonBooleanIsRejectedWithPath() {
        val raw = DiscoveryDraftCodec.encodeToString(draft(screenOf(proposalWith(FieldProposal("jadwal", "Jadwal", FieldType.DATE, withTime = true)))))
        val fields = DiscoveryDraftCodec.decode(raw).screens.single().proposal?.entity?.fields.orEmpty()
        assertTrue(fields.first { it.key == "jadwal" }.withTime)
        assertFalse(fields.first { it.key == "tanggal_kunjungan" }.withTime)
        assertEquals(raw, DiscoveryDraftCodec.encodeToString(DiscoveryDraftCodec.decode(raw)), "dokumen draf byte-stabil")
        val error = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(raw.replace("\"withTime\":true", "\"withTime\":\"ya\"")) }
        assertTrue(error.path.endsWith(".withTime"), error.path)
    }

    @Test
    fun proposalValidator_withTimeOnlyOnDate_andSeedFollowsMode() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val table = base.copy(
            view = ViewProposal.Table(columns = listOf("nama", "tanggal_kunjungan")),
            widget = WidgetKind.TABLE,
            seed = listOf(mapOf("nama" to "Budi", "status" to "Menunggu", "tanggal_kunjungan" to "2026-10-01"))
        )
        val onText = table.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("catatan", "Catatan", FieldType.TEXT, withTime = true))))
        assertTrue(onText.exceptionOrNull()!!.message!!.contains("bukan DATE"), onText.exceptionOrNull()?.message)
        val ok = table.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("jadwal", "Jadwal", FieldType.DATE, withTime = true)))).getOrThrow()
        assertTrue(ok.entity!!.fields.first { it.key == "jadwal" }.withTime)
        assertTrue(ok.toInteractiveScreen().getOrThrow().spec.entities.single().field("jadwal")!!.withTime, "konversi meneruskan withTime")
    }

    @Test
    fun proposalEdit_replaceDateWithTime_dropsOrSamplesSeedValueByRequired() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val seeded = base.copy(seed = base.seed.map { it + ("tanggal_kunjungan" to "2026-10-01") })
        val optional = seeded.applyEdits(listOf(ProposalEdit.ReplaceField("tanggal_kunjungan", FieldProposal("tanggal_kunjungan", "Kunjungan", FieldType.DATE, withTime = true)))).getOrThrow()
        assertTrue(optional.seed.all { "tanggal_kunjungan" !in it }, "nilai tanggal-saja dibuang: tidak sah pada field withTime")
        val required = seeded.applyEdits(listOf(ProposalEdit.ReplaceField("tanggal_kunjungan", FieldProposal("tanggal_kunjungan", "Kunjungan", FieldType.DATE, required = true, withTime = true)))).getOrThrow()
        assertTrue(required.seed.all { it["tanggal_kunjungan"] == "2026-01-01T09:00" }, "wajib: diganti contoh waktu")
    }
}
