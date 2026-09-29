package com.eventverse.app.infrastructure

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Isolasi tenant yang **benar-benar ditegakkan**, bukan hanya "policy ada".
 *
 * Koneksi test adalah owner (superuser), yang melewati RLS. Karena itu setiap pemeriksaan berjalan di dalam transaksi
 * dengan `SET LOCAL ROLE wemade_app`, peran yang dipakai `DatabaseFactory` untuk kerja ber-tenant. Untuk setiap tabel
 * ber-`tenant_id` di schema modul maupun `public`, dengan konteks tenant T, tidak boleh ada satu pun baris milik tenant
 * lain yang terbaca.
 */
class TenantRlsIsolationTest {

    @BeforeTest
    fun setup() { DatabaseFactory.init() }

    /** Koneksi **owner** eksplisit. `transaction {}` tanpa argumen memakai database terakhir, yaitu pool tenant bila DB_APP_USER aktif. */
    private fun <T> owner(block: org.jetbrains.exposed.sql.Transaction.() -> T): T =
        kotlinx.coroutines.runBlocking { DatabaseFactory.dbQuery { org.jetbrains.exposed.sql.transactions.TransactionManager.current().block() } }

    private val schemas = ModuleSchemaMap.byModule.keys.map(ModuleSchemaMap::schemaOf) + "public"

    private fun tenantTables(): List<String> = owner {
        exec(
            """
            SELECT table_schema || '.' || table_name FROM information_schema.columns
            WHERE column_name = 'tenant_id' AND table_schema IN (${schemas.joinToString { "'$it'" }})
              AND table_name NOT IN ('tenant_billable_quotes')
            ORDER BY 1
            """.trimIndent()
        ) { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
    }.orEmpty()

    /** Jumlah baris [table] yang terlihat sebagai wemade_app dengan konteks [tenant]: (milik sendiri, milik lain). */
    private fun visibleAs(tenant: String, table: String): Pair<Long, Long> = owner {
        exec("SET LOCAL ROLE wemade_app")
        exec("SET LOCAL app.current_tenant_id = '$tenant'")
        exec("SELECT count(*) FILTER (WHERE tenant_id = '$tenant'), count(*) FILTER (WHERE tenant_id <> '$tenant') FROM $table") { rs ->
            rs.next(); rs.getLong(1) to rs.getLong(2)
        }!!
    }

    private fun ownerCount(table: String, tenant: String): Long = owner {
        exec("SELECT count(*) FROM $table WHERE tenant_id = '$tenant'") { rs -> rs.next(); rs.getLong(1) }!!
    }

    @Test
    fun appRole_seesOnlyItsOwnTenantRows_inEveryTenantTable() {
        val tables = tenantTables()
        assertTrue(tables.size > 40, "daftar tabel tenant mencurigakan: ${tables.size}")
        val leaks = mutableListOf<String>()
        listOf("ten-demo-001", "ten-klinik-uji").forEach { tenant ->
            tables.forEach { t ->
                val (own, others) = visibleAs(tenant, t)
                if (others > 0) leaks += "$t: tenant $tenant melihat $others baris tenant lain"
                // RLS tidak boleh juga menyembunyikan data tenant sendiri.
                if (own != ownerCount(t, tenant)) leaks += "$t: tenant $tenant hanya melihat $own dari ${ownerCount(t, tenant)} barisnya sendiri"
            }
        }
        assertEquals(emptyList(), leaks)
    }

    @Test
    fun whenAppUserConfigured_tenantQueriesReallyRunAsIt() {
        // Suite yang dijalankan dengan DB_APP_USER harus benar-benar memakai pool tenant; tanpa ini run "RLS aktif"
        // bisa lulus diam-diam lewat pool owner (daemon Gradle yang tidak mewarisi env, misalnya).
        val appUser = System.getenv("DB_APP_USER") ?: return
        val user = kotlinx.coroutines.runBlocking {
            DatabaseFactory.dbQuery(com.eventverse.app.domain.tenant.TenantId("ten-demo-001")) {
                org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec("SELECT current_user") { rs -> rs.next(); rs.getString(1) }
            }
        }
        assertEquals(appUser, user)
    }

    @Test
    fun noRepository_opensItsOwnTransaction_bypassingDatabaseFactory() {
        // Transaksi mentah = pool tenant tanpa konteks tenant: di bawah RLS baca kosong & tulis ditolak, diam-diam.
        // Ditemukan di PostgresWashingBatchRepository (B8). Semua akses DB lewat DatabaseFactory.dbQuery.
        val offenders = java.io.File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" && it.name != "DatabaseFactory.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l ->
                if (Regex("""(^|[^A-Za-z.])(newSuspendedTransaction|transaction)\s*[({]""").containsMatchIn(l) && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")) "${f.name}:${i + 1}" else null
            } }.toList()
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun appRole_withoutTenantContext_seesNothing() {
        // Lupa SET app.current_tenant_id = fail-closed: tidak ada baris sama sekali, bukan semua tenant.
        val t = "crm_sales.crm_leads"
        val seen = owner {
            exec("SET LOCAL ROLE wemade_app")
            exec("SELECT count(*) FROM $t") { rs -> rs.next(); rs.getLong(1) }!!
        }
        assertEquals(0, seen)
    }
}
