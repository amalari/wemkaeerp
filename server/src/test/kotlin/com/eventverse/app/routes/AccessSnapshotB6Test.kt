package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.rbac.AccessDecisionCodec
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Alarm B6** (TRD-PLAT-001 NFR Security): keputusan wewenang nyata dari DB repo B dibekukan sebelum `BusinessModule`
 * dipindah menjadi data. Setiap tahap B6c–B6f wajib menghasilkan snapshot **identik**. Selisih = ada orang yang
 * kehilangan atau mendapat akses karena refactor.
 *
 * Prinsipal yang diuji, semuanya lewat `GET /api/tenant/me/access` dengan repository Postgres sungguhan:
 * - setiap pengguna di tabel `users` (peran, jabatan, divisi apa adanya);
 * - setiap jabatan tenant tanpa divisi;
 * - setiap jabatan × divisi (dan tanpa jabatan × divisi) di tenant yang punya penugasan divisi.
 *
 * Tenant `factory-NNNNN` dikecualikan: dibuat test integrasi lain di DB dev dan jumlahnya bertambah setiap run.
 *
 * Tulis ulang snapshot **hanya** saat data DB sengaja berubah: `WRITE_ACCESS_SNAPSHOT=1 ./gradlew :server:test --tests '*AccessSnapshotB6Test*'`.
 * Dihapus setelah B6 selesai.
 */
class AccessSnapshotB6Test {

    private val snapshotFile = File("src/test/resources/access-snapshot-b6.txt")

    private data class Principal(val label: String, val slug: String, val token: String)

    private fun principals(): List<Principal> {
        val url = "jdbc:postgresql://${System.getenv("DB_HOST") ?: "localhost"}:${System.getenv("DB_PORT") ?: "5432"}/${System.getenv("DB_NAME") ?: "wemade_erp"}"
        DriverManager.getConnection(url, System.getenv("DB_USER") ?: "postgres", System.getenv("DB_PASSWORD") ?: "postgres").use { c ->
            fun rows(sql: String): List<List<String?>> = c.createStatement().executeQuery(sql).use { rs ->
                val n = rs.metaData.columnCount
                buildList { while (rs.next()) add((1..n).map { rs.getString(it) }) }
            }
            val out = mutableListOf<Principal>()
            rows("select t.slug, u.username, u.role, u.custom_role_id, u.department_id from users u join tenants t on t.id = u.tenant_id where t.slug !~ '^factory-[0-9]+$' order by t.slug, u.username")
                .forEach { (slug, user, role, roleId, dept) ->
                    val r = Role.valueOf(role!!)
                    out += Principal("user:$slug:$user", slug!!, TestAuth.principalToken(slug, r, roleId, dept))
                }
            val roles = rows("select t.slug, r.id from custom_roles r join tenants t on t.id = r.tenant_id where t.slug !~ '^factory-[0-9]+$' order by 1, 2")
            roles.forEach { (slug, roleId) ->
                out += Principal("role:$slug:$roleId", slug!!, TestAuth.principalToken(slug, Role.SALES, roleId, null))
            }
            rows("select distinct t.slug, a.department_id from department_module_assignments a join tenants t on t.id = a.tenant_id where t.slug !~ '^factory-[0-9]+$' order by 1, 2")
                .forEach { (slug, dept) ->
                    (roles.filter { it[0] == slug }.map { it[1] } + null).forEach { roleId ->
                        out += Principal("dept:$slug:$dept:${roleId ?: "-"}", slug!!,
                            TestAuth.principalToken(slug, Role.SALES, roleId, dept))
                    }
                }
            return out
        }
    }

    @Test
    fun accessDecisions_ofEveryRealPrincipal_areUnchanged() = testApplication {
        DatabaseFactory.init()
        application { module() }
        val lines = principals().flatMap { p ->
            val body = client.get("/api/tenant/me/access") {
                header("X-Tenant-Slug", p.slug)
                header(HttpHeaders.Authorization, "Bearer ${p.token}")
            }.bodyAsText()
            AccessDecisionCodec.decode(JsonParser.parseObject(body)).entries
                .sortedBy { it.key.name }
                .map { (m, d) -> "${p.label}|${m.name}|${d.config.level}|${d.config.scope}|${d.config.allowedDesks?.sorted()}|${d.source}" }
        }
        if (System.getenv("WRITE_ACCESS_SNAPSHOT") == "1") {
            snapshotFile.parentFile.mkdirs()
            snapshotFile.writeText(lines.joinToString("\n") + "\n")
        }
        val expected = snapshotFile.readLines().filter { it.isNotBlank() }
        assertEquals(expected.size, lines.size, "jumlah (prinsipal × modul) berubah")
        val diff = expected.zip(lines).filter { (a, b) -> a != b }
        assertEquals(emptyList(), diff.take(20), "keputusan wewenang berubah (maks. 20 selisih pertama ditampilkan)")
    }
}
