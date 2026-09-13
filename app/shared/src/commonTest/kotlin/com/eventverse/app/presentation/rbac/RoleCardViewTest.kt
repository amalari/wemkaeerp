package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.rbac.components.resolveAccessibleModulesForRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleCardViewTest {

    private val tenantId = TenantId("ten-demo-001")
    private val warehouseDept = Department(
        id = DepartmentId("dept-warehouse"),
        code = "warehouse",
        displayName = "Gudang & Logistik",
        shortName = "GDG",
        colorHex = 0xFF2563EB
    )

    @Test
    fun `resolveAccessibleModulesForRole includes both department assignments and role permissions`() {
        val warehouseRole = CustomRole(
            id = RoleId("role-warehouse"),
            tenantId = tenantId,
            name = "Staff Gudang & Logistik",
            description = "Staff gudang",
            departmentId = warehouseDept.id.value,
            modulePermissions = mapOf(
                BusinessModule.ORG_CHART to ModuleAccessConfig(AccessLevel.VIEW, DataScope.SUBORDINATE_DATA),
                BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA),
                BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE)
            )
        )

        val assignments = mapOf(
            BusinessModule.INVENTORY to listOf(
                DepartmentModuleAssignment(
                    id = "dma-1",
                    departmentId = warehouseDept.id.value,
                    departmentName = warehouseDept.displayName,
                    accessLevel = AccessLevel.OPERATE,
                    scope = DataScope.ALL_TENANT_DATA,
                    specificRoleIds = emptySet()
                )
            ),
            BusinessModule.FULFILLMENT to listOf(
                DepartmentModuleAssignment(
                    id = "dma-2",
                    departmentId = warehouseDept.id.value,
                    departmentName = warehouseDept.displayName,
                    accessLevel = AccessLevel.MANAGE,
                    scope = DataScope.ALL_TENANT_DATA,
                    specificRoleIds = emptySet()
                )
            )
        )

        val accessible = resolveAccessibleModulesForRole(warehouseRole, warehouseDept, assignments)
        val moduleKeys = accessible.map { it.module }.toSet()

        // Harus mencakup modul dari penugasan divisi (INVENTORY & FULFILLMENT)
        assertTrue(moduleKeys.contains(BusinessModule.INVENTORY))
        assertTrue(moduleKeys.contains(BusinessModule.FULFILLMENT))

        // HARUS MENCANTUMKAN ORG_CHART yang berasal dari wewenang bawaan jabatan
        assertTrue(moduleKeys.contains(BusinessModule.ORG_CHART), "Bagan Organisasi harus tercantum di kartu jabatan")

        // Modul NONE (CRM_SALES) tidak boleh muncul
        assertFalse(moduleKeys.contains(BusinessModule.CRM_SALES), "Modul dengan akses NONE tidak boleh muncul")

        // Verifikasi detail item ORG_CHART
        val orgChartItem = accessible.first { it.module == BusinessModule.ORG_CHART }
        assertEquals(AccessLevel.VIEW, orgChartItem.accessLevel)
        assertEquals(DataScope.SUBORDINATE_DATA, orgChartItem.scope)
        assertTrue(orgChartItem.isSpecificToRole)
    }

    @Test
    fun `operator role with NONE access to ORG_CHART does not show ORG_CHART`() {
        val operatorRole = CustomRole(
            id = RoleId("role-operator"),
            tenantId = tenantId,
            name = "Operator Mesin Jahit",
            description = "Operator",
            modulePermissions = mapOf(
                BusinessModule.ORG_CHART to ModuleAccessConfig(AccessLevel.NONE),
                BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY)
            )
        )

        val accessible = resolveAccessibleModulesForRole(operatorRole, null, emptyMap())
        val moduleKeys = accessible.map { it.module }.toSet()

        assertFalse(moduleKeys.contains(BusinessModule.ORG_CHART))
        assertTrue(moduleKeys.contains(BusinessModule.OPERATOR_EXEC))
    }
}
