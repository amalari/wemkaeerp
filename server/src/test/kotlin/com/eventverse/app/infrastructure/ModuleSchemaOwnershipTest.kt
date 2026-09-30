package com.eventverse.app.infrastructure

import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Penjaga B8: batas modul terlihat di database. Setiap tabel modul tinggal di schema modulnya, `public` hanya untuk
 * platform, dan pemindahan tidak diam-diam mematikan isolasi tenant (RLS + hak akses `wemade_app`).
 *
 * Tabel baru yang tidak didaftarkan di [ModuleSchemaMap] menggagalkan test ini. Itu disengaja: tanpanya, tabel modul
 * baru (termasuk modul hasil AI) kembali menumpuk di `public`.
 */
class ModuleSchemaOwnershipTest {

    private companion object {
        /**
         * Utang RLS yang **sudah ada sebelum B8** (baseline DB B 2026-09-29): tabel ber-`tenant_id` tanpa row level
         * security. Bukan akibat pemindahan schema; dicicil terpisah. Hanya boleh berkurang.
         */
        val RLS_DEBT: Set<String> = emptySet() // V77 melunasi 10 tabel baseline 2026-09-29
    }

    @BeforeTest
    fun setup() { DatabaseFactory.init() }

    private fun <T> rows(sql: String, map: (java.sql.ResultSet) -> T): List<T> = transaction {
        exec(sql) { rs -> buildList { while (rs.next()) add(map(rs)) } }
    }.orEmpty()

    private val moduleSchemas get() = (ModuleSchemaMap.byModule.keys.map(ModuleSchemaMap::schemaOf) + ModuleSchemaMap.platformSchemas.keys)

    @Test
    fun everyModuleTable_livesInItsModuleSchema_andPublicHoldsOnlyPlatformTables() {
        val actual = rows("SELECT schemaname || '.' || tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema','ops')") { it.getString(1) }.toSet()

        val missing = ModuleSchemaMap.expectedQualified - actual
        assertEquals(emptySet(), missing, "tabel modul belum ada di schema modulnya")

        val strayInPublic = actual.filter { it.startsWith("public.") }.map { it.removePrefix("public.") }.toSet() - ModuleSchemaMap.platform
        assertEquals(emptySet(), strayInPublic, "tabel non-platform di public — daftarkan di ModuleSchemaMap dan pindahkan ke schema modulnya")

        val unknown = actual.filterNot { it.startsWith("public.") } - ModuleSchemaMap.expectedQualified
        assertEquals(emptySet(), unknown.toSet(), "tabel di schema modul yang tidak terdaftar di ModuleSchemaMap")
    }

    @Test
    fun movedTenantTables_keepRowLevelSecurity_andAppRoleGrants() {
        val tenantScoped = rows(
            """
            SELECT c.table_schema || '.' || c.table_name FROM information_schema.columns c
            WHERE c.column_name = 'tenant_id' AND c.table_schema IN (${moduleSchemas.joinToString { "'$it'" }})
            """.trimIndent()
        ) { it.getString(1) }
        assertTrue(tenantScoped.isNotEmpty(), "tidak ada tabel ber-tenant_id di schema modul")

        val withoutRls = rows(
            """
            SELECT n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r' AND NOT c.relrowsecurity AND n.nspname IN (${moduleSchemas.joinToString { "'$it'" }})
            """.trimIndent()
        ) { it.getString(1) }.toSet()
        val lacking = (tenantScoped.toSet() intersect withoutRls).map { it.substringAfter('.') }.toSet()
        assertEquals(emptySet(), lacking - RLS_DEBT, "tabel tenant tanpa RLS (baru, atau hilang saat dipindah)")
        // Ratchet: utang yang sudah dibayar wajib dihapus dari ledger, supaya ledger hanya bisa mengecil.
        assertEquals(emptySet(), RLS_DEBT - lacking, "tabel ini kini ber-RLS — hapus dari RLS_DEBT")

        val missingGrant = tenantScoped.filterNot { t ->
            rows("SELECT has_table_privilege('wemade_app', '$t', 'SELECT,INSERT,UPDATE,DELETE')") { it.getBoolean(1) }.single()
        }
        assertEquals(emptyList(), missingGrant, "wemade_app tidak bisa membaca/menulis tabel modul")

        val missingUsage = moduleSchemas.filterNot { s -> rows("SELECT has_schema_privilege('wemade_app', '$s', 'USAGE')") { it.getBoolean(1) }.single() }
        assertEquals(emptyList(), missingUsage, "wemade_app tanpa USAGE pada schema modul")
    }
}
