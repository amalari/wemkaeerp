package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.*

/**
 * Menguji **pipeline**-nya, bukan aturannya.
 *
 * Aturan wewenang sudah diuji di `AccessDecisionEngineTest`. Yang diuji di sini adalah hal yang
 * membuat aturan itu sampai ke layar: begitu persona atau matriksnya berubah, apakah wewenang
 * efektif ikut berubah **tanpa ada yang memanggil "hitung ulang"**? Justru pemanggilan yang
 * terlupakan itulah kegagalan yang paling mungkin terjadi, dan paling sulit terlihat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RbacAccessPolicyRepositoryTest {

    /**
     * Scope repository sengaja **bukan** scope milik `runTest`.
     *
     * `effectivePermissions` dibangun dengan `stateIn(..., SharingStarted.Eagerly)`, yang berarti
     * ada coroutine yang memang tidak pernah selesai selama repository hidup. Dijadikan anak dari
     * `runTest`, coroutine itu membuat setiap test menggantung sampai batas waktu — kegagalan yang
     * menyesatkan, karena yang salah adalah pemasangan testnya, bukan kodenya.
     *
     * `UnconfinedTestDispatcher` membuat perubahan state merambat seketika, sehingga assertion
     * dapat ditulis persis setelah aksinya tanpa penjadwalan manual.
     */
    private val scheduler = TestCoroutineScheduler()
    private val repositoryScope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val tenantId = TenantId("ten-demo-001")

    private lateinit var repository: RbacAccessPolicyRepository

    private val jabatanA = CustomRole(
        id = RoleId("role-a"),
        tenantId = tenantId,
        name = "Kepala Penjualan",
        description = "",
        departmentId = "dept-sales",
        modulePermissions = mapOf(
            BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE),
            BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.VIEW)
        )
    )

    private val jabatanB = CustomRole(
        id = RoleId("role-b"),
        tenantId = tenantId,
        name = "Staf Gudang",
        description = "",
        departmentId = "dept-warehouse",
        modulePermissions = mapOf(
            BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE),
            BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.MANAGE)
        )
    )

    private fun persona(name: String, departmentId: String, role: CustomRole) = TestingPersona(
        userId = "usr-${role.id.value}",
        name = name,
        tenantId = tenantId,
        tenantSlug = "wemade-demo",
        departmentId = departmentId,
        departmentName = departmentId,
        roleId = role.id,
        roleTitle = role.name
    )

    @BeforeTest
    fun setup() {
        // Tanpa API client: repository harus tetap berfungsi penuh secara offline, dan itulah
        // yang membuatnya bisa diuji tanpa server sama sekali.
        repository = RbacAccessPolicyRepository(
            apiClientProvider = { null },
            scope = repositoryScope
        )
        repository.syncRoles(listOf(jabatanA, jabatanB))
    }

    @AfterTest
    fun teardown() {
        repositoryScope.cancel()
    }

    @Test
    fun `switching persona should replace effective permissions entirely`() {
        repository.setPersona(persona("Budi", "dept-sales", jabatanA))

        assertEquals(AccessLevel.MANAGE, repository.accessFor(BusinessModule.CRM_SALES).level)
        assertEquals(AccessLevel.VIEW, repository.accessFor(BusinessModule.INVENTORY).level)

        repository.setPersona(persona("Siti", "dept-warehouse", jabatanB))

        assertEquals(AccessLevel.NONE, repository.accessFor(BusinessModule.CRM_SALES).level)
        assertEquals(AccessLevel.MANAGE, repository.accessFor(BusinessModule.INVENTORY).level)
    }

    @Test
    fun `editing the matrix should update permissions without re-selecting the persona`() {
        repository.setPersona(persona("Budi", "dept-sales", jabatanA))
        assertEquals(AccessLevel.MANAGE, repository.accessFor(BusinessModule.CRM_SALES).level)

        // Admin menutup akses CRM untuk jabatan A di layar RBAC.
        repository.syncRoles(
            listOf(
                jabatanA.updateModuleAccess(BusinessModule.CRM_SALES, AccessLevel.NONE),
                jabatanB
            )
        )

        assertEquals(
            AccessLevel.NONE,
            repository.accessFor(BusinessModule.CRM_SALES).level,
            "Perubahan matriks harus langsung terasa tanpa memilih ulang persona"
        )
    }

    @Test
    fun `assigning a module to a division should open it for that division's persona`() {
        repository.setPersona(persona("Budi", "dept-sales", jabatanA))
        assertEquals(AccessLevel.NONE, repository.accessFor(BusinessModule.QUALITY_CONTROL).level)

        repository.syncAssignments(
            mapOf(
                BusinessModule.QUALITY_CONTROL to listOf(
                    DepartmentModuleAssignment(
                        departmentId = "dept-sales",
                        departmentName = "Penjualan",
                        accessLevel = AccessLevel.OPERATE
                    )
                )
            )
        )

        val decision = repository.accessDecisions.value.getValue(BusinessModule.QUALITY_CONTROL)
        assertEquals(AccessLevel.OPERATE, decision.config.level)
        assertEquals(AccessSource.DEPARTMENT, decision.source)
        assertTrue(
            decision.grantedByDepartmentOnly,
            "Harus ditandai sebagai pemberian divisi, bukan bukti jabatannya sudah benar"
        )
    }

    @Test
    fun `a division added later should work for a persona pointed at it`() {
        // Divisi dan jabatan yang belum ada saat kode ini ditulis.
        val jabatanBaru = CustomRole(
            id = RoleId("role-sablon-901"),
            tenantId = tenantId,
            name = "Kepala Sablon",
            description = "",
            departmentId = "dept-sablon-901",
            modulePermissions = mapOf(
                BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.OPERATE)
            )
        )
        repository.syncRoles(listOf(jabatanA, jabatanB, jabatanBaru))
        repository.setPersona(persona("Wati", "dept-sablon-901", jabatanBaru))

        assertEquals(AccessLevel.OPERATE, repository.accessFor(BusinessModule.PRODUCTION_MRP).level)
        assertEquals(AccessLevel.NONE, repository.accessFor(BusinessModule.CRM_SALES).level)
    }

    @Test
    fun `clearing the persona should close every module`() {
        repository.setPersona(persona("Budi", "dept-sales", jabatanA))
        assertTrue(repository.accessFor(BusinessModule.CRM_SALES).isAccessible)

        repository.setPersona(null)

        assertTrue(
            BusinessModule.entries.none { repository.accessFor(it).isAccessible },
            "Tanpa persona tidak ada modul yang boleh terbuka"
        )
    }
}
