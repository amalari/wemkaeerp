package com.eventverse.app.infrastructure

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gerbang integrasi Postgres untuk kolom `TEXT[]` (TRD-FIELD-003 §5/Risiko): ini **pemakaian kolom larik pertama**
 * di repo, jadi tes yang hanya memeriksa teks SQL tidak membuktikan Exposed `array<String>` berperilaku benar di
 * driver/skema ini. Tes:
 *  1. membuat tabel dengan DDL **persis** yang dihasilkan `SpecColumns.sqlDefinition()` untuk MULTI_SELECT
 *     (`TEXT[] NOT NULL` + CHECK `<@` + CHECK `cardinality`), lalu
 *  2. menulis/membaca kolom `TEXT[]` lewat Exposed `array<String>` (round-trip), `null` ↔ belum diisi, dan
 *  3. memastikan CHECK menolak elemen di luar opsi, melebihi `maxSelections`, dan larik kosong.
 *
 * Berlari hanya bila Postgres scratch tersedia (`:server:test` menyetel `DB_NAME=wemake_erp_scratch_test` dan
 * pagar `wemade.requireScratchDb`); tanpa DB tes **dilewati** (`assumeTrue`) — bukan diturunkan diam-diam.
 */
class PrototypeMultiSelectPgTest {

    private object Probe : Table("public.multiselect_probe") {
        val id = varchar("id", 64)
        val layanan = array<String>("layanan")
        val tambahan = array<String>("tambahan").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    /** Cermin `SpecColumn.sqlDefinition()` untuk field MULTI_SELECT required dengan `maxSelections = 2`. */
    private val createTableSql = """
        CREATE TABLE public.multiselect_probe (
            id varchar(64) PRIMARY KEY,
            layanan TEXT[] NOT NULL
                CHECK (layanan <@ ARRAY['Digitizing','Hooping','Selesai']::text[])
                CHECK (cardinality(layanan) > 0)
                CHECK (cardinality(layanan) <= 2),
            tambahan TEXT[]
        )
    """.trimIndent()

    private fun runSql(sql: String) = runBlocking { DatabaseFactory.dbQuery { TransactionManager.current().exec(sql) } }

    private fun cleanup() = runCatching { runSql("DROP TABLE IF EXISTS public.multiselect_probe") }

    @Test
    fun textArrayColumn_roundTripsAndEnforcesCheckConstraints() {
        val started = runCatching { DatabaseFactory.init() }
        assumeTrue("Postgres scratch DB tidak tersedia → gate integrasi TEXT[] dilewati: ${started.exceptionOrNull()?.message}", started.isSuccess)
        try {
            runBlocking {
                runSql("DROP TABLE IF EXISTS public.multiselect_probe")
                runSql(createTableSql)

                // Tulis/round-trip Exposed array<String>; kolom opsional null ↔ belum diisi.
                DatabaseFactory.dbQuery {
                    Probe.insert { it[id] = "r1"; it[layanan] = listOf("Digitizing", "Hooping"); it[tambahan] = null }
                    Probe.insert { it[id] = "r2"; it[layanan] = listOf("Selesai"); it[tambahan] = listOf("a") }
                }

                val r1 = DatabaseFactory.dbQuery { Probe.selectAll().where { Probe.id eq "r1" }.single() }
                assertEquals(listOf("Digitizing", "Hooping"), r1[Probe.layanan], "round-trip larik TEXT[] byte-stabil")
                assertNull(r1[Probe.tambahan], "kolom opsional: null ↔ belum diisi")
                val r2 = DatabaseFactory.dbQuery { Probe.selectAll().where { Probe.id eq "r2" }.single() }
                assertEquals(listOf("a"), r2[Probe.tambahan])

                // CHECK opsi: elemen di luar opsi ditolak Postgres, bukan hanya aplikasi.
                val outOfOptions = runCatching {
                    DatabaseFactory.dbQuery { Probe.insert { it[id] = "r3"; it[layanan] = listOf("Cuci") } }
                }
                assertTrue(outOfOptions.isFailure, "elemen di luar opsi harus ditolak CHECK <@")
                // CHECK maxSelections: cardinality <= 2.
                val tooMany = runCatching {
                    DatabaseFactory.dbQuery { Probe.insert { it[id] = "r4"; it[layanan] = listOf("Digitizing", "Hooping", "Selesai") } }
                }
                assertTrue(tooMany.isFailure, "melebihi maxSelections harus ditolak CHECK cardinality <= 2")
                // CHECK kosong: cardinality > 0 ("[]" tidak pernah tersimpan; penulis memetakan "" → null pada kolom opsional).
                val empty = runCatching {
                    DatabaseFactory.dbQuery { Probe.insert { it[id] = "r5"; it[layanan] = emptyList() } }
                }
                assertTrue(empty.isFailure, "larik kosong harus ditolak CHECK cardinality > 0")
            }
        } finally {
            cleanup()
        }
    }
}
