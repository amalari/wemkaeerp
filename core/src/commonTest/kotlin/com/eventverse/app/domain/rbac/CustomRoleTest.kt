package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.*

class CustomRoleTest {

    private val sampleTenantId = TenantId("tenant-wemade-01")

    @Test
    fun presets_should_provide_expected_roles() {
        val presets = CustomRole.createFactoryPresets(sampleTenantId)
        assertEquals(6, presets.size)

        val roleNames = presets.map { it.name }
        assertTrue(roleNames.contains("Owner / Direktur Pabrik"))
        assertTrue(roleNames.contains("Kepala Produksi (PPIC)"))
        assertTrue(roleNames.contains("Kepala Penjualan (Head of Sales)"))
        assertTrue(roleNames.contains("Sales Eksekutif"))
        assertTrue(roleNames.contains("Staff Gudang & Logistik"))
        assertTrue(roleNames.contains("Operator Mesin Jahit"))
    }

    @Test
    fun head_of_sales_preset_should_have_subordinate_data_scope() {
        val headSales = CustomRole.createFactoryPresets(sampleTenantId).first { it.id.value.endsWith("sales-head") }

        val crmAccess = headSales.getAccess(BusinessModule.CRM_SALES)
        assertEquals(AccessLevel.MANAGE, crmAccess.level)
        assertEquals(DataScope.SUBORDINATE_DATA, crmAccess.scope)

        val sampleAccess = headSales.getAccess(BusinessModule.SAMPLING_ORDER)
        assertEquals(AccessLevel.MANAGE, sampleAccess.level)
        assertEquals(DataScope.SUBORDINATE_DATA, sampleAccess.scope)
    }

    @Test
    fun owner_preset_should_have_manage_access_to_all_modules() {
        val owner = CustomRole.createFactoryPresets(sampleTenantId).first { it.id.value.endsWith("owner") }

        for (module in BusinessModule.entries) {
            assertTrue(
                owner.hasAccess(module, AccessLevel.MANAGE),
                "Owner must have MANAGE access to ${module.displayName}"
            )
            assertEquals(DataScope.ALL_TENANT_DATA, owner.getAccess(module).scope)
        }
    }

    @Test
    fun operator_preset_should_have_restricted_access() {
        val operator = CustomRole.createFactoryPresets(sampleTenantId).first { it.id.value.endsWith("operator") }

        // Operator has OPERATE access on OPERATOR_EXEC with OWN_DATA_ONLY
        val execAccess = operator.getAccess(BusinessModule.OPERATOR_EXEC)
        assertEquals(AccessLevel.OPERATE, execAccess.level)
        assertEquals(DataScope.OWN_DATA_ONLY, execAccess.scope)

        // Operator must NOT have access to costing or inventory
        assertFalse(operator.hasAccess(BusinessModule.COSTING_HPP, AccessLevel.VIEW))
        assertFalse(operator.hasAccess(BusinessModule.INVENTORY, AccessLevel.VIEW))
        assertEquals(AccessLevel.NONE, operator.getAccess(BusinessModule.COSTING_HPP).level)
    }

    @Test
    fun updating_module_access_should_return_new_immutable_instance() {
        val role = CustomRole(
            id = RoleId("custom-cutter"),
            tenantId = sampleTenantId,
            name = "Staff Potong Bahan",
            description = "Petugas potong kain"
        )

        assertEquals(AccessLevel.NONE, role.getAccess(BusinessModule.INVENTORY).level)

        val updated = role.updateModuleAccess(
            module = BusinessModule.INVENTORY,
            level = AccessLevel.OPERATE,
            scope = DataScope.ALL_TENANT_DATA
        )

        // Original remains unchanged
        assertEquals(AccessLevel.NONE, role.getAccess(BusinessModule.INVENTORY).level)
        // Updated has new access
        assertEquals(AccessLevel.OPERATE, updated.getAccess(BusinessModule.INVENTORY).level)
        assertEquals(DataScope.ALL_TENANT_DATA, updated.getAccess(BusinessModule.INVENTORY).scope)
    }

