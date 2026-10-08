package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

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
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
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
 * **Opt-in** (`RUN_ACCESS_SNAPSHOT=1`); tanpa itu test dilewati dan tampil *skipped*, bukan hilang. Alasannya: seluruh
 * `:server:test` dikunci ke database ber-nama `scratch` (`server/build.gradle.kts`, pagar di `DatabaseFactory.init()`),
 * sedangkan test ini membaca **data nyata** (pengguna, jabatan, penugasan divisi). Di scratch yang kosong prinsipalnya
 * lebih sedikit, jadi selisih jumlahnya bukan bukti ada yang kehilangan akses — dijalankan otomatis ia hanya
 * menutupi kegagalan sungguhan. Alarm ini bermakna hanya terhadap salinan data nyata, sebelum/selama B6c–B6f.
 *
 * **Prosedur tiap tahap B6** (wajib sebelum merge; lihat PLAN-dual-track §B6):
 * 1. Salin DB dev ke DB scratch khusus: `docker exec wemade-postgres sh -c "createdb -U postgres wemake_erp_scratch_b6 &&
 *    pg_dump -U postgres wemake_erp | psql -U postgres wemake_erp_scratch_b6"`.
 * 2. **Awal B6c**, buat snapshot baru dari salinan itu — snapshot lama dibuat dari DB dev pada waktu lain dan datanya
 *    sudah bergeser, jadi jangan dianggap acuan:
 *    `DB_NAME=wemake_erp_scratch_b6 RUN_ACCESS_SNAPSHOT=1 WRITE_ACCESS_SNAPSHOT=1 ./gradlew :server:test --tests '*AccessSnapshotB6Test*'`.
 * 3. Tiap tahap berikutnya, bandingkan dengan snapshot itu (tanpa `WRITE_ACCESS_SNAPSHOT`):
 *    `DB_NAME=wemake_erp_scratch_b6 RUN_ACCESS_SNAPSHOT=1 ./gradlew :server:test --tests '*AccessSnapshotB6Test*'`.
 *
 * Tulis ulang snapshot **hanya** saat data sengaja berubah, dengan sadar. **Dipertahankan sampai cutover** repo B
 * menggantikan produksi: alarm tambahan untuk setiap perubahan RBAC/entitlement. Jangan menghapus test ini; menjadikannya
 * opt-in hanya memindahkan *kapan* ia dijalankan, bukan *apakah*.
 */
class AccessSnapshotB6Test {

    private val snapshotFile = File("src/test/resources/access-snapshot-b6.txt")

    @BeforeTest
    fun hanyaBilaDiminta() = assumeTrue(
        "RUN_ACCESS_SNAPSHOT bukan 1 - alarm B6 dilewati (butuh salinan data nyata; lihat KDoc kelas)",
        System.getenv("RUN_ACCESS_SNAPSHOT") == "1"
    )

    private data class Principal(val label: String, val slug: String, val token: String)

    private fun principals(): List<Principal> {
        val url = "jdbc:postgresql://${System.getenv("DB_HOST") ?: "localhost"}:${System.getenv("DB_PORT") ?: "5432"}/${System.getenv("DB_NAME") ?: "wemade_erp"}"
        DriverManager.getConnection(url, System.getenv("DB_USER") ?: "postgres", System.getenv("DB_PASSWORD") ?: "postgres").use { c ->
            fun rows(sql: String): List<List<String?>> = c.createStatement().executeQuery(sql).use { rs ->
                val n = rs.metaData.columnCount
                buildList { while (rs.next()) add((1..n).map { rs.getString(it) }) }
            }
            // Daftar prinsipal dibaca dari DB_NAME (default: DB repo A, belum dimigrasi) — tabel RBAC bisa di schema
            // modul (B8, V76) atau masih di public. Menemukannya di mana pun menjaga set prinsipal tetap sama.
            fun table(name: String) = rows("select coalesce(to_regclass('dynamic_rbac.$name'), to_regclass('public.$name'))::text").single()[0]!!
            val customRoles = table("custom_roles")
            val assignments = table("department_module_assignments")
            val out = mutableListOf<Principal>()
            rows("select t.slug, u.username, u.role, u.custom_role_id, u.department_id from users u join tenants t on t.id = u.tenant_id where t.slug !~ '^factory-[0-9]+$' order by t.slug, u.username")
                .forEach { (slug, user, role, roleId, dept) ->
                    val r = Role.valueOf(role!!)
                    out += Principal("user:$slug:$user", slug!!, TestAuth.principalToken(slug, r, roleId, dept))
                }
            val roles = rows("select t.slug, r.id from $customRoles r join tenants t on t.id = r.tenant_id where t.slug !~ '^factory-[0-9]+$' order by 1, 2")
            roles.forEach { (slug, roleId) ->
                out += Principal("role:$slug:$roleId", slug!!, TestAuth.principalToken(slug, Role.SALES, roleId, null))
            }
            rows("select distinct t.slug, a.department_id from $assignments a join tenants t on t.id = a.tenant_id where t.slug !~ '^factory-[0-9]+$' order by 1, 2")
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
