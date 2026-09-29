package com.eventverse.app.presentation.rbac

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

import com.eventverse.app.domain.pack.GarmentModules

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
                GarmentModules.ORG_CHART to ModuleAccessConfig(AccessLevel.VIEW, DataScope.SUBORDINATE_DATA),
                GarmentModules.INVENTORY to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA),
                GarmentModules.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE)
            )
        )

        val assignments = mapOf(
            GarmentModules.INVENTORY to listOf(
                DepartmentModuleAssignment(
                    id = "dma-1",
                    departmentId = warehouseDept.id.value,
                    departmentName = warehouseDept.displayName,
                    accessLevel = AccessLevel.OPERATE,
                    scope = DataScope.ALL_TENANT_DATA,
                    specificRoleIds = emptySet()
                )
            ),
            GarmentModules.FULFILLMENT to listOf(
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
        assertTrue(moduleKeys.contains(GarmentModules.INVENTORY))
        assertTrue(moduleKeys.contains(GarmentModules.FULFILLMENT))

        // HARUS MENCANTUMKAN ORG_CHART yang berasal dari wewenang bawaan jabatan
        assertTrue(moduleKeys.contains(GarmentModules.ORG_CHART), "Bagan Organisasi harus tercantum di kartu jabatan")

        // Modul NONE (CRM_SALES) tidak boleh muncul
        assertFalse(moduleKeys.contains(GarmentModules.CRM_SALES), "Modul dengan akses NONE tidak boleh muncul")

        // Verifikasi detail item ORG_CHART
        val orgChartItem = accessible.first { it.module == GarmentModules.ORG_CHART }
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
                GarmentModules.ORG_CHART to ModuleAccessConfig(AccessLevel.NONE),
                GarmentModules.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY)
            )
        )

        val accessible = resolveAccessibleModulesForRole(operatorRole, null, emptyMap())
        val moduleKeys = accessible.map { it.module }.toSet()

        assertFalse(moduleKeys.contains(GarmentModules.ORG_CHART))
        assertTrue(moduleKeys.contains(GarmentModules.OPERATOR_EXEC))
    }
}
