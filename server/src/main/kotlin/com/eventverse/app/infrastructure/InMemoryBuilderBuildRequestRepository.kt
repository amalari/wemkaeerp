package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BuildRequest
import com.eventverse.app.domain.builder.BuilderBuildRequestRepository
import com.eventverse.app.domain.tenant.TenantId

/** Pasangan [BuilderBuildRequestRepository] untuk test ktor. */
class InMemoryBuilderBuildRequestRepository : BuilderBuildRequestRepository {
    val rows = mutableListOf<BuildRequest>()

    override suspend fun findByTenant(tenantId: TenantId) = rows.filter { it.tenantId == tenantId }
    override suspend fun findAll() = rows.toList()
    override suspend fun save(request: BuildRequest): BuildRequest {
        rows.removeAll { it.id == request.id }
        rows.add(request)
        return request
    }
}
