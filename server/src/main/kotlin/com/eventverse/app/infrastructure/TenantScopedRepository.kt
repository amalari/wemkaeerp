package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId

/**
 * Base contract/decorator ensuring every operational repository query executes
 * strictly within the boundaries of the active TenantContext.
 */
abstract class TenantScopedRepository<T> {

    /**
     * Executes an operation strictly bound to the active tenant.
     * Throws an IllegalStateException if no tenant context is provided.
     */
    protected inline fun <R> withTenantScope(
        context: TenantContext?,
        block: (tenantId: TenantId) -> R
    ): R {
        val activeContext = context 
            ?: error("Tenant security violation: Operation attempted without active TenantContext")
        
        check(activeContext.isAccessible) {
            "Tenant access forbidden: Tenant '${activeContext.slug.value}' is suspended or inaccessible"
        }

        return block(activeContext.tenantId)
    }

    /**
     * Validates that an entity belonging to a specific tenant matches the current context.
     */
    protected fun validateTenantOwnership(entityTenantId: TenantId, activeTenantId: TenantId) {
        if (entityTenantId != activeTenantId) {
            throw SecurityException(
                "Cross-tenant data violation: Active tenant '${activeTenantId.value}' attempted to access data of tenant '${entityTenantId.value}'"
            )
        }
    }
}
