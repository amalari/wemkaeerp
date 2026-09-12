package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 * PostgreSQL implementation of UserRepository utilizing JetBrains Exposed and HikariCP.
 */
class PostgresUserRepository : UserRepository {

    override suspend fun findById(id: UserId): User? = DatabaseFactory.dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq id.value }
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? = DatabaseFactory.dbQuery {
        val query = if (tenantId != null) {
            UsersTable.selectAll().where { 
                (UsersTable.tenantId eq tenantId.value) and (UsersTable.username eq username.value) 
            }
        } else {
            UsersTable.selectAll().where { 
                UsersTable.tenantId.isNull() and (UsersTable.username eq username.value) 
            }
        }
        query.map { toUser(it) }.singleOrNull()
    }

    override suspend fun findByEmail(email: EmailAddress): User? = DatabaseFactory.dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email.value }
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun save(user: User): Result<User> = runCatching {
        DatabaseFactory.dbQuery(user.tenantId) {
            val exists = UsersTable.selectAll()
                .where { UsersTable.id eq user.id.value }
                .count() > 0

            if (exists) {
                UsersTable.update({ UsersTable.id eq user.id.value }) {
                    it[tenantId] = user.tenantId?.value
                    it[username] = user.username.value
                    it[email] = user.email.value
                    it[role] = user.role.name
                    it[isActive] = user.isActive
                    it[departmentId] = user.departmentId
                    it[customRoleId] = user.customRoleId
                }
            } else {
                UsersTable.insert {
                    it[id] = user.id.value
                    it[tenantId] = user.tenantId?.value
                    it[username] = user.username.value
                    it[email] = user.email.value
                    it[role] = user.role.name
                    it[isActive] = user.isActive
                    it[departmentId] = user.departmentId
                    it[customRoleId] = user.customRoleId
                }
            }
            user
        }
    }

    override suspend fun findAllByTenant(tenantId: TenantId): List<User> = DatabaseFactory.dbQuery(tenantId) {
        UsersTable.selectAll()
            .where { UsersTable.tenantId eq tenantId.value }
            .map { toUser(it) }
    }

    private fun toUser(row: ResultRow): User = User(
        id = UserId(row[UsersTable.id]),
        tenantId = row[UsersTable.tenantId]?.let { TenantId(it) },
        username = Username(row[UsersTable.username]),
        email = EmailAddress(row[UsersTable.email]),
        role = Role.valueOf(row[UsersTable.role]),
        customPermissions = emptySet(),
        isActive = row[UsersTable.isActive],
        departmentId = row[UsersTable.departmentId],
        customRoleId = row[UsersTable.customRoleId]
    )
}
