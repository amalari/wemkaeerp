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

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Implementasi in-memory untuk test dan pengembangan tanpa database, sejajar dengan
 * [InMemoryRoleRepository] dan kerabatnya.
 */
class InMemoryModuleAssignmentRepository : ModuleAssignmentRepository {

    private data class Key(val tenantId: String, val module: BusinessModule, val assignmentKey: String)

    private val storage = mutableMapOf<Key, DepartmentModuleAssignment>()

    override suspend fun findAllByTenant(
        tenantId: TenantId
    ): Map<BusinessModule, List<DepartmentModuleAssignment>> =
        storage.entries
            .filter { it.key.tenantId == tenantId.value }
            .groupBy({ it.key.module }, { it.value })

    override suspend fun upsert(
        tenantId: TenantId,
        module: BusinessModule,
        assignment: DepartmentModuleAssignment
    ): Result<DepartmentModuleAssignment> = runCatching {
        val stored = assignment.copy(
            id = assignment.id.ifBlank { "${module.name}-${assignment.assignmentKey}" }
        )
        storage[Key(tenantId.value, module, stored.id)] = stored
        stored
    }

    override suspend fun remove(
        tenantId: TenantId,
        module: BusinessModule,
        assignmentKey: String
    ): Result<Unit> = runCatching {
        storage.remove(Key(tenantId.value, module, assignmentKey))
        Unit
    }
}
