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
 * A0(C6) Irisan 2 — `DATE` + `withTime`: invarian, format simpan (`TTTT-BB-HH'T'JJ:MM`, waktu dinding tanpa zona),
 * dan penyimpanan (SQL `TIMESTAMP`, Exposed `datetime`, route hasil generate). Entitas uji = antrean bordir.
 */
class PrototypeDateWithTimeTest {

    private fun dateTime(required: Boolean = false) = FieldSpec("jadwal", "Jadwal", FieldType.DATE, required = required, withTime = true)
    private fun dateOnly(required: Boolean = false) = FieldSpec("tanggal", "Tanggal", FieldType.DATE, required = required)
    private fun table(vararg f: FieldSpec) = SpecTable.of("bordir_uji", EntitySpec("pesanan_bordir", "Pesanan bordir", f.toList()))

    // ---- invarian ------------------------------------------------------------------------------

    @Test
    fun fieldSpec_withTimeOnNonDate_isRejected_forEveryOtherType() {
        FieldType.entries.filter { it != FieldType.DATE }.forEach { type ->
            // ENUM dan MULTI_SELECT wajib membawa opsi; kalau tidak, penolakannya bukan karena withTime.
            val options = if (type == FieldType.ENUM || type == FieldType.MULTI_SELECT) listOf("a") else emptyList()
            val result = runCatching { FieldSpec("k", "K", type, options, withTime = true) }
            assertTrue(result.isFailure, "$type dengan withTime harus ditolak")
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("bukan DATE"), "pesan jelas: ${result.exceptionOrNull()?.message}")
        }
        assertTrue(dateTime().withTime)
        assertFalse(dateOnly().withTime, "bawaan false")
    }

    // ---- format simpan -------------------------------------------------------------------------

    @Test
    fun accepts_withTimeField_takesOnlyMinutePrecisionWallClock() {
        val f = dateTime()
        assertTrue(f.accepts("2026-10-08T14:30"))
        assertTrue(f.accepts("2026-02-28T00:00"))
        assertTrue(f.accepts(""), "kosong tetap sah")
        assertFalse(f.accepts("2026-10-08"), "tanggal-saja ditolak pada field withTime")
        assertFalse(f.accepts("2026-10-08T14:30:15"), "detik ditolak")
        assertFalse(f.accepts("2026-10-08T14:30Z"), "zona ditolak")
        assertFalse(f.accepts("2026-10-08T14:30+07:00"), "offset ditolak")
        assertFalse(f.accepts("2026-10-08 14:30"), "pemisah spasi ditolak")
        assertFalse(f.accepts("2026-10-08T24:00"), "jam di luar rentang")
        assertFalse(f.accepts("2026-10-08T14:60"), "menit di luar rentang")
        assertFalse(f.accepts("2026-13-08T14:30"), "bulan di luar rentang")
        assertFalse(f.accepts("besok pagi"))
    }

    @Test
    fun accepts_dateOnlyField_rejectsDateTime_noSilentCoercion() {
        assertTrue(dateOnly().accepts("2026-10-08"))
        assertFalse(dateOnly().accepts("2026-10-08T14:30"), "tanggal-jam ditolak pada field tanggal-saja")
    }

    @Test
    fun dateFieldValues_sample_isValidForItsOwnMode() {
        listOf(true, false).forEach { withTime ->
            assertTrue(DateFieldValues.isValid(DateFieldValues.sample(withTime), withTime), "sample withTime=$withTime")
        }
        assertEquals("2026-01-01T09:00", DateFieldValues.sample(true))
    }

    // ---- penyimpanan ---------------------------------------------------------------------------

    @Test
    fun sqlDefinition_withTime_isTimestampWithoutZone_andKeepsNotNull() {
        assertEquals("TIMESTAMP", table(dateTime()).columns.single().sqlDefinition())
        assertEquals("TIMESTAMP NOT NULL", table(dateTime(required = true)).columns.single().sqlDefinition())
        assertEquals("DATE", table(dateOnly()).columns.single().sqlDefinition(), "tanggal-saja tetap DATE")
        assertFalse("TIMESTAMPTZ" in table(dateTime()).columns.single().sqlDefinition(), "tanpa zona")
    }

    @Test
    fun exposedTable_withTime_usesDatetimeColumn_andImportsIt() {
        val file = SpecPostgresWriter.tableFile(table(dateTime(), dateOnly()))
        assertTrue("val jadwal = datetime(\"jadwal\").nullable()" in file, file)
        assertTrue("val tanggal = date(\"tanggal\").nullable()" in file, file)
        assertTrue("import org.jetbrains.exposed.sql.kotlin.datetime.datetime" in file, file)
    }

    @Test
    fun repository_withTime_writesAndReadsThroughLocalDateTime() {
        val repo = SpecPostgresWriter.repositoryFile(table(dateTime(), dateOnly(required = true)))
        assertTrue("it[T.jadwal] = row[\"jadwal\"].takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }" in repo, repo)
        assertTrue("it[T.tanggal] = LocalDate.parse(row[\"tanggal\"])" in repo, "tanggal-saja tetap LocalDate")
        assertTrue("\"jadwal\" to (r[T.jadwal]?.toString() ?: \"\")" in repo, "baca konsisten dengan tulis: string TTTT-BB-HHTJJ:MM")
        assertTrue("import kotlinx.datetime.LocalDateTime" in repo)
    }

    @Test
    fun routes_withTime_literalAndValidationCheckTheTimeFormat() {
        val t = table(dateTime(), dateOnly())
        val routes = SpecRoutesWriter.routesFile("bordir_uji", t)
        assertTrue("FieldSpec(\"jadwal\", \"Jadwal\", FieldType.DATE, listOf(), false, withTime = true)" in routes, "entityLiteral membawa withTime")
        assertTrue("FieldSpec(\"jadwal\", \"Jadwal\", FieldType.DATE, withTime = true)" in routes, "DATE_FIELDS membawa withTime")
        assertTrue("FieldSpec(\"tanggal\", \"Tanggal\", FieldType.DATE)" in routes, "tanggal-saja tanpa withTime")
        assertTrue("f.withTime" in routes && "LocalDateTime.parse(v)" in routes && "TTTT-BB-HHTJJ:MM" in routes, "dateProblem memeriksa format menurut withTime")
        assertTrue("import kotlinx.datetime.LocalDateTime" in routes)
    }

    @Test
    fun entityLiteral_withTimeOnly_omitsNumberParams_andUsesNamedArgument() {
        val literal = SpecRoutesWriter.entityLiteral(EntitySpec("e", "E", listOf(dateTime())))
        assertEquals("EntitySpec(\"e\", \"E\", listOf(FieldSpec(\"jadwal\", \"Jadwal\", FieldType.DATE, listOf(), false, withTime = true)))", literal)
    }
}
