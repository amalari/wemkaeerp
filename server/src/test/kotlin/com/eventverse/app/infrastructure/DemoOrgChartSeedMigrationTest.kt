package com.eventverse.app.infrastructure

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD-PLAT-010 T2/Q2: migrasi V98 menyemai divisi dan karyawan untuk `ten-demo-cmt` dan `ten-demo-d2c` di Postgres
 * sungguhan (DB scratch, Flyway jalan di `DatabaseFactory.init()`). Idempoten diuji dengan menjalankan ulang isi
 * berkas migrasinya; `ten-demo-001` tidak boleh berubah.
 */
class DemoOrgChartSeedMigrationTest {

    private val demoTenants = listOf("ten-demo-cmt", "ten-demo-d2c")
    private val reference = "ten-demo-001"

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
    }

    private fun count(table: String, tenant: String): Long = runBlocking {
        DatabaseFactory.dbQuery {
            TransactionManager.current().exec(
                "SELECT count(*) FROM org_chart.$table WHERE tenant_id = '$tenant' AND archived_at IS NULL"
            ) { rs -> rs.next(); rs.getLong(1) } ?: -1L
        }
    }

    /** Fingerprint isi baris tenant (bukan sekadar jumlah) supaya perubahan diam-diam pun terdeteksi. */
    private fun fingerprint(tenant: String): String = runBlocking {
        DatabaseFactory.dbQuery {
            TransactionManager.current().exec(
                "SELECT coalesce(string_agg(d::text, '|' ORDER BY d.id), '') FROM org_chart.departments d WHERE tenant_id = '$tenant'"
            ) { rs -> rs.next(); rs.getString(1) } +
                TransactionManager.current().exec(
                    "SELECT coalesce(string_agg(e::text, '|' ORDER BY e.id), '') FROM org_chart.employees e WHERE tenant_id = '$tenant'"
                ) { rs -> rs.next(); rs.getString(1) }
        }.orEmpty()
    }

    private fun migrationStatements(): List<String> {
        val sql = requireNotNull(javaClass.classLoader.getResource("db/migration/V98__seed_org_chart_demo_cmt_d2c.sql")) {
            "V98 tidak ditemukan di classpath"
        }.readText()
        return sql.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
            .split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun demoCmtAndD2c_haveSeededDepartmentsAndEmployeesEquivalentToDemo001() {
        val refDepts = count("departments", reference)
        val refEmps = count("employees", reference)
        assertTrue(refDepts > 0 && refEmps > 0, "Prasyarat: ten-demo-001 berisi data V4/V7")

        demoTenants.forEach { tenant ->
            assertEquals(refDepts, count("departments", tenant), "divisi $tenant")
            assertEquals(refEmps, count("employees", tenant), "karyawan $tenant")
        }
    }

    @Test
    fun seededEmployees_referenceOnlyTheirOwnTenant_andExecutiveHasNoDepartment() {
        demoTenants.forEach { tenant ->
            val crossTenant = runBlocking {
                DatabaseFactory.dbQuery {
                    TransactionManager.current().exec(
                        """
                        SELECT count(*) FROM org_chart.employees e
                        LEFT JOIN org_chart.departments d ON d.id = e.department_id
                        LEFT JOIN org_chart.employees b ON b.id = e.reports_to_id
                        WHERE e.tenant_id = '$tenant'
                          AND ((d.id IS NOT NULL AND d.tenant_id <> e.tenant_id) OR (b.id IS NOT NULL AND b.tenant_id <> e.tenant_id))
                        """.trimIndent()
                    ) { rs -> rs.next(); rs.getLong(1) }
                }
            }
            assertEquals(0L, crossTenant, "$tenant tidak boleh menunjuk divisi/atasan tenant lain")
            val executivesWithDept = runBlocking {
                DatabaseFactory.dbQuery {
                    TransactionManager.current().exec(
                        "SELECT count(*) FROM org_chart.employees WHERE tenant_id = '$tenant' AND level = 'EXECUTIVE' AND department_id IS NOT NULL"
                    ) { rs -> rs.next(); rs.getLong(1) }
                }
            }
            assertEquals(0L, executivesWithDept, "Direksi non-divisi (V7)")
        }
    }

    @Test
    fun rerunningMigration_isIdempotent_andLeavesDemo001Untouched() {
        val before = (demoTenants + reference).associateWith { fingerprint(it) }

        runBlocking {
            DatabaseFactory.dbQuery { migrationStatements().forEach { TransactionManager.current().exec(it) } }
        }

        (demoTenants + reference).forEach { tenant ->
            assertEquals(before.getValue(tenant), fingerprint(tenant), "$tenant berubah setelah migrasi dijalankan ulang")
        }
    }

    @Test
    fun repository_readsSeededDemoTenantsThroughNormalPath() = runBlocking {
        demoTenants.forEach { tenant ->
            val depts: List<Department> = PostgresDepartmentRepository().findAllByTenant(TenantId(tenant))
            assertTrue(depts.isNotEmpty(), "$tenant terbaca lewat repository (jalur RLS aplikasi)")
        }
    }
}
