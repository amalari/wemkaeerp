package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.*

class AccessDecisionEngineTest {

    private val tenantId = TenantId("ten-demo-001")
    private val salesDept = "dept-sales"
    private val warehouseDept = "dept-warehouse"

    private fun persona(
        name: String = "Achmad",
        departmentId: String? = salesDept,
        roleId: String? = "role-sales",
        isOwner: Boolean = false,
        isSuperAdmin: Boolean = false
    ) = TestingPersona(
        userId = "usr-test",
        name = name,
        tenantId = tenantId,
        tenantSlug = "wemade-demo",
        departmentId = departmentId,
        departmentName = "Penjualan & CRM",
        roleId = roleId?.let { RoleId(it) },
        roleTitle = "Sales Eksekutif",
        isOwnerOrSuperAdmin = isOwner,
        isPlatformSuperAdmin = isSuperAdmin
    )

    private fun role(
        id: String,
        permissions: Map<BusinessModule, ModuleAccessConfig>
    ) = CustomRole(
        id = RoleId(id),
        tenantId = tenantId,
        name = "Role $id",
        description = "",
        modulePermissions = permissions
    )

    private fun assignment(
        departmentId: String,
        level: AccessLevel,
        scope: DataScope = DataScope.ALL_TENANT_DATA,
        specificRoleIds: Set<String> = emptySet()
    ) = DepartmentModuleAssignment(
        departmentId = departmentId,
        departmentName = "Divisi $departmentId",
        accessLevel = level,
        scope = scope,
        specificRoleIds = specificRoleIds
    )

    @Test
    fun `evaluate when persona is owner should always return manage`() {
        val result = AccessDecisionEngine.evaluate(
            persona = persona(name = "Hendra", departmentId = null, roleId = null, isOwner = true),
            module = BusinessModule.COSTING_HPP,
            role = null,
            assignments = emptyList()
        )

        assertEquals(AccessLevel.MANAGE, result.level)
        assertEquals(DataScope.ALL_TENANT_DATA, result.scope)
    }

    @Test
    fun `evaluate when role grants higher than department should take role access`() {
        val salesRole = role(
            "role-sales-head",
            mapOf(BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.SUBORDINATE_DATA))
        )

        val result = AccessDecisionEngine.evaluate(
            persona = persona(roleId = "role-sales-head"),
            module = BusinessModule.CRM_SALES,
            role = salesRole,
            assignments = listOf(assignment(salesDept, AccessLevel.VIEW))
        )

