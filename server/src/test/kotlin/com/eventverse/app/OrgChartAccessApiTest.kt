package com.eventverse.app

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Penjagaan wewenang pada rute Bagan Organisasi.
 *
 * Berkas ini lahir dari empat bug yang lolos sampai pengujian manual, dan semuanya layak diingat
 * karena bentuknya sangat umum:
 *
 * 1. **Menu disembunyikan, endpoint dibiarkan terbuka.** Operator yang matriksnya menutup modul
 *    ORG_CHART tetap menerima seluruh daftar karyawan — lengkap dengan email dan telepon — hanya
 *    dengan memanggil `GET /api/tenant/employees` memakai tokennya sendiri.
 * 2. **`scope` dibaca tanpa memeriksa `level`.** `ModuleAccessConfig` memakai
 *    `ALL_TENANT_DATA` sebagai scope bawaan, termasuk saat `level = NONE`. Kode penyaring yang
 *    hanya melihat scope karenanya membaca "tanpa akses" sebagai "seluruh data pabrik".
 * 3. **Jangkauan data hanya menyaring baca, tidak tulis.** Seorang kepala Penjualan dengan
 *    jangkauan sempit berhasil membuat karyawan di divisi Gudang — lalu karyawan yang baru saja
 *    ia buat itu tidak muncul di daftarnya sendiri.
 * 4. **List menyaring dengan benar, detail per-id tidak.** `GET /employees` menyaring 12 menjadi
 *    7, tetapi id karyawan berpola (`emp-joko`) dan `GET /employees/{id}` memeriksa *level* tanpa
 *    pernah memeriksa *scope* — melewati seluruh penyaringan di atas dengan menebak satu id. Dan
 *    T-Shape membawa penumpang gelap: memvalidasi id fokusnya saja tidak cukup, karena
 *    `superior`/`peerHeads`/`subordinates` bisa membawa orang yang sama sekali di luar jangkauan.
 *
 * Keempatnya tidak tertangkap satu pun unit test domain, karena domainnya memang benar — yang
 * salah adalah cara route memakainya.
 */
class OrgChartAccessApiTest {

    private val slug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")

    private val salesDeptId = "dept-sales"
    private val warehouseDeptId = "dept-warehouse"

    private fun tenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(slug),
                    name = TenantName("Pabrik Uji Wewenang"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    private fun role(id: String, deptId: String?, level: AccessLevel, scope: DataScope) = CustomRole(
        id = RoleId(id),
        tenantId = tenantId,
        name = "Jabatan $id",
        description = "",
        departmentId = deptId,
        modulePermissions = mapOf(
            GarmentModules.ORG_CHART to ModuleAccessConfig(level, scope)
        )
    )

    /**
     * Merakit aplikasi uji lengkap dengan divisi, karyawan, dan matriks jabatan.
     *
     * Karyawan sengaja disebar ke dua divisi supaya penyaringan jangkauan punya sesuatu untuk
     * dibuang — daftar yang seluruhnya satu divisi akan lolos uji apa pun.
     */
    private fun ApplicationTestBuilder.installApp(vararg roles: CustomRole) {
        val deptRepo = InMemoryDepartmentRepository()
        val empRepo = InMemoryEmployeeRepository()
        val roleRepo = InMemoryRoleRepository()

        runBlocking {
            deptRepo.restoreDefaultPresets(tenantId)
            val depts = deptRepo.findAllByTenant(tenantId)
            empRepo.restoreDefaultPresets(tenantId, depts)
            roles.forEach { roleRepo.save(it) }
        }

        application {
            module(
                tenantRepository = tenantRepo(),
                departmentRepository = deptRepo,
                employeeRepository = empRepo,
                roleRepository = roleRepo,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository()
            )
        }
    }

    private suspend fun HttpResponse.employeeCount(): Int =
        Regex("\"id\"\\s*:").findAll(bodyAsText()).count()

    // ── Bug 1: endpoint terbuka meski modulnya tertutup ──────────────────────────────────────

    @Test
    fun listEmployees_whenModuleAccessIsNone_shouldBeForbidden() = testApplication {
        installApp(role("role-operator", salesDeptId, AccessLevel.NONE, DataScope.ALL_TENANT_DATA))

        val response = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-operator", departmentId = salesDeptId)
        }

        assertEquals(
            HttpStatusCode.Forbidden,
            response.status,
            "Menyembunyikan menu bukan penjagaan: endpoint-nya harus ikut menolak"
        )
    }

