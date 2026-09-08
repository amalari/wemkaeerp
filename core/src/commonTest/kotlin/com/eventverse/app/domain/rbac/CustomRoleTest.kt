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
        val headSales = CustomRole.createFactoryPresets(sampleTenantId).first { it.id.value == "role-sales-head" }

        val crmAccess = headSales.getAccess(BusinessModule.CRM_SALES)
        assertEquals(AccessLevel.MANAGE, crmAccess.level)
        assertEquals(DataScope.SUBORDINATE_DATA, crmAccess.scope)

        val sampleAccess = headSales.getAccess(BusinessModule.SAMPLING_ORDER)
        assertEquals(AccessLevel.MANAGE, sampleAccess.level)
        assertEquals(DataScope.SUBORDINATE_DATA, sampleAccess.scope)
    }

    @Test
    fun owner_preset_should_have_manage_access_to_all_modules() {
        val owner = CustomRole.createFactoryPresets(sampleTenantId).first { it.id.value == "role-owner" }

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
        val operator = CustomRole.createFactoryPresets(sampleTenantId).first { it.id.value == "role-operator" }

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
}
