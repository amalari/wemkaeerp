package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.brief.BriefRenderer
import com.eventverse.app.domain.discovery.brief.CaptureEntry
import com.eventverse.app.domain.discovery.brief.RequirementsBrief
import com.eventverse.app.domain.discovery.handoff.SpecColumn
import com.eventverse.app.domain.discovery.handoff.SpecPostgresWriter
import com.eventverse.app.domain.discovery.handoff.SpecTable
import com.eventverse.app.domain.discovery.handoff.sqlDefinition
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.SpecOpCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Suntingan pasca-pembuatan `SetFieldWithTime` / `SetFieldValidation` (A0(C6)/A0(C9) Irisan 2, Track A). Keputusan yang
 * dikunci: operasi **ditolak** bila ada nilai seed tak sah di bentuk baru (tanpa koersi diam-diam); nilai kosong sah.
 * Fixture non-garment: antrean klinik gigi dan pesanan bordir.
 */
class FieldParamOpsTest {

    private fun screen(
        fields: List<FieldSpec>,
        rows: List<Map<String, String>>,
        entity: String = "antrean_klinik"
    ) = InteractiveScreen(
        PrototypeSpec(
            listOf(EntitySpec(entity, "Antrean klinik", fields)),
            listOf(ScreenSpec("tabel", "Tabel", WidgetKind.TABLE, entity, table = TableConfig(fields.map { it.key })))
        ),
        mapOf(entity to rows.mapIndexed { i, r -> PrototypeRow("r${i + 1}", r) })
    )

    private fun dateScreen(rows: List<Map<String, String>>, withTime: Boolean = false) =
        screen(listOf(FieldSpec("pasien", "Pasien", FieldType.TEXT), FieldSpec("jadwal", "Jadwal", FieldType.DATE, withTime = withTime)), rows)

    private fun textScreen(rows: List<Map<String, String>>, validation: TextValidation = TextValidation.NONE) =
        screen(listOf(FieldSpec("pasien", "Pasien", FieldType.TEXT), FieldSpec("kontak", "Kontak", FieldType.TEXT, validation = validation)), rows)

    private fun setTime(s: InteractiveScreen, on: Boolean, field: String = "jadwal") =
        SpecOpApplier.apply(s, SpecOp.SetFieldWithTime("antrean_klinik", field, on))

    private fun setValidation(s: InteractiveScreen, v: TextValidation, field: String = "kontak") =
        SpecOpApplier.apply(s, SpecOp.SetFieldValidation("antrean_klinik", field, v))

    // ---- SetFieldWithTime ----------------------------------------------------------------------

    @Test
    fun setWithTime_onEmptySeedOrBlankValues_changesFieldAndKeepsRows() {
        val s = dateScreen(listOf(mapOf("pasien" to "Budi"), mapOf("pasien" to "Sari", "jadwal" to "")))
        val on = setTime(s, true).getOrThrow()
        assertTrue(on.spec.entities.single().field("jadwal")!!.withTime)
        assertEquals(s.seed, on.seed)
        assertEquals(s, setTime(on, false).getOrThrow(), "dapat dikembalikan")
    }

    @Test
    fun setWithTime_dateOnlyValueInSeed_isRejectedNotCoerced() {
        val s = dateScreen(listOf(mapOf("pasien" to "Budi", "jadwal" to "2026-10-08")))
        val error = setTime(s, true).exceptionOrNull()?.message.orEmpty()
        assertTrue("1 baris" in error && "2026-10-08" in error, error)
    }

    @Test
    fun setWithTime_timestampValueInSeed_isRejectedWhenTurningOff() {
        val s = dateScreen(listOf(mapOf("pasien" to "Budi", "jadwal" to "2026-10-08T14:30")), withTime = true)
        assertTrue(setTime(s, false).isFailure)
    }