    @Test
    fun listEmployees_whenModuleAccessIsNone_shouldLeakNoEmployeeData() = testApplication {
        installApp(role("role-operator", salesDeptId, AccessLevel.NONE, DataScope.ALL_TENANT_DATA))

        val body = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-operator", departmentId = salesDeptId)
        }.bodyAsText()

        // Penolakan yang tetap membocorkan nama di badan pesan sama saja dengan tidak menolak.
        assertFalse(body.contains("@"), "Penolakan tidak boleh membawa data karyawan apa pun")
    }

    // ── Akses Baca Global (GLOBAL_ONLY) ──────────────────────────────────────────────────

    @Test
    fun listEmployees_withViewAccess_shouldSeeAllEmployeesAcrossDepartments() = testApplication {
        installApp(
            role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
            role("role-hrd", warehouseDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA)
        )

        val salesHeadCount = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }.employeeCount()

        val hrdCount = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-hrd", departmentId = warehouseDeptId)
        }.employeeCount()

        assertTrue(salesHeadCount > 0, "Prasyarat: data contoh harus ada")
        assertEquals(
            hrdCount,
            salesHeadCount,
            "Karena modul berstatus GLOBAL_ONLY, seluruh pengguna berhak VIEW melihat seluruh karyawan pabrik"
        )
    }

    // ── Wewenang tulis ───────────────────────────────────────────────────────────────────────

    @Test
    fun createEmployee_withViewOnlyAccess_shouldBeForbidden() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))

        val response = client.post("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"Uji Tembus","email":"uji@test.local","departmentId":"$salesDeptId",
                   |"level":"STAFF_OPERATOR","roleTitle":"Staf","phone":""}""".trimMargin()
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun restorePresets_withOperateAccess_shouldStillRequireManage() = testApplication {
        // Memuat ulang template menimpa seluruh struktur organisasi. Itu bukan pekerjaan harian,
        // jadi OPERATE saja tidak cukup.
        installApp(role("role-spv", salesDeptId, AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA))

        val response = client.post("/api/tenant/employees/restore-presets") {
            asStaff(slug, customRoleId = "role-spv", departmentId = salesDeptId)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun createEmployee_withOperateAccess_shouldSucceed() = testApplication {
        installApp(role("role-spv", salesDeptId, AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA))

        val response = client.post("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-spv", departmentId = salesDeptId)
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"Staf Baru","email":"staf.baru@test.local","departmentId":"$salesDeptId",
                   |"level":"STAFF_OPERATOR","roleTitle":"Staf","phone":""}""".trimMargin()
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun restorePresets_withFullScopeAndManage_shouldSucceed() = testApplication {
        installApp(role("role-hrd", salesDeptId, AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA))

        val response = client.post("/api/tenant/employees/restore-presets") {
            asStaff(slug, customRoleId = "role-hrd", departmentId = salesDeptId)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Detail per-id & T-Shape pada modul GLOBAL_ONLY ──────────────────────────────────────

    @Test
    fun getEmployeeDetail_withViewAccess_shouldSucceedForAnyEmployee() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))

        // emp-joko (PPIC) dan emp-budi (Sales) sama-sama dapat dilihat karena bagan bersifat enterprise-wide
        val resJoko = client.get("/api/tenant/employees/emp-joko") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }
        assertEquals(HttpStatusCode.OK, resJoko.status)

        val resBudi = client.get("/api/tenant/employees/emp-budi") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }
        assertEquals(HttpStatusCode.OK, resBudi.status)
    }

    @Test
    fun tShape_withViewAccess_shouldIncludeSurroundingNodesAcrossDepartments() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))

        val body = client.get("/api/tenant/employees/emp-budi/t-shape") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }.bodyAsText()

        assertTrue(body.contains("hendra.owner@wemade.id"), "Superior direktur harus ada pada bagan global")
        assertTrue(body.contains("emp-joko"), "Peer heads antar divisi harus tampil pada bagan global")
        assertTrue(body.contains("emp-budi"), "Fokus node harus ada")
    }

    // ── Jalur lama tidak boleh ikut tertutup ─────────────────────────────────────────────────

    @Test
    fun listEmployees_withTokenCarryingNoFactoryIdentity_shouldBehaveAsBefore() = testApplication {
        // Token tanpa divisi dan tanpa jabatan tidak punya sumbu wewenang sama sekali. Menutupnya
        // akan mematikan pemasangan route lama alih-alih menjaganya.
        installApp()

        val response = client.get("/api/tenant/employees") { asTenant(slug) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.employeeCount() > 0)
    }
}
