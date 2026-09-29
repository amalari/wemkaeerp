package com.eventverse.app.infrastructure

import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CustomRolesTable
import com.eventverse.app.infrastructure.tables.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class PostgresRoleRepository : RoleRepository {

    override suspend fun findById(tenantId: TenantId, id: RoleId): CustomRole? = DatabaseFactory.dbQuery(tenantId) {
        CustomRolesTable.selectAll()
            .where { (CustomRolesTable.tenantId eq tenantId.value) and (CustomRolesTable.id eq id.value) }
            .map { toCustomRole(it) }
            .singleOrNull()
    }

    override suspend fun findAllByTenant(tenantId: TenantId): List<CustomRole> = DatabaseFactory.dbQuery(tenantId) {
        CustomRolesTable.selectAll()
            .where { CustomRolesTable.tenantId eq tenantId.value }
            .map { toCustomRole(it) }
    }

    override suspend fun save(role: CustomRole): Result<CustomRole> = runCatching {
        val tId = role.tenantId ?: error("TenantId is required for saving role")
        DatabaseFactory.dbQuery(tId) {
            val exists = CustomRolesTable.selectAll()
                .where { (CustomRolesTable.tenantId eq tId.value) and (CustomRolesTable.id eq role.id.value) }
                .count() > 0

            val permissionsJson = ModulePermissionsSerializer.toJson(role.modulePermissions)

            if (exists) {
                CustomRolesTable.update({ (CustomRolesTable.tenantId eq tId.value) and (CustomRolesTable.id eq role.id.value) }) {
                    it[name] = role.name
                    it[description] = role.description
                    it[isSystemDefault] = role.isSystemDefault
                    it[modulePermissions] = permissionsJson
                    it[userCount] = role.userCount
                    it[departmentId] = role.departmentId
                }
            } else {
                CustomRolesTable.insert {
                    it[id] = role.id.value
                    it[tenantId] = tId.value
                    it[name] = role.name
                    it[description] = role.description
                    it[isSystemDefault] = role.isSystemDefault
                    it[modulePermissions] = permissionsJson
                    it[userCount] = role.userCount
                    it[departmentId] = role.departmentId
                }
            }
            role
        }
    }

    override suspend fun delete(tenantId: TenantId, id: RoleId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            CustomRolesTable.deleteWhere {
                (CustomRolesTable.tenantId eq tenantId.value) and (CustomRolesTable.id eq id.value)
            }
        }
    }

    override suspend fun restoreDefaultPresets(tenantId: TenantId, pack: com.eventverse.app.domain.pack.DomainPack): Result<List<CustomRole>> = runCatching {
        val presets = CustomRole.createFactoryPresets(tenantId, pack)
        presets.forEach { save(it).getOrThrow() }
        presets
    }

    override suspend fun countUsersWithRole(tenantId: TenantId, id: RoleId): Int = DatabaseFactory.dbQuery(tenantId) {
        UsersTable.selectAll()
            .where { (UsersTable.tenantId eq tenantId.value) and (UsersTable.role eq id.value) }
            .count()
            .toInt()
    }

    private fun toCustomRole(row: ResultRow): CustomRole = CustomRole(
        id = RoleId(row[CustomRolesTable.id]),
        tenantId = TenantId(row[CustomRolesTable.tenantId]),
        name = row[CustomRolesTable.name],
        description = row[CustomRolesTable.description],
        isSystemDefault = row[CustomRolesTable.isSystemDefault],
        modulePermissions = ModulePermissionsSerializer.fromJson(row[CustomRolesTable.modulePermissions]),
        userCount = row[CustomRolesTable.userCount],
        departmentId = row[CustomRolesTable.departmentId]
    )
}