        assertEquals(AccessLevel.MANAGE, result.level)
        assertEquals(DataScope.SUBORDINATE_DATA, result.scope)
    }

    @Test
    fun `evaluate when department grants higher than role should take department access`() {
        val salesRole = role(
            "role-sales",
            mapOf(BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.VIEW, DataScope.OWN_DATA_ONLY))
        )

        val result = AccessDecisionEngine.evaluate(
            persona = persona(),
            module = BusinessModule.CRM_SALES,
            role = salesRole,
            assignments = listOf(assignment(salesDept, AccessLevel.OPERATE, DataScope.SUBORDINATE_DATA))
        )

        assertEquals(AccessLevel.OPERATE, result.level)
        assertEquals(DataScope.SUBORDINATE_DATA, result.scope)
    }

    @Test
    fun `evaluate when neither role nor department grants should return none`() {
        val result = AccessDecisionEngine.evaluate(
            persona = persona(),
            module = BusinessModule.QUALITY_CONTROL,
            role = role("role-sales", emptyMap()),
            assignments = listOf(assignment(warehouseDept, AccessLevel.MANAGE))
        )

        assertEquals(AccessLevel.NONE, result.level)
        assertFalse(result.isAccessible)
    }

    @Test
    fun `evaluate when assignment targets other department should be ignored`() {
        val result = AccessDecisionEngine.evaluate(
            persona = persona(departmentId = salesDept),
            module = BusinessModule.INVENTORY,
            role = null,
            assignments = listOf(assignment(warehouseDept, AccessLevel.MANAGE))
        )

        assertEquals(AccessLevel.NONE, result.level)
    }

    @Test
    fun `evaluate when assignment targets specific roles should apply only to those roles`() {
        val assignments = listOf(
            assignment(salesDept, AccessLevel.MANAGE, specificRoleIds = setOf("role-sales-head"))
        )

        val forHead = AccessDecisionEngine.evaluate(
            persona = persona(roleId = "role-sales-head"),
            module = BusinessModule.CRM_SALES,
            role = null,
            assignments = assignments
        )
        val forStaff = AccessDecisionEngine.evaluate(
            persona = persona(roleId = "role-sales"),
            module = BusinessModule.CRM_SALES,
            role = null,
            assignments = assignments
        )

        assertEquals(AccessLevel.MANAGE, forHead.level)
        assertEquals(AccessLevel.NONE, forStaff.level)
    }

    @Test
    fun `evaluate when department has several assignments should take the highest`() {
        val result = AccessDecisionEngine.evaluate(
            persona = persona(roleId = "role-sales-head"),
            module = BusinessModule.SAMPLING_ORDER,
            role = null,
            assignments = listOf(
                assignment(salesDept, AccessLevel.VIEW),
                assignment(salesDept, AccessLevel.MANAGE, specificRoleIds = setOf("role-sales-head"))
            )
        )

        assertEquals(AccessLevel.MANAGE, result.level)
    }

    @Test
    fun `evaluate when module is global only should force all tenant data scope`() {
        assertTrue(BusinessModule.COSTING_HPP.isGlobalOnly, "Prasyarat uji: COSTING_HPP harus GLOBAL_ONLY")

        val result = AccessDecisionEngine.evaluate(
            persona = persona(),
            module = BusinessModule.COSTING_HPP,
            role = role(
                "role-sales",
                mapOf(BusinessModule.COSTING_HPP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.OWN_DATA_ONLY))
            ),
            assignments = emptyList()
        )

        assertEquals(AccessLevel.VIEW, result.level)
        assertEquals(DataScope.ALL_TENANT_DATA, result.scope)
    }

    @Test
    fun `evaluate when module is hierarchical should preserve narrow scope`() {
        assertFalse(BusinessModule.CRM_SALES.isGlobalOnly, "Prasyarat uji: CRM_SALES harus HIERARCHICAL")

        val result = AccessDecisionEngine.evaluate(
            persona = persona(),
            module = BusinessModule.CRM_SALES,
            role = role(
                "role-sales",
                mapOf(BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY))
            ),
            assignments = emptyList()
        )

        assertEquals(DataScope.OWN_DATA_ONLY, result.scope)
    }

    @Test
    fun `evaluateAll should cover every business module`() {
        val roles = CustomRole.createFactoryPresets(tenantId)
        val salesHead = roles.first { it.id.value.endsWith("sales-head") }

        val result = AccessDecisionEngine.evaluateAll(
            persona = persona(roleId = salesHead.id.value),
            roles = roles,
            assignments = mapOf(
                BusinessModule.CRM_SALES to listOf(assignment(salesDept, AccessLevel.VIEW))
            )
        )

        assertEquals(BusinessModule.entries.size, result.size)
        assertEquals(AccessLevel.MANAGE, result.getValue(BusinessModule.CRM_SALES).level)
        assertEquals(AccessLevel.NONE, result.getValue(BusinessModule.OPERATOR_EXEC).level)
    }

    /**
     * Divisi dan jabatan yang belum pernah ada saat kode ini ditulis.
     *
     * Ini penjaga terhadap kemunduran yang paling mudah terjadi: seseorang menambahkan pencocokan
     * berdasarkan kode divisi yang diketik langsung ("sales", "qc", …) demi kepraktisan, dan sejak
     * itu setiap divisi baru diam-diam kehilangan wewenangnya.
     */
    @Test
    fun `evaluate when department and role are newly created should still grant access`() {
        val sablonDept = "dept-sablon-custom-001"
        val sablonRole = role(
            "role-sablon-head-777",
            mapOf(BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.VIEW))
        ).copy(departmentId = sablonDept)

        val newPersona = persona(
            name = "Penguji Sablon",
            departmentId = sablonDept,
            roleId = "role-sablon-head-777"
        )

        val fromRoleOnly = AccessDecisionEngine.evaluate(
            persona = newPersona,
            module = BusinessModule.PRODUCTION_MRP,
            role = sablonRole,
            assignments = emptyList()
        )
        val fromNewAssignment = AccessDecisionEngine.evaluate(
            persona = newPersona,
            module = BusinessModule.QUALITY_CONTROL,
            role = sablonRole,
            assignments = listOf(assignment(sablonDept, AccessLevel.MANAGE))
        )

        assertEquals(AccessLevel.VIEW, fromRoleOnly.level)
        assertEquals(AccessLevel.MANAGE, fromNewAssignment.level)
    }

    @Test
    fun `matchRole should pick a role from any department without knowing its code`() {
        val bordirDept = com.eventverse.app.domain.orgchart.Department(
            id = com.eventverse.app.domain.orgchart.DepartmentId("dept-bordir-042"),
            code = "bordir_komputer",
            displayName = "Bordir Komputer",
            shortName = "Bordir",
            colorHex = 4280624107L,
            isCustom = true,
            tenantId = tenantId
        )
        val kepalaBordir = role(
            "role-bordir-kepala",
            BusinessModule.entries.associateWith { ModuleAccessConfig(AccessLevel.MANAGE) }
        ).copy(departmentId = bordirDept.id.value)
        val stafBordir = role(
            "role-bordir-staf",
            mapOf(BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.OPERATE))
        ).copy(departmentId = bordirDept.id.value)

        fun employee(name: String, title: String) = com.eventverse.app.domain.orgchart.OrgNode(
            id = com.eventverse.app.domain.orgchart.OrgNodeId("emp-$name"),
            name = name,
            email = "$name@wemade.id",
            department = bordirDept,
            level = com.eventverse.app.domain.orgchart.HierarchyLevel.STAFF_OPERATOR,
            roleTitle = title,
            tenantId = tenantId
        )

        val roles = listOf(kepalaBordir, stafBordir)

        assertEquals(
            kepalaBordir.id,
            TestingPersona.matchRole(employee("wati", "Kepala Divisi Bordir"), roles)?.id
        )
        assertEquals(
            stafBordir.id,
            TestingPersona.matchRole(employee("joni", "Operator Mesin Bordir"), roles)?.id
        )
    }

    /**
     * Inti dari seluruh fitur: persona berjabatan A harus melihat **persis** apa yang dikonfigurasi
     * untuk jabatan A, lalu berganti ke jabatan B harus berpindah sepenuhnya ke konfigurasi B.
     */
    @Test
    fun `switching persona between two roles should mirror each role configuration exactly`() {
        val jabatanA = role(
            "role-a",
            mapOf(
                BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE),
                BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.VIEW),
                BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.NONE)
            )
        )
        val jabatanB = role(
            "role-b",
            mapOf(
                BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE),
                BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.MANAGE),
                BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.OPERATE)
            )
        )
        val roles = listOf(jabatanA, jabatanB)

        // Divisi sengaja dikosongkan agar yang diuji murni jalur jabatan.
        val asA = AccessDecisionEngine.evaluateAll(
            persona(departmentId = null, roleId = "role-a"), roles, emptyMap()
        )
        val asB = AccessDecisionEngine.evaluateAll(
            persona(departmentId = null, roleId = "role-b"), roles, emptyMap()
        )

        BusinessModule.entries.forEach { module ->
            assertEquals(
                jabatanA.getAccess(module).sanitizeFor(module).level,
                asA.getValue(module).level,
                "Persona jabatan A menyimpang dari konfigurasi A pada $module"
            )
            assertEquals(
                jabatanB.getAccess(module).sanitizeFor(module).level,
                asB.getValue(module).level,
                "Persona jabatan B menyimpang dari konfigurasi B pada $module"
            )
        }
    }

    @Test
    fun `persona carrying a role must not keep owner bypass`() {
        // Konstruktor menolaknya, sehingga wewenang jabatan tidak mungkin tertimpa bypass owner
        // tanpa ada yang menyadarinya.
        assertFailsWith<IllegalArgumentException> {
            TestingPersona(
                userId = "usr-x",
                name = "Penyamar",
                tenantId = tenantId,
                tenantSlug = "wemade-demo",
                departmentId = salesDept,
                departmentName = "Penjualan",
                roleId = RoleId("role-sales"),
                roleTitle = "Sales",
                isOwnerOrSuperAdmin = true
            )
        }
    }

    @Test
    fun `explain should name the source of the granted access`() {
        val roleGrant = AccessDecisionEngine.explain(
            persona = persona(roleId = "role-sales"),
            module = BusinessModule.CRM_SALES,
            role = role("role-sales", mapOf(BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.OPERATE))),
            assignments = emptyList()
        )
        val departmentGrant = AccessDecisionEngine.explain(
            persona = persona(roleId = "role-sales"),
            module = BusinessModule.CRM_SALES,
            role = role("role-sales", emptyMap()),
            assignments = listOf(assignment(salesDept, AccessLevel.OPERATE))
        )

        assertEquals(AccessSource.ROLE, roleGrant.source)
        assertFalse(roleGrant.grantedByDepartmentOnly)

        assertEquals(AccessSource.DEPARTMENT, departmentGrant.source)
        assertTrue(
            departmentGrant.grantedByDepartmentOnly,
            "Akses yang datang hanya dari divisi harus ditandai, supaya tidak disalahartikan " +
                "sebagai bukti jabatannya sudah benar"
        )
    }

    @Test
    fun `evaluateAll when persona has unknown role should fall back to department access`() {
        val result = AccessDecisionEngine.evaluateAll(
            persona = persona(roleId = "role-does-not-exist"),
            roles = emptyList(),
            assignments = mapOf(
                BusinessModule.CRM_SALES to listOf(assignment(salesDept, AccessLevel.OPERATE))
            )
        )

        assertEquals(AccessLevel.OPERATE, result.getValue(BusinessModule.CRM_SALES).level)
        assertEquals(AccessLevel.NONE, result.getValue(BusinessModule.INVENTORY).level)
    }

    @Test
    fun `explain when persona is platform superadmin should bypass tenant entitlement limits`() {
        // Ketika RBAC tidak disambungkan ke tenant (mis. grantedModules kosong), superadmin platform
        // tetap berwenang penuh (MANAGE) dan asalnya tercatat SUPERADMIN_BYPASS, sehingga menunya tidak hilang.
        val decision = AccessDecisionEngine.explain(
            persona = persona(name = "Superadmin", departmentId = null, roleId = null, isSuperAdmin = true),
            module = BusinessModule.DYNAMIC_RBAC,
            role = null,
            assignments = emptyList(),
            grantedModules = emptySet()
        )

        assertEquals(AccessSource.SUPERADMIN_BYPASS, decision.source)
        assertEquals(AccessLevel.MANAGE, decision.config.level)
        assertTrue(decision.config.isAccessible)
        assertFalse(decision.blockedByEntitlement)
    }
}
