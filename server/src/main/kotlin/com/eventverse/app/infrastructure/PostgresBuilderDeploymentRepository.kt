package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BuilderDeploymentRepository
import com.eventverse.app.domain.builder.Deployment
import com.eventverse.app.domain.builder.DeploymentConflictException
import com.eventverse.app.domain.builder.DeploymentId
import com.eventverse.app.domain.builder.DeploymentNumber
import com.eventverse.app.domain.builder.DeploymentStatus
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DeploymentsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Deployment tenant (tabel V81). Invarian "tepat satu deployment aktif per tenant" ditegakkan **di sini**
 * (bukan hanya di use case) karena `save` adalah satu-satunya pintu masuk baris baru; pelanggarannya
 * [DeploymentConflictException], bukan diam-diam menimpa.
 */
class PostgresBuilderDeploymentRepository : BuilderDeploymentRepository {

    override suspend fun findByTenant(tenantId: TenantId): List<Deployment> = DatabaseFactory.dbQuery {
        DeploymentsTable.selectAll()
            .where { DeploymentsTable.tenantId eq tenantId.value }
            .orderBy(DeploymentsTable.number, order = SortOrder.DESC)
            .map(::toDeployment)
    }

    override suspend fun findActive(tenantId: TenantId): Deployment? = DatabaseFactory.dbQuery {
        DeploymentsTable.selectAll()
            .where { DeploymentsTable.tenantId eq tenantId.value }
            .map(::toDeployment)
            .firstOrNull { it.isActive }
    }

    override suspend fun nextNumber(tenantId: TenantId): DeploymentNumber = DatabaseFactory.dbQuery {
        val highest = DeploymentsTable.selectAll()
            .where { DeploymentsTable.tenantId eq tenantId.value }
            .maxOfOrNull { it[DeploymentsTable.number] } ?: 0
        DeploymentNumber(highest + 1)
    }

    override suspend fun save(deployment: Deployment): Result<Deployment> = runCatching {
        DatabaseFactory.dbQuery {
            if (deployment.isActive) {
                val existingActive = DeploymentsTable.selectAll()
                    .where { DeploymentsTable.tenantId eq deployment.tenantId.value }
                    .map(::toDeployment)
                    .firstOrNull { it.isActive && it.id != deployment.id }
                if (existingActive != null) {
                    throw DeploymentConflictException(
                        "Tenant ${deployment.tenantId.value} sudah ada deployment aktif #${existingActive.number.value}"
                    )
                }
            }
            val updated = DeploymentsTable.update({ DeploymentsTable.id eq deployment.id.value }) {
                it[status] = deployment.status.name
                it[activatedAt] = deployment.activatedAt
            }
            if (updated == 0) {
                DeploymentsTable.insert {
                    it[id] = deployment.id.value
                    it[tenantId] = deployment.tenantId.value
                    it[number] = deployment.number.value
                    it[packCode] = deployment.packCode.value
                    it[packVersion] = deployment.packVersion
                    it[appBuild] = deployment.appBuild
                    it[blueprintRevision] = deployment.blueprintRevision
                    it[status] = deployment.status.name
                    it[draftId] = deployment.draftId
                    it[createdAt] = deployment.createdAt ?: kotlinx.datetime.Clock.System.now()
                    it[activatedAt] = deployment.activatedAt
                }
            }
        }
        deployment
    }

    private fun toDeployment(row: ResultRow) = Deployment(
        id = DeploymentId(row[DeploymentsTable.id]),
        tenantId = TenantId(row[DeploymentsTable.tenantId]),
        number = DeploymentNumber(row[DeploymentsTable.number]),
        packCode = DomainPackCode(row[DeploymentsTable.packCode]),
        packVersion = row[DeploymentsTable.packVersion],
        appBuild = row[DeploymentsTable.appBuild],
        blueprintRevision = row[DeploymentsTable.blueprintRevision],
        status = DeploymentStatus.valueOf(row[DeploymentsTable.status]),
        draftId = row[DeploymentsTable.draftId],
        createdAt = row[DeploymentsTable.createdAt],
        activatedAt = row[DeploymentsTable.activatedAt]
    )
}
