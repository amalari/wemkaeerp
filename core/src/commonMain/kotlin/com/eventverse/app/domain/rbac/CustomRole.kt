package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.tenant.TenantId
import kotlin.jvm.JvmInline

@JvmInline
value class RoleId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoleId cannot be blank" }
    }
}

/**
 * Domain entity representing a custom role configured by the tenant admin.
 * Immutable by DDD rules; mutations return a modified copy.
 */
data class CustomRole(
    val id: RoleId,
    val tenantId: TenantId?,
    val name: String,
    val description: String,
    val isSystemDefault: Boolean = false,
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig> = emptyMap(),
    val userCount: Int = 0
) {
    init {
        require(name.isNotBlank()) { "Role name cannot be blank" }
    }

    fun getAccess(module: BusinessModule): ModuleAccessConfig =
        modulePermissions[module] ?: ModuleAccessConfig(AccessLevel.NONE)

    fun hasAccess(module: BusinessModule, requiredLevel: AccessLevel): Boolean {
        val config = getAccess(module)
        return config.level.isAtLeast(requiredLevel)
    }

    fun updateModuleAccess(
        module: BusinessModule,
        level: AccessLevel,
        scope: DataScope = DataScope.ALL_TENANT_DATA
    ): CustomRole {
        val updated = modulePermissions.toMutableMap()
        updated[module] = ModuleAccessConfig(level, scope)
        return copy(modulePermissions = updated)
    }

    fun updateMetadata(newName: String, newDescription: String): CustomRole {
        require(newName.isNotBlank()) { "Role name cannot be blank" }
        return copy(name = newName.trim(), description = newDescription.trim())
    }

    fun updateUserCount(newCount: Int): CustomRole {
        require(newCount >= 0) { "User count cannot be negative" }
        return copy(userCount = newCount)
    }

    companion object {
        /**
         * Factory presets out-of-the-box for garment factories.
         * Scoped by tenantId to prevent primary key collision across multi-tenant database.
         */
        fun createFactoryPresets(tenantId: TenantId?): List<CustomRole> {
            val prefix = if (tenantId == null || tenantId.value == "ten-demo-001") "" else "${tenantId.value}-"
            return listOf(
                CustomRole(
                    id = RoleId("role-${prefix}owner"),
                    tenantId = tenantId,
                    name = "Owner / Direktur Pabrik",
                    description = "Pemilik usaha dengan akses penuh ke seluruh modul, keuangan rahasia, dan manajemen staf.",
                    isSystemDefault = true,
                    userCount = 1,
                    modulePermissions = BusinessModule.entries.associateWith {
                        ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
                    }
                ),
                CustomRole(
                    id = RoleId("role-${prefix}ppic"),
                    tenantId = tenantId,
                    name = "Kepala Produksi (PPIC)",
                    description = "Merencanakan alokasi mesin jahit, SPK potong/jahit, memantau bahan baku, dan kontrol kualitas.",
                    isSystemDefault = true,
                    userCount = 2,
                    modulePermissions = mapOf(
                        BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.SUBORDINATE_DATA),
                        BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA),
                        BusinessModule.TECH_PACK_BOM to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA),
                        BusinessModule.COSTING_HPP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA),
                        BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.SUBORDINATE_DATA),
                        BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA),
                        BusinessModule.FULFILLMENT to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA)
                    )
                ),
                CustomRole(
                    id = RoleId("role-${prefix}sales-head"),
                    tenantId = tenantId,
                    name = "Kepala Penjualan (Head of Sales)",
                    description = "Memantau target prospek seluruh sales bawahan, menyetujui sampling order, dan evaluasi komisi.",
                    isSystemDefault = true,
                    userCount = 1,
                    modulePermissions = mapOf(
                        BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.SUBORDINATE_DATA),
                        BusinessModule.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.SUBORDINATE_DATA),
                        BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.TECH_PACK_BOM to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.COSTING_HPP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.SUBORDINATE_DATA),
                        BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.FULFILLMENT to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA)
                    )
                ),
                CustomRole(
                    id = RoleId("role-${prefix}sales"),
                    tenantId = tenantId,
                    name = "Sales Eksekutif",
                    description = "Mencatat prospek pelanggan, mengajukan sampling baju, dan memantau progres pesanan.",
                    isSystemDefault = true,
                    userCount = 4,
                    modulePermissions = mapOf(
                        BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY),
                        BusinessModule.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY),
                        BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.TECH_PACK_BOM to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.COSTING_HPP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.OWN_DATA_ONLY),
                        BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.FULFILLMENT to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA)
                    )
                ),
                CustomRole(
                    id = RoleId("role-${prefix}warehouse"),
                    tenantId = tenantId,
                    name = "Staff Gudang & Logistik",
                    description = "Menerima bahan baku kain, mengelola pengeluaran aksesoris, dan mencetak surat jalan packing.",
                    isSystemDefault = true,
                    userCount = 3,
                    modulePermissions = mapOf(
                        BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA),
                        BusinessModule.TECH_PACK_BOM to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.COSTING_HPP to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.FULFILLMENT to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
                    )
                ),
                CustomRole(
                    id = RoleId("role-${prefix}operator"),
                    tenantId = tenantId,
                    name = "Operator Mesin Jahit",
                    description = "Input pencapaian hasil jahitan harian pada antarmuka tablet tanpa akses dokumen lainnya.",
                    isSystemDefault = true,
                    userCount = 14,
                    modulePermissions = mapOf(
                        BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.SAMPLING_ORDER to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.TECH_PACK_BOM to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.COSTING_HPP to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.PRODUCTION_MRP to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA),
                        BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.OWN_DATA_ONLY),
                        BusinessModule.QUALITY_CONTROL to ModuleAccessConfig(AccessLevel.NONE),
                        BusinessModule.FULFILLMENT to ModuleAccessConfig(AccessLevel.NONE)
                    )
                )
            )
        }
    }
}
