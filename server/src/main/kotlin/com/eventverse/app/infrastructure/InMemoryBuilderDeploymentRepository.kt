package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BuilderDeploymentRepository
import com.eventverse.app.domain.builder.Deployment
import com.eventverse.app.domain.builder.DeploymentConflictException
import com.eventverse.app.domain.builder.DeploymentNumber
import com.eventverse.app.domain.tenant.TenantId

/**
 * Fake repository builder untuk test server (pola `InMemory*Repository`): menegakkan invarian
 * tepat-satu-aktif & riwayat per-tenant tanpa Postgres.
 */
class InMemoryBuilderDeploymentRepository : BuilderDeploymentRepository {

    private val rows = mutableListOf<Deployment>()

    override suspend fun findByTenant(tenantId: TenantId): List<Deployment> =
        rows.filter { it.tenantId == tenantId }.sortedByDescending { it.number.value }

    override suspend fun findActive(tenantId: TenantId): Deployment? =
        findByTenant(tenantId).firstOrNull { it.isActive }

    override suspend fun nextNumber(tenantId: TenantId): DeploymentNumber =
        DeploymentNumber((rows.filter { it.tenantId == tenantId }.maxOfOrNull { it.number.value } ?: 0) + 1)

    override suspend fun save(deployment: Deployment): Result<Deployment> = runCatching {
        if (deployment.isActive) {
            val conflicting = rows.firstOrNull { it.tenantId == deployment.tenantId && it.isActive && it.id != deployment.id }
            if (conflicting != null) {
                throw DeploymentConflictException(
                    "Tenant ${deployment.tenantId.value} sudah ada deployment aktif #${conflicting.number.value}"
                )
            }
        }
        rows.removeAll { it.id == deployment.id }
        rows.add(deployment)
        deployment
    }
}