    @Test
    fun blank_role_name_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            CustomRole(
                id = RoleId("role-invalid"),
                tenantId = sampleTenantId,
                name = "   ",
                description = "Invalid"
            )
        }
    }

    @Test
    fun access_level_hierarchy_should_be_consistent() {
        assertTrue(AccessLevel.MANAGE.isAtLeast(AccessLevel.OPERATE))
        assertTrue(AccessLevel.MANAGE.isAtLeast(AccessLevel.VIEW))
        assertTrue(AccessLevel.MANAGE.isAtLeast(AccessLevel.NONE))

        assertTrue(AccessLevel.OPERATE.isAtLeast(AccessLevel.VIEW))
        assertFalse(AccessLevel.OPERATE.isAtLeast(AccessLevel.MANAGE))

        assertTrue(AccessLevel.VIEW.isAtLeast(AccessLevel.NONE))
        assertFalse(AccessLevel.VIEW.isAtLeast(AccessLevel.OPERATE))
    }

    @Test
    fun scope_capabilities_should_match_enterprise_garment_nature() {
        // Shared Enterprise Master Data should be GLOBAL_ONLY
        assertTrue(BusinessModule.INVENTORY.isGlobalOnly)
        assertTrue(BusinessModule.COSTING_HPP.isGlobalOnly)
        assertTrue(BusinessModule.PRODUCTION_MRP.isGlobalOnly)
        assertTrue(BusinessModule.TECH_PACK_BOM.isGlobalOnly)
        assertTrue(BusinessModule.QUALITY_CONTROL.isGlobalOnly)
        assertTrue(BusinessModule.FULFILLMENT.isGlobalOnly)

        assertEquals(setOf(DataScope.ALL_TENANT_DATA), BusinessModule.INVENTORY.supportedScopes)
        assertEquals(setOf(DataScope.ALL_TENANT_DATA), BusinessModule.COSTING_HPP.supportedScopes)

        // Transactional / Boundary documents should be HIERARCHICAL
        assertTrue(BusinessModule.CRM_SALES.isHierarchical)
        assertTrue(BusinessModule.SAMPLING_ORDER.isHierarchical)
        assertTrue(BusinessModule.OPERATOR_EXEC.isHierarchical)

        val allScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA)
        assertEquals(allScopes, BusinessModule.CRM_SALES.supportedScopes)
        assertEquals(allScopes, BusinessModule.OPERATOR_EXEC.supportedScopes)
    }

    @Test
    fun global_only_modules_should_sanitize_scope_to_all_tenant_data() {
        // Attempting to configure OWN_DATA_ONLY on INVENTORY should sanitize to ALL_TENANT_DATA
        val invalidInventoryConfig = ModuleAccessConfig(
            level = AccessLevel.OPERATE,
            scope = DataScope.OWN_DATA_ONLY
        )
        val sanitized = invalidInventoryConfig.sanitizeFor(BusinessModule.INVENTORY)
        assertEquals(DataScope.ALL_TENANT_DATA, sanitized.scope)

        // For hierarchical modules, scope should remain as configured
        val validCrmConfig = ModuleAccessConfig(
            level = AccessLevel.OPERATE,
            scope = DataScope.OWN_DATA_ONLY
        )
        val untouched = validCrmConfig.sanitizeFor(BusinessModule.CRM_SALES)
        assertEquals(DataScope.OWN_DATA_ONLY, untouched.scope)
    }

    @Test
    fun department_module_assignment_should_track_specificity_and_updates() {
        val fullDeptAssign = DepartmentModuleAssignment(
            departmentId = "dept-sales",
            departmentName = "Penjualan",
            accessLevel = AccessLevel.OPERATE,
            specificRoleIds = emptySet()
        )
        assertTrue(fullDeptAssign.appliesToAllRoles)

        val specificRoleAssign = DepartmentModuleAssignment(
            departmentId = "dept-sales",
            departmentName = "Penjualan",
            accessLevel = AccessLevel.MANAGE,
            specificRoleIds = setOf("role-head-sales")
        )
        assertFalse(specificRoleAssign.appliesToAllRoles)
        assertEquals(1, specificRoleAssign.specificRoleIds.size)

        val updated = specificRoleAssign.updateAccess(
            level = AccessLevel.VIEW,
            newScope = DataScope.SUBORDINATE_DATA
        )
        assertEquals(AccessLevel.VIEW, updated.accessLevel)
        assertEquals(DataScope.SUBORDINATE_DATA, updated.scope)
    }
}
