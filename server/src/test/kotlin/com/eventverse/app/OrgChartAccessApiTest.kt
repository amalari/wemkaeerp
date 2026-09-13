package com.eventverse.app

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
            BusinessModule.ORG_CHART to ModuleAccessConfig(level, scope)
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

    // ── Bug 2: scope sempit yang tidak benar-benar mempersempit ──────────────────────────────

    @Test
    fun listEmployees_withSubordinateScope_shouldReturnFewerThanAllTenantScope() = testApplication {
        installApp(
            role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA),
            role("role-hrd", warehouseDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA)
        )

        val narrowed = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }.employeeCount()

        val everything = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-hrd", departmentId = warehouseDeptId)
        }.employeeCount()

        assertTrue(everything > 0, "Prasyarat: data contoh harus ada untuk disaring")
        assertTrue(
            narrowed < everything,
            "SUBORDINATE_DATA harus benar-benar memangkas payload ($narrowed vs $everything); " +
                "kalau sama, scope-nya hanya label"
        )
    }

    @Test
    fun listEmployees_withSubordinateScope_shouldOnlyContainOwnDepartment() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA))

        val body = client.get("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }.bodyAsText()

        val warehouse = runBlocking {
            InMemoryDepartmentRepository().let { repo ->
                repo.restoreDefaultPresets(tenantId)
                repo.findAllByTenant(tenantId).first { it.id.value == warehouseDeptId }
            }
        }
        assertFalse(
            body.contains(warehouse.displayName),
            "Divisi lain tidak boleh muncul di payload kepala divisi Penjualan"
        )
    }

    // ── Wewenang tulis ───────────────────────────────────────────────────────────────────────

    @Test
    fun createEmployee_withViewOnlyAccess_shouldBeForbidden() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA))

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

    // ── Jangkauan data juga membatasi TULIS, bukan hanya baca ────────────────────────────────

    @Test
    fun createEmployee_intoADepartmentOutsideScope_shouldBeForbidden() = testApplication {
        installApp(role("role-spv", salesDeptId, AccessLevel.OPERATE, DataScope.SUBORDINATE_DATA))

        val response = client.post("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-spv", departmentId = salesDeptId)
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"Sisipan Lintas Divisi","email":"sisipan@test.local",
                   |"departmentId":"$warehouseDeptId","level":"STAFF_OPERATOR",
                   |"roleTitle":"Staf Gudang","phone":""}""".trimMargin()
            )
        }

        assertEquals(
            HttpStatusCode.Forbidden,
            response.status,
            "Menulis ke divisi yang tidak bisa dilihat menghasilkan data yang pembuatnya sendiri " +
                "tidak akan pernah temukan"
        )
    }

    @Test
    fun createEmployee_intoOwnDepartment_shouldStillSucceed() = testApplication {
        installApp(role("role-spv", salesDeptId, AccessLevel.OPERATE, DataScope.SUBORDINATE_DATA))

        val response = client.post("/api/tenant/employees") {
            asStaff(slug, customRoleId = "role-spv", departmentId = salesDeptId)
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"Staf Sales Baru","email":"sales.baru@test.local",
                   |"departmentId":"$salesDeptId","level":"STAFF_OPERATOR",
                   |"roleTitle":"Staf","phone":""}""".trimMargin()
            )
        }

        // Penjagaan yang menolak divisi sendiri bukan penjagaan, melainkan kerusakan.
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun restorePresets_withNarrowScope_shouldBeForbiddenEvenWithManage() = testApplication {
        // MANAGE sudah terpenuhi; yang kurang adalah jangkauannya. Memuat ulang template menimpa
        // seluruh divisi, jadi tidak ada jangkauan sempit yang masuk akal untuknya.
        installApp(role("role-head", salesDeptId, AccessLevel.MANAGE, DataScope.SUBORDINATE_DATA))

        val response = client.post("/api/tenant/employees/restore-presets") {
            asStaff(slug, customRoleId = "role-head", departmentId = salesDeptId)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun restorePresets_withFullScopeAndManage_shouldSucceed() = testApplication {
        installApp(role("role-hrd", salesDeptId, AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA))

        val response = client.post("/api/tenant/employees/restore-presets") {
            asStaff(slug, customRoleId = "role-hrd", departmentId = salesDeptId)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Detail per-id juga harus tunduk jangkauan, bukan hanya level ─────────────────────────

    @Test
    fun getEmployeeDetail_forEmployeeOutsideScope_shouldBeForbidden() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA))

        // emp-joko adalah kepala PPIC — di luar jangkauan seorang kepala Penjualan. Id-nya tebakan
        // yang wajar (pola `emp-<nama depan>`), bukan sesuatu yang perlu ditemukan lewat list dulu.
        val response = client.get("/api/tenant/employees/emp-joko") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }

        assertEquals(
            HttpStatusCode.Forbidden,
            response.status,
            "List menyaring dengan benar tidak berguna kalau detailnya bisa ditembus dengan menebak id"
        )
    }

    @Test
    fun getEmployeeDetail_forEmployeeWithinScope_shouldSucceed() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA))

        val response = client.get("/api/tenant/employees/emp-budi") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun tShape_forFocusOutsideScope_shouldBeForbidden() = testApplication {
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA))

        val response = client.get("/api/tenant/employees/emp-joko/t-shape") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun tShape_forOwnFocus_shouldStripSurroundingNodesOutsideScope() = testApplication {
        // Ini bug yang paling mudah terlewat: memvalidasi fokusnya saja terasa cukup, tetapi
        // T-Shape membawa superior dan peerHeads sebagai penumpang gelap di respons yang sama.
        installApp(role("role-sales-head", salesDeptId, AccessLevel.VIEW, DataScope.SUBORDINATE_DATA))

        val body = client.get("/api/tenant/employees/emp-budi/t-shape") {
            asStaff(slug, customRoleId = "role-sales-head", departmentId = salesDeptId)
        }.bodyAsText()

        // emp-hendra (Direktur, superior Budi) dan Joko/Siti/Anton (peerHeads divisi lain) tidak
        // boleh muncul sama sekali — mereka persis yang ditolak GET /{id} di atas.
        assertFalse(body.contains("hendra.owner@wemade.id"), "Superior di luar divisi harus tersaring")
        assertFalse(body.contains("emp-joko"), "peerHeads dari divisi lain harus tersaring")
        assertTrue(body.contains("emp-budi"), "Fokusnya sendiri harus tetap ada")
    }

    @Test
    fun tShape_withAllTenantScope_shouldKeepEverySurroundingNode() = testApplication {
        // Penjagaan yang menyaring untuk semua orang, termasuk yang berjangkauan penuh, bukan
        // penjagaan yang benar — hanya kerusakan dengan wajah berbeda.
        installApp(role("role-hrd", salesDeptId, AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))

        val body = client.get("/api/tenant/employees/emp-budi/t-shape") {
            asStaff(slug, customRoleId = "role-hrd", departmentId = salesDeptId)
        }.bodyAsText()

        assertTrue(body.contains("hendra.owner@wemade.id"))
        assertTrue(body.contains("emp-joko"))
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
