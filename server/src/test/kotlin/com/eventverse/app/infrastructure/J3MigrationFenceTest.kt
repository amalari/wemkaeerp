package com.eventverse.app.infrastructure

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang [J3MigrationFence] — PLAN-module-ownership-lanes Track C3: membuktikan pagar P4 punya gigi.
 *
 * 1. migrasi J3 asli (V90 `layanan_change_request`) lolos;
 * 2. fixture pelanggar yang merujuk `crm_sales.*` **gagal** (REFERENCES, JOIN, dan SET search_path);
 * 3. migrasi J3 yang patuh tidak dituduh — pemindai tidak kosong;
 * 4. migrasi garment tetap diadili standar B8, bukan standar J3 (pembatasan P4 khusus J3).
 */
class J3MigrationFenceTest {

    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private fun read(path: String): String =
        checkNotNull(File(repoRoot, path).takeIf { it.exists() }) { "Berkas tidak ditemukan: $path" }.readText()

    @Test
    fun `migrasi j3 asli hanya merujuk schema dalam daftar putih`() {
        val migrationDir = File(repoRoot, "server/src/main/resources/db/migration")
        val j3Migrations = migrationDir.listFiles { f -> f.isFile && f.extension == "sql" }.orEmpty()
            .sortedBy { it.name }
            .map { it.name to it.readText() }
            .filter { (_, sql) -> J3MigrationFence.isJ3Migration(sql) }

        assertTrue(
            j3Migrations.any { it.first.startsWith("V90__") },
            "V90 layanan_change_request tidak terdeteksi sebagai migrasi J3 — aturan deteksi C1 rusak"
        )

        j3Migrations.forEach { (name, sql) ->
            val found = J3MigrationFence.scan(name, sql)
            assertEquals(emptyList(), found, "Migrasi J3 $name melanggar pagar P4:\n${found.joinToString("\n")}")
        }
    }

    @Test
    fun `fixture pelanggar yang merujuk crm_sales harus gagal`() {
        val path = "server/src/test/resources/db/migration-fixture/V901__j3_fixture_referencing_crm_sales.sql"
        val found = J3MigrationFence.scan(path.substringAfterLast('/'), read(path))

        assertTrue(found.isNotEmpty(), "Fixture pelanggar lolos — pagar tidak punya gigi (C3)")
        assertTrue(
            found.any { it.kind == "REFERENCES" && it.target == "crm_sales.crm_leads" },
            "FK ke crm_sales harus tertangkap: $found"
        )
        assertTrue(
            found.any { it.kind == "FROM/JOIN" && it.target == "crm_sales.deals" },
            "JOIN ke crm_sales harus tertangkap: $found"
        )
        assertTrue(
            found.any { it.kind == "SET search_path" && it.target == "crm_sales" },
            "SET search_path ke schema lain harus tertangkap: $found"
        )
    }

    @Test
    fun `migrasi j3 yang patuh tidak dituduh`() {
        val sql = """
            CREATE SCHEMA IF NOT EXISTS layanan_change_request;

            CREATE TABLE IF NOT EXISTS layanan_change_request.change_requests (
                id        VARCHAR(64) PRIMARY KEY,
                tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
                user_id   VARCHAR(64) REFERENCES users(id)
            );

            INSERT INTO module_catalog_entries (id, module_id) VALUES ('mce-x', 'layanan_change_request');

            SELECT c.judul
            FROM layanan_change_request.change_requests c
            JOIN layanan_change_request.change_requests d ON d.id = c.id;

            SET search_path = "${'$'}user", public, layanan_change_request;
        """.trimIndent()

        assertEquals(emptyList(), J3MigrationFence.scan("inline-patuh.sql", sql))
    }

    @Test
    fun `migrasi garment tetap memakai standar b8 bukan standar j3`() {
        val garment = """
            CREATE SCHEMA IF NOT EXISTS crm_sales;
            CREATE TABLE crm_sales.deals (
                id      VARCHAR(64) PRIMARY KEY,
                lead_id VARCHAR(64) REFERENCES sampling_order.sampling_orders(id)
            );
        """.trimIndent()

        assertEquals(emptyList(), J3MigrationFence.scan("v-garment.sql", garment))
    }
}