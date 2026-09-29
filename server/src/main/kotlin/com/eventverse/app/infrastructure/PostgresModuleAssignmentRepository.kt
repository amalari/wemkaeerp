package com.eventverse.app.infrastructure

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DepartmentModuleAssignmentsTable
import com.eventverse.app.infrastructure.tables.DepartmentsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere

class PostgresModuleAssignmentRepository : ModuleAssignmentRepository {

    override suspend fun findAllByTenant(
        tenantId: TenantId
    ): Map<BusinessModule, List<DepartmentModuleAssignment>> = DatabaseFactory.dbQuery(tenantId) {
        // Join ke departments hanya untuk mengambil nama tampilnya. Nama itu bagian dari entity
        // domain agar layar tidak perlu mencari ulang divisi per baris.
        DepartmentModuleAssignmentsTable
            .join(
                DepartmentsTable,
                JoinType.LEFT,
                onColumn = DepartmentModuleAssignmentsTable.departmentId,
                otherColumn = DepartmentsTable.id
            )
            .selectAll()
            .where { DepartmentModuleAssignmentsTable.tenantId eq tenantId.value }
            .mapNotNull { row ->
                val module = ModuleIdCodec.fromStoredName(row[DepartmentModuleAssignmentsTable.module], "department_module_assignments.module")
                    ?: return@mapNotNull null

                module to toAssignment(row)
            }
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun upsert(
        tenantId: TenantId,
        module: BusinessModule,
        assignment: DepartmentModuleAssignment
    ): Result<DepartmentModuleAssignment> = runCatching {
        val rowId = resolveRowId(tenantId, module, assignment)
        DatabaseFactory.dbQuery(tenantId) {
            val exists = DepartmentModuleAssignmentsTable.selectAll()
                .where {
                    (DepartmentModuleAssignmentsTable.tenantId eq tenantId.value) and
                        (DepartmentModuleAssignmentsTable.id eq rowId)
                }
                .count() > 0

            if (exists) {
                DepartmentModuleAssignmentsTable.update({
                    (DepartmentModuleAssignmentsTable.tenantId eq tenantId.value) and
                        (DepartmentModuleAssignmentsTable.id eq rowId)
                }) {
                    it[departmentId] = assignment.departmentId
                    it[DepartmentModuleAssignmentsTable.module] = module.name
                    it[accessLevel] = assignment.accessLevel.name
                    it[dataScope] = assignment.scope.name
                    it[specificRoleIds] = toJsonArray(assignment.specificRoleIds)
                    it[allowedDesks] = toJsonArray(assignment.allowedDesks.orEmpty())
                }
            } else {
                DepartmentModuleAssignmentsTable.insert {
                    it[id] = rowId
                    it[DepartmentModuleAssignmentsTable.tenantId] = tenantId.value
                    it[departmentId] = assignment.departmentId
                    it[DepartmentModuleAssignmentsTable.module] = module.name
                    it[accessLevel] = assignment.accessLevel.name
                    it[dataScope] = assignment.scope.name
                    it[specificRoleIds] = toJsonArray(assignment.specificRoleIds)
                    it[allowedDesks] = toJsonArray(assignment.allowedDesks.orEmpty())
                }
            }
            assignment.copy(id = rowId)
        }
    }

    override suspend fun remove(
        tenantId: TenantId,
        module: BusinessModule,
        assignmentKey: String
    ): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            DepartmentModuleAssignmentsTable.deleteWhere {
                (DepartmentModuleAssignmentsTable.tenantId eq tenantId.value) and
                    (DepartmentModuleAssignmentsTable.module eq module.name) and
                    (DepartmentModuleAssignmentsTable.id eq assignmentKey)
            }
            Unit
        }
    }

    /**
     * Id baris untuk satu penugasan.
     *
     * Diturunkan dari `assignmentKey` domain — divisi + himpunan jabatan — dan disisipi nama modul
     * karena satu divisi memegang penugasan terpisah per modul. Deterministik supaya menyimpan
     * penugasan yang sama dua kali memperbarui baris yang sama alih-alih menumpuk duplikat.
     */
    private fun resolveRowId(
        tenantId: TenantId,
        module: BusinessModule,
        assignment: DepartmentModuleAssignment
    ): String {
        if (assignment.id.isNotBlank()) return assignment.id
        val roleSegment = if (assignment.appliesToAllRoles) {
            "all"
        } else {
            assignment.specificRoleIds.sorted().joinToString("-")
        }
        val raw = "dma-${tenantId.value}-${assignment.departmentId}-${module.name}-$roleSegment"
        return raw.lowercase().replace("[^a-z0-9-]".toRegex(), "-").take(64)
    }

    private fun toAssignment(row: ResultRow) = DepartmentModuleAssignment(
        departmentId = row[DepartmentModuleAssignmentsTable.departmentId],
        departmentName = row.getOrNull(DepartmentsTable.displayName)
            ?: row[DepartmentModuleAssignmentsTable.departmentId],
        accessLevel = runCatching {
            AccessLevel.valueOf(row[DepartmentModuleAssignmentsTable.accessLevel])
        }.getOrDefault(AccessLevel.NONE),
        scope = runCatching {
            DataScope.valueOf(row[DepartmentModuleAssignmentsTable.dataScope])
        }.getOrDefault(DataScope.ALL_TENANT_DATA),
        specificRoleIds = parseJsonArray(row[DepartmentModuleAssignmentsTable.specificRoleIds]),
        allowedDesks = parseJsonArray(row[DepartmentModuleAssignmentsTable.allowedDesks])
            .takeIf { it.isNotEmpty() },
        id = row[DepartmentModuleAssignmentsTable.id]
    )

    private fun <T> ResultRow.getOrNull(column: Column<T>): T? =
        runCatching { this[column] }.getOrNull()

    private fun toJsonArray(values: Set<String>): String =
        "[${values.sorted().joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"

    private fun parseJsonArray(raw: String?): Set<String> {
        if (raw.isNullOrBlank() || raw == "[]") return emptySet()
        return "\"([^\"]+)\"".toRegex().findAll(raw)
            .map { it.groupValues[1] }
            .toSet()
    }
}