    @Test
    fun setWithTime_sameValue_returnsSameScreen_andMixedSeedReportsEveryOffendingRow() {
        val s = dateScreen(listOf(mapOf("jadwal" to "2026-10-08"), mapOf("jadwal" to "2026-10-09"), mapOf("jadwal" to "")))
        assertSame(s, setTime(s, false).getOrThrow())
        assertTrue("2 baris" in setTime(s, true).exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun setWithTime_nonDateOrMissing_isRejectedWithMessage() {
        val s = dateScreen(listOf(mapOf("pasien" to "Budi")))
        assertTrue("hanya untuk field DATE" in setTime(s, true, "pasien").exceptionOrNull()?.message.orEmpty())
        assertTrue("tidak ada" in setTime(s, true, "hantu").exceptionOrNull()?.message.orEmpty())
        val unknownEntity = SpecOpApplier.apply(s, SpecOp.SetFieldWithTime("tak_ada", "jadwal", true))
        assertTrue("Entitas" in unknownEntity.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun setWithTime_changesStorageColumn_dateToTimestamp() {
        val before = dateScreen(emptyList())
        val after = setTime(before, true).getOrThrow()
        fun column(s: InteractiveScreen): SpecColumn = SpecTable.of("klinik_uji", s.spec.entities.single()).columns.single { it.name == "jadwal" }
        assertTrue(column(before).sqlDefinition().startsWith("DATE"))
        assertTrue(column(after).sqlDefinition().startsWith("TIMESTAMP"))
        assertTrue("jadwal" in SpecPostgresWriter.tableFile(SpecTable.of("klinik_uji", after.spec.entities.single())))
    }

    // ---- SetFieldValidation ----------------------------------------------------------------------

    /** `when` tanpa `else`: entri baru di [TextValidation] tanpa contoh = kompilasi gagal (Kontrak 6). */
    private fun badValue(v: TextValidation): String? = when (v) {
        TextValidation.NONE -> null
        TextValidation.EMAIL -> "bukan-email"
        TextValidation.PHONE -> "tidak-ada-digit"
    }

    @Test
    fun setValidation_everyEntry_acceptsValidSeed_andRejectsInvalidSeedWithoutChangingAnything() {
        TextValidation.entries.forEach { v ->
            val good = textScreen(listOf(mapOf("pasien" to "Budi", "kontak" to TextValidations.sample(v)), mapOf("pasien" to "Sari")))
            val changed = setValidation(good, v).getOrThrow()
            assertEquals(v, changed.spec.entities.single().field("kontak")!!.validation, "$v")
            badValue(v)?.let { bad ->
                val s = textScreen(listOf(mapOf("pasien" to "Budi", "kontak" to bad)))
                val error = setValidation(s, v).exceptionOrNull()?.message.orEmpty()
                assertTrue("1 baris" in error && bad in error, "$v: $error")
            }
        }
    }

    @Test
    fun setValidation_loosening_alwaysSucceeds_andRoundTripsBack() {
        val s = textScreen(listOf(mapOf("kontak" to "a@b.id")), TextValidation.EMAIL)
        val none = setValidation(s, TextValidation.NONE).getOrThrow()
        assertEquals(TextValidation.NONE, none.spec.entities.single().field("kontak")!!.validation)
        assertEquals(s, setValidation(none, TextValidation.EMAIL).getOrThrow())
        assertSame(s, setValidation(s, TextValidation.EMAIL).getOrThrow())
    }

    @Test
    fun setValidation_narrowingOverExistingFreeTextSeed_isRejected() {
        val s = textScreen(listOf(mapOf("kontak" to "0812-3456-7890")))
        assertTrue(setValidation(s, TextValidation.EMAIL).isFailure)
        assertTrue(setValidation(s, TextValidation.PHONE).isSuccess)
    }

    @Test
    fun setValidation_onLongTextOrOtherTypes_isRejected() {
        FieldType.entries.filter { it != FieldType.TEXT }.forEach { type ->
            val field = PrototypeFieldTypeSampleFields.fieldFor(type)
            val s = screen(listOf(field), emptyList())
            val error = SpecOpApplier.apply(s, SpecOp.SetFieldValidation("antrean_klinik", field.key, TextValidation.EMAIL)).exceptionOrNull()?.message.orEmpty()
            assertTrue("hanya untuk field TEXT" in error, "$type: $error")
        }
    }

    // ---- applyAll, brief, kawat ------------------------------------------------------------------

    @Test
    fun applyAll_logsRejectedAndAcceptedOps_andBriefRendersBoth() {
        val s = dateScreen(listOf(mapOf("jadwal" to "2026-10-08")))
        val ops = listOf(SpecOp.SetFieldWithTime("antrean_klinik", "jadwal", true), SpecOp.SetFieldWithTime("antrean_klinik", "jadwal", false))
        val applied = SpecOpApplier.applyAll(s, ops, "2026-10-09T00:00:00Z")
        assertEquals(listOf(false, true), applied.log.map { it.ok }, "yang pertama ditolak (seed tanggal-saja), yang kedua tak mengubah apa pun")
        val brief = RequirementsBrief("klinik", emptyList(), applied.log + CaptureEntry("t", SpecOp.SetFieldValidation("e", "kontak", TextValidation.PHONE), true, null), emptyList())
        val text = BriefRenderer.markdown(brief)
        assertTrue("tanggal dan jam" in text && "nomor telepon" in text, text)
    }

    @Test
    fun specOpCodec_newOps_roundTripEveryEntry() {
        val ops = listOf(true, false).map { SpecOp.SetFieldWithTime("e", "f", it) } +
            TextValidation.entries.map { SpecOp.SetFieldValidation("e", "f", it) }
        ops.forEach { op ->
            assertEquals(op, SpecOpCodec.decode(JsonParser.parseObject(SpecOpCodec.encode(op).encode())).getOrThrow(), "$op")
        }
        assertFalse(ops.isEmpty())
    }

    @Test
    fun specOpCodec_newOps_rejectMissingWrongTypeAndUnknownValues() {
        fun fails(raw: String) = SpecOpCodec.decode(JsonParser.parseObject(raw)).exceptionOrNull()?.message.orEmpty()
        val head = """"type":"SetFieldWithTime","entityId":"e","field":"f""""
        listOf("", ""","withTime":null""", ""","withTime":"true"""", ""","withTime":1""").forEach { tail ->
            assertTrue("withTime" in fails("{$head$tail}"), "withTime tail=$tail: ${fails("{$head$tail}")}")
        }
        val vHead = """"type":"SetFieldValidation","entityId":"e","field":"f""""
        listOf("", ""","validation":null""", ""","validation":"email"""", ""","validation":"URL"""", ""","validation":""""", ""","validation":5""").forEach { tail ->
            assertTrue("validation" in fails("{$vHead$tail}").lowercase() || "validasi" in fails("{$vHead$tail}").lowercase(), "validation tail=$tail: ${fails("{$vHead$tail}")}")
        }
    }
}
