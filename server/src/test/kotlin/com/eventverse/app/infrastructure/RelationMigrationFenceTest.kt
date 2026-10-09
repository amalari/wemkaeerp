package com.eventverse.app.infrastructure

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Penjaga migrasi tabel link tipe field `RELATION` (C7, TRD-FIELD-001 Track B).
 *
 * 1. Migrasi `V99` adalah migrasi modul garment (`crm_sales`), jadi diadili standar B8 — bukan J3.
 *    Pagar J3 ([J3MigrationFence]) harus **meloloskannya** (tidak ada schema J3 yang disentuh).
 * 2. FR-1: tabel merujuk id target sebagai string — **tidak ada** `REFERENCES` ke schema modul lain;
 *    satu-satunya FK adalah ke tabel platform di `public` (`tenants`, `custom_field_definitions`).
 * 3. Isolasi tenant: RLS dipasang lewat `apply_tenant_rls_in` (pola V76/V90).
 */
class RelationMigrationFenceTest {

    private val fileName = "V99__custom_field_relation_links.sql"

    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private val sql: String
        get() = checkNotNull(File(repoRoot, "server/src/main/resources/db/migration/$fileName").takeIf { it.exists() }) {
            "Migrasi tidak ditemukan: $fileName"
        }.readText()

    @Test
    fun `migrasi link RELATION lolos pagar J3`() {
        assertFalse(J3MigrationFence.isJ3Migration(sql), "Migrasi crm_sales (garment) tidak boleh dianggap migrasi J3")
        assertEquals(emptyList(), J3MigrationFence.scan(fileName, sql), "Pagar J3 menuduh migrasi yang seharusnya lolos")
    }

    @Test
    fun `tanpa FK ke schema modul lain`() {
        // REFERENCES yang terkualifikasi schema (mis. `REFERENCES crm_sales.deals(id)`) = kopling lintas schema modul.
        val qualifiedRefs = Regex("""(?i)\bREFERENCES\s+([A-Za-z_][A-Za-z0-9_]*)\s*\.""")
            .findAll(sql).map { it.groupValues[1].lowercase() }.toSet()
        assertTrue(qualifiedRefs.isEmpty(), "FK lintas schema modul dilarang (FR-1): $qualifiedRefs")
    }

    @Test
    fun `RLS tenant dipasang pada tabel link baru`() {
        assertTrue(sql.contains("custom_field_relation_links"), "tabel link harus dibuat")
        assertTrue(
            sql.contains("apply_tenant_rls_in('crm_sales', 'custom_field_relation_links')"),
            "RLS schema-aware wajib dipasang (isolasi tenant)"
        )
    }
}
